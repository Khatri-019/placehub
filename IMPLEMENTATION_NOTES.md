# PlaceHub Lite — Implementation Notes

Longer rationale for decisions. Operational status lives in `CLAUDE.md`; the spec is `BUILD_SPEC.md`.

## Versions (verified 2026-11-15 against start.spring.io metadata)
| Component | Version | Source |
|-----------|---------|--------|
| Spring Boot | 4.1.2 | start.spring.io `bootVersion` (latest GA) |
| Spring Cloud | 2025.1.4 (Oakwood) | start.spring.io bom-ranges: `Spring Boot >=4.0.0 and <4.2.0-M1` |
| springdoc-openapi | 3.2.0 | start.spring.io dependency-ranges for Boot 4.1.x |
| Spring Cloud Gateway Server WebFlux | 5.0.4 | via Cloud BOM |
| Spring Kafka | 4.1.2 | via Boot BOM |
| Hibernate ORM | 7.4.13 | via Boot BOM |
| Testcontainers | 2.0.5 | via Boot BOM (new `org.testcontainers.<module>` packages) |
| WireMock | 3.13.2 (`org.wiremock:wiremock-standalone`) | latest stable 3.x on Maven Central (4.x is beta) |
| Kafka image | `apache/kafka:4.3.2` | latest stable patch of the 4.3 line on Docker Hub (4.4.0 just released) |
| Java | 25 LTS (`--release 25`) | Local builds require JDK 25; images use temurin 25 |

## Decisions where the spec is silent
1. **`placehub-common` module.** Holds only DTO-free servlet utilities: `ApiError`/`ApiException`,
   `GlobalExceptionHandler`, `CorrelationIdFilter`, JSON 401/403 handlers, `CommonSecurityConfiguration`
   (internal-token chain + resource-server chain), `AuthUser`, `PageResponse`/`Paging`, OpenAPI config, startup
   logger. Services include it through `scanBasePackages`. No entities or event DTOs are shared. The reactive
   gateway keeps its own small copies (it must not pull in Spring MVC).
2. **JWT secret format.** Raw UTF-8 bytes by default; `base64:` prefix selects Base64. Startup fails if < 32 bytes.
   Decoders also validate `iss=placehub-user-service` and require `sub`.
3. **Internal auth.** A `SecurityFilterChain` with `securityMatcher("/internal/**")` and `@Order(1)` runs an
   `InternalTokenFilter` (constant-time `MessageDigest.isEqual`). Services without internal endpoints
   (notification) still get the chain, and with no token configured it rejects everything.
4. **Gateway edge filter.** A single `WebFilter` at highest precedence: 404 for `/internal/**` (before security, so
   even authenticated callers get 404), strips every `X-User-*` header and `X-Internal-Token`, and generates or
   propagates `X-Correlation-Id` (sanitised to `[A-Za-z0-9._-]{1,64}`).
5. **Compose env handling.** Required variables use `${VAR:?message}`, so a missing `.env` fails fast with a clear
   error. `application-dev.yml` supplies `localhost` host-port defaults for running a service outside Docker. In
   containers the compose env vars override those defaults even though the `dev` profile is active.
6. **Postgres memory.** With the 256m limit, Postgres runs with `shared_buffers=64MB` and `max_connections=40`.
7. **Kafka healthcheck heap.** The CLI healthcheck JVM runs with `KAFKA_HEAP_OPTS=-Xmx64m`. Otherwise it inherits
   the broker's 256m–512m heap and could OOM the 768m container.
8. **Memory limits (user requirement, overrides spec §12.2).** Every JVM runs with `mem_limit: 384m` and
   `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"`. Kafka 768m,
   MySQL 512m (`--innodb-buffer-pool-size=64M --performance-schema=OFF`), MongoDB 384m (`--wiredTigerCacheSizeGB
   0.25`), each Postgres 256m, nginx 64m. The Dockerfile keeps the spec default `-XX:MaxRAMPercentage=75`; the
   compose environment overrides it.
9. **Single backend Dockerfile.** `backend/Dockerfile` takes `--build-arg MODULE=<module>` and builds
   `-pl <module> -am` with a BuildKit cache mount for `~/.m2`. The runtime image (`eclipse-temurin:25-jre`) installs
   `curl` for the compose healthchecks (it is absent from the 25 image) and runs as a non-root `placehub` user.
10. **Seed data.** Repeatable Flyway migrations (`R__seed_dev_*.sql`) under `classpath:db/seed-dev`, a location that
    only `application-dev.yml` adds. Inserts are idempotent (`ON CONFLICT DO NOTHING` / `INSERT IGNORE`). Passwords are
    a precomputed BCrypt(10) hash of `PlaceHub@123`. Deadlines are `UTC_TIMESTAMP(6) + INTERVAL n DAY`.
11. **MySQL mapping.** UUIDs as `CHAR(36)` (`hibernate.type.preferred_uuid_jdbc_type=CHAR`), Instants as
    `DATETIME(6)` UTC (`preferred_instant_jdbc_type=TIMESTAMP`, `jdbc.time_zone=UTC`). Enums carry
    `@JdbcTypeCode(VARCHAR)`: Hibernate 7 otherwise maps `@Enumerated(STRING)` to a native MySQL `ENUM` and
    `ddl-auto=validate` fails. Job descriptions use `columnDefinition = "TEXT"`.
12. **Job visibility.** Non-published jobs (DRAFT and CLOSED) return 404 to students and non-owning recruiters. Edits
    by a non-owner return 403, as the spec scenario requires. A published job's deadline can only be moved to a
    future time.
13. **Circuit breakers.** Resilience4j `CircuitBreakerRegistry` is used directly: count-based window 10, minimum 5
    calls, 50% failure rate, 10 s open. The calls run synchronously on the request thread, so the MDC correlation id
    still reaches the Feign interceptor; the Spring Cloud CircuitBreaker wrapper would run them on a TimeLimiter
    thread pool. Only infrastructure failures (I/O, 5xx, timeouts) count as failures. A Feign 404 becomes a 404
    ("Job not found"). Anything else, including an open circuit, becomes 503. Feign timeouts are connect 2 s and
    read 3 s per client.
14. **Application persistence.** `ApplicationService` makes the remote calls outside any transaction. `ApplicationWriter`
    is the only class that mutates applications, and each change happens in one `@Transactional` method together
    with its outbox row. `OutboxWriter` uses `Propagation.MANDATORY`, so it cannot write outside that transaction.
    The duplicate check relies on the unique constraint: `DataIntegrityViolationException` becomes 409.
15. **Outbox backoff.** Added a `next_attempt_at` column with exponential backoff (2^n s, at most 60 s). With a 1 s
    relay and a 5 s send timeout, 10 back-to-back attempts would otherwise use up the retry budget within about a
    minute of a Kafka outage. The relay also stops a batch at its first failure. The producer uses
    `max.block.ms=5000` so a dead broker can't hang the relay thread for the default 60 s.
16. **Recruiter note visibility.** `recruiterNote` is returned to the owning recruiter and admins only, never to the
    student. It reads as internal review feedback, and the spec is silent.
17. **422 phrase.** `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7 in favour of `UNPROCESSABLE_CONTENT`.
    It is still used so the error body reads "Unprocessable Entity", matching the spec's example.
18. **Kafka health.** Boot 4 has no Kafka health indicator, so Application and Notification Service each register a
    small `kafka` `HealthIndicator` that calls `describeCluster` with a 3 s timeout. While Kafka is down the
    containers report DOWN, but they keep serving: Application Service accepts applications and buffers events in the
    outbox.
19. **Notification documents.** UUIDs are stored as strings. Indexes are created explicitly in `IndexInitializer`
    (an `InitializingBean`, so they exist before the listener containers start), with
    `spring.data.mongodb.auto-index-creation=false`. Pagination uses `MongoTemplate` with an explicit count.
20. **Frontend.** Pinned to well-known majors (React 19.2, React Router 7, Vite 7, TypeScript 5.9, Vitest 3.2,
    ESLint 9) instead of the newest TS 7 / React Router 8 / Vite 8 / ESLint 10, to keep an unattended build low-risk.
    Styling is plain CSS: `tokens.css` plus CSS modules. An event bus (`toastBus`) lets the Axios interceptor raise
    toasts. The token lives in `sessionStorage` and its `exp` is checked on load.
21. **Test JVM time zone.** Surefire and failsafe run with `-Duser.timezone=UTC`: the host zone `Asia/Calcutta` is a
    legacy name that PostgreSQL 17 rejects at connection time. Failsafe's `classesDirectory` is set explicitly,
    because overriding the plugin configuration dropped Boot's default and failsafe ran against the repackaged jar.
22. **Smoke script extras.** Besides the 16 spec scenarios, the script also checks withdraw → 409 on a second
    withdraw → recruiter notified (a Definition of Done item).

## v3 (Phase 9) decisions

### 9.0 Groundwork
23. **Git baseline.** The repo was already initialised on `main` at the user's request after v2 (`305851c`). The
    requested baseline commit `chore: baseline v2 build` (`2144986`) adds Section 20 and the ignore rules on top of it.
24. **Extension leftovers.** `.vscode/settings.json` is kept with the two Java build settings (the unrelated Python
    setting was dropped), and `.vscode/extensions.json` recommends the Java, Spring, ESLint and Playwright extensions.
    The rest of `.vscode/` is ignored. `.github/modernize/` holds the app-modernization extension's own hook scripts;
    it is kept on disk so the extension keeps working, and it is gitignored.
25. **Health groups.** `management.endpoint.health.probes.enabled=true`; liveness includes `livenessState,ping`;
    readiness includes `readinessState` plus `db` (user, job, application) or `mongo` (notification). Compose
    healthchecks use `/actuator/health/liveness`, so a Kafka, DB or Eureka outage never marks a JVM unhealthy.
26. **Kafka unresolvable host (bug found by the 9.0 outage test).** When the kafka container is stopped its DNS name
    stops resolving, and Kafka client constructors throw `ConfigException: No resolvable bootstrap urls`. Two of these
    ran during startup and aborted the context: the health indicator's `AdminClient` and the notification listener
    consumers. Fix: the health indicator creates its admin client lazily, and the notification listeners use
    `auto-startup=false` and are started by `KafkaListenerStarter` (scheduled every 5 s; it waits for the host to
    resolve, then calls `KafkaAdmin.initialize()` and starts the registry). The producer was already lazy, so the
    outbox simply retries.
27. **Timezone.** `TimeZone.setDefault(UTC)` runs first in every `main`, and the spring-boot plugin passes
    `-Duser.timezone=UTC`. Verified by `spring-boot:run` on an India Standard Time host with the plugin flag
    overridden.
28. **Enforcer.** `requireJavaVersion [25,)` and `requireMavenVersion [3.9,)` in the parent POM's `<plugins>`.
29. **Playwright.** Pinned `@playwright/test` 1.56.1 and `@axe-core/playwright` 4.11.0 (Chromium only), in
    `frontend/placehub-web/e2e`. Tests arrange their own recruiters, companies and jobs through the gateway API, so
    seeded demo data stays clean. The baseline suite of 8 v2 flows passed on the first run and found no UI bugs.

### 9.1 Numeric CTC
30. `jobs.ctc_lpa DECIMAL(6,2)` (Flyway V2). Publishing a FULL_TIME job without it returns 400, and so does a PUT on
    a published FULL_TIME job that drops it. The existing smoke job and the JobServiceIT payload now send `ctcLpa`;
    they were updated in place, not removed.
31. The seed is a repeatable migration, so editing it re-runs it: idempotent `UPDATE ... WHERE ctc_lpa IS NULL` sets
    the CTC on existing databases, and `INSERT IGNORE` / `ON CONFLICT DO NOTHING` add recruiter2 and Meridian.
32. `ClientDtos.JobSummary` keeps a 7-argument constructor for older callers. Snapshots are nullable because rows
    created before v3 have none.
33. Playwright fix (a test bug, not an app bug): the admin users table is newest-first, so the test searches for the
    seeded student instead of expecting it on page 1.

### 9.2 Resumes
34. **Storage.** A `resumes` bytea table in users-db, with a partial unique index guaranteeing one current version
    per user. Listings use a Spring Data interface projection, so metadata queries never load the PDF bytes.
35. **Delete rule across services.** User Service cannot see applications, so Application Service calls
    `POST /internal/users/{u}/resumes/{r}/reference` while applying (the existing application-to-user direction). A
    referenced version can never be deleted (409) or pruned. Pruning keeps the newest 3 versions plus referenced
    ones.
36. **Validation.** The `%PDF-` magic bytes are authoritative. The declared type must be pdf, x-pdf or octet-stream
    (anything else is 415). The size limit is exactly 2 097 152 bytes (larger is 413). Filenames keep only the last
    path segment, allow `[A-Za-z0-9._ ()-]`, and are capped at 100 characters ending in `.pdf`. Bug found by
    `ResumeValidatorTest`: leading dots were stripped before the `.pdf` suffix, so `....pdf` became `pdf.pdf`; the
    order is now fixed.
37. **Body limits.** The gateway sets a 1 MB `RequestSize` on every route and 2.2 MB on a dedicated
    `POST /api/users/me/resume` route (`order(-1)`). User Service multipart is 2 MB per file and 2.2 MB per request.
    `MaxUploadSizeExceededException` maps to 413.
38. **422 shape.** A new `Violations` collector puts every failing reason in one 422: `details.reasons` (strings,
    unchanged) plus `details.violations[{code,message}]`. Its headline is "You are not eligible for this job" when any
    NOT_ELIGIBLE reason exists (as in v2), otherwise the single violation's message.
39. **Downloads.** `PdfDownloads` (common) sets `application/pdf`, `attachment; filename*=UTF-8''...`, `nosniff`, and
    `private, no-store`. The frontend fetches the PDF with axios (`responseType: blob`) and opens a blob URL. The tab
    opens synchronously so pop-up blockers allow it.
40. **Smoke on Windows.** Native curl can't read MSYS `/tmp` paths inside `-F file=@...`, so the script converts the
    path with `cygpath -m` when it's available. The smoke student now uploads a resume first, because resumes are
    required by default.
41. **Playwright.** Headless Chromium downloads PDFs instead of rendering them, so the resume test asserts the
    authenticated blob fetch (bearer header, no token in the URL, `application/pdf`, `nosniff`) and the popup, not
    the popup's URL. The admin jobs test searches instead of expecting the seeded job on page 1.

### 9.3 Placement policy engine
42. **Package and storage.** `com.placehub.application.policy` (pure `PolicyModel`, `PolicyEvaluator`,
    `PolicyValidator`, `ConsentText`; services; entities). Bands are stored as JSON text on the version row.
    Repositories are nested interfaces, so `@EnableJpaRepositories(considerNestedRepositories = true)` is set
    explicitly. The default policy v1 is a versioned migration (V4) with a fixed id, not dev-only seed data. A partial
    unique index guarantees exactly one active version.
43. **Evaluator rules** (order): locked → BLOCK; internship not covered → ALLOW; missing CTC → BLOCK; unplaced →
    ALLOW; otherwise the band matched with exact arithmetic (`job >= current*from`, `job < current*to`). The ratio is
    reported rounded HALF_UP to 2 decimals but never used for matching (17.99/12 rounds to 1.50 and is still BLOCK).
44. **Apply pipeline** (`ApplyRequirements`, shared with the preview): Job Service eligibility → resume
    (`policy.resumeRequired`) → policy (lock, band, consent, consent limit) → one 422 with every violation → duplicate
    pre-check (409) → persist; the DB unique constraint remains the final guard. A pending consent does not make
    `canApply` false in the preview, because consent is given in the apply request.
45. **Consent.** Valid only when `accepted == true` and the signed name matches the student's full name (trimmed,
    case-insensitive, whitespace collapsed). The record stores the rendered text and its sha256, the band label, the
    policy version, the last `X-Forwarded-For` entry and the user agent. It is created inside `ApplicationWriter.create`
    after the application row exists, so both commit together. Consent sent for a non-consent band is ignored.
    Because consent is bound to the `jobId` of the same request, it cannot be replayed onto another job.
46. **Selection and locking.** `ApplicationWriter.changeStatus(...SELECTED)` calls `PlacementService.recordOnCampus`
    (MANDATORY propagation) for FULL_TIME applications. The lock decision uses the application's
    `lockOnPlacementSnapshot` (the band at apply time), so policy edits never change an existing application. A first
    placement (unplaced → no band) never locks. Withdrawals of the other non-terminal applications run in the same
    transaction as SYSTEM, each with a history row and a status-changed event (`actorType: SYSTEM`, `reason`,
    `changedByUserId: null`). They follow the *active* policy's `withdrawOtherApplicationsOnLock`.
47. **Events.** Status-changed gains the optional `actorType` and `reason`; the fixture was updated and
    `eventVersion` stays 1. A new topic `placehub.placement.recorded.v1` (key studentUserId) and DLT, with fixture
    `contracts/events/placement-recorded.v1.json` and contract tests on both sides. Outbox rows are relayed `ORDER BY
    seq` (new BIGSERIAL), because one transaction can write several events with the same timestamp.
48. **Admin endpoints** live under `/api/admin/{policy,placements,students,consents}`, all routed by the gateway to
    application-service. The class-level `@PreAuthorize("hasRole('ADMIN')")` covers them all. The consent CSV is
    RFC 4180-quoted with formula-injection protection (a leading `= + - @ \t \r` gets a `'` prefix).
49. **Status history pulled forward from 9.4** (V5), because policy withdrawals must record SYSTEM transitions.
    History is ordered by `seq`.
50. **Deviation: UI timing.** The 9.3–9.5 frontend (policy editor, simulator, placements, consents, consent modal,
    placement banner, policy badges, bulk actions, portals) is built in the 9.6 redesign, so no page is built twice.
    Backend behaviour was verified per sub-phase through tests and the gateway.

### 9.4 Auto-shortlisting
51. Job Service exposes the *effective* criteria in the internal summary (configured value, or the eligibility value
    when unset). Application Service therefore never needs the job's eligibility rules. The strictness rule is
    enforced on create and update (400). A null `screeningMode` on update keeps the current mode, so v2 clients
    can't accidentally switch it off.
52. `ApplicationWriter.create(app, consent, autoShortlist)` writes, in one transaction: application → creation history
    → APPLICATION_CREATED (`autoShortlisted: true`) → consent → SYSTEM shortlist history → APPLICATION_STATUS_CHANGED
    (`actorType: SYSTEM`, `changedByUserId: null`). The two events relay in `seq` order. They land on different
    topics, so order is guaranteed only at the outbox (Kafka orders within a partition, per topic).
53. **Bulk status:** at most 100 distinct ids, each through the single-update path in its own transaction. Ids that
    don't exist or belong to another recruiter's job both come back as `NOT_FOUND`, so a bulk call cannot probe other
    recruiters' applications.
54. **History visibility:** participants only (404 otherwise). For the student, other users' ids and recruiter notes
    on non-withdrawal transitions are blanked (the note is recruiter-internal, decision 16).

55. **Code splitting:** each role area is one lazy chunk loaded through a barrel (`pages/student|recruiter|admin`),
    so navigation inside an area never waits for another chunk. `scripts/size-check.mjs` (`npm run size`, also run in
    the frontend Dockerfile) fails the build when the initial JS+CSS+HTML exceeds 120 KB gzip, a role chunk plus the
    shared chunks it imports exceeds 80 KB, or a role chunk is referenced from index.html. The preloaded Inter woff2
    (47 KB, already compressed) is reported separately rather than counted in the JS/CSS budget.
56. **Auto-shortlisted flag (app V6):** a boolean on the application, set in the same transaction as the SYSTEM
    shortlist, so recruiter lists show the "Auto" badge without reading history per row.
57. **Portal redirects:** an anonymous visitor to `/student|recruiter|admin/**` is sent to that area's login portal;
    shared pages go to the `/login` chooser. Sign-out returns to the user's own portal.
58. **Theme:** `data-theme` is applied in `main.tsx` before the first render (no flash); preference in localStorage
    behind try/catch, default follows `prefers-color-scheme`. All colours are tokens; component CSS has no hex values.
59. **CLS fixes found by Lighthouse:** headers that fill in after a fetch reserve fixed height (single line, ellipsis)
    so the table skeleton below does not move; the mobile user-menu button gets an aria-label (its text is hidden).
60. **No gateway response compression:** Lighthouse suggests it for JSON, but compressing responses that carry
    tokens alongside user-controlled input is BREACH-prone and scores are already ≥ 96; nginx gzips static assets.
61. **Lighthouse method:** user flow (lighthouse 12.8.2 + puppeteer-core, `scripts/lighthouse/`) in one tab, because
    the session token is per-tab sessionStorage. Default mobile emulation and throttling against the nginx build.

## Security review (Phase 9.7)

An independent reviewer (fresh-context subagent given only the repo and the permission matrix of §2 + §20.7)
checked the matrix live and in code. No critical or high findings. Confirmed correct: resume IDOR (owner-scoped
lookups, 404 for outsiders), consent forgery/replay (server computes version, band, text and hash; signed name must
match; one row per application), admin-only policy/placement/consent endpoints (gateway and direct), bulk status and
auto-screen ownership, portal probing (403 only after a correct password, identical 401s), upload abuse (magic bytes,
413/415, filename sanitising, attachment + nosniff), sort whitelists and LIKE escaping, hidden recruiter notes and
consent IP/UA, gateway header stripping and `/internal` 404s. Each fix below was preceded by a failing test.

| # | Severity | Finding | Resolution |
|---|----------|---------|------------|
| F1 | Medium | A deactivated account's token kept working until expiry (≤ 60 min) | Fixed. Gateway `AccountStatusFilter` checks the status via `/internal/users/{id}` (cached 15 s, fails open if the User Service is unreachable) and returns 401 "Your account is deactivated". Tests: `AccountStatusFilterTest` (3), `GatewaySecurityTest` +1; verified live (401 within 15 s). |
| F2 | Medium | Service, DB, Kafka, Eureka and observability ports published on all interfaces; example secrets in use | Fixed for exposure: those ports bind to `127.0.0.1` (gateway 8080 and frontend 5173 stay public). Services log a WARN when `JWT_SECRET`/`INTERNAL_SERVICE_TOKEN` are the committed example values (`ExampleSecretsTest`). Refusing to start was rejected: the required quick start is `cp .env.example .env`. |
| F3 | Low | Consent client IP forgeable via `X-Forwarded-For` on direct calls | Fixed. The gateway strips client-sent `X-Gateway-Token` and adds its own (internal secret); application-service uses the last XFF entry only on attested requests, the socket address otherwise. Tests: `ClientIpTest` (2), `GatewaySecurityTest` header test, `PolicyFlowIT`. |
| F4 | Low | Single status update returns 403 (not 404) for another recruiter's application | Accepted. The v2 smoke check 14 and the spec require 403 here; ids are random UUIDs. |
| F5 | Low | No sign-in throttling; registration reveals existing emails (409) | Throttling fixed: 10 failed sign-ins per email per 15 min → 429, unknown emails counted the same way (`LoginThrottleTest` 2, `AuthServiceTest` +1; verified live). The 409 on duplicate registration is spec behaviour and kept (would need email verification). |
| F6 | Low | Students received `recruiterOwnerUserId` | Fixed for applications (null in the student view; `ApplicationServiceTest`). Job/company owner ids stay: the job API contract exposes them to all roles. |
| F7 | Low | A published job's CTC/employment type could change (e.g. relabel as internship to skip the policy) | Fixed: 409 on change after publishing; the job form disables both fields (`JobServiceTest` +1). |
| F8 | Low | Resume marked referenced before create; consent-limit check-then-insert race (suspected) | Race fixed: `ApplicationWriter` re-counts under `pg_advisory_xact_lock` per student (`ApplicationWriterConsentLimitTest` 2). The early reference mark is kept on purpose (fail-safe: worst case an unused version cannot be deleted). |
| F9 | Info | Body validation before the role check on admin POST/PUT; public actuator/OpenAPI; no CSP; placement implied in withdrawal notices; shared HS256 secret | Fixed: URL rule `/api/admin/**` → ADMIN in the common chain (`PolicyFlowIT` +2 assertions). Accepted/documented: OpenAPI and metrics are only on loopback debug ports (gateway `/actuator/prometheus` stays for the observability profile); CSP needs the deploy-specific API origin (listed as hardening); the withdrawal wording is intended product behaviour; HS256 is the locked spec choice (RS256 + JWKS listed as hardening). |

## Checklist (mirrors spec section 17; proof recorded in CLAUDE.md)
See `CLAUDE.md` section 4.
