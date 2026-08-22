# MeterForge Developer Onboarding Guide

Welcome to **MeterForge**! This directory contains the complete technical onboarding documentation for software engineers and architects joining or reviewing this codebase.

---

## What is MeterForge?

**MeterForge** is a high-throughput, multi-tenant API rate limiting and metering platform. It proves hard distributed systems concepts—including atomic multi-policy rate limiting, transactional outbox propagation, and idempotent usage aggregation—using modern Java, reactive Spring Cloud Gateway, Redis Lua, Apache Kafka, PostgreSQL, and Next.js.

### Key Capabilities

1. **Workspace Management**: Multi-tenant workspace isolation with role-based access control (`OWNER`, `MEMBER`, `VIEWER`).
2. **API Product & Route Catalog**: Register backend services with static, parameterized (`/v1/forecast/{city}`), or wildcard (`/v1/files/**`) route patterns and variable cost units.
3. **Consumers & Applications**: Onboard API consumers with one or more client applications.
4. **Cryptographic Credentials**: Issue 256-bit API keys with HMAC-SHA256 hashing and server-side pepper (raw secrets are shown once and never persisted).
5. **Reusable Plans & Limit Policies**: Configure short-term token-bucket rate limits and fixed-window daily/monthly quotas.
6. **Reactive API Gateway**: Pure non-blocking proxy engine that evaluates all rate limits and quotas in **one atomic Redis Lua script** using Redis server `TIME`.
7. **Idempotent Telemetry Pipeline**: At-least-once Kafka streaming with deduplicated PostgreSQL ingestion and atomic hourly/daily aggregation rollups.
8. **Operations UI & Request Lab**: Next.js dashboard with interactive concurrency burst simulation and real-time telemetry inspection.

---

## Onboarding Documentation Index

Read through these guides in order to build a deep, end-to-end understanding of the platform:

| Guide | Description | Key Takeaway |
|---|---|---|
| **[01. Architecture & Components](./01_architecture_and_components.md)** | System topology, deployable units, responsibility boundaries, and technology stack. | Learn why the Gateway has zero database access and how the control plane and worker interact. |
| **[02. Database Schema & Migrations](./02_database_schema_and_migrations.md)** | Complete PostgreSQL entity-relationship model, table definitions, constraints, indexes, and Flyway migrations (`V1`–`V5`). | Understand the database source of truth, audit logs, outbox events, and usage aggregation rollups. |
| **[03. Codebase & Class Hierarchy](./03_codebase_and_class_hierarchy.md)** | Directory tree, Maven multi-module structure, package-by-feature layout, and class responsibilities. | Navigate the Java modules (`contracts`, `control-plane`, `gateway`, `worker`) and Next.js frontend with confidence. |
| **[04. Core Runtime Flows](./04_core_runtime_flows.md)** | Step-by-step walkthroughs of mutation outbox commit, config projection propagation, gateway limiter execution, and telemetry ingestion. | Trace what happens under the hood on every API call and state change. |
| **[05. Developer Guide & Cheatsheet](./05_developer_guide_and_cheatsheet.md)** | Local environment setup, running tests (Testcontainers & Vitest), debugging tips, and definition of done. | Start developing and running tests locally within 5 minutes. |

---

## Core Domain Vocabulary

Always use these terms consistently across code, database schemas, APIs, and UI:

```text
Workspace
   └── User Memberships (OWNER, MEMBER, VIEWER)
   └── API Products
        └── API Routes (Cost Units, Priority)
        └── Plans
             └── Limit Policies (RATE / Token Bucket, QUOTA / Fixed Window)
   └── Consumers
        └── Consumer Applications
             └── API Credentials (HMAC hashed, never raw)
             └── Subscriptions (Application + Product + Plan)
```

| Term | Definition |
|---|---|
| **Workspace** | One tenant / API provider organization. All tenant-owned rows contain a `workspace_id`. |
| **Product** | One protected logical API (e.g., *Weather API*) mapped to an upstream backend URL. |
| **Route** | One HTTP method and path pattern within a product (e.g., `GET /v1/forecast/{city}`). |
| **Consumer** | An external customer or business partner consuming the APIs. |
| **Application** | A software client application belonging to a consumer. |
| **Credential** | An API key issued to an application (`mf_<env>_<publicId>_<secret>`). |
| **Plan** | A reusable set of rate policies and quota policies for a specific product. |
| **Subscription** | Assignment of one product plan to one consumer application. |
| **Rate Policy** | Short-term token-bucket rule (capacity, refill tokens, refill period seconds). |
| **Quota Policy** | Long-term fixed-window allowance (day or month allowance in usage units). |
| **Usage Unit** | Integer request weight/cost consumed when a route is matched (default: 1). |

---

## 5-Minute Quick Start

```bash
# 1. Start all 8 containers (Postgres, Redis, Kafka, WireMock, Control Plane, Gateway, Worker, Web)
docker compose up --build

# 2. Open Operations UI in your browser
http://localhost:3001

# 3. Log in with Seeded Admin Credentials
Email: owner@meterforge.local
Password: password123

# 4. Open Request Lab & Test Concurrency Burst
Navigate to "Request Lab" (/acme-apis/lab) -> Click "Fire Burst (10 Requests)"
Result: Exactly 5 Allowed (200 OK) + 5 Blocked (429 Rate Limited)
```

Proceed to **[01. Architecture & Components](./01_architecture_and_components.md)** to continue onboarding.
