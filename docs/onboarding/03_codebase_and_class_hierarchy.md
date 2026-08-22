# 03. Codebase and Class Hierarchy

This document maps out the code organization, Maven multi-module structure, package-by-feature conventions, and critical classes across the entire codebase.

---

## 1. Directory Structure

```text
meterforge/
├── backend/
│   ├── contracts/             # Shared contracts, DTOs, projections & event models
│   ├── control-plane/         # REST API, JWT security, entity CRUD, JPA persistence
│   ├── gateway/               # Spring Cloud Gateway WebFlux, Redis Lua Limiter
│   └── worker/                # Outbox poller, Redis cache projector, usage ingestion
├── frontend/
│   └── web/                   # Next.js 16 App Router Operations UI & Request Lab
├── infra/
│   └── wiremock/              # Upstream WireMock stub mappings (__admin/mappings)
├── scripts/
│   ├── demo_traffic.ps1       # PowerShell automated traffic burst generator
│   └── demo_traffic.sh        # Bash automated traffic burst generator
├── compose.yaml               # 8-container local production topology
├── pom.xml                    # Root Maven aggregator POM (Java 25 LTS)
└── AGENTS.md                  # Authoritative architecture and progress blueprint
```

---

## 2. Shared Contracts Module (`backend/contracts`)

This module is a lightweight Java library with zero Spring framework dependencies, shared between `control-plane`, `gateway`, and `worker`.

```text
io.meterforge.contracts/
├── common/
│   ├── LimitPolicyKind.java       # Enum: RATE, QUOTA
│   ├── QuotaPeriod.java           # Enum: DAY, MONTH
│   ├── ResourceStatus.java        # Enum: ACTIVE, DISABLED, REVOKED, EXPIRED, RETIRED
│   └── Role.java                  # Enum: OWNER, MEMBER, VIEWER
├── event/
│   ├── ConfigEventEnvelope.java   # Envelope with schemaVersion, eventId, aggregateVersion, payload
│   ├── ProductConfigurationChangedV1.java
│   ├── RouteConfigurationChangedV1.java
│   ├── CredentialConfigurationChangedV1.java
│   ├── PlanConfigurationChangedV1.java
│   ├── SubscriptionConfigurationChangedV1.java
│   ├── UsageRecordedV1.java       # Gateway usage telemetry event schema
│   ├── UsageDecision.java         # Enum: ALLOWED, RATE_LIMITED, UNAUTHORIZED, BLOCKED, NOT_FOUND
│   └── UsageOutcome.java          # Enum: SUCCESS, CLIENT_ERROR, SERVER_ERROR, NOT_FORWARDED
└── projection/
    ├── CredentialProjection.java   # Stored at rf:v1:cfg:credential:<publicId>
    ├── ProductProjection.java      # Stored at rf:v1:cfg:product:<productId>
    ├── RouteProjection.java
    ├── PlanProjection.java
    ├── PolicyProjection.java
    └── SubscriptionProjection.java # Stored at rf:v1:cfg:subscription:<subscriptionId>
```

---

## 3. Control-Plane Module (`backend/control-plane`)

The Control Plane is built with **Spring Boot 3.4 (Spring MVC + JPA)**. It organizes features into packages containing `api`, `application`, `domain`, and `infrastructure` layers.

```text
io.meterforge.controlplane/
├── identity/                  # Authentication & Staff Users
│   ├── api/AuthController.java
│   ├── application/AuthService.java
│   ├── domain/User.java & UserRepository.java
│   └── infrastructure/JwtService.java
├── workspace/                 # Tenant Scoping & RBAC
│   ├── api/WorkspaceController.java
│   ├── application/WorkspaceService.java
│   ├── domain/Workspace.java, WorkspaceMember.java
│   └── infrastructure/WorkspaceSecurityService.java
├── product/                   # API Products & Route Catalog
│   ├── api/ProductController.java, RouteController.java
│   ├── application/ProductService.java, RouteService.java
│   ├── domain/ApiProduct.java, ApiRoute.java
│   └── domain/RoutePatternParser.java & RouteAmbiguityDetector.java
├── consumer/                  # Consumers & Applications
│   ├── api/ConsumerController.java, ApplicationController.java
│   └── application/ConsumerService.java, ApplicationService.java
├── credential/                # API Key Generation & Cryptography
│   ├── api/CredentialController.java
│   ├── application/CredentialService.java (HMAC-SHA256 generation)
│   └── domain/ApiCredential.java
├── plan/                      # Plans & Limit Policies
│   ├── api/PlanController.java, LimitPolicyController.java
│   └── application/PlanService.java, LimitPolicyService.java
├── subscription/              # Subscriptions
│   ├── api/SubscriptionController.java
│   └── application/SubscriptionService.java
├── outbox/                    # Transactional Outbox
│   ├── domain/OutboxEvent.java & OutboxEventRepository.java
│   └── application/OutboxEventPublisher.java
├── usage/                     # Analytics Query REST API
│   ├── api/UsageController.java
│   └── application/UsageService.java (Aggregates & Timeseries queries)
└── audit/                     # Append-Only Audit Logging
    └── application/AuditLogService.java
```

---

## 4. API Gateway Core (`backend/gateway`)

The API Gateway is built on **Spring Cloud Gateway (WebFlux / Project Reactor)**. It contains **no database connection** and executes non-blocking reactive pipelines.

```text
io.meterforge.gateway/
├── credential/
│   ├── ApiKeyAuthenticator.java     # Extracts key, loads projection, constant-time HMAC verify
│   └── ApiKeyHmacVerifier.java      # MessageDigest.isEqual constant-time comparison
├── routing/
│   ├── ProductRouteMatcher.java     # Specificity-ordered route matcher (Static > Param > Wildcard)
│   └── SubscriptionResolver.java    # Fetches SubscriptionProjection from Redis
├── ratelimit/
│   ├── LuaRateLimiter.java          # Executes rate_limiter.lua against Redis
│   └── LimiterDecision.java         # Typed decision (allowed, remaining, retryAfter, resetAfter)
├── filter/
│   └── GatewayProxyFilter.java      # Main GlobalFilter orchestrating Auth -> Limiter -> WebClient Proxy -> Telemetry
├── metering/
│   └── UsageEventPublisher.java     # Kafka template publisher for meterforge.usage.v1
└── config/
    ├── RedisConfiguration.java      # ReactiveStringRedisTemplate
    └── GatewayProperties.java       # Gateway instance ID & server pepper
```

### Gateway Redis Lua Script
- Located at: [`backend/gateway/src/main/resources/scripts/rate_limiter.lua`](file:///c:/Users/dhrubo/projects/meterforge/backend/gateway/src/main/resources/scripts/rate_limiter.lua)
- Evaluates token-bucket refill math and fixed-window calendar quotas **in a single Redis round-trip** with zero mutation on rate-limit denial.

---

## 5. Worker Service (`backend/worker`)

The Worker handles asynchronous background jobs, configuration projection caching, and high-volume usage ingestion.

```text
io.meterforge.worker/
├── outbox/
│   └── OutboxPollerService.java     # @Scheduled poller reading unpublished outbox rows -> Kafka
├── configprojection/
│   └── ConfigProjectionConsumer.java# Kafka listener on meterforge.config.v1 updating Redis cache
└── usageingestion/
    ├── UsageIngestionConsumer.java  # Kafka listener on meterforge.usage.v1
    └── UsageIngestionService.java   # JdbcClient batch insert & atomic SQL upsert rollups
```

---

## 6. Frontend Web UI (`frontend/web`)

The frontend is a **Next.js 16 App Router** single-page operations application built with **React 19, TypeScript, and Tailwind CSS 4**.

```text
frontend/web/
├── app/
│   ├── [workspaceSlug]/
│   │   ├── page.tsx                 # Workspace Overview & Quick Launch
│   │   ├── products/                # API Products & Route Catalog CRUD
│   │   ├── consumers/               # Consumer & App Management
│   │   │   └── [consumerId]/        # Details & Raw API Key Reveal Dialog
│   │   ├── plans/                   # Plans & Token Bucket / Quota configuration
│   │   ├── subscriptions/           # Active Subscription bindings
│   │   ├── lab/                     # Interactive Concurrency Burst Request Lab
│   │   ├── usage/                   # Analytics Dashboard & Raw Telemetry Stream
│   │   └── audit-logs/              # Workspace Audit Log Viewer
│   ├── api/[...path]/route.ts       # Dynamic runtime proxy to Control Plane
│   └── login/page.tsx               # Sign-in page with seeded 1-click logins
├── lib/
│   ├── api/                         # Typed REST API Client functions (React Query hooks)
│   │   ├── auth.ts, client.ts, products.ts, consumers.ts, plans.ts, subscriptions.ts, usage.ts
│   │   └── types.ts                 # TypeScript interfaces matching backend models
│   └── utils.ts                     # Tailwind class merge helper (cn)
└── components/
    ├── ui/                          # Button, Card, Input, Dialog, Select, Table, Badge
    ├── header.tsx                   # Workspace switcher & user profile
    └── sidebar.tsx                  # Workspace navigation menu
```

---

Next, proceed to **[04. Core Runtime Flows](./04_core_runtime_flows.md)** to see how these classes interact at runtime.
