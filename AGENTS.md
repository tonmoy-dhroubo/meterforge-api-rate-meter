# MeterForge — API Rate Limiting and Metering Platform (Portfolio Edition)

This file is the authoritative implementation blueprint for this repository.

MeterForge is a **solo resume/portfolio project**. It runs locally through Docker Compose and is intentionally scoped to prove a few hard distributed-systems concepts well without becoming a commercial API-management platform.

**For now, keep architecture, schema, HTTP contracts, event contracts, milestones, deferred scope, and progress in this single `AGENTS.md`. Do not create additional planning/design Markdown files unless the user explicitly asks to split them later.**

If a request meaningfully expands scope — new backend service, new infrastructure dependency, new policy family, billing, external identity, full observability stack, etc. — stop and explain the scope impact before implementing it.

---

## 1. Instruction precedence

1. The user's current explicit request.
2. This `AGENTS.md`.
3. Existing working code and tests.
4. Official framework documentation.

If working code conflicts with this blueprint, explain the conflict and prefer the smallest safe migration over a broad rewrite.

---

## 2. How the coding agent must work

### Before changing code

1. Read this file completely.
2. Read **§17 Current Project Status**.
3. Inspect the working tree and preserve unrelated user changes.
4. Identify the smallest vertical slice that satisfies the request.
5. State the modules/files and tests expected to change.
6. Check official docs before guessing unfamiliar Spring/Kafka/Redis/Next.js APIs.
7. If persistence, HTTP contracts, Kafka contracts, Redis keys, or security semantics change, update the corresponding section of this file in the same change.

### During implementation

- One milestone or coherent vertical slice at a time.
- Backend behavior before dependent UI.
- Tests land with the behavior they cover.
- Prefer a small complete implementation over broad scaffolding.
- Do not add dependencies without a real need.
- Do not perform unrelated framework upgrades.
- Do not create abstractions merely to reduce line count.
- Do not commit/push/publish/deploy unless explicitly asked.

### Before declaring work complete

1. Run targeted tests.
2. Run the affected module's full test suite.
3. Run formatter/linter/type checker.
4. Run Testcontainers tests when PostgreSQL, Redis, Kafka, HTTP integration, or security behavior changed.
5. Update **§17 Current Project Status** truthfully.
6. Report what changed, what was verified, and what remains.

### Code style

Write deliberate production-style code, not tutorial code.

- No banner comments or comments that restate the next line.
- No Javadoc on everything.
- Comments explain non-obvious invariants/trade-offs/races only.
- No dangling `TODO`; put deferred items in §16.
- No `System.out` / `printStackTrace` in production code.
- Avoid vague names such as `CommonService`, `BaseService`, `Manager`, `Helper`, `Utils` unless the responsibility is genuinely precise.

---

## 3. Product definition

MeterForge lets an API provider:

1. Create a workspace.
2. Register an API product and routes.
3. Create consumers and their applications.
4. Issue API keys.
5. Create reusable plans with rate limits and quotas.
6. Assign a plan to an application through a subscription.
7. Proxy consumer traffic through a gateway.
8. Enforce limits atomically in Redis.
9. Publish usage events through Kafka.
10. Persist/aggregate usage in PostgreSQL.
11. Inspect and demo behavior through a small Next.js UI.

### Roles

| Role | Capabilities |
| --- | --- |
| `OWNER` | Everything in the workspace |
| `MEMBER` | Manage products, routes, consumers, applications, keys, plans, subscriptions |
| `VIEWER` | Read-only configuration, usage, and audit |

### Vocabulary

Use these names consistently in code, DB, API, UI, and tests:

- **Workspace**: one API-provider tenant.
- **Product**: one protected logical API.
- **Route**: one method/path inside a product.
- **Consumer**: an external customer/team using the API.
- **Application**: one software application belonging to a consumer.
- **Credential**: an API key belonging to an application.
- **Plan**: a reusable policy set for one product.
- **Subscription**: assignment of one product plan to one application.
- **Rate policy**: short-term token-bucket rule.
- **Quota policy**: UTC day/month allowance.
- **Usage unit**: integer request cost; route default is 1.

Do not alternate between tenant/workspace, client/consumer, app/application, package/plan, etc.

---

## 4. Scope

### Goals

1. `docker compose up --build` starts the local demo stack.
2. Reviewer can create product → route → plan → consumer → application → credential → subscription through the UI.
3. A request burst visibly produces allowed responses followed by 429s.
4. Limiter is mathematically correct under concurrency and across multiple gateway instances.
5. Duplicate Kafka delivery never double-counts durable usage.
6. Workspace A cannot access Workspace B's data.
7. Raw API keys are never persisted.
8. Real automated tests prove the hard parts.
9. README eventually contains measured evidence rather than generic scalability claims.

### Explicit non-goals

Do not build unless deliberately added later:

- Keycloak/external IdP.
- OIDC provider implementation.
- Grafana/Tempo/full OpenTelemetry stack.
- Alerting system.
- DLQ browser/replay UI.
- Billing/invoicing/payments.
- GraphQL cost analysis.
- Multi-region consistency.
- Kubernetes/Helm/Terraform.
- Redis Cluster deployment automation.
- Request/response body inspection/logging.
- Playwright/Selenium/Cypress.
- AI features added only for portfolio buzzwords.
- Separate microservice per domain entity.

### Seeded demo scenario

Seed:

- Workspace: `Acme APIs`
- Product: `Weather API`
- Route: `GET /v1/forecast/{city}`
- Free plan:
  - 5-unit token bucket, refill 5 every 10 seconds
  - 100 units/day UTC quota
- Consumer: `Northstar Labs`
- Application: `Northstar Demo App`
- Active credential and subscription

Reviewer flow:

1. Sign in as seeded owner.
2. Open Weather API.
3. Copy/create API key.
4. Open Request Lab.
5. Fire 10 requests concurrently against a fresh bucket.
6. See **exactly 5 allowed + 5 HTTP 429**.
7. Wait for refill and see requests become available again.
8. See allowed and blocked counts in Usage.
9. Revoke the key.
10. Next request becomes 401 after config propagation.

**Milestone 3 completing this flow is the real finish line.**

---

## 5. Architectural invariants

1. PostgreSQL is source of truth for durable configuration and usage analytics.
2. Redis stores:
   - rebuildable gateway config projections;
   - authoritative live rate/quota counters.
3. Config projections are rebuildable; counters are not perfectly reconstructable.
4. Local Redis uses AOF persistence. Counter loss may reset allowances and must be documented honestly.
5. Kafka is at-least-once; consumers are idempotent.
6. Gateway never queries PostgreSQL and receives no DB credentials.
7. Gateway is reactive WebFlux end to end: no JPA/JDBC/blocking DB calls.
8. Control plane uses Spring MVC + JPA.
9. Worker may use JDBC/JdbcClient for high-volume usage writes.
10. **Only control-plane owns/runs Flyway migrations.**
11. Every tenant-owned row contains `workspace_id` unless explicitly global.
12. Every tenant query is server-side workspace-scoped; never trust a client workspace header for authorization.
13. API-key raw secrets are shown once and never persisted.
14. Multiple credentials for one application share one subscription's counters.
15. All policies for one request pass/fail together.
16. One Redis Lua invocation evaluates all applicable policies.
17. Never implement Java read → modify → write rate decisions.
18. Redis `TIME` is the limiter clock.
19. If any policy denies, no applicable policy is consumed.
20. **Every gateway decision should emit a usage event when Kafka publishing is available**, including `ALLOWED`, `RATE_LIMITED`, and `UNAUTHORIZED`.
21. Rejected requests use `usageUnits = 0`; only admitted requests consume rate/quota units.
22. Usage ingestion is idempotent by `eventId`.
23. Config events are versioned; older/equal versions never overwrite newer Redis projection state.
24. Never log API keys, HMACs, JWTs, cookies, authorization headers, request bodies, or response bodies.
25. All timestamps are UTC (`Instant` / `timestamptz`).
26. Flyway migrations are append-only.
27. Do not claim end-to-end exactly-once metering. Durable aggregation is effectively-once over at-least-once Kafka delivery.

---

## 6. Technology baseline

| Area | Choice |
| --- | --- |
| Backend | Java LTS + current stable Spring Boot |
| Gateway | Spring Cloud Gateway Server WebFlux |
| DB | PostgreSQL |
| Control persistence | Spring Data JPA |
| Usage persistence | JDBC/JdbcClient where appropriate |
| Migrations | Flyway |
| Rate/quota store | Redis |
| Events | Kafka KRaft, single broker locally |
| Staff auth | Control-plane-issued JWT + seeded local users |
| Frontend | Next.js App Router + TypeScript |
| UI | Tailwind + shadcn/ui |
| Server state | TanStack Query |
| Forms | React Hook Form + Zod |
| Backend tests | JUnit 5, AssertJ, Mockito, Testcontainers, Awaitility |
| Frontend tests | Vitest + React Testing Library |
| Observability | Structured logs + Micrometer `/actuator/prometheus` |

### Version rule

During M0:

1. Resolve a compatible **stable Spring Boot + Spring Cloud** pair using official compatibility docs.
2. Pin exact Maven/plugin/container/Node/pnpm versions.
3. Do not use RC/milestone/beta/snapshot releases merely because they are newer.
4. After M0, do not perform major/minor framework upgrades during unrelated features.

---

## 7. Architecture and repository

### Deployables

| Component | Responsibilities | Must not do |
| --- | --- | --- |
| `control-plane` | JWT auth, workspace RBAC, config CRUD, audit, transactional outbox, usage query API | Proxy consumer traffic / make limiter decisions |
| `gateway` | Routing, API-key auth, Redis config lookup, Lua limiter, proxying, usage publishing | Query PostgreSQL / run Flyway / block on JDBC/JPA |
| `worker` | Publish outbox, project config to Redis, consume usage idempotently, aggregate | Serve main UI API / proxy traffic |
| `web` | Next.js operations UI | Duplicate domain logic / access DB/Redis/Kafka directly |

`web` is a frontend, not another backend microservice.

### Topology

```text
Browser -> web -> control-plane -> PostgreSQL
                        |
                      outbox
                        v
                      Kafka -> worker -> Redis config
                        ^         |
                        |         +-> PostgreSQL usage/aggregates
                        |
Consumer -> gateway -> Redis Lua -> Upstream
               |
               +------ usage events ------>
```

### Repository layout

```text
meterforge/
├── AGENTS.md
├── README.md                 # later; not a second design authority
├── compose.yaml
├── .env.example
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/
├── backend/
│   ├── contracts/
│   ├── control-plane/
│   ├── gateway/
│   └── worker/
├── frontend/
│   └── web/
├── infra/
│   └── wiremock/
├── scripts/
└── .github/workflows/
```

Do not create empty directories merely to match the tree.

### Java organization

Package by feature, then responsibility, e.g.:

```text
io.meterforge.controlplane.product/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Suggested control-plane features:

```text
identity, workspace, product, route, consumer, application,
credential, plan, subscription, usage, audit, outbox, common
```

Gateway:

```text
routing, credential, ratelimit, filter, metering, error, config, observability
```

Worker:

```text
outbox, configprojection, usageingestion, aggregation, observability
```

---

## 8. Persistence schema blueprint

This is the schema authority until migrations exist. Keep migrations and this section aligned.

### Conventions

- PostgreSQL schema: `meterforge`.
- DB names `snake_case`; API JSON `camelCase`.
- UUID primary IDs.
- UTC `timestamptz`.
- Mutable config rows: `created_at`, `updated_at`, `version bigint`.
- Tenant-owned tables include/index `workspace_id`.
- Explicit FK/unique/check/index constraints.
- No `ddl-auto=update`; JPA schema validation only.
- JSONB only for bounded audit/event metadata, not normal relational fields.

### Core tables

| Table | Important columns / rules |
| --- | --- |
| `users` | `id`, unique `email`, `password_hash`, `status`, timestamps |
| `workspaces` | `id`, `name`, unique `slug`, `status`, timestamps, `version` |
| `workspace_members` | `(workspace_id,user_id)` PK, `role`, `status`; at least one active OWNER |
| `api_products` | `workspace_id`, `name`, `slug`, `upstream_base_url`, `gateway_base_path`, `status`, `version`; unique workspace+slug/base path |
| `api_routes` | `workspace_id`, `product_id`, `http_method`, `path_pattern`, optional `upstream_path`, `cost_units`, `priority`, `status`, `version`; unique product+method+path |
| `consumers` | `workspace_id`, `name`, optional `external_reference`, `status`, `version` |
| `consumer_applications` | `workspace_id`, `consumer_id`, `name`, `status`, `version`; unique consumer+name |
| `api_credentials` | `workspace_id`, `application_id`, `public_id`, `secret_hmac`, safe display fields, environment, status/expiry, `version`; **never raw secret** |
| `plans` | `workspace_id`, `product_id`, `name`, `slug`, `status`, `version`; unique product+slug |
| `limit_policies` | `workspace_id`, `plan_id`, optional `route_id`, `kind`, algorithm-specific fields, `enabled`, `version`; check RATE vs QUOTA fields |
| `subscriptions` | `workspace_id`, `application_id`, `product_id`, `plan_id`, status/effective dates, `version`; one current active subscription per application+product |
| `audit_logs` | append-only safe action/resource/request-id/summary fields; no secrets |
| `outbox_events` | event/aggregate IDs, aggregate version, type, schema version, payload, occurred/published/attempt fields |
| `usage_events` | `event_id` unique/PK, decision/outcome/status/usage dimensions; no sensitive request content |
| `usage_hourly` | bucket + bounded dimensions + counts/units/latency sums; atomic UPSERT increments |
| `usage_daily` | daily equivalent of hourly aggregates |

### Route semantics

Core supported patterns only:

```text
/v1/forecast
/v1/forecast/{city}
/v1/files/**
```

No arbitrary regex.

Precedence:

1. static beats variable;
2. variable beats terminal wildcard;
3. more specific path beats less specific;
4. `priority` breaks remaining ties;
5. ambiguous routes are rejected at configuration time.

### Policy fields

For `RATE / TOKEN_BUCKET`:

```text
capacity
refill_tokens
refill_period_seconds
```

For `QUOTA / FIXED_WINDOW`:

```text
quota_limit
quota_period = DAY | MONTH
```

`route_id = null` means product-wide. Route policies add constraints rather than replacing product policies.

### Usage event persistence

Store only bounded/safe fields such as:

```text
event_id, occurred_at, received_at,
workspace_id, product_id, route_id,
consumer_id, application_id, credential_id, subscription_id,
request_id, http_method, route_template,
decision, outcome, status_code, usage_units,
latency_ms, limiting_policy_id
```

Never persist:

```text
raw API key / JWT / cookies / headers / request body / response body /
raw IP / arbitrary query string / arbitrary unbounded path
```

---

## 9. Authentication, credential, Redis and Kafka contracts

### Staff auth

- Seed local development users in PostgreSQL.
- `POST /api/v1/auth/login` verifies email/password and issues a signed JWT.
- Browser session/token is stored in a **Secure, HttpOnly cookie** (`Secure` outside local HTTP).
- Never store staff JWT in `localStorage` or session storage.
- Workspace membership/role is resolved server-side from PostgreSQL.
- No refresh-token subsystem required in the portfolio release.

### API-key format

```text
mf_<environment>_<publicId>_<secret>
```

Persist:

```text
publicId
safe display prefix / last four
HMAC-SHA-256(serverPepper, canonicalFullKey)
status / expiry / application reference
```

Rules:

- Generate at least 256 bits of secret entropy with `SecureRandom`.
- Pepper comes from environment config, never DB.
- `publicId` performs lookup.
- Gateway recomputes HMAC and uses constant-time comparison.
- Do not use deliberately slow password hashes on the API-key hot path.
- Raw key is returned exactly once on create/rotate.
- Existing secret cannot be revealed; rotate instead.
- Rotation creates a new credential; old/new keys share subscription counters.

### Redis keys

```text
rf:v1:cfg:credential:<publicId>
rf:v1:cfg:product:<productId>
rf:v1:cfg:subscription:<subscriptionId>
rf:v1:cfg:version:<aggregateType>:<aggregateId>
rf:v1:rate:{<subscriptionId>}:<policyId>
rf:v1:quota:{<subscriptionId>}:<policyId>:<windowId>
```

Do not put raw secrets, emails, request IDs, consumer names, etc. in keys.

Gateway may have a small bounded Caffeine L1 cache for configuration only, with short TTL + best-effort invalidation. Live counters are never cached locally.

### Lua limiter

One script evaluates all applicable rate + quota policies.

Algorithm:

1. get Redis server `TIME`;
2. load/refill token buckets;
3. determine UTC quota windows;
4. evaluate all policies **without mutation**;
5. if any fail: return denial, mutate none;
6. if all pass: deduct/increment all counters atomically;
7. apply TTLs;
8. return typed decision.

Use scaled integer arithmetic if fractional refill is needed; avoid uncontrolled floating-point drift.

Quota windows:

- DAY = UTC midnight → next UTC midnight.
- MONTH = UTC month start → next month start.
- exact boundary belongs to the new window.

Typed decision contains at least:

```text
allowed
remaining
retryAfterSeconds
resetAfterSeconds
limitingPolicyId   # internal only
```

If Redis decision is unavailable/ambiguous, return 503 and do not proxy. Never blindly retry an ambiguous limiter call.

### Config event envelope

Configuration changes go through the transactional outbox and `meterforge.config.v1`.

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "occurredAt": "UTC timestamp",
  "workspaceId": "uuid",
  "aggregateType": "...",
  "aggregateId": "uuid",
  "aggregateVersion": 12,
  "payload": {}
}
```

Suggested event types:

```text
ProductConfigurationChangedV1
RouteConfigurationChangedV1
CredentialConfigurationChangedV1
PlanConfigurationChangedV1
SubscriptionConfigurationChangedV1
```

Prefer complete bounded projection snapshots over fragile partial patches.

Credential events may carry the HMAC verifier internally because gateway needs it; never log/display it.

Worker applies projection only when `aggregateVersion` is newer.

### Usage event: `UsageRecordedV1`

Topic: `meterforge.usage.v1`

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "occurredAt": "2026-08-17T00:00:00Z",
  "requestId": "uuid-or-ulid",
  "workspaceId": "uuid-or-null",
  "productId": "uuid-or-null",
  "routeId": "uuid-or-null",
  "consumerId": "uuid-or-null",
  "consumerApplicationId": "uuid-or-null",
  "credentialId": "uuid-or-null",
  "subscriptionId": "uuid-or-null",
  "method": "GET",
  "routeTemplate": "/v1/forecast/{city}",
  "decision": "ALLOWED",
  "outcome": "SUCCESS",
  "statusCode": 200,
  "usageUnits": 1,
  "latencyMs": 23,
  "limitingPolicyId": null,
  "gatewayInstanceId": "gateway-1"
}
```

Rate-limited:

```text
decision = RATE_LIMITED
statusCode = 429
usageUnits = 0
outcome = NOT_FORWARDED
```

Unauthorized:

```text
decision = UNAUTHORIZED
statusCode = 401
usageUnits = 0
outcome = NOT_FORWARDED
```

No key/token/header/body/query-string/raw-IP data in events.

### Usage idempotency

Worker transaction:

1. `INSERT usage_events ... ON CONFLICT(event_id) DO NOTHING`.
2. Only when insert succeeds, UPSERT/increment hourly + daily aggregates.
3. Commit DB transaction.
4. Ack Kafka only after commit.

Therefore redelivery does not double-count.

### Kafka outage behavior

Use Kafka producer's own bounded buffering/retry configuration. **Do not build a custom in-memory queue subsystem.**

Availability-first policy:

- If Redis decision succeeds, gateway may continue serving during temporary Kafka outage.
- Producer buffer/retries/delivery timeout must be bounded.
- Final publish failure increments metrics and logs safe context.
- A process crash during prolonged Kafka outage can lose usage events.
- Document this limitation; do not claim financially exact billing.

---

## 10. Runtime flows and failures

### Control-plane mutation

```text
authenticate
-> resolve workspace membership/role
-> validate
-> one PostgreSQL transaction:
     mutate resource
     + audit row
     + versioned outbox row
-> commit
-> return
```

Never call Kafka/Redis inside the DB transaction.

### Gateway request

```text
request
-> validate/generate request ID
-> match workspace/product/route
-> parse API-key publicId
-> load Redis config projection
-> constant-time HMAC verify
-> validate credential/app/consumer/subscription/product/route status
-> collect all applicable policies
-> ONE Lua decision
   -> denied: 429 + RATE_LIMITED event (0 units)
   -> allowed: proxy upstream + ALLOWED event
```

Unauthorized attempts should publish `UNAUTHORIZED` events when practical, without retaining key material.

### Proxy rules

- Strip `X-API-Key` before upstream.
- Strip hop-by-hop headers.
- Caller cannot choose arbitrary upstream host.
- Preserve safe query params.
- Stream bodies; do not aggregate arbitrary payloads in memory.
- Explicit connect/response timeouts.
- Upstream timeout → 504.
- Connection failure → 502.
- Once admitted/forwarded, request consumes units even if upstream returns 4xx/5xx/timeout.

### Failure table

| Failure | Required behavior |
| --- | --- |
| PostgreSQL down | control-plane 503; gateway continues from Redis |
| Redis down before decision | gateway 503; never false 429/fail-open |
| Redis ambiguous timeout | 503; no blind retry; do not proxy |
| Kafka down | availability-first; bounded producer semantics; possible loss on crash documented |
| Worker down | gateway continues; Kafka retains events; worker catches up later |
| Unknown/revoked/expired key | generic 401 |
| Missing Redis projection | never query PG; return safe unavailable/unauthorized result based on stage + metric |
| Duplicate usage event | no additional aggregate increment |
| Duplicate/old config event | ignored if version not newer |
| Upstream timeout | 504 + admitted usage timeout outcome |
| Upstream unavailable | 502 + admitted usage unavailable outcome |

---

## 11. HTTP API blueprint

Base control-plane path: `/api/v1`.

Errors use safe `application/problem+json`-style responses with:

```text
title, status, detail, code, requestId, optional fieldErrors
```

Never expose stack traces, SQL, Redis keys, internal class names, HMACs, or tokens.

### Auth

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/me
```

### Workspace/member

```text
GET/POST /api/v1/workspaces
GET/PATCH /api/v1/workspaces/{workspaceId}
GET/POST  /api/v1/workspaces/{workspaceId}/members
PATCH     /api/v1/workspaces/{workspaceId}/members/{userId}
```


### Products/routes

```text
GET/POST  /api/v1/workspaces/{workspaceId}/products
GET/PATCH /api/v1/workspaces/{workspaceId}/products/{productId}
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/activate
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/disable
GET/POST  /api/v1/workspaces/{workspaceId}/products/{productId}/routes
PATCH     /api/v1/workspaces/{workspaceId}/products/{productId}/routes/{routeId}
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/routes/{routeId}/disable
```

### Consumers/applications/credentials

```text
GET/POST  /api/v1/workspaces/{workspaceId}/consumers
GET/PATCH /api/v1/workspaces/{workspaceId}/consumers/{consumerId}
GET/POST  /api/v1/workspaces/{workspaceId}/consumers/{consumerId}/applications
GET/PATCH /api/v1/workspaces/{workspaceId}/applications/{applicationId}
GET/POST  /api/v1/workspaces/{workspaceId}/applications/{applicationId}/credentials
POST      /api/v1/workspaces/{workspaceId}/applications/{applicationId}/credentials/{credentialId}/rotate
POST      /api/v1/workspaces/{workspaceId}/applications/{applicationId}/credentials/{credentialId}/revoke
```

Only credential create/rotate returns the one-time raw key.

### Plans/policies

```text
GET/POST  /api/v1/workspaces/{workspaceId}/products/{productId}/plans
GET/PATCH /api/v1/workspaces/{workspaceId}/products/{productId}/plans/{planId}
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/plans/{planId}/activate
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/plans/{planId}/retire
POST      /api/v1/workspaces/{workspaceId}/products/{productId}/plans/{planId}/policies
PATCH/DELETE /api/v1/workspaces/{workspaceId}/products/{productId}/plans/{planId}/policies/{policyId}
```

### Subscriptions

```text
GET/POST  /api/v1/workspaces/{workspaceId}/applications/{applicationId}/subscriptions
GET/PATCH /api/v1/workspaces/{workspaceId}/subscriptions/{subscriptionId}
POST      /api/v1/workspaces/{workspaceId}/subscriptions/{subscriptionId}/suspend
POST      /api/v1/workspaces/{workspaceId}/subscriptions/{subscriptionId}/resume
POST      /api/v1/workspaces/{workspaceId}/subscriptions/{subscriptionId}/cancel
```

### Usage/audit

```text
GET /api/v1/workspaces/{workspaceId}/usage/summary
GET /api/v1/workspaces/{workspaceId}/usage/timeseries
GET /api/v1/workspaces/{workspaceId}/usage/events
GET /api/v1/workspaces/{workspaceId}/usage/events/{eventId}
GET /api/v1/workspaces/{workspaceId}/usage/top-routes
GET /api/v1/workspaces/{workspaceId}/usage/top-applications
GET /api/v1/workspaces/{workspaceId}/audit-logs
```

Usage queries require bounded time ranges and pagination for raw events.

### Gateway path

```text
/proxy/{workspaceSlug}/{productSlug}/**
X-API-Key: ...
```

Gateway strips `X-API-Key` before proxying.

### Tenancy rule

Tenant repository lookups must effectively scope by workspace:

```text
findByIdAndWorkspaceId(resourceId, workspaceId)
```

Do not load arbitrary IDs and rely only on controller-side checks. Cross-workspace resource IDs should normally appear as 404.

---

## 12. Frontend blueprint

### Design philosophy

- shadcn/ui default/new-york-like density.
- light/dark/system themes.
- restrained neutral palette + one accent.
- no gradients/glass/neon/excessive cards.
- tables and compact filters for operational data.
- every data surface: loading, empty, error, populated.
- every mutation: pending, validation error, server error, success.
- frontend permission gates are presentation only; backend remains authoritative.

### Fixed sitemap

Do not invent extra major sections without asking.

```text
/login
/[workspaceSlug]
/[workspaceSlug]/products
/[workspaceSlug]/products/[productId]
/[workspaceSlug]/consumers
/[workspaceSlug]/applications/[applicationId]
/[workspaceSlug]/plans
/[workspaceSlug]/usage
/[workspaceSlug]/request-lab
/[workspaceSlug]/audit
/[workspaceSlug]/settings
```

Navigation:

```text
Overview
API Products
Consumers
Plans
Usage
Request Lab
Audit Log
Settings
```

Product detail tabs:

```text
Overview | Routes | Plans | Usage | Settings
```

### Key pages

**Login**
- email/password;
- dev seed hint only in development;
- theme toggle;
- no token display.

**Overview**
- total, allowed, blocked, units, errors;
- simple allowed-vs-blocked chart;
- top routes/applications.

**Products**
- table: name, base path, upstream host, routes, plans, status, updated;
- create/edit product form.

**Routes**
- name, method, path pattern, upstream path, cost units, priority;
- show backend ambiguity errors and match example.

**Consumers/Application detail**
- consumer/app identity/status;
- credentials;
- subscriptions;
- recent usage.

**Credential dialog**
- raw key shown once;
- copy button + clear warning;
- never store in local/session storage, URL, analytics, logs, persistent query cache;
- clear secret state on close/unmount.

**Plans**
- plan + policy editor;
- human-readable policy sentence, e.g. “Burst 5 units, refill 5 every 10 seconds” / “100 units per UTC day.”

**Usage**
- filters: time, product, route, application, decision, request ID;
- summary/time series + raw events + top routes/apps.

**Request Lab** — central demo page

Inputs:

```text
product/route
safe credential selection OR pasted key in ephemeral memory
method/path/query/body for demo upstream
request count
concurrency
optional delay
```

Results:

```text
total / allowed / 429 / other errors
per-request status / latency / remaining / reset / retry-after / request ID
```

Rules:

- Request Lab calls only MeterForge gateway paths.
- Cap burst/concurrency to safe local-demo values.
- Pasted key stays in component memory only.
- Curl examples use `${METERFORGE_API_KEY}` by default.
- Clearly say Request Lab consumes real counters.

### Frontend data rules

- Web talks only to intended control-plane/gateway APIs.
- TanStack Query owns server state.
- React Hook Form + Zod for forms; backend remains business authority.
- No Redux/Zustand unless a real need emerges.
- Never store JWT/API key in local storage.
- URL search params may own shareable usage filters/time ranges.

---

## 13. Observability

Keep it useful and small.

### Logs

Structured JSON in containers; readable local console profile.

Safe fields may include service, requestId, workspaceId, productId, routeId, decision, status class.

Never log keys/HMACs/JWTs/cookies/authorization/body content.

### Metrics

At minimum:

```text
meterforge_gateway_requests_total
meterforge_gateway_request_duration_seconds
meterforge_rate_limit_decisions_total
meterforge_rate_limit_script_duration_seconds
meterforge_usage_publish_total
meterforge_usage_publish_failures_total
meterforge_usage_consumed_total
meterforge_usage_duplicates_total
meterforge_outbox_pending
meterforge_outbox_publish_failures_total
meterforge_config_projection_failures_total
```

Never use unbounded labels such as request ID, credential ID, raw path, email, consumer name.

### Health

- Gateway readiness depends on Redis.
- Control-plane readiness depends on PostgreSQL.
- Worker readiness depends on Kafka + PostgreSQL and Redis for projection.
- Liveness should represent process health, not every dependency.
- Expose `/actuator/prometheus`; do not require Prometheus/Grafana containers.

---

## 14. Testing strategy

Testing is part of every milestone.

### Unit

- JUnit 5 + AssertJ.
- Mockito only at boundaries.
- Test behavior/invariants, not trivial getters/framework code.

### PostgreSQL/Testcontainers

Required where relevant:

- Flyway clean DB migration.
- repository behavior + workspace isolation.
- unique/check/FK constraints.
- audit/outbox atomic transaction.
- usage insert idempotency + aggregate UPSERT.
- no H2 substitute.

### Redis/Lua/Testcontainers — highest-value suite

Token bucket:

- starts full;
- exactly capacity succeeds;
- next fails;
- cost > 1;
- partial refill;
- long idle clamps to capacity;
- exact refill boundary;
- retry-after semantics.

Quota:

- exact limit succeeds, next fails;
- UTC day rollover;
- month rollover including leap-year February;
- route cost units;
- multiple credentials share quota.

Multi-policy/concurrency:

- product + route policies apply;
- several rate policies apply;
- one failure consumes none;
- deterministic limiting policy;
- longest retry when several fail;
- **200 concurrent requests against fresh 5-unit bucket admit exactly 5**;
- two gateway instances share counters.

The real Lua script must run against real Redis. A Java reimplementation alone is insufficient.

### API-key tests

- documented format + entropy;
- only HMAC/safe metadata persisted;
- correct key verifies;
- wrong secret / unknown public ID fail generically;
- constant-time compare path;
- revoked/expired/suspended parent states fail;
- rotation produces different credential;
- old/new share subscription counters;
- secret absent from list/detail/log/audit/event output.

### Kafka/worker tests

- valid usage inserts/aggregates once;
- duplicate changes nothing;
- DB failure prevents ack/redelivers;
- aggregate boundary correctness;
- concurrent UPSERTs do not lose increments;
- duplicate/older config version cannot overwrite newer projection.

### Gateway tests

Use WebTestClient + WireMock:

- route precedence;
- disabled route/product;
- `X-API-Key` stripped;
- query preserved;
- allowed → proxy;
- rate denied → 429;
- Redis down → 503;
- upstream timeout → 504;
- connection failure → 502;
- allowed + blocked requests publish appropriate usage events when Kafka available;
- gateway module has no JPA/JDBC dependency.

### Tenancy tests

For each tenant-owned resource type:

- allowed role succeeds;
- viewer cannot mutate;
- workspace A cannot read/mutate workspace B ID;
- repository query itself scopes workspace.

### Frontend

Vitest + React Testing Library:

- login;
- workspace switcher;
- major list loading/empty/error/populated states;
- backend field-error mapping;
- one-time secret dialog;
- secret-redacted curl;
- Request Lab summaries/caps;
- usage filters;
- role-gated actions;
- theme basics.

No Playwright/Selenium/Cypress.

### Performance evidence

Optional until M4. If measured, record machine, command, concurrency/rate, duration, result, and limiter correctness. Never put an unmeasured performance number in README/resume.

---

## 15. Milestones

Five milestones only. Do not add more without explicit approval.

### M0 — Foundation

Deliver:

- Maven aggregator/wrapper;
- contracts/control-plane/gateway/worker shells;
- Next.js shell + light/dark/system;
- Compose: PostgreSQL, Redis AOF, Kafka KRaft, WireMock;
- health/logging skeleton;
- CI build/tests;
- exact stable versions pinned.

Exit:

- `docker compose up --build` runs infrastructure/app shells;
- no committed secrets;
- Boot/Cloud compatibility pinned.

### M1 — Auth, workspace, products, routes

Deliver:

- local login + HttpOnly JWT cookie;
- workspace/members/RBAC;
- product/route CRUD + state rules;
- deterministic route matching/ambiguity validation;
- product/route UI;
- audit + outbox foundation.

Exit:

- two workspaces cannot access each other;
- owner can create active product/route;
- mutation writes resource + audit + outbox atomically.

### M2 — Consumers, keys, plans, subscriptions, Lua limiter

Deliver:

- consumers/applications;
- one-time key create/rotate/revoke;
- plans and RATE/QUOTA policies;
- subscriptions;
- config event contracts;
- atomic Lua limiter;
- related UI.

Exit:

- all config needed for gateway request exists;
- 200-way fresh-bucket test admits exactly 5;
- no partial policy consumption;
- no existing raw key can be retrieved.

### M3 — Gateway + metering + Request Lab + usage dashboard

**Real finish line.**

Deliver:

- worker outbox publisher/config projector;
- Redis typed projections;
- reactive gateway route/key/limiter/proxy flow;
- usage events for allowed + rejected decisions;
- idempotent raw ingestion + hourly/daily aggregates;
- usage APIs;
- Request Lab;
- basic Overview/Usage UI.

Exit:

- seeded 10-request burst = exactly 5 allowed + 5×429;
- Usage displays allowed + blocked counts;
- revoke → 401 after propagation;
- duplicate Kafka delivery does not change final totals.

### M4 — Portfolio polish (optional)

Only after M3 works:

- README polish + diagram + screenshots/GIF;
- optional small load-test script;
- one honest measured performance result;
- CI cleanup;
- resume bullet based on implemented behavior.

M4 does **not** justify adding Keycloak, Grafana, Tempo, alerts, billing, Kubernetes, or new microservices.

---

## 16. Deferred scope

Track cuts here instead of `TODO`s or extra planning files.

- External IdP/Keycloak.
- Full OIDC/BFF architecture.
- Grafana/Tempo/full OTel infrastructure.
- Alert rules/incidents.
- DLQ browser/replay tooling.
- Billing/invoicing/pricing/payments.
- Redis Cluster deployment.
- Multi-region limiting.
- Kubernetes/Helm/Terraform.
- Raw-usage table partitioning unless volume justifies it.
- Strict/no-loss metering.
- Automatic credential rotation overlap scheduler.
- Sliding-window policy algorithms.
- Browser E2E automation.

---

## 17. Current Project Status

This replaces a separate `PROGRESS.md` for now. Keep it truthful.

**Current milestone:** `M2 — Consumers, Credentials, Plans, Subscriptions, Redis Projections (Completed)`

### Completed

- [x] Milestone M0 — Foundation complete (multi-module Maven, Docker Compose stack with 8 healthy containers, Next.js frontend).
- [x] Milestone M1 — Auth, workspace, products, routes complete:
  - [x] Flyway migrations `V1__init_schema.sql` and `V2__seed_demo_data.sql` with full relational DDL and seeded demo workspace `Acme APIs`, `Weather API`, `GET /v1/forecast/{city}`, demo users (`owner@meterforge.local`, `member@meterforge.local`, `viewer@meterforge.local`).
  - [x] Identity and JWT authentication (`POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/me`) with HttpOnly session cookies.
  - [x] Server-side Workspace scoping and RBAC authorization (`OWNER`, `MEMBER`, `VIEWER`) with last-active-owner demotion protection.
  - [x] Product and Route catalog CRUD and state transitions (`activate` / `disable`).
  - [x] Route syntax parser supporting static, variable `{varName}`, and wildcard `**` patterns.
  - [x] Route ambiguity detection engine rejecting structurally equivalent route paths at configuration time.
  - [x] Transactional Outbox pattern: entity mutations, audit logs, and versioned `ConfigEventEnvelope` outbox rows commit in the exact same DB transaction.
  - [x] Full Spring Boot Testcontainers test suite with 13 tests covering Auth, RBAC, Product/Route lifecycle, route ambiguity, and tenant isolation.
  - [x] Frontend Operations UI (`frontend/web`) with Login, Products, and Audit Logs pages.
- [x] Milestone M2 — Consumers, Credentials, Plans, Subscriptions, Redis Projections complete:
  - [x] Flyway migrations `V3__m2_consumers_credentials_plans_subscriptions.sql` and `V4__seed_m2_demo_data.sql` adding `consumers`, `consumer_applications`, `api_credentials`, `plans`, `limit_policies` (with checks for rate vs quota fields), `subscriptions` (with partial unique active index).
  - [x] Seeded demo consumer `Northstar Labs`, application `Northstar Demo App`, `Free Tier` plan with token bucket rate policy (5 capacity / 5 tokens / 10s) and fixed-window quota policy (100 units / day), seeded dev credential (`nsdemo123456`), and active subscription.
  - [x] API Key generation using 256-bit `SecureRandom` entropy and HMAC-SHA256 server pepper hashing. Raw API keys are returned strictly once on creation/rotation and never stored in database or logs.
  - [x] Control-plane REST APIs and domain services for Consumers, Applications, Credentials (issue, rotate, revoke), Plans, Limit Policies, and Subscriptions.
  - [x] Worker Transactional Outbox poller (`OutboxPollerService`) polling `meterforge.outbox_events` and publishing versioned `ConfigEventEnvelope` to Kafka `meterforge.config.v1`.
  - [x] Worker Redis Projection consumer (`ConfigProjectionConsumer`) projecting configuration snapshots (`rf:v1:cfg:credential:<publicId>`, `rf:v1:cfg:product:<productId>`, `rf:v1:cfg:subscription:<subscriptionId>`) guarded by version key `rf:v1:cfg:version:<aggregateType>:<aggregateId>`.
  - [x] Testcontainers integration tests covering full consumer/app lifecycle, credential issuance/rotation/revocation with HMAC verification, plan/subscription configuration, and worker outbox-to-Redis projection flow with version guard.
  - [x] Frontend Operations UI for Consumers (`/[workspaceSlug]/consumers`), Consumer details with one-time raw key reveal dialog (`/[workspaceSlug]/consumers/[consumerId]`), Plans & Limit Policies (`/[workspaceSlug]/plans`), and Subscriptions (`/[workspaceSlug]/subscriptions`).
  - [x] Vitest tests, TypeScript validation (`tsc --noEmit`), and Next.js production build passing with 0 errors.

### In progress

- [ ] Milestone M3 planning & design (Gateway Core, Atomic Redis Lua Limiter, Upstream Proxying, Request Lab).

### Next

- [ ] Begin Milestone M3 — Gateway Core, Atomic Redis Lua Limiter, Upstream Proxying, and Request Lab.

### Known limitations

- Local Redis uses AOF persistence; counter recovery is documented honestly.
- Availability-first Kafka metering can lose events during prolonged outage + process crash.

### Verification commands

```text
.\mvnw.cmd test
cd frontend/web && pnpm typecheck && pnpm lint && pnpm test
docker compose ps
```

Never mark behavior complete because scaffolding exists. Milestone tests + exit criteria must pass.

---

## 18. Definition of done

A slice is done only when applicable requirements are satisfied:

1. Migration/schema exists if persistence changed.
2. This file's schema/API/event contract matches implementation.
3. Workspace scoping + authorization are server-side.
4. Failure behavior is explicit.
5. Unit tests cover important domain behavior.
6. Real dependency Testcontainers tests cover changed infrastructure boundaries.
7. Gateway stays reactive/non-blocking.
8. No key/token/body secret is persisted/logged.
9. Kafka consumer behavior remains idempotent.
10. Redis limiter remains one atomic multi-policy decision.
11. UI includes normal/loading/empty/error behavior when relevant.
12. Formatter/linter/typecheck/tests pass.
13. §17 is updated truthfully.
14. No dead code, broad TODO, or unexplained disabled test remains.

---

## 19. README and resume framing

README is not a second architecture authority. Create/polish it once the product exists.

After M3, README should show:

1. What MeterForge is.
2. Request Lab screenshot/GIF.
3. Small architecture diagram.
4. Five-minute quick start.
5. Seeded demo instructions.
6. 5-allowed/5-blocked verification.
7. Testing commands.
8. Measured performance result only if actually measured.
9. Honest failure/consistency limitations.
10. Deferred scope.

Avoid “enterprise grade”, “production ready”, “infinitely scalable”, or “exactly once” language without evidence.

Reasonable end-state resume bullet after M3/M4:

> Built MeterForge, a multi-tenant API rate-limiting and usage-metering platform using Spring Boot, Spring Cloud Gateway/WebFlux, Redis Lua, Kafka, PostgreSQL, and Next.js. Implemented atomic multi-policy rate/quota enforcement, one-time API-key lifecycle, transactional configuration outbox, versioned Redis projections, and idempotent usage aggregation over an at-least-once event stream.

---

## 20. Final guardrails

- Build the smallest complete system that proves the architecture.
- Do not create artificial microservices.
- Do not query PostgreSQL from gateway.
- Do not give gateway DB credentials.
- Do not run Flyway outside control-plane.
- Do not implement Java read/modify/write limiter logic.
- Do not run one Lua call per policy.
- Do not partially consume counters on denial.
- Do not blindly retry ambiguous Redis decisions.
- Do not store raw API keys.
- Do not store JWT/API keys in browser local storage.
- Do not trust caller-supplied workspace identity.
- Do not ack usage Kafka records before durable DB completion.
- Do not double-count on Kafka redelivery.
- Do not log bodies, keys, tokens, cookies, auth headers, or HMAC verifiers.
- Do not omit blocked/unauthorized events merely because they consume zero units.
- Do not claim exactly-once end-to-end metering.
- Do not build a custom Kafka buffering subsystem; use bounded producer semantics.
- Do not add Keycloak, Grafana, Tempo, alerts, billing, Kubernetes, or another backend service without explicit approval.
- Do not move to the next milestone until the current milestone's tests and exit criteria pass.

When a requested change conflicts with an invariant above, stop and explain the conflict before implementing it.
