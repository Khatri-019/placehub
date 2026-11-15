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
  v3: application-service raised to **512m** (user approved more resources; was 344/384 = 89.6%) → 343/512 (67%).
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
- [x] 9.3 Placement policy (backend) — app V4 (policies + seeded v1, states, placements, consents, audit, snapshots,
      outbox seq) + V5 history; PolicyEvaluatorTest 19 (12 boundary rows), PolicyValidatorTest 5,
      ConsentAndSummaryTest 5, ApplicationServiceTest +7 policy cases; PolicyFlowIT 2 (consent 422/201 + row,
      lock-band selection → lock + SYSTEM withdrawals + placement events, STUDENT_LOCKED, unlock audited, CSV,
      403s, versioning keeps snapshots); notification +5 (placement topic, SYSTEM wording); app verify 152 tests;
      compose: policy v1 active, simulate 12→20 = REQUIRE_CONSENT 1.67; smoke 35/35; Playwright 10/10.
      Policy UI (admin pages, consent modal, banner, badges) is delivered with the 9.6 redesign
- [x] 9.4 Auto-shortlist (backend) — job V3 screening columns; JobServiceTest +1 (strictness 400, defaults, keep
      mode on null, effective criteria in summary); job verify 33; ApplicationServiceTest +5 (decision, apply flag,
      bulk per-item with foreign → NOT_FOUND, auto-screen ownership/mode, history visibility); PolicyFlowIT +1
      (CREATED then STATUS_CHANGED SYSTEM by seq, both SENT, 2-row history, manual reject, foreign 404);
      app+notif verify 178; smoke 35/35; Playwright 10/10
- [x] 9.5 Login portals — AuthServiceTest +2 (mismatch 403 naming the right login, wrong password still 401 for
      mismatched/unknown), UserServiceIT +1 (403/401/200/400); user verify 41. UI: /login chooser, /login/:portal,
      ProtectedRoute redirects to the area's portal, logout → own portal; Login.test +2, ProtectedRoute.test +1
- [x] 9.6 UI redesign — src/ui (Badge, Card/StatCard, DataTable, Modal/Drawer/ConfirmDialog, Tabs, Stepper/Timeline,
      Skeleton, EmptyState, Markdown), all pages redone + admin Policy (band editor, live validation, consent preview,
      diff, debounced draft simulator, history, audit)/Placements (lock/unlock reason, off-campus)/Consents (CSV);
      recruiter bulk status + auto-screen + Auto badge; app V6 auto_shortlisted. React.lazy chunks per role.
      Proof: vitest 33, lint 0, tsc 0; `npm run size` initial 109.4 KB gzip (font 47.1 KB woff2 reported apart),
      student 16.0 / recruiter 12.9 / admin 14.3 KB incl. shared; Lighthouse flow (mobile, prod nginx) Perf/A11y/BP/
      SEO: landing 99/100/100/100, login 97/100/100/100, student jobs 96/100/100/100, recruiter applicants
      97/100/100/100, CLS 0.000 all; axe WCAG 2.1 AA 0 violations on 8 pages × 2 themes; Playwright 18/18;
      app verify 143 unit + 9 IT; smoke 35/35
      Palette (tokens.css only; no hex elsewhere): light text #1c1b2e/bg #f7f6fa 15.67:1, muted #5b5a72/white 6.67:1;
      dark text #ecebf5/bg #14132a 15.37:1, muted #b6b4cf/#201e3e 7.91:1; white on primary #c2560f 4.53:1
- [x] 9.7 Security review — fresh subagent: 0 critical/high, 2 medium, 6 low, info (IMPLEMENTATION_NOTES table).
      Fixed test-first: F1 gateway account-status filter (deactivated → 401 ≤ 15 s; live), F2 debug ports on
      127.0.0.1 + example-secret WARN, F3 gateway-attested XFF, F5 login throttle 10/15 min/email (live 429), F6, F7
      CTC/type fixed after publish, F8 advisory-lock consent recount, F9 /api/admin/** URL rule. Accepted: F4 (smoke
      requires 403), dup-email 409, CSP/RS256 as hardening. Proof: gateway 13, user 29+10 IT, job 25+3 IT, app 147+9
      IT; smoke 64/64 (35 v2 + 29 v3); Playwright 24/24
- [x] 9.8 Final — clean `down -v` + `cp .env.example .env` + `up --build`: 12/12 healthy in 23 s; smoke 64/64; npm ci,
      lint 0, vitest 33, build, size 109.4/16.0/13.0/14.3 KB; Playwright 24/24; `./mvnw clean verify` 263 tests
      (238 unit + 25 IT), 0 skipped; Lighthouse landing 99/100/100/100, login 100/100/100/100, student jobs 95/100/
      100/100, recruiter applicants 96/100/100/100, CLS ≤ 0.021; docker stats ≈ 2.4 GiB, app-service 344/384 MiB
      (89.6%, below the 90% raise rule; no OOM, 0 restarts). Outputs in docs/verification/

Definition of Done (v2 §17): all [x] (see git history of this file). Frontend click-through gap closes with Playwright.

## 5. Current state
- Phase 9 (v3) complete and committed. Post-review: application-service mem 512m; logs clean (only startup
  Kafka coordinator rebalances and the intended example-secret WARNs).

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
