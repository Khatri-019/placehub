# PlaceHub Lite

PlaceHub Lite is a campus placement management system for students, recruiters and placement administrators (TPOs).
Recruiters publish jobs with eligibility rules. Students browse jobs, see exactly why they are or are not eligible,
and apply. The platform tracks each application through its lifecycle and notifies students and recruiters when
something changes. It is built as four Spring Boot microservices behind Eureka and Spring Cloud Gateway, with Kafka and
a transactional outbox for events, one database per service, and a React frontend.

> `BUILD_SPEC.md` is the specification. `IMPLEMENTATION_NOTES.md` records the decisions where the spec is silent.

---

## 1. Architecture

```mermaid
flowchart TB
    FE[React Frontend :5173] -->|HTTPS/JSON + Bearer JWT| GW[API Gateway :8080]
    GW --> US[User Service :8081]
    GW --> JS[Job Service :8082]
    GW --> AS[Application Service :8083]
    GW --> NS[Notification Service :8084]
    AS -->|Feign + X-Internal-Token| US
    AS -->|Feign + X-Internal-Token| JS
    US --- PG1[(PostgreSQL users)]
    JS --- MY[(MySQL jobs)]
    AS --- PG2[(PostgreSQL applications + outbox)]
    AS -->|outbox relay| K[[Kafka]]
    K --> NS
    NS --- MG[(MongoDB notifications)]
    EU[Eureka :8761]
    GW -.register.-> EU
    US -.register.-> EU
    JS -.register.-> EU
    AS -.register.-> EU
    NS -.register.-> EU
```

| Application | Port | Owns | Data store (container, host port) |
|-------------|------|------|-----------------------------------|
| Discovery Server (Eureka) | 8761 | Service registry | none |
| API Gateway | 8080 | Routing, JWT pre-validation, CORS, correlation ids, `/internal/**` block | none |
| User Service | 8081 | Accounts, registration, login, JWT issuance, academic profile | PostgreSQL 17 `placehub_users` (`users-db`, 5433) |
| Job Service | 8082 | Companies, jobs, eligibility rules and validation | MySQL 8.4 `placehub_jobs` (`jobs-db`, 3307) |
| Application Service | 8083 | Applications, status lifecycle, outbox, Kafka producer | PostgreSQL 17 `placehub_applications` (`applications-db`, 5434) |
| Notification Service | 8084 | Kafka consumer, in-app notifications, console email | MongoDB 8 `placehub_notifications` (`notifications-db`, 27018) |
| Kafka (KRaft, `apache/kafka:4.3.2`) | 9092 internal / 29092 host | Event broker | `kafka-data` volume |
| Frontend (nginx) | 5173 | React 19 single-page app | browser |

Each database runs in its own container with its own credentials, so no service can reach another service's data.
The gateway is the only supported client entry point. The internal service ports are published only for debugging.

**Stack:** Java 25 LTS, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (Gateway Server WebFlux, Netflix Eureka, OpenFeign,
Resilience4j), Spring Security OAuth2 Resource Server, Spring Data JPA/MongoDB, Flyway, Spring Kafka, springdoc 3.2.0,
Testcontainers 2, React 19 + TypeScript + Vite 7.

### Route table (gateway)

| Path | Service |
|------|---------|
| `/api/auth/**`, `/api/users/**`, `/api/admin/users/**` | user-service |
| `/api/companies/**`, `/api/jobs/**`, `/api/recruiter/jobs/**`, `/api/admin/jobs/**`, `/api/admin/companies/**` | job-service |
| `/api/applications/**`, `/api/admin/applications/**` | application-service |
| `/api/notifications/**` | notification-service |
| `/internal/**` | **404 at the gateway, never routed** |

## 2. Prerequisites

- **Docker Desktop with 8 GB of memory** (Compose v2). The default stack uses about 2.5 GB at rest; the per-container
  limits add up to about 4.5 GB.
- For running outside Docker: **JDK 25** and **Node 22 LTS**. The Maven wrapper is included, so no Maven install is
  needed.
- For the smoke test: `bash`, `curl` and `jq`. On Windows use Git Bash or WSL.

## 3. Quick start

```bash
cp .env.example .env
COMPOSE_PARALLEL_LIMIT=2 docker compose up --build    # add -d to run in the background
./scripts/e2e-smoke.sh
```

`COMPOSE_PARALLEL_LIMIT=2` limits how many images build at once, so the six Maven builds don't exhaust memory on an
8 GB Docker host. It is also set in `.env.example`. The first build downloads dependencies into a BuildKit cache, so
later builds are much faster.

**Staged startup for low-RAM machines:**

```bash
docker compose up -d users-db jobs-db applications-db notifications-db kafka discovery-server
docker compose up -d user-service job-service
docker compose up -d application-service notification-service api-gateway frontend
```

**Running one service outside Docker:** start the infrastructure with Compose, stop the container for the service you
want to run, then run it on the host. The `dev` profile defaults to the host ports (5433, 3307, 5434, 27018, 29092,
8761) and to the local example secrets:

```bash
docker compose up -d users-db jobs-db applications-db notifications-db kafka discovery-server
cd backend
./mvnw install -DskipTests                       # once: puts placehub-common in the local repository
./mvnw -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev
```

The plugin runs the JVM with `-Duser.timezone=UTC`. Use the same flag when you start a service from an IDE: legacy
host zone ids such as `Asia/Calcutta` are rejected by PostgreSQL 17.

## 4. URLs

| What | URL |
|------|-----|
| Frontend | http://localhost:5173 |
| Gateway | http://localhost:8080 (health: `/actuator/health`) |
| Eureka dashboard | http://localhost:8761 |
| User Service Swagger UI | http://localhost:8081/swagger-ui.html |
| Job Service Swagger UI | http://localhost:8082/swagger-ui.html |
| Application Service Swagger UI | http://localhost:8083/swagger-ui.html |
| Notification Service Swagger UI | http://localhost:8084/swagger-ui.html |
| Prometheus (observability profile) | http://localhost:9090 |
| Grafana (observability profile) | http://localhost:3000 (anonymous viewer, or admin/admin) |

Swagger UI calls the services directly on their debug ports. Use "Authorize" with a token from `POST /api/auth/login`.
Internal endpoints are tagged `internal` and need the `X-Internal-Token` header.

Observability is off by default: `docker compose --profile observability up -d`.

## 5. Demo credentials (local development only)

Seed data is loaded **only in the `dev` profile** (the Compose default) and uses fixed UUIDs that match across
services. The password for every account is `PlaceHub@123`.

> ⚠️ These accounts and the secrets in `.env.example` are for local demos only. Never use them anywhere shared.

| Email | Role | Details |
|-------|------|---------|
| `admin@placehub.local` | ADMIN | — |
| `recruiter@placehub.local` | RECRUITER | Northstar Technologies, Talent Acquisition Lead |
| `recruiter2@placehub.local` | RECRUITER | Meera Recruiter, Meridian Analytics (v3) |
| `student@placehub.local` | STUDENT | CSE, 2027, CGPA 8.60, 0 backlogs |
| `student2@placehub.local` | STUDENT | ME, 2027, CGPA 6.40, 2 backlogs |

Seeded jobs (all owned by the seeded recruiter at Northstar Technologies): **Graduate Software Engineer** (published;
CGPA ≥ 7.5, 0 backlogs, CSE/ISE/AIML, 2027), **Data Analyst Intern** (published; CGPA ≥ 6.0, ≤ 2 backlogs, all
branches, 2027/2028), **Embedded Systems Engineer** (published; CGPA ≥ 7.0, ECE/EEE/ETE) and **Platform Engineer**
(draft, hidden from students). Meridian Analytics (owned by `recruiter2@`) has **Senior Data Engineer** (20 LPA) and
**Quant Developer** (30 LPA). Seeded CTCs: Graduate Software Engineer 12, Embedded Systems Engineer 8, Data Analyst
Intern none. Placement policy **v1** is active (see below). There are no seeded applications, so the demo starts clean.

Each role has its own sign-in page: http://localhost:5173/login/student, `/login/recruiter`, `/login/admin`
(`/login` lets you pick). Signing in through another role's portal is refused with 403, but only after a correct
password, so the portal cannot be used to probe which emails exist.

Public registration can never create an admin. In other environments, create one with a manual SQL insert into
`users` on `users-db` using a BCrypt hash.

## 6. Demo walkthrough

1. Open http://localhost:5173 and sign in as `student@placehub.local`.
2. **Browse jobs → Graduate Software Engineer.** The eligibility panel says you are eligible. Click **Apply now**.
3. Sign out and sign in as `student2@placehub.local`. Open the same job. The panel lists three reasons (CGPA, backlogs,
   branch) and the Apply button is disabled. The API answers `POST /api/applications` with 422 and `details.reasons`.
4. Sign in as `recruiter@placehub.local` → **My jobs → Applicants** for Graduate Software Engineer → **Review**.
   Only valid next statuses are offered. Click **Mark shortlisted**.
5. Sign back in as the student. **Notifications** shows "Application submitted" and "Your application for Graduate
   Software Engineer is now SHORTLISTED". The recruiter also received "New applicant for …".
6. Optional: as the student, withdraw from **My applications**. The recruiter is notified.

**v3 walkthrough (placement policy, consent, auto-shortlist):**

1. Sign in at `/login/student` as `student@placehub.local`, open **Profile & resume** and upload a PDF (≤ 2 MB). Without
   a resume, **Apply** is disabled and the API answers 422 `RESUME_REQUIRED`.
2. Apply to **Graduate Software Engineer** (12 LPA). As `recruiter@` move it to **Shortlisted → Selected**. The student
   is now placed at 12 LPA; the dashboard banner explains the bands that now apply.
3. As the student, browse jobs: **Embedded Systems Engineer** shows *Blocked* (below 1.5×), **Senior Data Engineer**
   (20 LPA, 1.67×) shows *Consent needed*, **Quant Developer** (30 LPA, 2.5×) is *Open*, and the internship is
   unaffected. Apply to Senior Data Engineer: read the consent to the end, tick the box and type your name.
4. As `recruiter2@`, switch Senior Data Engineer to **Auto-shortlist** in the job form: new qualifying applicants start
   as *Shortlisted* by SYSTEM (an "Auto" badge marks them). **Apply to existing applicants** re-screens open ones.
   Select several applicants to change their status in bulk.
5. Select the student at 20 LPA: that band locks on placement, so the student is locked and their other open
   applications are withdrawn by SYSTEM. Any new application returns 422 `STUDENT_LOCKED`.
6. As `admin@`, open **Placement policy**: edit bands (gaps/overlaps are flagged live), preview the consent text,
   try the simulator against the unsaved draft, then publish with a change note. Earlier applications keep their
   policy snapshot. **Placements** unlocks the student (reason required, audited); **Consents** exports CSV.

| Placement policy editor | Student job details |
|---|---|
| ![Policy editor](docs/screenshots/admin-policy-light.png) | ![Job details](docs/screenshots/student-job-details-light.png) |
| ![Policy editor, dark](docs/screenshots/admin-policy-dark.png) | ![Recruiter applicants](docs/screenshots/recruiter-applicants-light.png) |

**Kafka-down demo (outbox):**

```bash
docker compose stop kafka
# apply to a job as a student: the API still returns 201 (the event waits in outbox_events)
docker compose start kafka
# within seconds the notification appears
```

**Dependency-down demo:** `docker compose stop job-service`, then try to apply. You get 503 "Eligibility could not be
verified. Try again shortly." and nothing is created. `docker compose start job-service` restores it.

## 6a. v3 features

### Placement policy
The placement office configures CTC bands as multiples of a placed student's current CTC: each band blocks, requires
consent, or allows the application, and may lock the student when they are selected in it. The policy is versioned
(every save is a new version with a change note and an audit entry), can exclude internships, can cap active consents,
and decides whether a resume is required and whether a lock withdraws other open applications. Every application stores
the policy version, decision and band it was made under. Consents store the exact rendered text, its SHA-256 hash,
the signed name, client IP and user agent; only admins can see or export them.

### Resumes
Students upload PDFs (magic-byte checked, ≤ 2 MB); the last versions are kept and versions referenced by applications
are never deleted. An application pins the resume version it was made with. Students download their own; recruiters
only through applications to their own jobs; tokens never appear in URLs.

### Auto-shortlisting, history and bulk actions
A job can use manual review or auto-shortlist applicants meeting stricter CGPA/backlog criteria. Every status change is
recorded in a history timeline (actor USER or SYSTEM). Recruiters can change many applications at once; each item
succeeds or fails on its own.

### UI
Light and dark themes (toggle saved in the browser; defaults to the OS setting), self-hosted Inter, a component set in
`src/ui`, and one lazily loaded chunk per role. Budgets are enforced by `npm run size` (also in the Docker build):
initial load 109 KB gzip (≤ 120), role chunks 13–16 KB (≤ 80). Lighthouse (mobile, production build): landing 99/100/100/100,
login 97/100/100/100, student jobs 96/100/100/100, recruiter applicants 97/100/100/100 (Performance/Accessibility/
Best practices/SEO), CLS 0. axe reports no WCAG 2.1 AA violations on the key pages in either theme.

| Landing | Student dashboard (dark) |
|---|---|
| ![Landing](docs/screenshots/landing-light.png) | ![Student dashboard](docs/screenshots/student-dashboard-dark.png) |

## 7. Events, outbox, delivery semantics

| Topic | Producer | Consumer group | Key |
|-------|----------|----------------|-----|
| `placehub.application.created.v1` | application-service | `notification-service` | applicationId |
| `placehub.application.status-changed.v1` | application-service | `notification-service` | applicationId |
| `placehub.application.created.v1.DLT` | notification-service error handler | none (inspect manually) | same |
| `placehub.application.status-changed.v1.DLT` | notification-service error handler | none | same |

All topics have 3 partitions and replication factor 1. Contract fixtures are in `contracts/events/`. Each service has
its own Java records for them, and contract tests in both services check those records against the fixtures.

- **Transactional outbox.** Every application change and its `outbox_events` row are written in the same database
  transaction. If the change commits, the event exists. A relay (`@Scheduled(fixedDelay = 1000)`) claims up to 50 due
  `PENDING` rows with `FOR UPDATE SKIP LOCKED`. It sends each one with `kafkaTemplate.send(...).get(5s)` using
  `acks=all` and an idempotent producer, then marks it `SENT`.
- **Retries.** A failed send increments `attempts`, records `last_error` and schedules the next try with exponential
  backoff (2^n seconds, at most 60 s). After 10 attempts the row is marked `FAILED` and logged at ERROR. A batch stops
  at its first failure, because a failure usually means the broker is down.
- **Delivery is at-least-once.** A crash between the Kafka send and marking the row `SENT` causes a resend. There is no
  exactly-once claim.
- **Idempotent consumer.** Notification Service stores notifications with a unique index on
  `(eventId, recipientUserId)`. A redelivered event triggers `DuplicateKeyException`, which is treated as "already
  processed". One event can produce two notifications (student and recruiter), which is why the key is compound.
- **Ordering** is per application, because messages are keyed by `applicationId`.
- **Dead-letter topics.** The consumer retries 3 times, 1 s apart, then publishes to `<topic>.DLT`. Malformed events
  (bad JSON, a missing `eventId`, an unknown `eventVersion` or the wrong `eventType`) go straight to the DLT.
- Correlation ids travel from the gateway through Feign and into a Kafka header (`X-Correlation-Id`), so consumer log
  lines carry the id of the original request.

Recipients: `APPLICATION_CREATED` notifies the student ("Application submitted") and the recruiter ("New applicant
for {jobTitle}"). A status change to `WITHDRAWN` notifies the recruiter. Any other status change notifies the student.
Emails are simulated by `ConsoleEmailSender`, which logs `[SIMULATED EMAIL]` lines.

## 8. Security model

- **JWT.** User Service issues HS256 access tokens with claims `sub` (user UUID), `email`, `role`, `name`,
  `iss=placehub-user-service`, `iat` and `exp` (TTL `JWT_TTL_MINUTES`, default 60). `JWT_SECRET` must be at least 32
  bytes (raw text, or a `base64:` prefix for Base64). Every service fails fast at startup otherwise.
- **The gateway pre-validates** the signature and expiry, returning 401 early. This is defence in depth only.
  **Every business service re-validates the JWT** as an OAuth2 Resource Server, maps `role` to `ROLE_<role>`, and
  enforces access with `@PreAuthorize` plus ownership checks in the service layer. Identity always comes from the
  token. No service reads user ids or roles from bodies or custom headers.
- **Internal endpoints** (`/internal/**`) require `X-Internal-Token` equal to `INTERNAL_SERVICE_TOKEN`. A dedicated
  security filter chain, ordered first, compares them in constant time. The gateway returns 404 for `/internal/**`
  and strips any client-supplied `X-Internal-Token` and `X-User-*` headers.
- **CORS** is configured only at the gateway, for `FRONTEND_ORIGIN`, and duplicate headers are removed with
  `DedupeResponseHeader`.
- **Errors** follow one standard body (`timestamp`, `status`, `error`, `message`, `path`, `correlationId`,
  `details`). Stack traces are never returned. Applications that belong to someone else return 404, not 403, so ids
  can't be probed.
- **Passwords** are hashed with BCrypt (strength 10). Hashes are never returned or logged, and request DTOs mask
  passwords in `toString`.

**Known limitations (by design in v1):**

- A deactivated user's existing token stays valid until it expires (at most 60 minutes). Deactivation takes effect
  at the next login. Application Service also refuses to create applications for non-`ACTIVE` students through the
  academic-profile check.
- Tokens use a shared symmetric key, there are no refresh tokens, and the frontend keeps the token in
  `sessionStorage`.

## 9. Environment variables

| Variable | Used by | Local example |
|----------|---------|---------------|
| `JWT_SECRET` | user, gateway, job, application, notification | `change-me-local-dev-secret-at-least-32-bytes!!` |
| `JWT_TTL_MINUTES` | user | `60` |
| `INTERNAL_SERVICE_TOKEN` | user, job, application | `change-me-local-internal-token` |
| `FRONTEND_ORIGIN` | gateway | `http://localhost:5173` |
| `USERS_DB_URL` / `USERS_DB_USER` / `USERS_DB_PASSWORD` | user | `jdbc:postgresql://users-db:5432/placehub_users`, `placehub_users`, `users_pw` |
| `JOBS_DB_URL` / `JOBS_DB_USER` / `JOBS_DB_PASSWORD` | job | `jdbc:mysql://jobs-db:3306/placehub_jobs?allowPublicKeyRetrieval=true&useSSL=false`, `placehub_jobs`, `jobs_pw` |
| `JOBS_DB_ROOT_PASSWORD` | jobs-db container | `jobs_root_pw` |
| `APPLICATIONS_DB_URL` / `_USER` / `_PASSWORD` | application | `jdbc:postgresql://applications-db:5432/placehub_applications`, `placehub_applications`, `applications_pw` |
| `NOTIFICATIONS_MONGO_USER` / `_PASSWORD` | notifications-db container | `placehub_notifications`, `notif_pw` |
| `NOTIFICATIONS_MONGO_URI` | notification | `mongodb://placehub_notifications:notif_pw@notifications-db:27017/placehub_notifications?authSource=admin` |
| `KAFKA_BOOTSTRAP_SERVERS` | application, notification | `kafka:9092` |
| `EUREKA_URL` | all Spring apps | `http://discovery-server:8761/eureka` |
| `SPRING_PROFILES_ACTIVE` | all Spring apps | `dev` (enables seed data) |
| `VITE_API_BASE_URL` | frontend build arg (baked in at build time) | `http://localhost:8080` |
| `COMPOSE_PARALLEL_LIMIT` | docker compose | `2` |

`.env` is git-ignored. Never commit it.

## 10. Tests

```bash
cd backend && ./mvnw verify                 # unit tests + Testcontainers integration tests (*IT)
cd frontend/placehub-web && npm ci && npm run lint && npm test && npm run build && npm run size
cd frontend/placehub-web && npx playwright install chromium && npm run e2e   # browser tests against the stack
./scripts/e2e-smoke.sh                       # against a running stack; GATEWAY_URL overrides the target
cd scripts/lighthouse && npm install && node flow.mjs <chrome-path> report.html   # optional Lighthouse flow
```

- **Unit tests** always run and need no Docker. They cover registration and login rules, JWT claims and expiry, every
  eligibility rule (fixed `Clock`), every status-transition pair, the apply flow with mocked Feign dependencies
  (eligible, 422, 409, 503, inactive), ownership rules, circuit-breaker behaviour, the outbox relay, consumer mapping,
  idempotency and the event contracts.
- **Integration tests** (`*IT`, run by failsafe in `verify`) use Testcontainers: PostgreSQL, MySQL, MongoDB and Kafka,
  with WireMock standing in for Feign dependencies. They are annotated
  `@Testcontainers(disabledWithoutDocker = true)`, so **without Docker they are reported as skipped** and the build
  still passes.
- The **smoke script** runs the 16 acceptance scenarios from spec §11.3, a withdraw → recruiter-notified check and the
  eight v3 scenarios (resume required, auto-shortlist, the full placement/consent/lock flow, unlock audit, a new policy
  version with simulation and unchanged snapshots, consent CSV, portal 403/401). It creates fresh users each run and
  restores the seeded policy bands and screening mode it changes, so it can be rerun.
- **Playwright** (`frontend/placehub-web/e2e`) drives the real stack in headless Chromium: the v2 journeys, resume
  upload, the consent modal, the policy editor and simulator, bulk shortlisting, the three portals, theme and policy
  ribbon, a 390×844 mobile browse, and axe checks with screenshots (written to `docs/screenshots/`). It prints PASS/FAIL per step and exits non-zero on any failure.
- The JVM tests run with `-Duser.timezone=UTC` (configured in the parent POM).

## 11. Reset

```bash
docker compose down        # stop, keep data
docker compose down -v     # ⚠️ stop AND delete all local data (every database volume and Kafka)
```

Seed data is reapplied on the next start.

## 12. Known limitations and production hardening

- RS256 signing with a JWKS endpoint and key rotation, instead of a shared HS256 secret.
- Refresh tokens and token revocation (a deactivation currently waits for token expiry).
- mTLS or OAuth2 client-credentials for service-to-service calls, instead of a shared internal token.
- HttpOnly secure cookies, instead of `sessionStorage`, for the browser token.
- Login rate limiting (skipped: the gateway's `RequestRateLimiter` needs Redis).
- A recruiter approval flow: the schema and the `placehub.recruiter.auto-approve` flag already support
  `PENDING_APPROVAL`.
- Re-applying after withdrawal: blocked in v1 by the unique `(job_id, student_user_id)` constraint.
- TPO verification of academic data: academic fields are read-only after registration.
- Kubernetes deployment, managed Kafka with replication, and a real SMTP adapter behind `EmailSender`.
- Outbox rows marked `FAILED` need manual replay (reset `status` to `PENDING`), and DLT messages are inspected
  manually.

## 13. Troubleshooting

- **503 from the gateway right after startup.** Services take roughly 30 s to register with Eureka and for the
  gateway's registry cache to refresh. The smoke script waits for gateway health and a successful admin login first.
- **Port conflicts** on 5173, 8080–8084, 8761, 5433, 5434, 3307, 27018 or 29092: stop whatever is using the port, or
  change the host side of the mapping in `docker-compose.yml`.
- **MySQL is slow on first start.** It initialises its data directory, so `job-service` waits for its healthcheck.
  Give it a minute.
- **Docker memory.** Give Docker Desktop 8 GB. If a container exits with code 137 (OOM-killed), raise only that
  container's `mem_limit`. Use `COMPOSE_PARALLEL_LIMIT=2` (or the staged startup above) during builds.
- **Windows.** Run the smoke script from Git Bash or WSL with `jq` installed. Keep `backend/mvnw` with LF line
  endings (enforced by `.gitattributes`).
