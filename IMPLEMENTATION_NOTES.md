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

## Checklist (mirrors spec section 17; proof recorded in CLAUDE.md)
See `CLAUDE.md` section 4.
