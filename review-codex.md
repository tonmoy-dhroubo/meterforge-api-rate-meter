# MeterForge Code Review

Review date: 2026-08-18

## Executive summary

MeterForge has a sensible four-deployable shape and several good implementation choices: the gateway has no database dependency, usage ingestion uses an insert-first idempotency gate and aggregate upserts in one transaction, the limiter is a single two-phase Lua invocation, tenant-facing repositories usually include `workspace_id`, and secrets are represented by HMACs in PostgreSQL.

However, the repository is not currently “Portfolio Edition Ready” as claimed in `AGENTS.md` §17. A clean reviewer flow is blocked in several independent places: the frontend and authentication API disagree on response shape, some pages send a workspace slug to UUID endpoints, browser Request Lab traffic is cross-origin with no usable preflight path, seeded PostgreSQL configuration is never projected to Redis, and the control-plane and gateway do not share the same API-key pepper. Configuration updates can also be discarded because outbox versions are captured before JPA increments them, while policy changes do not increment the plan aggregate at all.

The highest-value next slice is therefore not new functionality. It is to make the already-defined seeded end-to-end flow real, then add tests at the boundaries that currently drift independently.

## Review scope and method

- Read the complete `AGENTS.md`, including architecture, contracts, tests, milestone exits, and current status.
- Inspected the clean `main` worktree (255 tracked files; 165 Java files plus the Next.js application, migrations, Compose, scripts, CI, and README).
- Traced control-plane mutations through outbox publication, Redis projection, gateway authentication/routing/limiting, Kafka usage publication, ingestion, analytics, and frontend consumers.
- Reviewed tests against the explicit test matrix in `AGENTS.md` §14.
- Did not modify product code or expand project scope.

## Findings

### P0 — blocks the defined demo or a core correctness invariant

#### 1. The frontend cannot establish the authenticated application state

The backend login response is `{ user: UserProfileResponse, token }`, with workspaces nested under `user.workspaces`; `GET /me` returns `UserProfileResponse` directly (`backend/control-plane/src/main/java/io/meterforge/controlplane/identity/api/dto/AuthResponse.java:3`, `backend/control-plane/src/main/java/io/meterforge/controlplane/identity/api/dto/UserProfileResponse.java:6`, `backend/control-plane/src/main/java/io/meterforge/controlplane/identity/api/AuthController.java:29-50`). The frontend declares both calls as `{ user, memberships }` (`frontend/web/lib/api/auth.ts:9-23`, `frontend/web/lib/api/types.ts:22-25`), and login immediately evaluates `data.memberships[0]` (`frontend/web/app/login/page.tsx:27-30`).

Impact: successful login can throw in the success callback, and a later `/me` response produces no `user`, so the auth provider treats a valid session as unauthenticated.

Improve by defining one shared JSON contract for login and `/me` (preferably the same profile shape), removing the JWT from the response body because the cookie is authoritative, and adding frontend contract tests against representative backend JSON.

#### 2. Overview, Usage, and Request Lab send a slug where the control-plane requires a UUID

The control-plane paths bind `workspaceId` as `UUID` (for example `backend/control-plane/src/main/java/io/meterforge/controlplane/usage/api/UsageController.java:19-40` and `backend/control-plane/src/main/java/io/meterforge/controlplane/product/api/ProductController.java:27-38`). The Overview, Usage, and Request Lab pages pass the route segment `acme-apis` directly into those clients (`frontend/web/app/[workspaceSlug]/page.tsx:26-40`, `frontend/web/app/[workspaceSlug]/usage/page.tsx:38-61`, `frontend/web/app/[workspaceSlug]/usage/page.tsx:101-105`, `frontend/web/app/[workspaceSlug]/lab/page.tsx:23-47`).

Impact: these major pages receive 400 responses even after authentication. Overview hides the problem by converting failures to empty data.

Improve by resolving the current membership once and consistently passing `currentMembership.workspaceId`; add tests that assert a slug route produces UUID API calls and that errors render instead of silently becoming empty state.

#### 3. The Docker web-to-control-plane route and browser-to-gateway route are not viable

The Next rewrite uses `CONTROL_PLANE_URL` with a default of `http://localhost:8080` (`frontend/web/next.config.ts:3-12`), while Compose sets the unused `NEXT_PUBLIC_API_URL` and exposes the web on host port 3001 (`compose.yaml:145-153`). Inside the web container, `localhost:8080` is not the control-plane container.

Request Lab performs a direct browser fetch to the gateway and adds `X-API-Key` (`frontend/web/lib/api/gateway.ts:32-56`). That is cross-origin from the web port and triggers an OPTIONS preflight. The gateway has no CORS configuration, and its highest-precedence filter authenticates every non-actuator request before routing (`backend/gateway/src/main/java/io/meterforge/gateway/filter/GatewayProxyFilter.java:43-92`), so the preflight is answered as unauthorized.

Impact: the primary UI cannot call either backend correctly in the Compose topology.

Improve by wiring the control-plane service URL through the actual Next server rewrite and exposing the gateway through a same-origin Next rewrite (or deliberately configuring bounded gateway CORS and bypassing authentication only for valid preflight requests). Align README URLs with Compose’s `3001` default.

#### 4. A clean stack never creates the seeded Redis configuration, and API-key peppers disagree

`V2` and `V4` insert seeded products, routes, credentials, plans, policies, and subscriptions directly, but insert no corresponding outbox records (`backend/control-plane/src/main/resources/db/migration/V2__seed_demo_data.sql:1-33`, `backend/control-plane/src/main/resources/db/migration/V4__seed_m2_demo_data.sql:1-39`). The worker only projects Kafka config events; there is no startup reconciliation or PostgreSQL-to-Redis rebuild path (`backend/worker/src/main/java/io/meterforge/worker/outbox/OutboxPollerService.java:59-128`, `backend/worker/src/main/java/io/meterforge/worker/configprojection/ConfigProjectionConsumer.java:42-247`).

Separately, generated credentials use the control-plane default pepper `meterforge_default_secret_pepper_value_change_in_prod`, while gateway verification defaults to `dev-secret-pepper-change-in-production-12345678` (`backend/control-plane/src/main/java/io/meterforge/controlplane/credential/domain/ApiKeyGenerator.java:20-23`, `backend/gateway/src/main/java/io/meterforge/gateway/config/MeterForgeGatewayProperties.java:7-12`). Compose passes neither `API_KEY_PEPPER` nor a matching Spring property (`compose.yaml:92-137`). The seeded HMAC also matches neither default.

Impact: the seeded credential is absent from Redis, and even manually projected or newly issued credentials do not verify under default Compose configuration.

Improve by using one required shared pepper property in control-plane and gateway, passing it to both services, failing startup on a placeholder/missing value, and implementing an idempotent initial projection/rebuild mechanism. The clean-stack integration test must start from migrations and prove the seeded key reaches the gateway.

#### 5. Versioned config updates can be dropped, including credential revocation and policy edits

Entities use JPA `@Version`, but services construct outbox envelopes using `getVersion()` before the transaction flush increments the version (for example product updates at `backend/control-plane/src/main/java/io/meterforge/controlplane/product/application/ProductService.java:120-164` and credential revocation at `backend/control-plane/src/main/java/io/meterforge/controlplane/credential/application/CredentialService.java:112-156`). The projector rejects equal versions (`backend/worker/src/main/java/io/meterforge/worker/configprojection/ConfigProjectionConsumer.java:53-65`).

Plan policy changes are worse: adding/toggling a `LimitPolicy` never changes the `Plan` row, yet the event uses `plan.getVersion()` as the aggregate version (`backend/control-plane/src/main/java/io/meterforge/controlplane/plan/application/PlanService.java:100-137`, `185-230`). After the first plan projection, subsequent policy snapshots commonly carry the same version and are ignored.

Impact: revoke/disable/update operations can remain stale in Redis indefinitely, violating the seeded revoke-to-401 flow and monotonic projection invariant.

Improve by allocating the aggregate version deliberately inside the mutation (or flushing before envelope creation where appropriate), ensuring every aggregate snapshot change advances that aggregate’s version, and testing create → update → revoke/disable through the real outbox and Redis projector.

### P1 — high-impact correctness, tenancy, or security gap

#### 6. Parent consumer/application/plan status is never enforced at the gateway

The control-plane emits `ConsumerConfigurationChangedV1` and `ApplicationConfigurationChangedV1`, but the projector handles only credential, product, route, plan, and subscription events (`backend/worker/src/main/java/io/meterforge/worker/configprojection/ConfigProjectionConsumer.java:67-241`). The credential projection contains parent IDs, not parent status (`backend/contracts/src/main/java/io/meterforge/contracts/projection/CredentialProjection.java:1-17`). `ApiKeyAuthenticator` checks only credential status/expiry/revocation (`backend/gateway/src/main/java/io/meterforge/gateway/credential/ApiKeyAuthenticator.java:101-118`), and `SubscriptionResolver` checks only subscription effective status (`backend/gateway/src/main/java/io/meterforge/gateway/routing/SubscriptionResolver.java:72-86`). Plan status is not checked either.

Impact: disabling a consumer, application, or plan does not stop traffic, contrary to the gateway flow in `AGENTS.md` §10.

Improve by placing the bounded parent-state snapshot needed for a decision into the credential/subscription projection and updating it when parent status changes. Add one gateway test per suspended parent state.

#### 7. Gateway routing does not implement the declared tenant/product path and is nondeterministic across overlapping products

The contract is `/proxy/{workspaceSlug}/{productSlug}/**`, but the matcher ignores both slugs and scans every active product globally, matching the request directly against `gatewayBasePath` (`backend/gateway/src/main/java/io/meterforge/gateway/routing/ProductRouteMatcher.java:54-84`, `87-119`). Products are evaluated in Redis Set/SCAN order, and route specificity is calculated only within the first matching product. The database permits the same base path in different workspaces (`backend/control-plane/src/main/resources/db/migration/V1__init_schema.sql:42-57`).

Impact: common base paths across tenants can produce inconsistent 403s or route selection depending on Redis iteration order. Nested base paths can also select a less-specific product first.

Improve by parsing and validating the workspace/product slugs from the documented gateway prefix, loading one keyed product projection, then matching routes deterministically inside it. Add two-workspace same-base-path and nested-base-path tests.

#### 8. Quota windows use the gateway clock, and token-bucket key expiry can reset allowance early

`LuaRateLimiter` chooses DAY/MONTH keys and TTLs with `LocalDate.now`/`YearMonth.now` on the gateway (`backend/gateway/src/main/java/io/meterforge/gateway/ratelimit/LuaRateLimiter.java:67-96`). Redis `TIME` is used only after the key has already been selected. This violates the invariant that Redis is the limiter clock and allows skewed gateway instances to disagree at exact UTC boundaries. The extra 3600 seconds also makes reset headers overstate the real boundary.

The Lua script expires every rate key after `max(60, refill_period * 3)` (`backend/gateway/src/main/resources/scripts/rate_limiter.lua:104-113`). A bucket whose time-to-full exceeds that TTL can disappear and restart full before it should.

Improve by deriving quota window identity/reset inside the Lua script from Redis time and setting rate-key TTL from the maximum time required to refill to capacity plus a margin. Add day/month boundary, skewed-instance, large-capacity/slow-refill, and leap-February tests.

#### 9. A policy ID can be mutated through another plan, and route-scoped policies are not ownership-validated

`togglePolicy` loads the policy by `(policyId, workspaceId)` but never checks `policy.planId == planId` (`backend/control-plane/src/main/java/io/meterforge/controlplane/plan/application/PlanService.java:125-137`). An authorized member can therefore send plan A’s path with plan B’s policy ID and mutate plan B’s policy. Create/add policy also accepts any `routeId` without proving it belongs to the plan’s product (`backend/control-plane/src/main/java/io/meterforge/controlplane/plan/application/PlanService.java:63-93`, `backend/control-plane/src/main/java/io/meterforge/controlplane/plan/application/PlanService.java:100-120`). The schema’s separate foreign keys do not enforce matching workspace/product ownership (`backend/control-plane/src/main/resources/db/migration/V3__m2_consumers_credentials_plans_subscriptions.sql:57-82`).

Improve by using repository queries scoped by workspace + plan + policy and validating route workspace/product before save. Back this with cross-plan and cross-product negative tests.

#### 10. Raw secrets and bearer tokens have wider exposure than the blueprint permits

The seeded raw API key is committed in README, Request Lab state, and both traffic scripts (`README.md:79-93`, `frontend/web/app/[workspaceSlug]/lab/page.tsx:30-32`, `scripts/demo_traffic.ps1:1-8`, `scripts/demo_traffic.sh:1-9`), contradicting the “raw API keys are never persisted” invariant and the Request Lab `${METERFORGE_API_KEY}` rule. Credential issue/rotate mutations also retain their response—including `rawKey`—in TanStack Query’s mutation cache after the reveal dialog closes (`frontend/web/app/[workspaceSlug]/consumers/[consumerId]/page.tsx:103-132`, `384-416`).

The login response returns the JWT in JSON despite also setting the HttpOnly cookie (`backend/control-plane/src/main/java/io/meterforge/controlplane/identity/application/AuthService.java:73-85`). Cookie authentication is combined with globally disabled CSRF protection (`backend/control-plane/src/main/java/io/meterforge/controlplane/config/SecurityConfig.java:42-50`).

Improve by requiring the demo key through an environment variable or issuing it interactively, resetting mutation state immediately after transferring the one-time secret to the dialog, removing the token response field, and using an explicit CSRF strategy appropriate for the same-origin cookie UI.

#### 11. Rejected traffic persists an arbitrary raw path as `routeTemplate`

Unauthorized and not-found branches pass the incoming path into usage events (`backend/gateway/src/main/java/io/meterforge/gateway/filter/GatewayProxyFilter.java:70-92`, `105-124`). The worker persists that value into a `VARCHAR(1024)` without validation (`backend/worker/src/main/java/io/meterforge/worker/usageingestion/UsageIngestionService.java:27-67`).

Impact: arbitrary/unbounded paths enter durable telemetry despite the explicit safe-field contract, can leak path data, and can poison Kafka consumption if the value exceeds the database column.

Improve by using a fixed bounded sentinel such as `UNMATCHED`/`UNAUTHORIZED` when no configured route template exists, validating all event bounds before persistence, and adding malicious long-path tests.

#### 12. Required observability is mostly configuration without implementation

All services advertise `/actuator/prometheus`, but no module includes `micrometer-registry-prometheus`, and there is no code registering the required MeterForge counters/timers. Searches found none of the required metric names. Structured JSON logging and liveness/readiness groups are also absent; public health endpoints use `show-details: always` (`backend/control-plane/src/main/resources/application.yaml:28-35`, `backend/gateway/src/main/resources/application.yaml:12-19`, `backend/worker/src/main/resources/application.yaml:37-44`).

Improve by adding the Prometheus registry, the bounded metrics already listed in `AGENTS.md` §13, explicit readiness/liveness groups, and safe container logging. Do not add a Prometheus/Grafana stack.

### P2 — contract completeness, test quality, and maintainability

#### 13. HTTP/UI contracts have drifted and several in-scope operations are missing

Examples: plans are implemented at `/workspaces/{workspaceId}/plans` instead of under products; plan PATCH/retire and policy DELETE are absent; subscriptions lack PATCH/suspend/resume and use a workspace-level create path; credential rotate/revoke omit the application segment (`backend/control-plane/src/main/java/io/meterforge/controlplane/plan/api/PlanController.java:24-171`, `backend/control-plane/src/main/java/io/meterforge/controlplane/subscription/api/SubscriptionController.java:20-77`, `backend/control-plane/src/main/java/io/meterforge/controlplane/credential/api/CredentialController.java:20-79`). The fixed sitemap names `/request-lab` and `/audit`, while the implementation uses `/lab` and `/audit-logs`; settings and application-detail routes are absent (`frontend/web/components/sidebar.tsx:23-73`).

Improve by choosing the blueprint contract (preferred because it is authoritative) and making backend, frontend, tests, and §17 agree. This is completion of defined scope, not scope expansion.

#### 14. Usage queries do not consistently enforce bounded, valid ranges

Raw event queries allow both `from` and `to` to be absent (`backend/control-plane/src/main/java/io/meterforge/controlplane/usage/application/UsageService.java:224-260`), contrary to the bounded-range requirement. Summary/timeseries accept reversed or arbitrarily large ranges (`backend/control-plane/src/main/java/io/meterforge/controlplane/usage/application/UsageService.java:342-352`). Hourly aggregates are filtered by bucket start, so a non-hour-aligned `from` can exclude events that occurred after `from` but share the earlier hour bucket (`backend/control-plane/src/main/java/io/meterforge/controlplane/usage/application/UsageService.java:20-48`, `backend/control-plane/src/main/java/io/meterforge/controlplane/usage/application/UsageService.java:63-84`).

Improve by enforcing maximum lookback, `from < to`, and documented bucket semantics; raw events should always have a bounded default or required range. Add edge tests around partial buckets.

#### 15. Database constraints do not fully protect the modeled invariants

Status/kind values have few checks, rate/quota fields are not required to be positive or mutually exclusive, and cross-table tenant consistency is not enforced by composite foreign keys (`backend/control-plane/src/main/resources/db/migration/V1__init_schema.sql`, `backend/control-plane/src/main/resources/db/migration/V3__m2_consumers_credentials_plans_subscriptions.sql:57-105`). The last-active-owner rule exists only in application code, and seeded migrations use `IF NOT EXISTS`, which can conceal schema drift rather than fail loudly.

Improve append-only with focused check constraints and composite tenant-aware foreign keys where practical. Keep service validation as well; database protection is the final guard against bugs and manual writes.

#### 16. The automated test suite does not prove the blueprint’s highest-risk paths

Only three frontend tests exist (`frontend/web/app/page.test.tsx`, `frontend/web/app/[workspaceSlug]/usage/page.test.tsx`); they do not cover the auth JSON mismatch, workspace identifier choice, one-time secret cache clearing, Request Lab/CORS, or role-gated mutations. Gateway tests cover a 10-request burst, basic auth, quota count, and proxying, but not the required 200-way test, multi-policy rollback, partial refill, boundary rollover, two gateway instances, Redis failure, upstream timeout/connection failure, query preservation, or usage publication (`backend/gateway/src/test/java/io/meterforge/gateway/GatewayLimiterIntegrationTests.java:199-346`). Worker tests inject already-versioned events and therefore miss control-plane version generation and seeded bootstrap.

Improve with a small number of vertical integration tests: clean migration → outbox/bootstrap → Redis → gateway; create/update/revoke propagation; plan policy update propagation; two-workspace routing; usage publish/duplicate ingest/query; and frontend API-contract tests.

#### 17. CI and local verification are not currently green

`pnpm typecheck`, Vitest, Next production build, and Maven compilation pass, but `pnpm lint` fails with eight errors (notably explicit `any` and `react-hooks/set-state-in-effect`). CI runs lint, so the current branch should not be described as clean. CI also installs pnpm 9 while `package.json` pins pnpm 11.22.0 (`.github/workflows/ci.yml:36-46`, `frontend/web/package.json:42`).

The backend integration suite could not run in this review because Docker Desktop was not running; Testcontainers reported no valid Docker environment. This is an environment blocker, not a test success or a product failure.

Improve by fixing lint, aligning the pinned pnpm version and using `pnpm install --frozen-lockfile`, then rerunning the full Testcontainers suite with Docker available.

#### 18. README and §17 overstate the current state

README uses port 3000 while Compose defaults to 3001, documents password `password` while migrations/UI use `password123`, labels Kafka 3.8 while Compose uses 4.3.1, says the worker has no public port while Compose exposes 8870, claims quotas use Redis time although window selection is in Java, and claims “sub-millisecond limiting decisions” without recorded measurement (`README.md:4-12`, `README.md:79-120`, `README.md:185-213`; `compose.yaml:33-36`, `compose.yaml:120-127`, `compose.yaml:145-153`).

`AGENTS.md` §15 says there are five milestones and defines M0–M4, while §17 claims “M0–M5 complete.” The completed checklist also says all tests/lint/builds pass despite the findings above.

Improve by downgrading §17 to the last verified state, removing the invented M5 label, correcting README commands/ports/credentials/versions, and retaining only measured performance claims.

## What is already strong

- Gateway module dependencies contain no JDBC/JPA/PostgreSQL driver, preserving the zero-database boundary.
- `UsageIngestionService` inserts the raw event with `ON CONFLICT DO NOTHING` and updates hourly/daily aggregates only after a successful insert in the same transaction.
- The Lua limiter separates read-only evaluation from mutation and therefore has the right basic all-or-nothing structure.
- Control-plane resource retrieval generally uses workspace-scoped repository methods, and role checks are server-side.
- API-key generation uses `SecureRandom`, HMAC-SHA-256, and constant-time comparison; credential list DTOs omit the HMAC and raw secret.
- Problem responses are bounded and generic for unhandled exceptions.
- Frontend credential reveal state is cleared when the dialog closes; the remaining issue is mutation-cache retention.
- The codebase is compact enough that the missing vertical tests and projection fixes can be added without introducing new services or infrastructure.

## Verification performed

| Command | Result |
| --- | --- |
| `git status --short` | Clean before review; only this report was added afterward. |
| `.\mvnw.cmd -DskipTests package` | Passed; all four backend modules compiled and packaged. |
| `.\mvnw.cmd test` | Blocked/fails because Docker Desktop is unavailable; 3 non-container tests passed before 8 Testcontainers classes errored during environment discovery. Gateway/worker modules were skipped after the reactor failure. |
| `pnpm typecheck` | Passed. |
| `pnpm lint` | Failed: 8 errors and 8 warnings. |
| `pnpm test` | Passed: 2 files, 3 tests. |
| `pnpm build` | Passed. |
| `docker compose config --quiet` | Compose configuration parsed successfully. |
| `docker info` | Failed because the Docker daemon was not running. |

## Recommended remediation order

1. Unify authentication/profile JSON and workspace UUID use; add frontend contract tests.
2. Correct Compose/Next routing and make Request Lab same-origin (or implement deliberate CORS/preflight handling).
3. Unify and require the API-key pepper; implement clean-start config bootstrap/rebuild.
4. Fix aggregate version allocation and plan policy versioning; prove revoke and policy propagation end to end.
5. Enforce parent states and deterministic `/proxy/{workspaceSlug}/{productSlug}/**` routing.
6. Move quota window calculation into Lua/Redis time and fix counter TTL semantics.
7. Close policy ownership, raw-path telemetry, secret-retention, and CSRF gaps.
8. Complete the in-scope API/sitemap operations, observability, and missing high-value tests.
9. Make lint/CI green, then update §17 and README to verified facts only.

## Overall assessment

The project demonstrates the intended technologies and contains promising implementations of the hardest primitives, but the boundaries between those primitives have not yet been proven together. The current priority should be one reliable, automated clean-stack vertical slice matching the seeded reviewer flow. Once that passes, the project will be much closer to the portfolio claim than it would be after adding any new feature.
