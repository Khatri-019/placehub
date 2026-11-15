# CLAUDE.md — PlaceHub Lite operational memory

## 1. Project summary
PlaceHub Lite is a campus placement system: 4 Spring Boot business services (user, job, application,
notification) + Eureka + Spring Cloud Gateway, Kafka with a transactional outbox, DB-per-service, and a
React 19 + Vite frontend, all runnable with `docker compose up --build`.
**BUILD_SPEC.md is the authority.** This file is short operational memory; longer rationale lives in
IMPLEMENTATION_NOTES.md. On a new session/compaction: read this file first, resume from "Current state".

## 2. Locked decisions
- Versions (verified on start.spring.io 2026-11-15): Spring Boot **4.1.2**, Spring Cloud **2025.1.4**,
  springdoc-openapi **3.2.0**, Java **25 LTS** (`maven.compiler.release=25`; images use temurin 25),
  Maven wrapper 3.3.4 → Maven 3.10.0.
- Ports: eureka 8761, gateway 8080, user 8081, job 8082, application 8083, notification 8084, frontend 5173→80,
  users-db 5433, jobs-db 3307, applications-db 5434, notifications-db 27018, kafka 29092 (host) / 9092 (internal).
- DB per service, each in its own container + credentials: users-db (postgres:17), jobs-db (mysql:8.4),
  applications-db (postgres:17), notifications-db (mongo:8). MySQL UUIDs as CHAR(36).
- Auth: User Service issues HS256 JWT (`iss=placehub-user-service`, claims sub/email/role/name). Gateway
  pre-validates; **every business service is a Resource Server validating the JWT itself**; `role` → `ROLE_<role>`.
  `/internal/**` needs `X-Internal-Token` (constant-time compare, separate SecurityFilterChain ordered first);
  gateway returns 404 for `/internal/**` and strips `X-User-*` / `X-Internal-Token` from client requests.
  JWT_SECRET: raw UTF-8 bytes, or `base64:` prefix for Base64; fail fast if < 32 bytes.
- Outbox mandatory: `outbox_events` written in same tx; `@Scheduled(fixedDelay=1000)` relay, `FOR UPDATE SKIP LOCKED`,
  batch 50, `send().get(5s)`, cap 10 attempts → FAILED. **Spec-silent addition:** `next_attempt_at` exponential
  backoff (2^n s, max 60s) so a multi-minute Kafka outage doesn't exhaust attempts; relay stops the batch on first
  failure. `correlation_id` column → Kafka header `X-Correlation-Id`. Producer `max.block.ms=5000`.
- Kafka: `apache/kafka` KRaft single broker, String serializer + manual Jackson 3 mapping, topics 3 partitions,
  DLTs 3 partitions; consumer `DefaultErrorHandler(FixedBackOff(1000,3))` → `<topic>.DLT`; invalid events non-retryable.
- Notification idempotency: unique Mongo index `(eventId, recipientUserId)`; `insert` + catch DuplicateKeyException.
- Fixed seed UUIDs (spec §13): admin …0001, recruiter …0002, student …0003, student2 …0004 (prefix 00000000-…),
  company 10000000-…-0001, jobs 20000000-…-0001..0004. Password `PlaceHub@123`. Seeds via Flyway location
  `classpath:db/seed-dev` added only in `application-dev.yml` (repeatable `R__` migrations, idempotent inserts).
- Spec-silent: shared `backend/placehub-common` jar (servlet-only auto-config) holds ONLY utilities: error model,
  ApiException types, global exception handler, correlation-id filter, JSON 401/403 handlers, JWT decoder support,
  internal-token filter, PageResponse/paging helper. No entities, no event DTOs. Gateway (reactive) has its own copies.
- Spec-silent: circuit breakers via Spring Cloud `CircuitBreakerFactory` (Resilience4j) wrapping Feign calls;
  only 5xx/IO/timeouts are recorded; Feign 404 → 404. TimeLimiter 8s (> Feign read timeout 3s).
- Spec-silent: notification `type` values: APPLICATION_CREATED (student), NEW_APPLICANT (recruiter),
  APPLICATION_STATUS_CHANGED (student), APPLICATION_WITHDRAWN (recruiter).
- **Memory limits (user override of spec §12.2, Docker capped at 8 GB):**
  - each Spring Boot container `mem_limit: 384m`,
    `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"`
  - kafka 768m, `KAFKA_HEAP_OPTS="-Xms256m -Xmx512m"`; mysql 512m `--innodb-buffer-pool-size=64M --performance-schema=OFF`
  - mongo 384m `--wiredTigerCacheSizeGB 0.25`; each postgres 256m; frontend nginx 64m
  - prometheus/grafana only under profile `observability` (off by default)
  - build with `COMPOSE_PARALLEL_LIMIT=2` to avoid OOM during image builds
  - Measured `docker stats --no-stream` after a clean `down -v --rmi local` + `up --build` + smoke run
    (2026-11-15): application-service 312 MiB (81%), job 270 MiB (70%), user 266 MiB (69%), notification 221 MiB
    (58%), discovery 189 MiB (49%), gateway 180 MiB (47%), kafka 370 MiB (48%), jobs-db 209 MiB (41%),
    notifications-db 175 MiB (45%), users-db 38 MiB, applications-db 38 MiB, frontend 15 MiB. **Total ≈ 2.28 GiB.**
    No container OOM-killed (exit 137) or restarted, so no limit was raised. application-service is the closest to its
    limit; raise only its `mem_limit` (e.g. 448m) if it ever exits 137.
- Git: repo was **not** initialised at start; per instructions ("if git is initialised") no commits are made.

## 3. Commands (run from repo root unless noted; Git Bash)
- **JDK:** host default is JDK 22, so always `export JAVA_HOME="$HOME/.jdks/jdk-25.0.5+8"` first (Temurin 25.0.5,
  downloaded + sha256-verified this session).
- Backend build+tests: `cd backend && ./mvnw verify` (Testcontainers ITs skip without Docker)
- Unit tests only for one module: `cd backend && ./mvnw -pl user-service -am test`
- Run one service locally: start infra via compose, then `cd backend && ./mvnw install -DskipTests` once and
  `./mvnw -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev` (verified on :18081 with
  `--server.port=18081 --eureka.client.register-with-eureka=false`: health UP, admin login 200)
- Frontend: `cd frontend/placehub-web && npm ci && npm run lint && npm test && npm run build`
- Stack up: `cp .env.example .env && COMPOSE_PARALLEL_LIMIT=2 docker compose up --build -d`
- Down: `docker compose down` · Reset (deletes data): `docker compose down -v`
- Observability: `docker compose --profile observability up -d`
- Smoke: `./scripts/e2e-smoke.sh` (needs bash, curl, jq)
- Memory: `docker stats --no-stream`

## 4. Progress tracker
Phases:
- [x] 0 Inspect & plan — repo had only BUILD_SPEC.md; versions verified via start.spring.io API
- [x] 1 Foundation — `./mvnw install -pl discovery-server,api-gateway` (GatewaySecurityTest 8/8 pass);
      compose infra + discovery + gateway all healthy; `/eureka/apps` lists API-GATEWAY; gateway `/internal/users/x` → 404
- [x] 2 User Service — `./mvnw verify -pl user-service -am`: 17 unit + UserServiceIT 7/7 (Postgres TC); compose:
      gateway login student@placehub.local 200, /api/users/me 200, student→/api/admin/users 403, Eureka lists USER-SERVICE
- [x] 3 Job Service — `./mvnw verify -pl job-service -am`: 20 unit (EligibilityEvaluatorTest 10, JobServiceTest 10) +
      JobServiceIT 3/3 (MySQL TC); compose: seeded student sees 3 PUBLISHED jobs via gateway, draft job → 404
- [x] 4 Application Service — `./mvnw verify -pl application-service -am`: 96 unit (72 transition pairs,
      ApplicationServiceTest 13, DependencyGatewayTest 3, OutboxRelayTest 4, EventContractTest 2, TransactionBoundaryTest 2)
      + ApplicationServiceIT 4/4 (PG+Kafka TC, WireMock: apply→outbox→Kafka, 422, 503, 404, 409);
      compose: student2 → job1 = 422 with 3 reasons via gateway (Feign through Eureka)
- [x] 5 Kafka + Notification Service — `./mvnw verify -pl notification-service -am`: 12 unit (EventParserTest 5 incl.
      contract fixtures, NotificationFactoryTest 3, NotificationServiceTest 4) + NotificationServiceIT 2/2 (Mongo+Kafka TC:
      duplicate delivery → 1 per recipient, read scoping, invalid event → DLT without blocking); compose: fresh student
      applies to job 2 → APPLICATION_CREATED notification after 2 s; correlation id visible in consumer log;
      Eureka lists all 5 clients
- [x] 6 Frontend — `npm run lint` 0 warnings, `npm test` 22/22 (ProtectedRoute 6, Login 5, EligibilityPanel 4,
      StatusControl 7), `npm run build` OK; compose frontend serves `/` and SPA deep links (200); preflight + GET from
      :5173 return exactly one Access-Control-Allow-Origin; bundle references only http://localhost:8080
- [x] 7 Integration & hardening — full `./mvnw clean verify` BUILD SUCCESS (160 unit + 16 IT, 0 skipped);
      smoke 34/34; 503 + Kafka-down demos; Swagger UI 200 on 8081–8084; health shows db/mongo/kafka/discovery;
      observability profile: 6/6 Prometheus targets up, Grafana dashboard provisioned; permission matrix reviewed
- [x] 8 Documentation & handoff — README (§18 sections 1–14), IMPLEMENTATION_NOTES (22 decisions), this file

Definition of Done (§17):
- [x] Six separate deployable Boot apps, each starts independently — 6 images/containers, each healthy; job-service
      and kafka stopped/started individually during the resilience demo
- [x] Own DB container + credentials per business service — users-db, jobs-db, applications-db, notifications-db
- [x] Eureka shows all five clients — `/eureka/apps`: API-GATEWAY, USER, JOB, APPLICATION, NOTIFICATION (phase 5)
- [x] Gateway routes match §3.4; `/internal/**` blocked — GatewaySecurityTest + smoke step 16 (404)
- [x] Every service validates JWT; internal endpoints need internal token — *IT tests (401 w/o token, 401 wrong
      internal token), CommonSecurityConfiguration used by all 4 services
- [x] Student/recruiter registration+login; admin not self-registrable — smoke 1–2, UserServiceIT.adminCannotBeSelfRegistered
- [x] Recruiter creates company, creates + publishes job — smoke 3, JobServiceIT
- [x] Students see only published jobs — smoke 4, JobServiceIT.seedDataAndBrowseRules, compose draft → 404
- [x] Eligibility validated by Job Service with all reasons — EligibilityEvaluatorTest, compose student2 → 3 reasons
- [x] Apply: eligible 201, ineligible 422, duplicate 409, dependency down 503 — smoke 5–7; 503 via
      `docker compose stop job-service` → "Eligibility could not be verified. Try again shortly."
- [x] Recruiter views applicants, updates status with transition validation — smoke 9–11, ApplicationStatusTest (72)
- [x] Student can withdraw; recruiter notified — smoke "extra" step (withdraw 200, re-withdraw 409, APPLICATION_WITHDRAWN)
- [x] Events via outbox → Kafka → consumed idempotently — ApplicationServiceIT, NotificationServiceIT (duplicate → 1
      per recipient); Kafka stopped → apply 201 → notification ~8 s after Kafka restart
- [x] Users only see own notifications — smoke 13, NotificationServiceIT read scoping
- [~] Frontend supports every page in §7.2 against live data — all routes implemented on the typed API layer;
      lint/tests/build pass; served by nginx with working CORS. NOT click-tested in a browser (no browser tool)
- [x] `docker compose up --build` from clean clone with `.env` from `.env.example` — `down -v --rmi local`, `rm .env`,
      `cp .env.example .env`, `up --build -d` → all 12 containers healthy; smoke 34/34 (no git, so "clean clone" =
      volumes + images removed; build contexts exclude target/ and node_modules via .dockerignore)
- [x] Seed data dev-only, fixed UUIDs, supports demo — seed only in `classpath:db/seed-dev` (added by application-dev.yml);
      UserServiceIT/JobServiceIT use the seeded UUIDs; compose student2 → job1 = 3 reasons
- [x] `./mvnw verify` passes; frontend build/lint/tests pass — see phase 7 and 6
- [x] `scripts/e2e-smoke.sh` passes all 16 checks — 34 assertions (16 spec steps + withdraw extra), 0 failed
- [x] README accurate; no real secrets; no TODOs in core workflows — grep TODO/FIXME: none in code; only local
      example secrets in .env.example; `.env` git-ignored

## 5. Current state
- **All phases complete.** Stack is running from a clean rebuild (smoke data only; seeded students have no
  applications). Open item: frontend not click-tested in a real browser (no browser tool in this session).
- If resuming: nothing pending. To re-verify: `docker compose up --build -d`, `./scripts/e2e-smoke.sh`,
  `cd backend && ./mvnw verify`, `cd frontend/placehub-web && npm ci && npm run lint && npm test && npm run build`.
- Frontend pinned to known majors (React 19.2, React Router 7, Vite 7, TS 5.9, Vitest 3.2, ESLint 9) rather than the
  newest majors (TS 7, RR 8, Vite 8, ESLint 10) to reduce risk. No browser automation available: UI verified via
  tests/build + API checks, not click-through.
- Application Service decisions: circuit breakers use Resilience4j `CircuitBreakerRegistry` directly (synchronous →
  MDC correlation id preserved in the Feign interceptor; no Spring Cloud CB thread pool / TimeLimiter). Recruiter
  note is hidden from students. 422 uses `HttpStatus.UNPROCESSABLE_ENTITY` to keep the "Unprocessable Entity" phrase.
- Job Service: enum columns use `@JdbcTypeCode(VARCHAR)` (Hibernate would otherwise expect MySQL ENUM);
  `preferred_uuid_jdbc_type=CHAR`, `preferred_instant_jdbc_type=TIMESTAMP`; description `columnDefinition=TEXT`.
- Gotcha: bash heredocs in this tool collapse `\\` → `\`; write Java/regex/Dockerfile content with Write/Edit.
- Runtime image installs curl (temurin:25-jre lacks it; needed for healthchecks).
- Env notes: Docker Desktop launched manually at session start; no system mvn (use wrapper); jq installed to
  ~/bin/jq.exe (Git Bash PATH).

## 6. Known issues / deviations from spec
- Java 25 LTS is the runtime and compiler target; `BUILD_SPEC.md`, `pom.xml`, and the Docker images are aligned.
  Local builds use `~/.jdks/jdk-25.0.5+8`.
- VS Code's Java extension (redhat.java) auto-builds into `target/` and once caused a `NoClassDefFoundError` during
  failsafe. If it recurs, run `./mvnw clean verify` or pause the IDE build.
- Surefire/failsafe run with `-Duser.timezone=UTC`: host TZ `Asia/Calcutta` is rejected by Postgres 17 JDBC
  ("invalid value for parameter TimeZone"). Failsafe `classesDirectory` is set explicitly to target/classes.
- §12.2 says JVM `mem_limit: 512m` / `MaxRAMPercentage=75`: overridden by user to 384m and the flags above.
  Dockerfile keeps spec default `-XX:MaxRAMPercentage=75`; compose env overrides it.
