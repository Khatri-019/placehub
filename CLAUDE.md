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
- [x] 9.0 Groundwork — baseline commit 2144986; liveness=[livenessState,ping], readiness=[readinessState,db|mongo];
      compose → /health/liveness; Kafka stopped + restart app/notif → healthy in ~45 s, liveness UP, full health DOWN,
      apply 201 via gateway, outbox drained ~18 s after Kafka start, notification delivered; host spring-boot:run on
      India Standard Time with plugin TZ flag overridden → ready + login 200, 0 TZ errors; enforcer fails on JDK 22;
      Playwright baseline 8/8 (no UI bugs found); `./mvnw clean verify` 178 tests, 0 skipped
- [x] 9.1 Numeric CTC — job V2 migration + app V2 snapshots; JobServiceTest +3 (publish rule, summary), JobServiceIT
      summary ctc/type + Meridian; job+app modules 132 tests pass, user-service verify pass; compose: recruiter2 sees
      Quant 30.00/Senior DE 20.00, seeded CTCs 12/8/18, intern null; smoke 34/34; Playwright 8/8
- [x] 9.2 Resumes — user V2 `resumes`, app V3 `resume_id_snapshot`; ResumeValidatorTest 6 (magic bytes, 2 MB
      boundary, 415/413/400, traversal); UserServiceIT +2 (upload/download headers, 415, 413, prune 3 + referenced,
      409 delete, owner-scoped internal stream, other student 404, recruiter 403); ApplicationServiceTest +4 (IDOR
      outsiders 404 first, snapshot + pin, RESUME_REQUIRED with NOT_ELIGIBLE in one 422); ApplicationServiceIT +2;
      Gateway 1 MB / 2.2 MB RequestSize test; smoke 35/35; Playwright 10/10 (resume.spec 2)
- [ ] 9.3 Placement policy engine (versions, evaluator, consent, placements, locks, audit, admin UI)
- [ ] 9.4 Auto-shortlisting, status history, bulk status, auto-screen
- [ ] 9.5 Separate login portals (backend portal check + UI routes)
- [ ] 9.6 UI redesign (tokens, components, lazy routes, size budget, Lighthouse, dark theme)
- [ ] 9.7 Security review (fresh subagent) + test-first fixes
- [ ] 9.8 Tests, smoke, Playwright, screenshots, docs, clean final verification

Definition of Done (v2 §17): all [x] (see git history of this file). Frontend click-through gap closes with Playwright.

## 5. Current state
- Phase 9.3 (placement policy engine) next. Stack running with 9.2 images.

## 6. Known issues / deviations from spec
- Java 25 instead of 21 (user edit). VS Code redhat.java may race on `target/` → rerun `./mvnw clean verify`.
- Test/run JVMs use `-Duser.timezone=UTC` (host `Asia/Calcutta` rejected by Postgres 17). Failsafe classesDirectory set.
- Kafka clients throw on an unresolvable bootstrap host (stopped container) → health indicator lazy, notification
  listeners started by KafkaListenerStarter (auto-startup=false).
- Python edits must use encoding='utf-8' (cp1252 default crashed mid-write and truncated CLAUDE.md once).
- Bash heredocs in this tool mangle `\\` and some quotes → write source files with Write/Edit.
- §12.2 memory values overridden by the user (see §2). Dockerfile keeps MaxRAMPercentage=75; compose overrides.
- Smoke/IT payloads that publish FULL_TIME jobs must send `ctcLpa` and apply flows must upload a resume first (v3
  rules); those tests are updated in place, never removed.
