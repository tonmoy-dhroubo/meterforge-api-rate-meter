# 04. Core Runtime Flows

This document provides end-to-end technical walkthroughs of the four critical distributed data paths in **MeterForge**.

---

## Flow 1: Control-Plane Mutation & Transactional Outbox

When a user modifies configuration (e.g., creating a product, issuing an API key, updating a plan policy, or changing a subscription), the platform guarantees zero dual-write inconsistency between PostgreSQL and Kafka.

```text
HTTP Request (Staff JWT)
   │
   ▼
AuthController / WorkspaceSecurityService (RBAC check)
   │
   ▼
[ ONE POSTGRESQL TRANSACTION ]
   ├── 1. INSERT / UPDATE target entity (e.g., api_credentials)
   ├── 2. INSERT INTO audit_logs (Summary, metadata, request_id)
   └── 3. INSERT INTO outbox_events (ConfigEventEnvelope snapshot)
   │
   ▼
[ COMMIT TRANSACTION ]
   │
   ▼
HTTP 200 / 201 Response (Raw secret returned once if key creation)
```

### Why this design?
- If Kafka is unreachable when the user creates a key, the database transaction **still succeeds**.
- No partial writes: It is physically impossible for the database row to exist without its corresponding audit record and outbox event.

---

## Flow 2: Configuration Propagation to Redis

The `worker` service reads the outbox and projects fully materialized snapshots into Redis with monotonic version guards.

```text
OutboxPollerService (@Scheduled 500ms)
   │
   ├── SELECT * FROM outbox_events WHERE published_at IS NULL AND attempt_count < 10
   ├── KafkaTemplate.send("meterforge.config.v1", event.eventId, envelope)
   └── UPDATE outbox_events SET published_at = NOW() WHERE event_id = ?
              │
              ▼ (Kafka meterforge.config.v1)
ConfigProjectionConsumer (Worker)
   │
   ├── 1. Read aggregateType, aggregateId, aggregateVersion from envelope
   ├── 2. GET rf:v1:cfg:version:<aggregateType>:<aggregateId> from Redis
   │
   ├── IF aggregateVersion <= currentVersion:
   │      └── IGNORE (Out-of-order or duplicate event; discard safely)
   │
   └── IF aggregateVersion > currentVersion:
          ├── A. Write JSON projection to Redis:
          │      - rf:v1:cfg:credential:<publicId>
          │      - rf:v1:cfg:product:<productId>
          │      - rf:v1:cfg:subscription:<subscriptionId>
          ├── B. Maintain active product set: SADD / SREM rf:v1:cfg:products
          └── C. SET rf:v1:cfg:version:<aggregateType>:<aggregateId> = aggregateVersion
```

---

## Flow 3: Gateway Request Lifecycle & Atomic Lua Limiter

When an external consumer sends traffic through the Gateway (`http://localhost:8890`), the reactive WebFlux pipeline executes without making any SQL database calls.

```text
Consumer HTTP Request (e.g., GET /v1/forecast/london with X-API-Key)
   │
   ▼
GatewayProxyFilter (Global WebFlux Filter)
   │
   ├── 1. Sanitize or generate X-Request-ID
   │
   ├── 2. ProductRouteMatcher:
   │      - Reads active product IDs from Redis Set (cached in Caffeine L1 for 5s)
   │      - Matches gateway_base_path (e.g., /v1/forecast)
   │      - Sorts matching routes by specificity: Static > Variable {city} > Wildcard ** > Length > Priority
   │
   ├── 3. ApiKeyAuthenticator:
   │      - Extracts publicId (nsdemo123456) from mf_dev_nsdemo123456_...
   │      - Loads CredentialProjection from rf:v1:cfg:credential:<publicId>
   │      - Computes HMAC-SHA-256(serverPepper, fullKey)
   │      - Performs constant-time comparison via MessageDigest.isEqual(expectedHmac, computedHmac)
   │      - Validates credential status (ACTIVE), expiration, and environment
   │
   ├── 4. SubscriptionResolver:
   │      - Loads SubscriptionProjection from rf:v1:cfg:subscription:<subscriptionId>
   │      - Collects all applicable Plan & Route policies (Token Buckets + Quotas)
   │
   ├── 5. LuaRateLimiter (ONE atomic Redis call to rate_limiter.lua):
   │      - Keys: rate & quota Redis keys
   │      - Args: current Redis TIME, costUnits, capacities, refill rates, quota limits, window IDs
   │      - Algorithm:
   │          a. Load token buckets and refill based on (currentTime - lastRefillTime)
   │          b. Check all token bucket capacities >= costUnits
   │          c. Check all fixed-window quota allowances >= costUnits
   │          d. IF ANY POLICY FAILS: Return denied, mutate NOTHING.
   │          e. IF ALL POLICIES PASS: Deduct tokens, increment quota counters, set TTLs, return allowed.
   │
   ├── [BRANCH A: RATE LIMITED (Allowed = false)]
   │      ├── Return HTTP 429 Too Many Requests (with X-RateLimit-Retry-After headers)
   │      └── Publish UsageRecordedV1 (decision=RATE_LIMITED, units=0, outcome=NOT_FORWARDED)
   │
   ├── [BRANCH B: REDIS FAILURE / TIMEOUT]
   │      ├── Return HTTP 503 Service Unavailable (LIMITER_UNAVAILABLE problem JSON)
   │      └── Publish UsageRecordedV1 (decision=BLOCKED, outcome=UNAVAILABLE)
   │
   └── [BRANCH C: ALLOWED (Allowed = true)]
          ├── Strip X-API-Key & hop-by-hop headers
          ├── Forward request body stream to Upstream Base URL (WireMock / Target API)
          ├── Stream upstream HTTP response back to consumer
          └── Publish UsageRecordedV1 (decision=ALLOWED, units=costUnits, outcome=SUCCESS/ERROR, latencyMs)
```

---

## Flow 4: Usage Telemetry Ingestion & Idempotent Aggregation

Every Gateway decision emits an event to Kafka topic `meterforge.usage.v1`. The `worker` consumes these events and updates PostgreSQL aggregations idempotently.

```text
Kafka meterforge.usage.v1
   │
   ▼
UsageIngestionConsumer (Worker batch listener)
   │
   ▼
UsageIngestionService (Spring JdbcClient)
   │
   ▼
[ ONE POSTGRESQL TRANSACTION ]
   │
   ├── Step 1: Raw Event Deduplication
   │   INSERT INTO meterforge.usage_events (
   │       event_id, occurred_at, workspace_id, product_id, route_id,
   │       consumer_id, application_id, credential_id, subscription_id,
   │       request_id, http_method, route_template, decision, outcome,
   │       status_code, usage_units, latency_ms, limiting_policy_id, gateway_instance_id
   │   ) VALUES (...)
   │   ON CONFLICT (event_id) DO NOTHING;
   │
   └── Step 2: Atomic Rollups (Only executed if Step 1 inserted rows > 0)
       ├── Hourly Rollup:
       │   INSERT INTO meterforge.usage_hourly (
       │       bucket_start, workspace_id, product_id, route_id, consumer_id,
       │       application_id, subscription_id, decision, status_class,
       │       total_requests, total_units, total_latency_ms
       │   ) VALUES (date_trunc('hour', occurred_at), ..., 1, usage_units, latency_ms)
       │   ON CONFLICT (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class)
       │   DO UPDATE SET
       │       total_requests = usage_hourly.total_requests + 1,
       │       total_units = usage_hourly.total_units + EXCLUDED.total_units,
       │       total_latency_ms = usage_hourly.total_latency_ms + EXCLUDED.total_latency_ms;
       │
       └── Daily Rollup:
           INSERT INTO meterforge.usage_daily (...)
           ON CONFLICT (...)
           DO UPDATE SET
               total_requests = usage_daily.total_requests + 1,
               total_units = usage_daily.total_units + EXCLUDED.total_units,
               total_latency_ms = usage_daily.total_latency_ms + EXCLUDED.total_latency_ms;
   │
   ▼
[ COMMIT TRANSACTION ]
   │
   ▼
Acknowledge Kafka Consumer Offset
```

### Why this design?
- **Crash Resilience**: If the worker crashes before acknowledging Kafka, Kafka redelivers the batch. The `ON CONFLICT (event_id) DO NOTHING` clause returns `0 rows affected`, which skips Step 2 and **prevents double-counting**.

---

Next, proceed to **[05. Developer Guide & Cheatsheet](./05_developer_guide_and_cheatsheet.md)** to learn about local testing, debugging, and development commands.
