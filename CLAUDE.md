# CLAUDE.md — PlaceHub Lite operational memory

## 1. Project summary
PlaceHub Lite is a campus placement system: 4 Spring Boot business services (user, job, application, notification) +
Eureka + Spring Cloud Gateway, Kafka with a transactional outbox, DB-per-service, React 19 + Vite frontend, all
runnable with `docker compose up --build`. **BUILD_SPEC.md is the authority (v3 additions: Section 20).** Longer
rationale: IMPLEMENTATION_NOTES.md. On a new session/compaction: read this file first, resume from "Current state".

## 2. Locked decisions
- Versions: Spring Boot 4.1.2, Spring Cloud 2025.1.4, springdoc 3.2.0, **Java 25 LTS** (user switched from 21; images
  temurin 25), Maven wrapper 3.3.4 → Maven 3.10.0. Frontend: React 19.2, React Router 7, Vite 7, TS 5.9, Vitest 3.2.
- Ports: eureka 8761, gateway 8080, user 8081, job 8082, application 8083, notification 8084, frontend 5173→80,
  users-db 5433, jobs-db 3307, applications-db 5434, notifications-db 27018, kafka 29092 host / 9092 internal.
- DB per service: users-db (postgres:17), jobs-db (mysql:8.4, UUID CHAR(36)), applications-db (postgres:17),
  notifications-db (mongo:8). No cross-service DB access.
- Auth: User Service issues HS256 JWT (iss placehub-user-service; sub/email/role/name). Gateway pre-validates; every
  service re-validates (Resource Server, role → ROLE_x). `/internal/**` needs `X-Internal-Token` (constant time, chain
  @Order(1)); gateway 404s `/internal/**` and strips `X-User-*`/`X-Internal-Token`. JWT_SECRET ≥ 32 bytes (`base64:` ok).
- Outbox: row in same tx; relay `@Scheduled(fixedDelay=1000)`, `FOR UPDATE SKIP LOCKED`, batch 50, send().get(5s),
  10 attempts → FAILED, exponential `next_attempt_at` backoff (2^n s, max 60), stop batch on first failure,
  correlation id → Kafka header. Kafka: String serde + manual Jackson 3; 3 partitions; DLT after 3×1s retries.
- Notification idempotency: unique `(eventId, recipientUserId)`. Types: APPLICATION_CREATED, NEW_APPLICANT,
  APPLICATION_STATUS_CHANGED, APPLICATION_WITHDRAWN (+ v3 types, see §20).
- Seed UUIDs: users 00000000-…-0001 admin, -0002 recruiter, -0003 student, -0004 student2, -0005 recruiter2 (v3);
  companies 10000000-…-0001 Northstar, -0002 Meridian (v3); jobs 20000000-…-0001..0006. Password `PlaceHub@123`.
  Seeds only via `classpath:db/seed-dev` (added only in application-dev.yml), repeatable `R__`, idempotent.
- `backend/placehub-common`: DTO-free servlet utilities only (errors, correlation filter, security chains, paging).
- Circuit breakers: Resilience4j `CircuitBreakerRegistry` directly (synchronous, keeps MDC); only infra failures count.
- **Memory limits (user requirement; Docker capped at 8 GB):** JVM containers `mem_limit: 384m` with
  `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"`; kafka 768m
  (`KAFKA_HEAP_OPTS=-Xms256m -Xmx512m`); mysql 512m (`--innodb-buffer-pool-size=64M --performance-schema=OFF`);
  mongo 384m (`--wiredTigerCacheSizeGB 0.25`); postgres 256m each; nginx 64m; observability only under profile.
  Build with `COMPOSE_PARALLEL_LIMIT=2`. v2 measured total ≈ 2.28 GiB, application-service 312/384 MiB (81%), no OOM.
- Git: initialised on `main` after v2; commit per sub-phase (conventional commits); never push.

## 3. Commands (Git Bash, repo root unless noted)
- JDK: `export JAVA_HOME="$HOME/.jdks/jdk-25.0.5+8"` (host default is JDK 22). jq lives in ~/bin.
- Backend: `cd backend && ./mvnw verify` (ITs skip without Docker). One module: `./mvnw -pl job-service -am verify`.
- Local service: `./mvnw install -DskipTests` once, then `./mvnw -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev`.
- Frontend: `cd frontend/placehub-web && npm ci && npm run lint && npm test && npm run build && npm run e2e`.
- Stack: `cp .env.example .env && COMPOSE_PARALLEL_LIMIT=2 docker compose up --build -d`; reset `docker compose down -v`.
- Smoke: `./scripts/e2e-smoke.sh`. Memory: `docker stats --no-stream`. Observability: `--profile observability`.

## 4. Progress tracker
Phases 0–8 (v2): all [x]. Proof: `./mvnw clean verify` 160 unit + 16 IT pass; frontend lint/22 tests/build pass;
clean `down -v --rmi local` + `up --build` all healthy; smoke 34/34; 503 + Kafka-down demos; Lighthouse not run in v2.

Phase 9 (v3, BUILD_SPEC §20):
- [ ] 9.0 Groundwork: git baseline, .vscode/.github decision, liveness/readiness, TZ, enforcer, Playwright baseline
- [ ] 9.1 Numeric CTC (`ctcLpa`), recruiter2/Meridian seed, summary + snapshots
- [ ] 9.2 Resume upload/download, IDOR-safe application resume, frontend resume card
- [ ] 9.3 Placement policy engine (versions, evaluator, consent, placements, locks, audit, admin UI)
- [ ] 9.4 Auto-shortlisting, status history, bulk status, auto-screen
- [ ] 9.5 Separate login portals (backend portal check + UI routes)
- [ ] 9.6 UI redesign (tokens, components, lazy routes, size budget, Lighthouse, dark theme)
- [ ] 9.7 Security review (fresh subagent) + test-first fixes
- [ ] 9.8 Tests, smoke, Playwright, screenshots, docs, clean final verification

Definition of Done (v2 §17): all [x] (see git history of this file). Frontend click-through gap closes with Playwright.

## 5. Current state
- Phase 9.0 starting. Section 20 written to BUILD_SPEC.md.

## 6. Known issues / deviations from spec
- Java 25 instead of 21 (user edit). VS Code redhat.java may race on `target/` → rerun `./mvnw clean verify`.
- Test/run JVMs use `-Duser.timezone=UTC` (host `Asia/Calcutta` rejected by Postgres 17). Failsafe classesDirectory set.
- Bash heredocs in this tool mangle `\\` and some quotes → write source files with Write/Edit.
- §12.2 memory values overridden by the user (see §2). Dockerfile keeps MaxRAMPercentage=75; compose overrides.
- Smoke/IT payloads that publish FULL_TIME jobs must send `ctcLpa` and apply flows must upload a resume first (v3
  rules); those tests are updated in place, never removed.
