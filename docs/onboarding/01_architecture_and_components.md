# 01. Architecture and Components

This document outlines the distributed architecture of **MeterForge**, detailing each deployable component, its technical responsibilities, strict non-responsibilities, and the core architectural invariants.

---

## 1. High-Level System Topology

```text
Browser -> Next.js Web UI -> Control Plane -> PostgreSQL (Source of Truth)
                                    │
                              outbox_events
                                    ▼
                                  Kafka (meterforge.config.v1)
                                    │
                                    ▼
                          Worker Service -> Redis Config Projections
                                    │
                                    ▼ (meterforge.usage.v1)
Consumer -> Reactive Gateway -> Redis Lua -> Upstream WireMock / Target API
                  │
                  └─── Usage Recorded Event (Kafka)
                                    │
                                    ▼
                          Worker Ingestion -> PostgreSQL (usage_events & rollups)
```

---

## 2. Deployables & Boundary Responsibilities

The codebase is organized into four independent deployables and one shared contract library:

```text
meterforge/
├── backend/
│   ├── contracts/        # Shared DTOs, Event Envelopes, and Projections
│   ├── control-plane/    # REST API, JWT Auth, JPA Persistence, Outbox Writer
│   ├── gateway/          # Pure WebFlux Reactive Proxy & Atomic Lua Limiter
│   └── worker/           # Outbox Poller, Redis Projector, Kafka Telemetry Ingestor
└── frontend/
    └── web/              # Next.js 16 App Router Operations UI & Request Lab
```

### Component Responsibility Matrix

| Component | Primary Responsibilities | MUST NOT Do |
|---|---|---|
| **`control-plane`** | - JWT staff authentication & cookie sessions.<br>- Multi-tenant workspace RBAC (`OWNER`, `MEMBER`, `VIEWER`).<br>- Resource CRUD (Products, Routes, Consumers, Apps, Keys, Plans, Subscriptions).<br>- Transactional outbox event writing.<br>- Usage analytics query APIs (`/usage/summary`, `/usage/timeseries`, etc.). | - Proxy consumer traffic.<br>- Execute rate limiting algorithms.<br>- Write directly to Redis live counters. |
| **`gateway`** | - Reactive HTTP reverse proxy (WebFlux / Netty).<br>- Constant-time API Key HMAC-SHA256 authentication.<br>- Redis configuration projection cache lookups.<br>- Route specificity pattern matching.<br>- Atomic multi-policy Rate & Quota evaluation via Redis Lua.<br>- Non-blocking telemetry emission to Kafka `meterforge.usage.v1`. | - **Query PostgreSQL** (No JDBC drivers or database credentials).<br>- Block on I/O operations.<br>- Make Java read-modify-write rate limit calculations. |
| **`worker`** | - Poll `outbox_events` and publish to Kafka `meterforge.config.v1`.<br>- Project configuration snapshots to Redis with version fencing (`rf:v1:cfg:...`).<br>- Consume `meterforge.usage.v1` events from Kafka.<br>- Idempotent batch insertion to `usage_events`.<br>- Atomic UPSERT rollups into `usage_hourly` and `usage_daily`. | - Serve user-facing HTTP CRUD APIs.<br>- Proxy consumer traffic. |
| **`web`** | - Next.js Operations UI for managing workspace catalog.<br>- One-time raw API key display dialog.<br>- Interactive concurrency burst Request Lab.<br>- Telemetry analytics visualizer. | - Connect directly to PostgreSQL, Redis, or Kafka.<br>- Duplicate backend business rules. |
| **`contracts`** | - Shared JVM library containing event payloads (`ConfigEventEnvelope`, `UsageRecordedV1`), projection models (`ProductProjection`, `CredentialProjection`, `SubscriptionProjection`), and shared enums. | - Contain Spring beans or persistence dependencies. |

---

## 3. Infrastructure Backbone

### 1. PostgreSQL 17 (Source of Truth)
- **Role**: Authoritative relational data store for all configuration entities and historical telemetry.
- **Port**: `5432`
- **Schema**: `meterforge` (managed exclusively through Flyway migrations owned by `control-plane`).
- **Connection Pool**: HikariCP.

### 2. Redis 7 (Data Plane Cache & Live Limiter)
- **Role**: Ultra-low-latency in-memory cache and atomic counter engine.
- **Port**: `6379`
- **Persistence**: Append-Only File (`AOF`) enabled (`appendonly yes`).
- **Data Categories**:
  1. *Rebuildable Configuration Projections*: `rf:v1:cfg:credential:<publicId>`, `rf:v1:cfg:product:<productId>`, `rf:v1:cfg:subscription:<subscriptionId>`.
  2. *Live State Counters*: `rf:v1:rate:{subscriptionId}:policyId` (Token Bucket), `rf:v1:quota:{subscriptionId}:policyId:YYYYMMDD` (Fixed Window).

### 3. Apache Kafka 3.9 (KRaft Mode - ZooKeeperless)
- **Role**: Distributed, durable event streaming backbone.
- **Port**: `9092`
- **Topics**:
  - `meterforge.config.v1`: Configuration changes emitted from the transactional outbox.
  - `meterforge.usage.v1`: High-throughput telemetry events emitted by Gateway instances.

### 4. WireMock (Upstream Mock Service)
- **Role**: Simulates external API upstream targets (`/v1/forecast/{city}`) with customizable delays, headers, and payload responses for integration testing and local demos.
- **Port**: `9990` (mapped to internal `8080`).

---

## 4. Key Architectural Invariants

Every engineer working on MeterForge must adhere to these 10 core architectural invariants:

1. **Gateway Never Touches PostgreSQL**: The gateway operates purely on in-memory Redis projections and Kafka publishing. It has no SQL connection pool or database credentials.
2. **Atomic Rate Decisions in Redis Lua**: Token buckets and daily/monthly quotas for a request are evaluated in **one atomic Lua script invocation** using Redis server `TIME`. There are zero Java read-modify-write races.
3. **All-or-Nothing Limit Enforcement**: If any applicable policy denies the request, **no policy counters are mutated/consumed**, and an HTTP 429 is returned immediately.
4. **Availability-First Gateway Telemetry**: Gateway proxies traffic even during transient Kafka degradation using bounded in-memory publisher buffers. Failed telemetry logs safe context without crashing the proxy.
5. **Idempotent Usage Ingestion**: Kafka delivery is at-least-once. The worker executes `INSERT INTO usage_events ... ON CONFLICT (event_id) DO NOTHING`. Hourly and daily aggregates are incremented **only** when the raw event insert succeeds.
6. **No Raw API Keys Stored**: Raw API keys (`mf_<env>_<publicId>_<secret>`) are generated via 256-bit `SecureRandom`, shown strictly once, and hashed using HMAC-SHA-256 with a server-side pepper before storage.
7. **Transactional Outbox Pattern**: Entity mutations, audit logs, and versioned `ConfigEventEnvelope` rows are committed in the **exact same PostgreSQL transaction**.
8. **Monotonic Version Fencing in Redis**: Config events carry an `aggregateVersion`. Older or duplicate events are rejected if Redis already holds a newer or equal version (`rf:v1:cfg:version:<type>:<id>`).
9. **Strict Workspace Isolation**: Every tenant query is scoped by `workspace_id` verified server-side via the authenticated user's membership. Client-supplied headers are never trusted for authorization.
10. **Zero Secrets in Logs or Events**: Never log or emit raw API keys, HMAC verifiers, JWT tokens, cookies, auth headers, or request/response payloads.

---

Next, proceed to **[02. Database Schema & Migrations](./02_database_schema_and_migrations.md)** to learn about the relational data model.
