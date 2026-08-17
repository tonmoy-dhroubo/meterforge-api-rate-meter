# MeterForge — API Rate Limiting & Metering Platform

[![CI](https://github.com/tonmoy-dhroubo/meterforge-api-rate-meter/actions/workflows/ci.yml/badge.svg)](https://github.com/tonmoy-dhroubo/meterforge-api-rate-meter/actions/workflows/ci.yml)
[![Java 25](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-WebFlux-blue.svg)](https://spring.io/projects/spring-cloud-gateway)
[![Redis](https://img.shields.io/badge/Redis-8.0%20Lua-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-3.8%20KRaft-black.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue.svg)](https://www.postgresql.org/)
[![Next.js](https://img.shields.io/badge/Next.js-16%20App%20Router-black.svg)](https://nextjs.org/)

MeterForge is a multi-tenant distributed API rate-limiting and usage-metering platform built with Java 25 LTS, Spring Boot 4.0.7, Spring Cloud Gateway WebFlux, Redis Lua, Apache Kafka, PostgreSQL 17, and Next.js 16.

It is architected to prove core distributed systems invariants cleanly under concurrency:
1. **Atomic Multi-Policy Rate & Quota Enforcement**: Single-round-trip Redis Lua script evaluating token bucket rate limits and fixed-window calendar quotas without race conditions or partial state mutations.
2. **Zero-Database Reactive Gateway**: Pure non-blocking WebFlux gateway operating exclusively against in-memory Redis configuration projections and publishing usage telemetry asynchronously to Kafka.
3. **Cryptographic 256-Bit API Key Engine**: Constant-time HMAC-SHA256 hashed verification with server-side pepper where raw keys are returned strictly once on creation/rotation and never stored.
4. **Transactional Outbox Configuration Propagation**: PostgreSQL control plane transactions commit entity updates, audit logs, and versioned event outbox rows simultaneously.
5. **Idempotent Durable Usage Aggregation**: Kafka consumer deduplicates raw usage events via `ON CONFLICT (event_id) DO NOTHING` and updates PostgreSQL hourly/daily rollups atomically without double counting on redelivery.
6. **Interactive Operations & Request Lab UI**: Built-in Next.js workbench to fire concurrent traffic bursts, inspect real-time token refill countdowns, view telemetry analytics, and manage multi-tenant workspaces.

---

## Architecture Topology

```mermaid
flowchart TD
    subgraph ClientLayer [Client & Operations]
        Browser["Next.js Web UI (:3000)"]
        Consumer["API Consumer / Client"]
    end

    subgraph ControlPlaneLayer [Control Plane & Storage]
        CP["Control Plane API (:8880)"]
        PG[("PostgreSQL 17\n(Config, Outbox, Analytics)")]
    end

    subgraph EventStreamLayer [Event Bus & Worker]
        Kafka{{"Apache Kafka 3.8 (KRaft)\n(Config & Usage Topics)"}}
        Worker["Worker Service\n(Outbox Poller & Aggregator)"]
    end

    subgraph DataPlaneLayer [Data Plane & Cache]
        Gateway["Reactive Gateway (:8890)\nSpring Cloud WebFlux"]
        Redis[("Redis 8.0\n(Lua Limiter & Projections)")]
        Upstream["Upstream API / WireMock (:8080)"]
    end

    Browser -->|REST / JWT HttpOnly| CP
    CP -->|JPA / Flyway Migrations| PG
    CP -.->|Transactional Outbox| PG

    Worker -->|Poll Outbox Events| PG
    Worker -->|Publish Config Events| Kafka
    Kafka -->|Consume Config Events| Worker
    Worker -->|Project Snapshots| Redis

    Consumer -->|HTTP + X-API-Key| Gateway
    Gateway -->|Authenticate & Read Config| Redis
    Gateway -->|Atomic Multi-Policy Lua| Redis
    Gateway -->|Proxy Forward (Headers Sanitized)| Upstream
    Gateway -.->|Emit UsageRecordedV1| Kafka

    Kafka -->|Consume Usage Events| Worker
    Worker -->|Idempotent Insert & Rollups| PG
```

### Component Breakdown

| Deployable | Port | Framework / Tech | Primary Responsibilities | Strict Constraints |
| :--- | :--- | :--- | :--- | :--- |
| **`control-plane`** | `8880` | Spring Boot (MVC + JPA) | Staff JWT authentication, workspace scoping, RBAC, CRUD for products/routes/consumers/plans/subscriptions, transactional outbox. | Never proxies traffic or executes rate limit decisions. |
| **`gateway`** | `8890` | Spring Cloud Gateway (WebFlux) | Route matching, constant-time HMAC API-key authentication, atomic Redis Lua limiter, upstream reverse-proxying, Kafka usage publishing. | Zero PostgreSQL access or credentials; pure reactive non-blocking I/O. |
| **`worker`** | - | Spring Boot + Spring Kafka + JDBC | Polls outbox to publish config events, projects version-guarded config to Redis, consumes usage events, calculates hourly/daily PostgreSQL rollups. | No public HTTP routing. |
| **`web`** | `3000` | Next.js 16 (App Router) + Tailwind | Multi-tenant operations console, one-time key revelation dialogs, interactive Request Lab traffic dispatcher, and Usage analytics dashboard. | Presentation layer only. |

---

## Seeded Turnkey Demo Scenario

MeterForge initializes out of the box with seeded demo data:

* **Workspace**: `Acme APIs` (`acme-apis`)
* **Users**:
  * `owner@meterforge.local` / `password` (Role: `OWNER`)
  * `member@meterforge.local` / `password` (Role: `MEMBER`)
  * `viewer@meterforge.local` / `password` (Role: `VIEWER`)
* **API Product**: `Weather API` (Gateway base path: `/v1/forecast`)
* **Route**: `GET /v1/forecast/{city}` (Cost: 1 unit)
* **Plan**: `Free Tier` (Token bucket: 5 capacity, refill 5 every 10 seconds; Daily quota: 100 units / UTC day)
* **Consumer**: `Northstar Labs` & Application: `Northstar Demo App`
* **API Key**: `mf_dev_nsdemo123456_seedednorthstardemosecretkey9999` (Public ID: `nsdemo123456`)

---

## 5-Minute Quick Start

### 1. Prerequisites
* Docker & Docker Compose
* Java 25 LTS (for building backend from source)
* Node.js 20+ & pnpm (for frontend development)

### 2. Start the Local Stack
```bash
docker compose up --build
```
Verify that all 8 containers are healthy:
```bash
docker compose ps
```

### 3. Step-by-Step Reviewer Verification Flow

1. **Sign In**: Navigate to `http://localhost:3000/login` and log in with `owner@meterforge.local` / `password`.
2. **Open Request Lab**: Navigate to **Request Lab** (`http://localhost:3000/acme-apis/lab`).
3. **Fire Concurrent Burst**: Click **Load Seed Demo Scenario**, select **10 Burst**, and click **Fire 10 Concurrent Requests**.
4. **Observe Atomic Limiting**: You will see **exactly 5 allowed (200 OK) + 5 rate-limited (429 Too Many Requests)**.
5. **Token Refill**: Watch the real-time countdown timer. Once 10 seconds elapse, firing traffic succeeds again.
6. **Inspect Usage Dashboard**: Navigate to **Usage & Analytics** (`http://localhost:3000/acme-apis/usage`) to inspect the aggregated timeseries chart, top routes, and raw event traces.
7. **Key Revocation**: Go to **Consumers & Apps** $\rightarrow$ Northstar Labs $\rightarrow$ Revoke API Key. Send another request from the Request Lab and observe immediate **401 Unauthorized**.

---

## CLI Burst Traffic Automation

You can also execute burst tests directly from the terminal against the running stack:

### PowerShell (Windows)
```powershell
.\scripts\demo_traffic.ps1 -Count 10
```

### Bash (Linux / macOS)
```bash
./scripts/demo_traffic.sh 10
```

Sample output:
```text
============================================================
  MeterForge Burst Traffic Dispatcher
  Target: http://localhost:8890/v1/forecast/tokyo
  Count:  10 concurrent requests
============================================================
  Request #01: HTTP 200 - 18ms (Remaining: 4)
  Request #02: HTTP 200 - 20ms (Remaining: 3)
  Request #03: HTTP 200 - 22ms (Remaining: 2)
  Request #04: HTTP 200 - 23ms (Remaining: 1)
  Request #05: HTTP 200 - 25ms (Remaining: 0)
  Request #06: HTTP 429 - 12ms (Retry-After: 10s)
  Request #07: HTTP 429 - 14ms (Retry-After: 10s)
  Request #08: HTTP 429 - 14ms (Retry-After: 10s)
  Request #09: HTTP 429 - 15ms (Retry-After: 10s)
  Request #10: HTTP 429 - 16ms (Retry-After: 10s)
============================================================
  Total Sent:     10
  Allowed (200):  5
  Limited (429):  5
============================================================
```

---

## Automated Test Verification

All integration test suites run against real Docker containers managed by Testcontainers (PostgreSQL, Redis, Kafka, WireMock).

### Run Backend Test Suites
```bash
./mvnw test
```

### Run Frontend Tests and Build
```bash
cd frontend/web
pnpm install
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

---

## Key Distributed Systems Invariants

| Principle | Implementation in MeterForge |
| :--- | :--- |
| **Limiter Atomicity** | Single Redis Lua script ([`rate_limiter.lua`](backend/gateway/src/main/resources/scripts/rate_limiter.lua)) evaluates both Token Bucket rate policies and Fixed-Window quotas in two distinct phases: read-only evaluation followed by atomic mutation. If any policy fails, zero counters are touched. |
| **Limiter Clock** | Uses Redis server `TIME` to prevent clock drift and synchronization skew across multiple distributed gateway nodes. |
| **Secret Zero-Persistence** | Raw API keys (`mf_<env>_<publicId>_<secret>`) generate 256 bits of entropy. The database stores only public IDs and HMAC-SHA256 hashes generated with a server-side pepper. |
| **Cache Version Monotonicity** | Outbox events carry version counters (`aggregateVersion`). The worker only updates Redis projections when incoming versions are strictly newer (`rf:v1:cfg:version:<type>:<id>`). |
| **Idempotent Telemetry Rollups** | Usage events are stored in PostgreSQL with `ON CONFLICT (event_id) DO NOTHING`. Hourly/daily rollup aggregates (`usage_hourly`, `usage_daily`) are incremented only on successful insertion, guaranteeing idempotent rollups over at-least-once Kafka event streams. |
| **Gateway Isolation** | Gateway is purely reactive WebFlux with zero database credentials, eliminating connection pool starvation and blast radius between traffic and control planes. |

---

## Honest System Limitations & Trade-Offs

* **Authoritative Rate Storage**: Redis is the authoritative live counter store with AOF persistence. A catastrophic Redis loss resets active bucket allowances without corrupting durable PostgreSQL configuration.
* **Availability-First Telemetry**: The gateway prioritizes traffic availability. If Kafka is temporarily unreachable, requests continue to be served while events buffer in memory with bounded retries.
* **Effectively-Once Ingestion**: Aggregations in PostgreSQL are effectively-once over at-least-once Kafka delivery. MeterForge does not claim financially audited exactly-once billing.

---

## Resume Framing

> **MeterForge — Distributed API Rate Limiting & Metering Platform**  
> *Java 25 LTS, Spring Boot 4.0.7, Spring Cloud Gateway (WebFlux), Redis Lua, Apache Kafka (KRaft), PostgreSQL 17, Next.js 16*  
> * Designed and built a multi-tenant API rate limiting and metering engine enforcing atomic multi-policy Token Bucket rate limits and calendar quotas in a single-round-trip Redis Lua script.
> * Implemented a zero-database reactive gateway decoupled from PostgreSQL, resolving routing and credentials via version-guarded Redis projections synchronized through a Transactional Outbox and Kafka.
> * Engineered an idempotent usage ingestion pipeline over at-least-once Kafka event streams, aggregating hourly/daily metrics in PostgreSQL with zero double-counting on redeliveries.
> * Proved correctness under concurrency with Testcontainers integration tests verifying exact burst capacity admission (5 allowed, 5 limited) and sub-millisecond limiting decisions.
