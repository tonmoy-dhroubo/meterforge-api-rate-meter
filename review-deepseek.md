# MeterForge — Deep Code Review (deepseek)

**Date:** 2026-08-18
**Branch:** `main` @ `1c2137a` (M0–M5 committed; prior reviews `review-claude.md`, `review-codex.md` exist in-repo)
**Scope:** Read-only review of the full stack — `contracts`, `control-plane`, `gateway`, `worker`, Flyway migrations (V1–V5), Docker Compose, Dockerfiles, GitHub Actions CI, demo scripts, and the entire Next.js frontend.
**Method:** Every main source file was read. High-impact claims were independently verified by running tooling and computing hashes, not by trusting docs:
- HMAC-SHA256 of the documented demo key recomputed against both services' peppers.
- `pnpm lint`, `pnpm typecheck`, `pnpm test` executed locally.
- Backend/contract JSON shapes compared against frontend TS types line-by-line.

---

## 1. Executive summary

The core primitives are genuinely well-engineered in isolation: the two-phase atomic Lua limiter, idempotent usage ingestion with ack-after-commit, HMAC/pepper key storage with constant-time compare, workspace-scoped repositories, and the zero-DB reactive gateway. A prior review pass (`868b8ca`) already fixed a batch of issues.

**However, the seeded reviewer flow that the entire project is built to demonstrate cannot work on a clean `docker compose up --build`, and config changes silently stop propagating after their first projection.** These are not cosmetic issues:

1. The seeded API key's HMAC matches **neither** service's pepper, and the documented key doesn't even match its own credential's `display_last_four` — a clean-stack demo key can never authenticate ([computed]).
2. Seed migrations write **no outbox rows** and there is no Redis bootstrap/reconciliation path — a clean stack's Redis is empty, so every gateway request is 401.
3. Outbox config events carry the **pre-flush `@Version`** value, so the worker's version guard rejects every update/revoke/disable event after the initial projection — including the "revoke → 401" claim.
4. The frontend/backend auth contract is mismatched, and Overview / Usage / Request Lab pass `workspaceSlug` into endpoints bound to `UUID workspaceId`.
5. Compose env wiring is broken in both directions (`CONTROL_PLANE_URL` vs `NEXT_PUBLIC_API_URL`; `NEXT_PUBLIC_GATEWAY_URL` points at a host the browser can't resolve; pepper never passed to either service).

Fix order and a per-issue breakdown follow.

---

## 2. Review summary table

| # | File | Line | Severity | Category | Issue |
|---|------|------|----------|----------|-------|
| 1 | `V4__seed_m2_demo_data.sql` / `ApiKeyGenerator.java:22` / `MeterForgeGatewayProperties.java:10` | 31 / 22 / 10 | 🔴 Critical | Security / Config | Seeded demo key HMAC matches neither pepper; documented key `...9999` ≠ seed `display_last_four` `7890` |
| 2 | `V2/V4` seed migrations + `ConfigProjectionConsumer.java:42` | — | 🔴 Critical | Correctness | No Redis bootstrap: seed data never projected; clean stack serves 401/NOT_FOUND for everything |
| 3 | `ProductService.java:91`, `CredentialService.java:92,142`, `PlanService.java:216` | 91 / 92 / 216 | 🔴 Critical | Correctness | Outbox event versions read pre-flush ⇒ worker rejects every update/revoke/disable after first projection |
| 4 | `identity/api/dto/AuthResponse.java:5` + `frontend lib/api/types.ts:22` | 5 / 22 | 🔴 Critical | Security / Contract | Login returns raw JWT in body; frontend types `{user,memberships}` vs backend `{id,email,status,workspaces}`; `data.memberships` is undefined → login redirect throws |
| 5 | `frontend app/[workspaceSlug]/page.tsx:38` , `usage/page.tsx:101`, `lab/page.tsx:46` | 38 / 101 / 46 | 🔴 Critical | Correctness | Slug passed to endpoints bound to `@PathVariable UUID workspaceId` → 400 on Overview/Usage/Lab |
| 6 | `compose.yaml:69-153` + `next.config.ts:3` + `lib/api/gateway.ts:32` | — | 🔴 Critical | Config | Compose env wiring broken both ways; `NEXT_PUBLIC_GATEWAY_URL=gateway:8090` unresolvable from browser |
| 7 | `gateway.ts:44-58` + `GatewayProxyFilter.java:88-112` | — | 🔴 Critical | Security | Request Lab cross-origin fetch with `X-API-Key` needs CORS preflight; gateway has none and authenticates before routing |
| 8 | `metering/UsageEventPublisher.java:38` + `KafkaProducerConfig.java:22-32` | 38 | 🟠 High | Concurrency | Blocking `KafkaTemplate.send()` on Netty event-loop thread; no `max.block.ms` bound → Kafka outage freezes gateway |
| 9 | `ratelimit/LuaRateLimiter.java:77-87` | 77 | 🟠 High | Correctness | Quota window ID + TTL computed on app clock, not Redis `TIME` (violates core invariant; skew double-allowance at UTC boundary) |
| 10 | `plan/application/PlanService.java:128` | 128 | 🟠 High | Security / Tenancy | `togglePolicy` loads policy by `(policyId, workspaceId)` without verifying `planId` → cross-plan toggle emits stale projection |
| 11 | `worker ConfigProjectionConsumer.java:206-241` + `SubscriptionResolver.java:53-82` | 206 | 🟠 High | Correctness | Subscription projection embeds plan policies; later plan edits never reach existing subscriptions |
| 12 | `GatewayProxyFilter.java:104,129` + `V5:17` | 104 | 🟠 High | Correctness / Security | Raw attacker-controlled path persisted as `routeTemplate` (unbounded, >1024 chars breaks ingestion) |
| 13 | `ApiKeyAuthenticator.java:101` + `SubscriptionResolver.java:84` + worker consumer | — | 🟠 High | Security | Consumer/application/plan status never enforced; worker drops `Consumer/ApplicationConfigurationChangedV1` |
| 14 | `GlobalExceptionHandler.java:78-94` | 78 | 🟠 High | Error handling | Catch-all swallows framework exceptions → 500 instead of 400/409 (malformed JSON, bad UUID, missing param, unique race, optimistic-lock) |
| 15 | `AuthService.java:84` + `login/page.tsx:28` | 84 | 🟠 High | Security | JWT in login body stored into TanStack `["auth","me"]` cache (defeats HttpOnly design) |
| 16 | `JwtAuthenticationFilter.java:39-58` | 39 | 🟠 High | Security | Valid JWT trusted for full lifetime; disabled/deleted user status never re-checked |
| 17 | `GatewayProxyFilter.java:184-186` + `rate_limiter.lua:66-99` | 184 | 🟡 Medium | Correctness | `Retry-After: 0` / `X-RateLimit-Reset: 0` on quota/rate denials; reset missing on allowed responses |
| 18 | `ApiKeyAuthenticator.java:35-38`, `ProductRouteMatcher.java:33-36` | 35 | 🟡 Medium | Correctness | L1 Caffeine caches have no invalidation; stale projection served up to 5s (revoke propagation bound) |
| 19 | `gateway/pom.xml`, `worker/pom.xml` + all `application.yaml` | — | 🟡 Medium | Observability | `/actuator/prometheus` advertised but `micrometer-registry-prometheus` missing ⇒ 404; none of §13 metrics registered |
| 20 | `ProductRouteMatcher.java:87-120` | 87 | 🟡 Medium | Contract | `/proxy/{workspaceSlug}/{productSlug}/**` contract not implemented; base-path prefix match has no segment boundary; disabled product/route → 404 not `BLOCKED` |
| 21 | `GatewayProxyFilter.java:288-295` | 288 | 🟡 Medium | Security | `X-Forwarded-For/Host/Proto` forwarded verbatim (spoofable) |
| 22 | `product/application/ProductService.java:214` | 214 | 🟡 Medium | Performance | N+1 route loads per product; also consumers + plans counts |
| 23 | `ConsumerApplicationService.java:35-40` | 35 | 🟡 Medium | Tenancy | Unscoped `consumerRepository.existsById` leaks cross-workspace UUID existence |
| 24 | `WorkspaceService.java:228-233` | 228 | 🟡 Medium | Concurrency | Last-active-OWNER protection is check-then-update TOCTOU |
| 25 | `.github/workflows/ci.yml:43` + `package.json:42` | 43 | 🟡 Medium | CI | CI installs pnpm 9; `packageManager` pins pnpm@11.22.0 |
| 26 | `frontend/web` (multiple) | — | 🟡 Medium | CI | `pnpm lint` fails: 8 errors, 8 warnings (contradicts AGENTS.md §17) |
| 27 | `README.md` | — | 🟡 Medium | Docs | Web port 3000 vs 3001; M4/M5 marked unbuilt; "Kafka 3.8" vs 4.3.1; unmeasured "sub-millisecond" claim; seed password `password` vs `password123` |
| 28 | `usage/application/UsageService.java:122` | 122 | 🔵 Low | Correctness | Invalid granularity silently falls back to HOUR; `from > to` accepted; no max-window bound |
| 29 | `usage/application/UsageService.java:66-72` | 66 | 🔵 Low | Correctness | Allowed 4xx/5xx counted in both `allowed` and error buckets |
| 30 | `config/FlywayConfig.java:15-26` | 15 | 🔵 Low | Config | Manual Flyway bean duplicates Boot auto-config |
| 31 | All three `application.yaml` (`show-details: always`) | — | 🔵 Low | Security | Health details publicly exposed; actuator `permitAll` |
| 32 | `rate_limiter.lua:50-57` | 50 | 🔵 Low | Correctness | Degenerate refill (`refill_tokens > period*1000`) can grant free tokens; capacity clamp only on refill; `max(60, refill*3)` TTL can expire slow buckets |
| 33 | `ProductRouteMatcher.java:154-156` | 154 | 🔵 Low | Correctness | `upstreamPath` discards request path (no `{var}` substitution) |
| 34 | `worker/application.yaml:17-26` vs `config/KafkaConfig.java:59-81` | 17 | 🔵 Low | Config | Dead serializer/deserializer config contradicts explicit beans |
| 35 | Various services | — | 🔵 Low | Consistency | `requestId` null on M2/M3 audit rows; `@Valid` missing on some PATCH bodies; idempotent revoke/cancel not enforced |
| 36 | `WorkerProjectionIntegrationTests.java`, `GatewayLimiterIntegrationTests.java` | — | ⚪ Info | Test coverage | Tests seed Redis/outbox directly; no clean-stack vertical slice; §14 matrix largely unimplemented (200-way burst, two-instance, Redis-down, UTC rollover, revoke-propagation, CORS) |

---

## 3. Detailed findings

### 🔴 Critical

---

**Issue #1 — Seeded demo key can never authenticate (pepper mismatch + bad seed HMAC)**

| Field | Details |
|-------|---------|
| **File** | `V4__seed_m2_demo_data.sql:31`, `ApiKeyGenerator.java:22`, `MeterForgeGatewayProperties.java:10` |
| **Severity** | 🔴 Critical |
| **Category** | Security / Config consistency |

**Problem:** The two services hardcode **different** default peppers:
- control-plane: `meterforge_default_secret_pepper_value_change_in_prod`
- gateway: `dev-secret-pepper-change-in-production-12345678`

`compose.yaml` passes **neither** (`API_KEY_PEPPER` in `.env.example` is never referenced). I recomputed HMAC-SHA256 of the documented demo key `mf_dev_nsdemo123456_seedednorthstardemosecretkey9999` against both peppers:

```
control-plane pepper → 11d9b48c…   ≠ seed a718cf22…
gateway pepper       → 53a6a744…   ≠ seed a718cf22…
```

The seed's `display_last_four` is `7890`, but the documented key ends in `9999`. So even a key generated by control-plane (pepper A) would fail verification at the gateway (pepper B), and the documented key doesn't match its own seed row.

**Impact:** The demo key cannot be authenticated under any configuration; "copy key → Request Lab" fails at step one.

**Fix:** One mandatory shared pepper env var (`API_KEY_PEPPER`) wired into both services in `compose.yaml`; fail-fast at startup if unset; recompute the V4 seed HMAC with the resolved pepper; update README/lab/scripts with the real key.

---

**Issue #2 — No Redis projection bootstrap on a clean stack**

| Field | Details |
|-------|---------|
| **File** | `V2__seed_demo_data.sql`, `V4__seed_m2_demo_data.sql`, `ConfigProjectionConsumer.java:42-66` |
| **Severity** | 🔴 Critical |
| **Category** | Correctness (demo-blocking) |

**Problem:** The gateway resolves credentials, products, and subscriptions **only** from Redis. The worker only projects events that arrive via Kafka, and neither seed migration inserts `outbox_events` rows. There is no startup reconciliation that reads PostgreSQL and projects to Redis.

**Impact:** On a fresh `docker compose up`, Redis is empty → every gateway request returns 401 (missing credential projection). The entire demo flow — 5 allowed/5×429, revoke→401, usage dashboard — cannot work.

**Fix:** Add a worker startup/reconciliation step (the worker legitimately owns PostgreSQL access) that projects all ACTIVE products/routes/credentials/plans/subscriptions to Redis when the corresponding `rf:v1:cfg:version:*` key is absent — per AGENTS.md invariant 3 (projections must remain rebuildable). Seed outbox rows alone is not enough (outbox rows get marked published).

---

**Issue #3 — Outbox config events carry stale `@Version` → updates/revokes silently never propagate**

| Field | Details |
|-------|---------|
| **File** | `ProductService.java:87-103,137-153,180-196`, `RouteService.java`, `CredentialService.java:88-95,138-145`, `PlanService.java:185-231` |
| **Severity** | 🔴 Critical |
| **Category** | Correctness / Config propagation |

**Problem:** Entities use `@Version` (in-memory initial value 0). `save()` on an already-managed entity does not flush; the increment happens at commit. Services build the outbox envelope with `entity.getVersion()` **after** `save()` but **before** flush, so the event carries the pre-update version. The worker's guard rejects `incomingVersion <= currentVersion`, so:

- Create → event version 0 → projected, version key = 0.
- First update / revoke / disable → event version 0 → `0 <= 0` → **silently dropped**.
- `PlanService.addPolicyToPlan` / `togglePolicy` never touch the `Plan` row at all, so every policy event reuses the same plan version and is dropped after the first projection.

This affects **every** mutation path — including credential `revoke` (the "revoke → 401" demo claim), product/route status changes, and plan policy edits. DB and Redis silently diverge.

**Fix:** `flushAndRefresh` (or read `version` after `entityManager.flush()`) before building the envelope; for policy mutations, explicitly bump + save the plan; add an integration test that mutates → outbox → worker → Redis and asserts the new state landed.

---

**Issue #4 — Frontend/backend auth contract mismatch (login throws; reload redirects to login)**

| Field | Details |
|-------|---------|
| **File** | `identity/api/dto/AuthResponse.java:5`, `UserProfileResponse.java:6-11`, `frontend lib/api/types.ts:22-25`, `login/page.tsx:28-30`, `lib/auth-context.tsx:42-43` |
| **Severity** | 🔴 Critical |
| **Category** | Contract mismatch |

**Problem:** Backend `/auth/login` and `/me` return `{id, email, status, workspaces}` (wrapped `user` only for login, with a raw `token`). Frontend types both as `{user, memberships: WorkspaceMembershipSummary[]}`:
- `login/page.tsx:29` reads `data.memberships[0]?.workspaceSlug` → `undefined` at runtime → `TypeError` inside `onSuccess` after a *successful* login.
- `auth-context.tsx:42` reads `meData?.user` → undefined → valid sessions are redirected back to `/login` on reload.

**Impact:** The UI cannot log in at all on the documented flow.

**Fix:** One shared profile shape for both endpoints (e.g. always `{user: {id,email,status,workspaces}, memberships: [...]}` derived server-side), drop `token` from the body (cookie is authoritative), and add a frontend contract test asserting the real JSON shapes.

---

**Issue #5 — Overview / Usage / Request Lab pass `workspaceSlug` into UUID-bound endpoints**

| Field | Details |
|-------|---------|
| **File** | `app/[workspaceSlug]/page.tsx:38-40`, `app/[workspaceSlug]/usage/page.tsx:101-105`, `app/[workspaceSlug]/lab/page.tsx:46-51`, `lib/api/usage.ts:86-166`, `UsageController.java:32` |
| **Severity** | 🔴 Critical |
| **Category** | Correctness |

**Problem:** `UsageController` (and Product/Consumer/Plan/Subscription controllers) bind `@PathVariable UUID workspaceId`. The products/consumers/plans/subscriptions pages correctly use `currentMembership.workspaceId`; the Overview, Usage, and Request Lab pages pass the URL **slug** → Spring returns 400 for every data fetch on those three pages.

**Impact:** Overview KPIs, Usage dashboard, and Request Lab catalog loading are all broken.

**Fix:** Resolve `currentMembership?.workspaceId` once and pass the UUID; add a test asserting slug pages issue UUID calls.

---

**Issue #6 — Compose env wiring broken in both directions**

| Field | Details |
|-------|---------|
| **File** | `compose.yaml:69-153`, `next.config.ts:3-14`, `lib/api/gateway.ts:32`, `frontend/web/Dockerfile` |
| **Severity** | 🔴 Critical |
| **Category** | Configuration |

**Problem:**
- `next.config.ts` reads `CONTROL_PLANE_URL` (default `http://localhost:8080`); compose sets `NEXT_PUBLIC_API_URL: http://control-plane:8080` which **no frontend code reads**. Inside the web container the `/api/*` rewrite therefore targets `localhost:8080` — the web container itself.
- `NEXT_PUBLIC_GATEWAY_URL: http://gateway:8090` is set, but the Request Lab `gateway.ts` is a client component; `NEXT_PUBLIC_*` vars are inlined at build time and the Dockerfile passes no build args, so it falls back to `http://localhost:8890` anyway — and even when resolved, `gateway:8090` is not resolvable from the browser.
- `API_KEY_PEPPER` / `JWT_SECRET` from `.env.example` are never consumed by compose.

**Impact:** Every UI API call inside the web container fails; Request Lab cannot reach the gateway from the browser.

**Fix:** Set `CONTROL_PLANE_URL: http://control-plane:8080` in compose; make Request Lab same-origin (Next.js API route proxying to the gateway) or add deliberate, bounded gateway CORS; wire the pepper/secret env through compose.

---

**Issue #7 — Request Lab browser→gateway preflight fails (no CORS, auth-before-route)**

| Field | Details |
|-------|---------|
| **File** | `lib/api/gateway.ts:44-58`, `GatewayProxyFilter.java:88-112` |
| **Severity** | 🔴 Critical |
| **Category** | Security / Error handling |

**Problem:** `X-API-Key` is a non-simple header, so the browser sends an `OPTIONS` preflight. The gateway's `WebFilter` authenticates every request (including `OPTIONS`) and has no CORS config → preflight gets 401 and no `Access-Control-Allow-*` headers; even a `GET` response would be unreadable cross-origin.

**Impact:** The Request Lab cannot perform any request from the browser — the central demo page is non-functional.

**Fix:** Same-origin proxy route in Next.js (recommended, keeps the gateway CORS-free) or a `CorsWebFilter` with a bounded allow-list placed before the proxy filter, letting unauthenticated preflight `OPTIONS` through.

---

### 🟠 High

---

**Issue #8 — Blocking Kafka publish on the Netty event-loop thread**

| Field | Details |
|-------|---------|
| **File** | `gateway/metering/UsageEventPublisher.java:38`, `gateway/config/KafkaProducerConfig.java:22-32` |
| **Severity** | 🟠 High |
| **Category** | Concurrency / Availability |

**Problem:** `emitUsage()` runs synchronously inside the reactive pipeline on the event-loop thread. `KafkaTemplate.send()` is the blocking classic producer API; with no `max.block.ms` set (default 60 s) and metadata uncached, a Kafka outage can pin an event-loop thread for up to 60 s per proxied request — stalling the entire gateway, directly contradicting the "availability-first" invariant.

**Fix:** Set a small `max.block.ms` (≈500–1000 ms), bounded `buffer.memory`, and/or publish via a dedicated bounded executor; count failures with a metric.

---

**Issue #9 — Quota windows computed on the app clock, not Redis `TIME`**

| Field | Details |
|-------|---------|
| **File** | `gateway/ratelimit/LuaRateLimiter.java:77-87`; `rate_limiter.lua:5-7` |
| **Severity** | 🟠 High |
| **Category** | Correctness (core invariant) |

**Problem:** DAY/MONTH window IDs (`2026-08-18`, `2026-08`) and TTLs are computed with `LocalDate.now`/`YearMonth.now(ZoneOffset.UTC)` in Java before the script runs; Redis `TIME` is used only for bucket refill. Two gateway instances with clock skew at a UTC boundary select different window keys → double allowance. This violates AGENTS.md invariant 18 ("Redis TIME is the limiter clock").

**Fix:** Compute the window key and remaining TTL inside Lua from `redis.call('TIME')` (e.g. `os.date("!%Y-%m-%d", now_sec)`), or have the script return the current window key so Java builds the key from the script's answer.

---

**Issue #10 — Cross-plan policy toggle**

| Field | Details |
|-------|---------|
| **File** | `plan/application/PlanService.java:126-138` |
| **Severity** | 🟠 High |
| **Category** | Security / Tenancy / Config propagation |

**Problem:** `togglePolicy(workspaceId, userId, planId, policyId, enabled)` loads the policy via `findByIdAndWorkspaceId(policyId, workspaceId)` and never checks `policy.getPlanId().equals(planId)`. A member can `PATCH /plans/{planB}/policies/{policyA}/...` and toggle a policy that belongs to plan A; the emitted snapshot covers plan B and omits the toggled policy, so plan A's Redis projection is never refreshed — DB and enforced config diverge silently.

**Fix:** Load with `findByIdAndWorkspaceIdAndPlanId` (or assert plan match) and 404/400 on mismatch.

---

**Issue #11 — Plan policy edits never reach existing subscriptions**

| Field | Details |
|-------|---------|
| **File** | `worker/configprojection/ConfigProjectionConsumer.java:206-241`, `gateway/routing/SubscriptionResolver.java:53-82` |
| **Severity** | 🟠 High |
| **Category** | Correctness |

**Problem:** The subscription projection embeds a snapshot of the plan's policies at subscription-event time. `SubscriptionResolver` returns the embedded policies whenever non-empty, so later `PlanConfigurationChangedV1` events (which only update `rf:v1:cfg:plan:<id>`) never change what the gateway enforces for existing subscriptions. Rate/quota edits become invisible.

**Fix:** Either stop embedding policies in the subscription projection (always hydrate from the plan key), or refresh dependent subscription keys when a plan changes, or re-hydrate when the plan version is newer.

---

**Issue #12 — Unbounded raw path persisted as `routeTemplate`**

| Field | Details |
|-------|---------|
| **File** | `GatewayProxyFilter.java:104,129`, `V5__m4_usage_events_and_aggregations.sql:17`, `worker/usageingestion/UsageIngestionService.java:27-43` |
| **Severity** | 🟠 High |
| **Category** | Correctness / Security |

**Problem:** UNAUTHORIZED and NOT_FOUND branches emit the raw attacker-controlled `path` as `routeTemplate`. Paths > 1024 chars overflow `VARCHAR(1024)`, the insert throws, and the consumer retries the poison record repeatedly. This also violates "never persist arbitrary unbounded path".

**Fix:** Use bounded sentinels (e.g. `<unmatched>` / `<unauthorized>`) or truncate; validate/`LEFT(…, 1024)` in the worker and add a dead-letter path for malformed events.

---

**Issue #13 — Consumer/application/plan status not enforced at the gateway**

| Field | Details |
|-------|---------|
| **File** | `gateway/credential/ApiKeyAuthenticator.java:101-119`, `gateway/routing/SubscriptionResolver.java:84-98`, `worker ConfigProjectionConsumer.java` |
| **Severity** | 🟠 High |
| **Category** | Security |

**Problem:** Only credential status/expiry and subscription status are checked. Disabling/archiving a consumer, application, or plan has no effect at the gateway. The worker drops `ConsumerConfigurationChangedV1` / `ApplicationConfigurationChangedV1` entirely (no handler branch).

**Fix:** Handle those events in the worker and embed `consumerStatus`/`applicationStatus`/`planStatus` into `CredentialProjection`/`SubscriptionProjection`; check them before admission; emit refreshed dependent projections when a parent's status changes.

---

**Issue #14 — Catch-all exception handler masks 4xx/409 as 500**

| Field | Details |
|-------|---------|
| **File** | `control-plane/common/api/GlobalExceptionHandler.java:78-94` |
| **Severity** | 🟠 High |
| **Category** | Error handling |

**Problem:** The `@ExceptionHandler(Exception.class)` intercepts everything before Spring's default resolvers, so `HttpMessageNotReadableException` (malformed JSON), `MethodArgumentTypeMismatchException` (bad UUID), `MissingServletRequestParameterException`, `ConstraintViolationException`, `DataIntegrityViolationException` (unique races), and `ObjectOptimisticLockingFailureException` all become 500s instead of 400/409.

**Fix:** Add dedicated handlers (or extend `ResponseEntityExceptionHandler`) for each, mapping to `application/problem+json`; keep the catch-all as last resort.

---

**Issue #15 — JWT returned in login body and stored in the TanStack cache**

| Field | Details |
|-------|---------|
| **File** | `identity/api/dto/AuthResponse.java:5`, `AuthService.java:84-85`, `login/page.tsx:28` |
| **Severity** | 🟠 High |
| **Category** | Security |

**Problem:** Login sets an HttpOnly cookie **and** returns the raw JWT in the JSON body; `login/page.tsx:28` writes the entire response — token included — into the `["auth","me"]` React Query cache (persisted in memory for the session, and any other consumer of that query key inherits it). The `Authorization: Bearer` path in `JwtAuthenticationFilter` makes the leaked token directly usable. The consumer-detail page likewise retains `rawKey` in mutation state after the reveal dialog closes.

**Fix:** Drop `token` from `AuthResponse`; clear secret-bearing query/mutation state immediately after transferring the one-time value.

---

**Issue #16 — JWT trusted for full lifetime; user status never re-checked**

| Field | Details |
|-------|---------|
| **File** | `identity/infrastructure/JwtAuthenticationFilter.java:39-58`, `AuthService.getMe` |
| **Severity** | 🟠 High |
| **Category** | Security |

**Problem:** Any validly signed JWT grants access for its full lifetime (86400 s default) regardless of user `status` (DISABLED) or deletion; no revocation beyond cookie clearing.

**Fix:** Re-check user status server-side per request (cheap indexed lookup), or at minimum on `/me` and authorization boundaries.

---

### 🟡 Medium (selected)

- **#17 Header semantics** — `GatewayProxyFilter.java:184-186`: on rate-only denial `X-RateLimit-Reset: 0`; on quota-only denial `Retry-After: 0`; allowed responses never set reset. Fix Lua to fill both from the bucket/window horizon and only emit headers when > 0.
- **#18 L1 cache invalidation** — `ApiKeyAuthenticator.java:35-38`, `ProductRouteMatcher.java:33-36`: `clearCache()` only invoked from tests; no pub/sub or version-key watch. A revoked credential stays served up to 5 s. Fix: subscribe to a config-invalidation channel or re-check the version key on hit; document the bound.
- **#19 Prometheus 404** — `gateway/pom.xml`, `worker/pom.xml` lack `micrometer-registry-prometheus`; no §13 metrics are registered. Add the registry and register `meterforge_*` counters (usage publish success/failure, limiter script duration, gateway requests, outbox pending/failures).
- **#20 Routing contract** — `ProductRouteMatcher.java:87-120` ignores `/proxy/{workspaceSlug}/{productSlug}/**`, scans all products, matches on base-path prefix with no segment boundary (`/v1/forecast2` matches `/v1/forecast`), and maps disabled products/routes to 404 rather than `BLOCKED`.
- **#21 `X-Forwarded-*` spoofing** — `GatewayProxyFilter.java:288-295` copies caller `X-Forwarded-For/Host/Proto` verbatim upstream. Overwrite from the socket peer.
- **#22 N+1** — `ProductService.mapToResponse:214` loads all routes per product for a count; consumers page counts per consumer; plans loads policies per plan.
- **#23 Unscoped `existsById`** — `ConsumerApplicationService.java:35-40` lets a workspace-A user distinguish an existing foreign UUID (200 + empty list) from a non-existent one (404).
- **#24 Last-owner TOCTOU** — `WorkspaceService.java:228-233` `countById… <= 1` then plain update races; two concurrent demotions can zero owners. Use `SELECT … FOR UPDATE` or a constraint.
- **#25 CI pnpm mismatch** — `.github/workflows/ci.yml:43` pins pnpm 9; `package.json:42` pins `pnpm@11.22.0`. Align.
- **#26 Lint red** — `pnpm lint` fails with 8 errors (3× `no-explicit-any` in `gateway.ts`, 3× `set-state-in-effect` in `usage/page.tsx`, 2× in `lab/page.tsx`/`subscriptions`) and 8 warnings. Contradicts AGENTS.md §17 "checks pass".
- **#27 README stale/misleading** — web port `3000` vs compose `3001`; M4/M5 shown unbuilt while committed; "Kafka 3.8" vs image `4.3.1`; "sub-millisecond limiting decisions" with **no measurement evidence** (violates §14); seed password documented `password` vs actual `password123` (quick-login buttons confirm).

### 🔵 Low / ⚪ Info (selected)

- **#28** `UsageService.java:122` invalid granularity silently → HOUR; `resolveTimeRange` allows `from > to` and unbounded windows.
- **#29** `UsageService.java:66-72` an ALLOWED 4xx/5xx is counted in both `allowedRequests` and error buckets (sums > total).
- **#30** `config/FlywayConfig.java:15-26` manual Flyway bean duplicates Boot auto-config.
- **#31** `show-details: always` + actuator `permitAll` on all three services (config details public).
- **#32** `rate_limiter.lua:50-57` — refill `consumed_time_ms` can be 0 for degenerate configs (free tokens); capacity reduction not clamped until a refill; bucket TTL `max(60, refill*3)` can expire slow-refill buckets early. Quota denial path never sets `retry_after_sec`.
- **#33** `ProductRouteMatcher.buildUpstreamUrl:154-156` — `upstreamPath` replaces the request path entirely (no `{var}` substitution, no wildcard tail).
- **#34** `worker/application.yaml:17-26` Json serializer/deserializer settings contradict the explicit `KafkaConfig` String beans (dead config).
- **#35** Audit `requestId` is `null` for M2/M3 mutations (consumer/app/credential/plan/subscription services); `@Valid` missing on some PATCH bodies; `revoke()`/`cancel()` not idempotent; `limit_policies.route_id` not validated to belong to the plan's product/workspace.
- **#36 Test-coverage gaps** (vs AGENTS.md §14 matrix): no 200-way fresh-bucket admission test, no multi-policy rollback test, no two-gateway-instance sharing test, no UTC boundary / leap-February quota test, no Redis-down 503 / upstream-timeout 504 / connection-failure 502 gateway tests, no revoke-propagation test through outbox→worker→Redis, no clean-stack vertical slice, only 3 Vitest tests in the frontend. All gateway tests inject Redis projections directly and bypass the outbox/worker path (which is exactly where #2 and #3 live).

---

## 4. Verified evidence (what I actually ran/computed)

- **HMAC computation:** `mf_dev_nsdemo123456_seedednorthstardemosecretkey9999` → `11d9b48c…` (control-plane pepper), `53a6a744…` (gateway pepper); neither equals the seeded `a718cf22…`; key's last-4 `9999` ≠ seed `display_last_four` `7890`.
- **`pnpm lint`:** fails — **8 errors, 8 warnings** (exit code 1).
- **`pnpm typecheck`:** passes (exit 0).
- **`pnpm test`:** passes — 2 files / 3 tests (with `act()` warnings in the usage test).
- **Seed migrations:** confirmed neither V2 nor V4 inserts `meterforge.outbox_events` rows (so nothing bootstraps Redis).
- **Compose env:** confirmed no `API_KEY_PEPPER`/`JWT_SECRET` wiring; `NEXT_PUBLIC_API_URL` unused by frontend; `CONTROL_PLANE_URL` not set by compose; `NEXT_PUBLIC_GATEWAY_URL=gateway:8090` not browser-resolvable.
- **Version flow:** confirmed `save()` on managed entities does not flush; outbox envelopes built from `getVersion()` before commit ⇒ stale versions.

---

## 5. What is genuinely strong (passed checks)

- ✅ **Gateway isolation** — no JDBC/JPA/PostgreSQL dependency or credentials; pure reactive end-to-end.
- ✅ **Lua limiter structure** — two-phase read-then-mutate; zero counter mutation on any denial; single atomic invocation for all policies; Redis `TIME` for bucket refill math.
- ✅ **Usage idempotency** — `INSERT … ON CONFLICT(event_id) DO NOTHING`, aggregates only on successful insert, `@Transactional` covering insert+rollups (ack-after-commit semantics).
- ✅ **Credential handling** — `SecureRandom` 256-bit, HMAC-SHA256 + pepper, `MessageDigest.isEqual` constant-time compare, raw key returned once and never persisted/logged.
- ✅ **Tenancy** — repository lookups consistently `findByIdAndWorkspaceId`; cross-workspace IDs 404; workspace membership enforced server-side.
- ✅ **Error responses** — bounded `application/problem+json`; no stack traces/SQL/class names/HMACs leaked.
- ✅ **Transactional outbox + audit** — entity save + audit + versioned outbox row commit in the same DB transaction (`TransactionalMutationService`).
- ✅ **Bounded caches** — Caffeine L1 with 5 s TTL and size caps; live counters never cached.
- ✅ **Route ambiguity validation** — structural-equivalence detection at config time; precedence model sound (static > variable > wildcard, specificity, priority).
- ✅ **WireMock demo upstream** — mapping matches `/v1/forecast/.*` with response templating.

---

## 6. Docs inconsistencies (AGENTS.md vs reality)

| Claim | Reality |
|-------|---------|
| §17 "pnpm lint / typecheck / build passing" | `pnpm lint` fails (8 errors / 8 warnings) |
| §17 "M4/M5 complete" | Committed, but README still shows them unbuilt; frontend has exactly 3 Vitest tests; no clean-stack vertical test |
| §9 "Redis TIME is the limiter clock" | Quota window IDs/TTLs come from the app clock (`LuaRateLimiter.java:77-87`) |
| §11 gateway path `/proxy/{workspaceSlug}/{productSlug}/**` | Implemented as base-path-prefix matching over all products (`ProductRouteMatcher.java`) |
| §13 Prometheus metrics | Endpoint 404; no metrics registered |
| §14 test matrix (200-way, two-instance, UTC rollover, Redis-down, revoke propagation, frontend contract tests) | Not present in the suite |
| §20 "Do not log API keys/HMACs" | Satisfied in logs; but README/lab/scripts hardcode the demo key (acceptable for a seeded local demo, should be flagged as such) |

---

## 7. Recommended fix order

1. **Unify the auth/profile JSON contract** and drop the token from the login body (#4, #15); add frontend contract tests.
2. **Unify the API-key pepper** — single mandatory env, wired via compose, recompute the V4 HMAC, fix the documented key everywhere (#1).
3. **Fix the stale-version outbox bug** — flush before reading version; bump plan on policy changes; add a mutation→outbox→worker→Redis propagation test (#3).
4. **Bootstrap Redis from PostgreSQL** on worker startup when the version key is absent (#2).
5. **Fix compose env wiring** (`CONTROL_PLANE_URL`) and make Request Lab same-origin or add bounded gateway CORS (#6, #7).
6. **Use `currentMembership.workspaceId`** on Overview/Usage/Lab (#5).
7. **Move quota windows into Lua/Redis time**; fix TTL/retry-after/reset semantics (#9, #17, #32).
8. **Plan/consumer/application status enforcement + worker handlers**; stop embedding stale plan policies (#11, #13).
9. **Close policy ownership, raw-path telemetry, cache invalidation, blocking Kafka send** (#8, #10, #12, #18).
10. **Add a clean-stack vertical integration test** (migrations → bootstrap → gateway → burst → usage ingest) — the single highest-value test, since every current integration test injects state directly.
11. **Align CI pnpm, make lint green, and correct README/§17 to verified facts only.**