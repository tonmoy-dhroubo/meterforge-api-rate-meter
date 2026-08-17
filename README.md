# MeterForge — API Rate Limiting & Metering Platform

MeterForge is a multi-tenant API rate-limiting and usage-metering platform built with Java 25 LTS, Spring Boot 4.0.7, Spring Cloud Gateway WebFlux, Redis Lua, Apache Kafka, PostgreSQL 17, and Next.js 16.

It is designed to solve hard distributed systems problems:
1. **Atomic Multi-Policy Rate & Quota Enforcement**: Single-round-trip Redis Lua script evaluating token bucket rate limits and fixed-window calendar quotas without race conditions or partial state mutations.
2. **Zero-Database Reactive Gateway**: Pure non-blocking WebFlux gateway operating exclusively against in-memory Redis configuration projections and publishing usage events asynchronously to Kafka.
3. **Cryptographic 256-Bit API Key Engine**: Constant-time HMAC-SHA256 hashed verification with server-side pepper where raw keys are returned strictly once on creation and never persisted.
4. **Transactional Outbox Configuration Propagation**: PostgreSQL control plane transactions commit entity updates, audit logs, and versioned event outbox rows simultaneously.
5. **Interactive Request Lab UI**: Built-in Next.js workbench to fire concurrent traffic bursts and visually inspect token-bucket refills and 429 responses in real time.

---

## Architecture Topology

```text
Browser -> web (Next.js) -> control-plane (:8880) -> PostgreSQL 17
                                   |
                             outbox_events
                                   v
                             Kafka (:9092) -> worker -> Redis Projections
                                   ^             |
                                   |             +-> PostgreSQL usage/aggregates (M4)
                                   |
Consumer -> gateway (:8890) -> Redis Lua (:6379) -> Upstream (WireMock :8080)
               |
               +--- UsageRecordedV1 events --->
```

### Component Roles

| Service | Port | Technology | Primary Responsibility |
| --- | --- | --- | --- |
| **`control-plane`** | `8880` | Spring Boot MVC + JPA + Flyway | Staff JWT auth, workspace RBAC, product/route/consumer/plan/subscription CRUD, outbox. |
| **`gateway`** | `8890` | Spring Cloud Gateway WebFlux | API-key auth, Redis config lookup, atomic Lua rate limiter, upstream proxying, usage publishing. |
| **`worker`** | - | Spring Boot + Kafka Consumer | Outbox poller, Redis config projector, idempotent usage aggregator. |
| **`web`** | `3000` | Next.js 16 (App Router) + Tailwind | Operations console and interactive Request Lab workbench. |
| **`postgres`** | `5432` | PostgreSQL 17 | Relational source of truth for config, outbox, and durable analytics. |
| **`redis`** | `6379` | Redis 8.0 Alpine | Authoritative token bucket / quota counters and rebuildable config projections. |
| **`kafka`** | `9092` | Apache Kafka 3.8 (KRaft) | Asynchronous decoupled event bus for config changes and usage events. |
| **`wiremock`** | `8080` | WireMock 3.12 | Upstream mock backend for demo Weather API routes. |

---

## Seeded Demo Scenario

The database automatically initializes with a turnkey scenario:

- **Workspace**: `Acme APIs` (`acme-apis`)
- **API Product**: `Weather API` (`/v1/forecast`)
- **Route**: `GET /v1/forecast/{city}` (1 cost unit)
- **Plan**: `Free Tier` (Token bucket: 5 capacity, refill 5 every 10 seconds; Quota: 100 units / day)
- **Consumer**: `Northstar Labs`
- **Application**: `Northstar Demo App`
- **API Key**: `mf_dev_nsdemo123456_seedednorthstardemosecretkey9999`

---

## 5-Minute Quick Start

### 1. Prerequisites
- Docker & Docker Compose
- Java 25 LTS (for building backend from source)
- Node.js 20+ & pnpm (for frontend development)

### 2. Start the Local Stack
```bash
docker compose up --build
```
Verify that all 8 containers are healthy:
```bash
docker compose ps
```

### 3. Open the Request Lab & Verify Rate Limiting
1. Open your browser to `http://localhost:3000/login`.
2. Sign in with the seeded credentials:
   - **Email**: `owner@meterforge.local`
   - **Password**: `password`
3. Navigate to **Request Lab** in the sidebar (`http://localhost:3000/acme-apis/lab`).
4. Click **Load Seed Demo Scenario** (selects `Weather API`, `GET /v1/forecast/tokyo`, and the seeded Northstar key).
5. Select **10 Burst** and click **Fire 10 Concurrent Requests**.
6. **Result**: You will immediately see **exactly 5 allowed (200 OK) + 5 rate-limited (429 Too Many Requests)**!
7. Watch the live countdown timer; once 10 seconds elapse and the token bucket refills, firing traffic succeeds again.

---

## Automated Test Verification

All integration tests run against real Docker Testcontainers (PostgreSQL, Redis, Kafka, WireMock).

### Run Backend Test Suite
```bash
# Run all tests across contracts, control-plane, gateway, and worker
./mvnw test
```

### Run Frontend Tests and Build
```bash
cd frontend/web
pnpm typecheck
pnpm test
pnpm build
```

---

## Milestone Progress

- [x] **Milestone M0**: Multi-module Maven setup on Java 25 LTS, Spring Boot 4.0.7, Spring Cloud 2025.1.2, Docker Compose with 8 containers, Next.js framework.
- [x] **Milestone M1**: Identity & JWT auth, Workspace scoping & RBAC (`OWNER`, `MEMBER`, `VIEWER`), Products & Routes catalog, route ambiguity detection, transactional outbox.
- [x] **Milestone M2**: Consumers & Applications, 256-bit API Key Engine with HMAC-SHA256 verifiers, Plans & Policies (Token Bucket & Calendar Quota), Subscriptions, Worker Outbox Poller, Redis Projection Engine.
- [x] **Milestone M3**: Pure WebFlux Gateway Engine, Atomic Redis Lua Multi-Policy Limiter (`rate_limiter.lua`), Upstream Proxying with Header Sanitization, Usage Event Publisher, Next.js Request Lab.
- [ ] **Milestone M4**: Worker Idempotent Usage Ingestion, PostgreSQL Hourly/Daily Aggregations, Analytics REST API, Usage Dashboard.
- [ ] **Milestone M5**: Final verification, end-to-end demo hardening, README metrics benchmark report.

---

## Honest System Limitations & Consistency Trade-Offs

- **Limiter Clock**: MeterForge uses Redis server `TIME` for monotonic token bucket math across multiple gateway instances.
- **Counter Storage**: Redis is the authoritative live rate/quota counter store with AOF persistence. A catastrophic Redis outage/restart resets active bucket allowances.
- **Kafka Availability-First**: Gateway does not block proxy traffic if Kafka publishing experiences a transient delay; usage events are buffered with bounded client retries. A gateway process crash during an extended Kafka outage may result in lost telemetry (effectively-once durable aggregation, not financial billing exactly-once).
- **Control Plane Isolation**: The gateway has zero database credentials or JDBC connections, ensuring complete blast-radius isolation between the data plane and control plane.
