# 05. Developer Guide and Cheatsheet

This document contains practical commands, local development workflows, testing instructions, debugging tools, and the definition of done for developing **MeterForge**.

---

## 1. Local Development Workflows

### Option A: Complete Docker Compose Stack
Best for reviewing the system out-of-the-box:

```bash
# Start all 8 containers in the background
docker compose up --build -d

# View real-time logs across all services
docker compose logs -f

# Shut down and remove all containers
docker compose down
```

### Option B: Local Hybrid Development (Recommended for Fast Coding)
Best for active feature development with instant recompilation:

```bash
# 1. Start only the background infrastructure in Docker
docker compose up -d postgres redis kafka wiremock

# 2. Run backend applications in separate terminal windows:
# Terminal 1: Control-Plane (Runs on port 8080)
./mvnw -pl backend/control-plane spring-boot:run

# Terminal 2: API Gateway (Runs on port 8090)
./mvnw -pl backend/gateway spring-boot:run

# Terminal 3: Worker Service (Runs on port 8070)
./mvnw -pl backend/worker spring-boot:run

# 3. Run Frontend Next.js Dev Server (Runs on port 3000):
cd frontend/web
pnpm install
pnpm dev
```

---

## 2. Seeded Test Data & Default Credentials

The platform seeds a complete working scenario via Flyway:

| Resource | Value | Notes |
|---|---|---|
| **Web UI** | `http://localhost:3001` (Docker) or `http://localhost:3000` (Dev) | Next.js Operations UI |
| **Owner Staff Login** | `owner@meterforge.local` / `password123` | Full workspace permissions |
| **Member Staff Login** | `member@meterforge.local` / `password123` | Catalog CRUD permissions |
| **Viewer Staff Login** | `viewer@meterforge.local` / `password123` | Read-only permissions |
| **Default Workspace** | `Acme APIs` (`acme-apis`) | UUID: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| **Seeded Product** | `Weather API` (`weather-api`) | Base Path: `/v1/forecast` |
| **Seeded Consumer** | `Northstar Labs` (`Northstar Demo App`) | — |
| **Seeded Plan** | `Free Tier` | 5 token capacity, refill 5 every 10s, 100 units/day quota |
| **Seeded API Key** | `mf_dev_nsdemo123456_f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8` | Public ID: `nsdemo123456` |

---

## 3. Testing Commands

### Backend Automated Test Suite

MeterForge uses **Testcontainers** to spin up isolated PostgreSQL, Redis, and Kafka containers during testing:

```bash
# Run tests across all Java modules
./mvnw test

# Run full build with verification
./mvnw verify

# Run specific Testcontainers integration test suites:
# Test Gateway reactive proxying, burst limiter, and 429 enforcement
./mvnw -pl backend/gateway test -Dtest=GatewayLimiterIntegrationTests

# Test HMAC API key hashing and constant-time verification
./mvnw -pl backend/control-plane test -Dtest=ApiKeyHmacConsistencyTests

# Test Worker outbox polling and versioned Redis projection cache
./mvnw -pl backend/worker test -Dtest=WorkerProjectionIntegrationTests

# Test Kafka usage telemetry ingestion and idempotent SQL rollups
./mvnw -pl backend/worker test -Dtest=UsageIngestionIntegrationTests
```

### Frontend Test Suite

```bash
cd frontend/web

# Run Vitest unit & component tests
pnpm test

# Run TypeScript strict typecheck
pnpm typecheck

# Run ESLint
pnpm lint
```

### Automated Traffic Burst Testing

Use the bundled scripts to test concurrency bursts directly against the Gateway:

#### PowerShell (Windows):
```powershell
# Fire 10 concurrent requests to test the 5-token bucket limiter
.\scripts\demo_traffic.ps1 -GatewayUrl "http://localhost:8890" -Count 10
```

#### Bash (Linux / macOS):
```bash
./scripts/demo_traffic.sh -u "http://localhost:8890" -c 10
```

#### Direct cURL Command:
```bash
curl -i http://localhost:8890/v1/forecast/london \
  -H "X-API-Key: mf_dev_nsdemo123456_f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8"
```

---

## 4. Debugging & Inspection Cheatsheet

### Inspecting Redis Keys & Limiter Counters
Connect to Redis CLI:
```bash
docker compose exec redis redis-cli
```
Helpful Redis commands:
```redis
# List all active projection keys
KEYS rf:v1:cfg:*

# View an API key projection
GET rf:v1:cfg:credential:nsdemo123456

# View live token bucket counter
HGETALL "rf:v1:rate:{bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb}:11111111-1111-1111-1111-111111111111"

# View active products set
SMEMBERS rf:v1:cfg:products
```

### Inspecting Kafka Topics
```bash
# Monitor live telemetry usage events emitted by Gateway
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic meterforge.usage.v1 \
  --from-beginning

# Monitor configuration outbox events
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic meterforge.config.v1 \
  --from-beginning
```

### Inspecting PostgreSQL Directly
```bash
docker compose exec postgres psql -U meterforge -d meterforge

# Useful SQL queries:
SELECT event_id, decision, status_code, usage_units, latency_ms FROM meterforge.usage_events ORDER BY occurred_at DESC LIMIT 10;
SELECT bucket_start, total_requests, total_units, total_latency_ms FROM meterforge.usage_hourly ORDER BY bucket_start DESC;
SELECT * FROM meterforge.outbox_events ORDER BY occurred_at DESC LIMIT 5;
```

---

## 5. Definition of Done & Code Standards

Before submitting changes or opening a pull request, ensure:

1. **Architecture Preserved**: Gateway contains **zero SQL queries** and makes all rate decisions in **one atomic Lua call**.
2. **Migrations & Entities Aligned**: Any schema change is accompanied by an append-only Flyway migration script in `backend/control-plane`.
3. **No Secrets Persisted or Logged**: Raw API keys, JWTs, and HMAC verifiers are never logged or saved to the database.
4. **Automated Tests Pass**:
   - `./mvnw clean test` passes cleanly across all modules.
   - `cd frontend/web && pnpm typecheck && pnpm test` passes with 0 errors.
5. **No Dual-Write Bugs**: Outbox rows and entity state commit in the same database transaction.
6. **No Dead Code**: Remove dangling `TODO` items, debug `System.out` statements, or commented-out code blocks.
