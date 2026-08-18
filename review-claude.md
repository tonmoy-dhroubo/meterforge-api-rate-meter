# MeterForge — Code Review

**Review date**: 2026-08-18
**Reviewer**: Claude Sonnet 4.6 (Thinking)
**Scope**: Full codebase as-is — no scope expansion, findings stay within the declared portfolio project boundaries.

---

## Executive Summary

MeterForge is architecturally coherent and the hardest primitives (Lua limiter two-phase structure, idempotent usage ingestion, HMAC-based API-key handling, reactive gateway) are implemented correctly in isolation. The project fails its own "seeded reviewer flow" test because at least four independent blocking problems must all be resolved before a clean `docker compose up --build` produces the defined demo experience. Beyond the demo blockers there are correctness gaps in the outbox versioning, quota window selection, policy ownership validation, and route matching that make the project's correctness claims currently unverifiable.

**What works well**: gateway has no database dependency; usage idempotency is correct; Lua eval/mutation split is right; workspace-scoped repository queries are consistent; HMAC generation uses `SecureRandom`, constant-time comparison, and never stores the raw key.

**What is broken or missing within scope**: summarized below by severity tier.

---

## P0 — Blocks the seeded demo flow

### 1. Login response shape does not match what the frontend expects

**Where**: `AuthResponse.java` vs `lib/api/types.ts`

`AuthResponse` is `{ user: UserProfileResponse, token }` where `UserProfileResponse` contains `.workspaces[]` (each workspace has `id`, `name`, `slug`, `role`). The frontend `MeResponse` type declares `{ user: UserSummary, memberships: WorkspaceMembershipSummary[] }`. The field `memberships` does not exist on the login response; the frontend accesses `data.memberships[0]?.workspaceSlug` on login success — this evaluates to `undefined` and navigates to `/undefined/products`.

`GET /me` returns `UserProfileResponse` directly (no wrapper), yet the frontend types it as `MeResponse` with a `user` sub-object. Every downstream call that reads `meData?.user` will receive the full profile object when calling `/me` but the nested `user` object when consuming the cached login data — these shapes are incompatible.

**Fix**: align one profile shape for both responses. Remove the `token` field from `AuthResponse` (cookie is authoritative). Rename `workspaces` → `memberships` in `UserProfileResponse`, or update the frontend to map `user.workspaces` to the membership list. Add a frontend contract test.

---

### 2. Several pages pass workspace slug where the control-plane requires UUID

**Where**: `app/[workspaceSlug]/page.tsx`, `app/[workspaceSlug]/usage/page.tsx`, `app/[workspaceSlug]/lab/page.tsx`

The URL segment is `[workspaceSlug]` (e.g., `acme-apis`). These pages pass it directly to API functions that build URLs like `/api/v1/workspaces/acme-apis/usage/...`. The control-plane binds `workspaceId` as `UUID` and returns 400 immediately.

`currentMembership.workspaceId` is available from the auth context. It is used correctly on the products/consumers/plans/subscriptions pages but forgotten on the three pages above.

**Fix**: use `currentMembership?.workspaceId` consistently; wire the lab page's API calls the same way. The `auth-context.tsx` already exposes it — no structural change needed.

---

### 3. The seeded credential HMAC matches neither service's default pepper

**Where**: `V4__seed_m2_demo_data.sql` L31, `ApiKeyGenerator.java` L22, `MeterForgeGatewayProperties.java` L10

The seeded HMAC in V4 (`a718cf22...`) was computed with a specific pepper. Control-plane default pepper is `meterforge_default_secret_pepper_value_change_in_prod`; gateway default pepper is `dev-secret-pepper-change-in-production-12345678`. These differ from each other *and* from whatever was used to produce the seeded HMAC. Compose passes `API_KEY_PEPPER` to neither service.

Consequence: the seeded key `mf_dev_nsdemo123456_...7890` verifies with the correct pepper, but neither service uses that pepper by default, so the key will always fail HMAC verification on a clean stack.

**Fix**:
1. Introduce a single mandatory env var (e.g., `METERFORGE_API_KEY_PEPPER`) used by both services under the same Spring property key.
2. Pass it from Compose to both `control-plane` and `gateway`.
3. Set the default for both to the same placeholder. Fail startup loudly if the placeholder is unchanged (optional but recommended).
4. Recompute the V4 HMAC with the standard placeholder, or use an idempotent startup reconciliation (see finding #4).

---

### 4. Seeded PostgreSQL data is never projected to Redis — no config bootstrap exists

**Where**: `V2__seed_demo_data.sql`, `V4__seed_m2_demo_data.sql`, `OutboxPollerService.java`

Flyway V2 and V4 insert the seeded product, route, credential, plan, policies, and subscription directly into PostgreSQL tables. Neither migration inserts any rows into `outbox_events`. The worker only projects entities it receives via Kafka config events. There is no reconciliation or rebuild path that bootstraps Redis from PostgreSQL on startup.

The gateway has no PostgreSQL access by design (correct). On a clean stack the gateway's Redis is empty — credential lookups return nothing, product matching finds nothing — so every request gets 401 even with the seeded key, and no route matches.

**Fix**: add either (a) a Flyway migration or application startup hook that inserts the seeded entities into `outbox_events` so the poller picks them up, or (b) an idempotent admin endpoint / startup bean in the worker that reads all active entities from PostgreSQL and projects them to Redis if the version key is absent. Option (b) is more robust for future freshness and is allowed by the architecture (worker owns PostgreSQL access).

---

### 5. Compose does not wire `CONTROL_PLANE_URL` to the Next.js container

**Where**: `compose.yaml` web service block, `next.config.ts` L3

`next.config.ts` reads `CONTROL_PLANE_URL` (not `NEXT_PUBLIC_API_URL`). Compose sets `NEXT_PUBLIC_API_URL: http://control-plane:8080` but never `CONTROL_PLANE_URL`. Inside the container the Next server rewrites `/api/*` to `http://localhost:8080/api/*` — which is not the control-plane container — so every UI API call fails.

Additionally, `NEXT_PUBLIC_GATEWAY_URL` is set but nothing in the running Next app uses it for the Request Lab; the lab performs a direct browser fetch to the gateway which is cross-origin and triggers CORS preflight. The gateway has no CORS configuration and its highest-precedence filter returns 401 for OPTIONS requests.

**Fix**:
1. Add `CONTROL_PLANE_URL: http://control-plane:8080` to the Compose `web` environment block.
2. Either route gateway traffic through a same-origin Next API route that proxies to the gateway, or add bounded CORS support to the gateway that allows OPTIONS preflights from the web origin to pass through before authentication.

---

## P1 — Correctness and security gaps

### 6. Outbox aggregate version is captured before JPA flush increments it

**Where**: `ProductService.java` create/update, `CredentialService.java` issue/revoke

Every mutation calls `entity.getVersion()` to populate the outbox envelope *before* the transaction commits. JPA `@Version` is incremented by the database at flush time. The in-memory version seen immediately after `save()` may reflect the pre-flush value. Specifically: create creates with version 0, emits outbox with version 0; the first update finds version 0 in the envelope also (unless Hibernate flushes eagerly in between). The projector rejects `incomingVersion <= currentVersion` and drops equal-version events.

Concrete sequence for a credential revoke:
1. Issue: version becomes 1, outbox carries version 1 → projected OK.
2. Revoke: `credential.revoke()` + `credentialRepository.save(credential)` returns the entity; `credential.getVersion()` is still `1` until the transaction commits. Outbox envelope carries version 1. Projector sees incoming=1, current=1 → skipped. Revoke never reaches Redis.

**Fix**: flush the entity explicitly (e.g., `entityManager.flush()`) before reading the post-increment version, or read the version from the returned saved entity after flush. This is a one-line fix per service but affects every mutation path.

---

### 7. Policy changes do not advance the plan aggregate version

**Where**: `PlanService.java` `addPolicyToPlan`, `togglePolicy`

`addPolicyToPlan` and `togglePolicy` save a `LimitPolicy` row but do not touch the `Plan` entity. The emitted `PlanConfigurationChangedV1` envelope uses `plan.getVersion()` — which has not changed. The worker's version guard drops any subsequent plan event with the same version after the first projection. Policy additions and toggles are effectively invisible to the gateway after the plan has been projected once.

**Fix**: `plan.setUpdatedAt(Instant.now())` alone is insufficient; increment the plan's `version` field (or force a JPA `@Version` bump via a `save(plan)` call) inside `addPolicyToPlan` and `togglePolicy`. Because `@Version` is managed by JPA, simply calling `planRepository.save(plan)` after touching any field will work once the flush-before-envelope issue (#6) is also fixed.

---

### 8. Quota window key and TTL are computed on the gateway clock, not from Redis TIME

**Where**: `LuaRateLimiter.java` L77–98

The Lua script's PHASE 1 reads Redis `TIME` (`now_sec`) but the quota key name (containing the date or year-month string) and its TTL are determined in Java before the script is called. Two gateway instances running in different time zones or with small clock drift can use different window keys at the UTC day boundary — one writes to `2026-08-18` while the other writes to `2026-08-19` — causing double allowance at exact boundaries.

**Fix**: move window identity calculation into the Lua script using `now_sec`. Compute `local day = math.floor(now_sec / 86400)` and `local month` from `now_sec` directly, making the window boundary atomically consistent with Redis server time. Pass the timezone offset (UTC=0) as a constant ARGV instead.

---

### 9. `togglePolicy` does not verify the policy belongs to the specified plan

**Where**: `PlanService.java` L128

```java
LimitPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
        .orElseThrow(() -> new ResourceNotFoundException("LimitPolicy", policyId));
```

This loads any policy in the workspace matching `policyId`. A `MEMBER` can call `PATCH /workspaces/{ws}/plans/{planA}/policies/{policyFromPlanB}/toggle` and successfully toggle a policy belonging to `planB`. No `planId` check is applied.

Similarly, `addPolicyToPlan` accepts any `routeId` in the request without verifying the route belongs to the plan's product, allowing cross-product policy scoping.

**Fix**: replace `findByIdAndWorkspaceId` with `findByIdAndWorkspaceIdAndPlanId`. For route ownership: join to `api_routes` and verify `route.product_id == plan.product_id && route.workspace_id == plan.workspace_id` before saving the policy.

---

### 10. Rejected requests with no matching route persist the raw incoming path as `routeTemplate`

**Where**: `GatewayProxyFilter.java` unauthorized and not-found branches, `UsageIngestionService.java` L57–59

The unauthorized branch emits `routeTemplate = path` (raw request path). The not-found branch emits `routeTemplate = path` before any route is resolved. The worker persists this value into `route_template VARCHAR(1024)` without validation. A caller can send `GET /v1/forecast/` + 1025 characters and cause a DB truncation error that kills the Kafka consumer partition.

**Fix**: use the sentinel string `"UNMATCHED"` for not-found events and `"UNAUTHORIZED"` for pre-route events. Validate that `routeTemplate.length() <= 1024` before the INSERT.

---

### 11. Raw API key committed in README and demo scripts

**Where**: `README.md` (curl examples), `scripts/demo_traffic.ps1`, `scripts/demo_traffic.sh`

The seeded raw key `mf_dev_nsdemo123456_...` is hardcoded in committed files. The blueprint states "raw API keys are never persisted." While this is a development key and the project is local-only, the practice trains the wrong habit for a portfolio security demo.

The login response also returns `token` in the JSON body despite the cookie being the authoritative auth mechanism. This creates an invitation to use `localStorage`, which the blueprint explicitly forbids.

**Fix**: replace the hardcoded key in scripts with `$METERFORGE_API_KEY` env var or a `--key-from-stdin` pattern. Remove `token` from `AuthResponse`; the cookie alone is sufficient. Note in README that the key must be issued interactively via the UI.

---

### 12. `loadActiveProducts()` fallback uses SCAN which is O(n) and non-deterministic

**Where**: `ProductRouteMatcher.java` L68–84

When the `rf:v1:cfg:products` set is empty (clean stack), the code falls back to `SCAN` over all keys matching `rf:v1:cfg:product:*`. On a clean stack where no keys have ever been written, the SCAN returns nothing — it is silently a no-op. The fallback cannot bootstrap a completely empty Redis.

**Fix**: the fallback is only useful after partial bootstrap. Add a log warning when falling back and explicitly add the found product IDs to the `rf:v1:cfg:products` set as a side effect, so subsequent requests use the fast path. Document that this fallback is not a substitute for the Redis bootstrap issue (#4).

---

### 13. Parent entity status (consumer, application, plan) is not enforced at the gateway

**Where**: `ApiKeyAuthenticator.java`, `SubscriptionResolver.java`

The credential projection contains no consumer/application status. If the consumer is suspended or the application is disabled, the credential still verifies. Similarly, the plan's `status` is not checked in `SubscriptionResolver`; a subscription referencing a disabled plan still resolves.

`ConsumerConfigurationChangedV1` and `ApplicationConfigurationChangedV1` events are published but consumed nowhere in the worker.

**Fix**: embed `consumerStatus` and `applicationStatus` into `CredentialProjection` and check them in `verifyCredential`. Embed `planStatus` into `SubscriptionProjection`. Handle consumer/application change events in the worker's `ConfigProjectionConsumer` to update the relevant projections.

---

## P2 — Contract completeness, test coverage, and code quality

### 14. JWT token used for `Authorization: Bearer` in integration tests, not the session cookie

**Where**: All backend integration tests

Test helper `loginAndGetToken` extracts `token` from the login response JSON and uses `Authorization: Bearer`. The production UI uses HttpOnly cookie authentication. Tests should exercise the same auth path as the actual system. The fact that Bearer also works may not be intentional.

**Fix**: use `MockMvc`'s cookie support: `mockMvc.perform(get(...).cookie(new Cookie("mf_session", token)))`. This validates the actual auth path the UI exercises.

---

### 15. `ProductService.mapToResponse` executes N+1 queries per product listing

**Where**: `ProductService.java` `mapToResponse`

```java
int routeCount = routeRepository.findByWorkspaceIdAndProductIdOrderByPriorityDescCreatedAtAsc(
    product.getWorkspaceId(), product.getId()).size();
```

This loads all route entities (not just a count) for each product in the list. For a workspace with 10 products and 20 routes each, the list endpoint executes 11 queries instead of 2.

**Fix**: add `countByWorkspaceIdAndProductId(UUID workspaceId, UUID productId)` to `ApiRouteRepository` and use it here.

---

### 16. Worker integration test creates outbox DDL manually, diverging from Flyway

**Where**: `WorkerProjectionIntegrationTests.java` L76–93

The test manually creates `meterforge.outbox_events` in `@BeforeEach`. The DDL in the test will silently diverge from the real V1 migration as the schema evolves — the test will pass while the production table structure is different.

**Fix**: share a test fixture SQL file via `@Sql` and keep it in sync with the control-plane migration, or point the worker test at the control-plane's Flyway migration classpath.

---

### 17. `UsageEventPublisher` may block the Netty event loop

**Where**: `UsageEventPublisher.java` L29–43

`publish()` is called inside a reactive flatMap in `GatewayProxyFilter` but uses a synchronous `CompletableFuture` from a non-reactive `KafkaTemplate`. The `kafkaTemplate.send()` call may block briefly on internal buffer access.

**Fix**: call `usagePublisher.publish()` after `response.writeWith(...)` completes, or wrap it in `Mono.fromRunnable` subscribed on `Schedulers.boundedElastic()` to keep usage publish off the Netty event loop.

---

### 18. `show-details: always` on health endpoints leaks internal state

**Where**: `application.yaml` in all three services

`show-details: always` makes `/actuator/health` include connection URLs and detailed component diagnostics publicly. Acceptable for local demo, but the README should note this is not production-appropriate or change it to `when-authorized`.

---

### 19. Prometheus registry dependency missing from all three services

**Where**: All three `pom.xml` files

All services expose `/actuator/prometheus` but none include `io.micrometer:micrometer-registry-prometheus`. The endpoint will return 404 at runtime. The `AGENTS.md` §13 specifies named metrics that must be registered.

**Fix**: add `micrometer-registry-prometheus` to each service's `pom.xml`. Register the minimum counters/timers per AGENTS.md §13.

---

### 20. `SecurityConfig` disables both CSRF and CORS without documentation

**Where**: `SecurityConfig.java` L44–45

```java
.csrf(AbstractHttpConfigurer::disable)
.cors(AbstractHttpConfigurer::disable)
```

CSRF is disabled globally without a token-based replacement. CORS is disabled rather than configured to the known web origin.

**Fix**: configure CORS to allow only the known web origin. Either implement double-submit cookie CSRF or document the specific threat model rationale for disabling it.

---

### 21. Rate bucket TTL formula is wrong for slow-refill plans

**Where**: `rate_limiter.lua` L127

```lua
local expire_sec = math.max(60, refill_period_sec * 3)
```

For a plan with capacity=1000, refill=1 token/hour (3600s): TTL = max(60, 10800) = 10800s = 3 hours. A bucket partially consumed 3.1 hours ago with 500 tokens remaining expires and resets to full, granting unearned capacity.

**Fix**: TTL should be at least `ceil(capacity / refillTokens) * refillPeriod` — the time to fully refill from empty. Use:
```lua
local max_refill_time = math.ceil(capacity_or_limit / refill_tokens) * refill_period_sec
local expire_sec = math.max(60, max_refill_time * 2)
```

---

### 22. Quota denial returns `Retry-After: 0` instead of reset time

**Where**: `rate_limiter.lua` L94–99

When quota is exceeded, `retry_after_sec` stays 0 but the gateway emits `Retry-After: 0`, telling clients to retry immediately. For a day-level quota this is misleading — the window won't reset for hours.

**Fix**: set `retry_after_sec = reset_after_sec` when quota is denied, since quota has no partial-refill concept.

---

### 23. Inconsistent aggregate type literals between services

**Where**: `ProductService.java` L88 vs `ConfigProjectionConsumer.java` L90

`ProductService` emits `aggregateType = "PRODUCT"`. The worker's version guard stores/checks `rf:v1:cfg:version:PRODUCT:<id>` from the outbox, but the consumer handles based on `eventType` first, so the functional flow works. However, the `aggregateType` mismatch means the version guard key differs between what the poller writes and what the consumer checks — version deduplication for products is effectively disabled.

**Fix**: standardize aggregate type strings with shared constants in the `contracts` module. Audit all `aggregateType` strings: `Plan` and `ApiCredential` are consistent; only `Product`/`ApiProduct` diverges.

---

### 24. CI uses pnpm 9 but `package.json` pins pnpm 11.22.0

**Where**: `.github/workflows/ci.yml`, `frontend/web/package.json` `packageManager` field

CI installs a significantly older pnpm than the project requires. `pnpm install --frozen-lockfile` may fail because the lockfile was generated with pnpm 11.

**Fix**: align CI pnpm version with `package.json#packageManager`.

---

## What is already correct and strong

| Area | Observation |
|---|---|
| Gateway module isolation | No JDBC/JPA/PostgreSQL driver. Reactive end to end. |
| Lua two-phase eval | Phase 1 reads without mutation; Phase 2 writes only on unanimous pass. |
| Usage idempotency | `ON CONFLICT(event_id) DO NOTHING`; aggregates only updated on new insert; Kafka ack after DB commit. |
| HMAC credential storage | `SecureRandom`, HMAC-SHA-256, constant-time comparison, HexFormat. Raw key never stored. |
| Workspace-scoped queries | All repository methods include `workspaceId`; security evaluator is server-side. |
| Error responses | `application/problem+json` with bounded fields; no stack traces, SQL, or internal class names. |
| Caffeine L1 cache | Short TTL (5s), bounded size (10k for credentials, 10 for products). Correct for config-only. |
| Kafka availability-first | Producer bounded retry; gateway continues serving during Kafka outage; documented limitation. |
| Subscription lazy hydration | Falls back to plan projection to hydrate policies if subscription has none. |
| Token-bucket arithmetic | Discrete tick advancement avoids floating-point drift. |
| Integration test quality | Testcontainers used correctly; burst concurrency test for 5+5 split is a real correctness proof. |

---

## Recommended fix order

1. Fix the login/profile JSON contract mismatch (P0 #1)
2. Pass `workspaceId` consistently in overview, usage, and lab pages (P0 #2)
3. Unify API key pepper across both services and Compose (P0 #3)
4. Add Redis bootstrap for seeded data via outbox seed rows or worker startup bean (P0 #4)
5. Fix Compose `CONTROL_PLANE_URL` and Request Lab gateway routing (P0 #5)
6. Fix outbox version capture (flush before reading post-increment version) (P1 #6)
7. Fix plan version bump on policy changes (P1 #7)
8. Move quota window ID computation to Lua/Redis TIME (P1 #8)
9. Add plan ownership check to `togglePolicy` and route-product ownership check to `addPolicyToPlan` (P1 #9)
10. Use sentinels for unmatched route templates in usage events (P1 #10)
11. Fix bucket TTL formula for slow-refill plans; set `retry_after_sec` for quota denials (P2 #21, #22)
12. Fix product aggregate type mismatch between control-plane and worker (P2 #23)
13. Add Prometheus registry dependency to all three services (P2 #19)
14. Fix CI pnpm version (P2 #24)
15. Remaining P2 items: N+1 route count query, `UsageEventPublisher` thread safety, health detail exposure, CSRF/CORS config

---

## Gaps vs AGENTS.md blueprint (within declared scope)

| Blueprint requirement | Current state |
|---|---|
| Single pepper, required at startup | Two different defaults; not passed by Compose; no fail-fast |
| Redis is limiter clock | Quota window key derived from Java clock, not Redis TIME |
| Gateway enforces consumer/application/plan status | Only credential status checked |
| Monotonic version projection (older never overwrites newer) | Broken for products (aggregate type mismatch); broken for any entity when outbox version = current DB version |
| Revoke-to-401 in seeded flow | Blocked by pepper mismatch, no Redis bootstrap, and outbox version bug |
| `routeTemplate` in usage is bounded and safe | Raw path persisted for unauthorized/not-found events |
| Prometheus `/actuator/prometheus` | Registry missing from all three services |
| Structured logging | No JSON log configuration in any service |
| Frontend contract matches backend | Login/profile shape mismatch; workspace slug vs UUID in 3 pages |
