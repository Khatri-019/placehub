# PlaceHub Lite: Autonomous Build Specification (v2)

## Campus Placement Management System (Spring Boot Microservices)

**Document purpose:** Single source of truth for implementing PlaceHub Lite. Hand this file to Claude Code and ask it to build the application end to end with minimal human intervention.

**Execution rule:** Do not stop after planning or scaffolding. Inspect the repository, make sensible decisions where details are unspecified, implement the complete working application, run tests, fix failures, and document exactly how to run it. Ask the user a question only if a genuinely blocking external credential or irreversible decision is required. Use local development defaults and mock integrations wherever possible.

**What changed from v1 (read this first):** v1 was a good product spec but left several gaps that would stall or break an autonomous build. v2 resolves them with concrete decisions. Summary:

| # | v1 gap | v2 decision |
|---|--------|-------------|
| 1 | "Spring Boot 3.x". Boot 3.5 and Spring Cloud 2025.0 left OSS support on 30 June 2026 | Spring Boot 4.x + Spring Cloud 2025.1.x (Oakwood), Java 25 LTS (Section 4) |
| 2 | Gateway path collision: `/api/recruiter/jobs` (Job Service) vs `/api/recruiter/jobs/{jobId}/applications` (Application Service) | Applicant listing moved to `/api/applications/job/{jobId}`. Every public path prefix maps to exactly one service (Section 3.4) |
| 3 | No admin job listing endpoint, but Admin UI needs one | Added `GET /api/admin/jobs` and `GET /api/admin/companies` (Section 5.2) |
| 4 | Application Service must fetch trusted academic data, but User Service had no such endpoint | Added `GET /internal/users/{id}/academic-profile` (Section 5.1) |
| 5 | Application Service must verify recruiter owns a job, but Job Service exposed no owner lookup; event payload needs `jobTitle`/`companyName` from nowhere | Added `GET /internal/jobs/{id}/summary` returning owner ID, title, company name, status (Section 5.2) |
| 6 | "Internal endpoints cannot be public bypasses" with no mechanism | Gateway rejects `/internal/**`; services require a shared `X-Internal-Token` header on internal routes (Section 6.2) |
| 7 | Unclear whether services trust gateway headers or re-validate JWT | Every service is an OAuth2 Resource Server validating the same JWT. No identity headers are trusted (Section 6.1) |
| 8 | Seed data spans 3 databases with no way to make IDs line up | Fixed, documented UUIDs for every seeded entity (Section 13) |
| 9 | Branch values free-text, so `"CSE"` vs `"Computer Science"` silently fails eligibility | Branch is an enum of codes shared by User and Job Service contracts (Section 8.0) |
| 10 | Outbox "if practical" | Transactional outbox is mandatory, with a scheduled relay (Section 5.3) |
| 11 | Recruiter-created company flow undefined (registration collects company name, Job Service owns companies) | One company per recruiter in v1; recruiter must create company profile before first job; UI prompts for it (Section 5.2) |
| 12 | Feign behaviour on ineligibility undefined (422 from Job Service would throw in Feign) | Validation endpoint always returns 200 with `eligible: false`; only Application Service returns 422 (Section 5.2) |
| 13 | CORS ownership unspecified, causes duplicate headers | CORS configured at the gateway only (Section 6.3) |
| 14 | Kafka image unspecified; Bitnami images are no longer a safe default | `apache/kafka` official image in KRaft mode (Section 12) |
| 15 | Frontend env var baked at build time, not documented | Vite build arg `VITE_API_BASE_URL`, served by nginx (Section 12) |
| 16 | Withdrawal never notifies the recruiter; creation never notifies recruiter | `recruiterUserId` is always in events; recruiter is notified on create and withdraw (Section 5.4) |
| 17 | No runnable proof of the 15 acceptance scenarios | `scripts/e2e-smoke.sh` is a required deliverable (Section 11.3) |
| 18 | Testcontainers tests fail hard when Docker is absent | Integration tests use `@Testcontainers(disabledWithoutDocker = true)`; unit tests always run (Section 11) |
| 19 | Deactivated user keeps working until JWT expiry | Short token TTL plus active-status checks on login and on internal academic-profile fetch; documented limitation (Section 6.1) |
| 20 | Re-applying after withdrawal undefined | Not allowed in v1 (unique constraint holds); UI and docs say so (Section 5.3) |

------------------------------------------------------------------------

# 1. Product Overview

## 1.1 Product name

**PlaceHub Lite**

## 1.2 Purpose

PlaceHub Lite is a campus placement management platform connecting students, recruiters, and placement administrators (TPOs). Recruiters publish job opportunities with eligibility rules. Students browse jobs and apply. The platform validates eligibility, tracks applications, and sends notifications when applications are created or their status changes.

This is intentionally the **simplified four-business-service version**. Do not add an Assessment Service, AI features, resume parsing, payment processing, or multi-tenant infrastructure in v1.

## 1.3 Primary goals

1. Demonstrate a genuine microservices architecture.
2. Have four independently runnable business Spring Boot services plus Eureka and Gateway.
3. Give every business service its own database and credentials. No cross-service database access.
4. Demonstrate synchronous service-to-service communication using OpenFeign through Eureka.
5. Demonstrate asynchronous communication using Kafka with a transactional outbox.
6. Provide JWT authentication and role-based authorization enforced in every service.
7. Provide a usable React frontend for the main user journeys.
8. Make the entire system runnable locally with `docker compose up --build`.
9. Include automated tests, an end-to-end smoke script, API documentation, seed data, and setup instructions.

## 1.4 Out of scope for v1

- Real university integrations.
- Real email provider credentials.
- Online examinations or code execution.
- AI/ML recommendations.
- Payment processing.
- Multi-tenant SaaS billing.
- Kubernetes deployment.
- Native mobile apps.
- Refresh tokens (access token only; documented limitation).
- Student self-editing of academic data (academic data is read-only after registration in v1; documented as a TPO-verification future feature).
- File uploads (resumes, logos).

------------------------------------------------------------------------

# 2. Users, Roles, and Permissions

| Role | Description |
|------|-------------|
| `STUDENT` | Browses published jobs, applies, tracks and withdraws own applications |
| `RECRUITER` | Manages own company profile and own jobs, reviews applications to own jobs |
| `ADMIN` | Platform administrator; views and manages users, companies, jobs, applications |

### Permission rules

- A student can only view their own profile, applications, and notifications.
- A recruiter can only manage the company and jobs they own, and only review applications for those jobs.
- An admin can access all `/api/admin/**` endpoints and can update any application status.
- Public registration never allows `ADMIN`. Admins exist only via seed data (dev profile) or a documented manual SQL step.
- Enforce permissions in backend services, not only in the frontend.
- Never trust a user ID, role, recruiter ID, or student ID supplied in a request body or custom header. Derive identity from the validated JWT `sub` and `role` claims.
- Inactive (`DEACTIVATED`) accounts cannot log in.

### Permission matrix (authoritative)

| Action | STUDENT | RECRUITER | ADMIN |
|--------|---------|-----------|-------|
| Browse published jobs | yes | yes | yes |
| View draft/closed job | no | own only | yes |
| Create/edit/publish/close job | no | own only | no (v1: admin can close any job) |
| Apply | yes | no | no |
| Withdraw application | own only | no | no |
| List applicants for a job | no | own job only | yes |
| Update application status | no | own job only | yes |
| List all users / toggle status | no | no | yes |
| Read notifications | own only | own only | own only |

------------------------------------------------------------------------

# 3. Architecture

## 3.1 Services

| Application | Port | Responsibility | Data store |
|-------------|------|----------------|------------|
| Discovery Server (Eureka) | 8761 | Service registration and discovery | None |
| API Gateway | 8080 | Single client entry point, routing, JWT pre-validation, CORS, correlation IDs | None |
| User Service | 8081 | Registration, login, JWT issuance, accounts, academic profile | PostgreSQL `placehub_users` (container `users-db`, host port 5433) |
| Job Service | 8082 | Companies, job postings, eligibility rules and validation | MySQL `placehub_jobs` (container `jobs-db`, host port 3307) |
| Application Service | 8083 | Applications, status lifecycle, outbox, Kafka producer | PostgreSQL `placehub_applications` (container `applications-db`, host port 5434) |
| Notification Service | 8084 | Kafka consumer, in-app notifications, console email adapter | MongoDB `placehub_notifications` (container `notifications-db`, host port 27018) |
| Kafka (KRaft) | 9092 internal / 29092 host | Event broker | Kafka volume |
| Frontend | 5173 | React UI served by nginx | Browser |

Each database runs in its **own container** with its own credentials. This is heavier than one shared Postgres, but makes "no cross-service database access" physically true and easy to demonstrate.

## 3.2 High-level architecture

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

## 3.3 Communication rules

### Synchronous REST (OpenFeign)

- Application Service calls User Service `GET /internal/users/{id}/academic-profile`.
- Application Service calls Job Service `POST /internal/jobs/{id}/validate-eligibility` and `GET /internal/jobs/{id}/summary`.
- Feign clients use Eureka service IDs (`user-service`, `job-service`) with Spring Cloud LoadBalancer.
- Feign attaches `X-Internal-Token` and `X-Correlation-Id` via a `RequestInterceptor`.
- Services never share repositories, entities, or database connections. Shared code is limited to an optional `placehub-common` module containing only DTO-free utilities (error model, correlation filter). If a shared module causes friction, duplicate the small classes instead; do not share entities or event DTOs across services.

### Asynchronous Kafka

- Application Service writes an outbox row in the same transaction as the application change. A scheduled relay publishes pending rows to Kafka and marks them sent.
- Notification Service consumes, persists an in-app notification, and logs a simulated email.
- Event payloads are JSON with `eventId` (UUID) and `eventVersion`.
- Consumers are idempotent via a unique index on `eventId`.

Topics:

| Topic | Producer | Consumer group | Key |
|-------|----------|----------------|-----|
| `placehub.application.created.v1` | application-service | `notification-service` | `applicationId` |
| `placehub.application.status-changed.v1` | application-service | `notification-service` | `applicationId` |
| `placehub.application.created.v1.DLT` | notification-service (error handler) | none (inspect manually) | same |
| `placehub.application.status-changed.v1.DLT` | notification-service (error handler) | none | same |

Topics are created at Application/Notification startup via `NewTopic` beans (3 partitions, replication factor 1 locally). Keying by `applicationId` preserves per-application ordering.

## 3.4 Service discovery and gateway routing

- Eureka server registers the gateway and all four business services.
- Gateway uses Spring Cloud Gateway Server WebFlux (`spring-cloud-starter-gateway-server-webflux`) with `lb://` URIs.
- Gateway is the only supported client entry point. Internal ports are published by Compose for debugging only, and the README says so.

### Route table (authoritative; each prefix maps to exactly one service)

| Path predicate | Target |
|----------------|--------|
| `/api/users/**`, `/api/auth/**` | `lb://user-service` |
| `/api/admin/users/**` | `lb://user-service` |
| `/api/companies/**`, `/api/jobs/**`, `/api/recruiter/jobs/**` | `lb://job-service` |
| `/api/admin/jobs/**`, `/api/admin/companies/**` | `lb://job-service` |
| `/api/applications/**`, `/api/admin/applications/**` | `lb://application-service` |
| `/api/notifications/**` | `lb://notification-service` |
| `/internal/**` | **Rejected with 404 by a gateway filter. Never routed.** |
| `/actuator/**` on downstream services | Not routed |

Gateway also exposes `/actuator/health` for itself.

------------------------------------------------------------------------

# 4. Technology Choices

**Version policy:** Spring Boot 3.5 and Spring Cloud 2025.0 reached end of open-source support on 30 June 2026. Use the current supported line.

| Concern | Choice |
|---------|--------|
| Java | 21 (LTS) |
| Build | Maven with wrapper (`mvnw`), one parent POM at `backend/pom.xml` with `<modules>` |
| Spring Boot | 4.1.x latest patch (fallback 4.0.x if 4.1 has an incompatibility) |
| Spring Cloud | 2025.1.x (Oakwood) latest patch, which supports Boot 4.0.x and 4.1.x |
| Gateway | `spring-cloud-starter-gateway-server-webflux` |
| Discovery | `spring-cloud-starter-netflix-eureka-server` / `-client` |
| Sync calls | `spring-cloud-starter-openfeign` + `spring-cloud-starter-circuitbreaker-resilience4j` |
| Security | Spring Security, `spring-boot-starter-oauth2-resource-server` (HS256 JWT decoder) in every service; JWT issuance via Nimbus JOSE (`NimbusJwtEncoder`) in User Service |
| Persistence | Spring Data JPA (User, Job, Application), Spring Data MongoDB (Notification) |
| Migrations | Flyway (`spring-boot-starter-flyway` on Boot 4; add `flyway-database-postgresql` / `flyway-mysql`) |
| Messaging | Spring Kafka (Boot-managed version) |
| DBs | PostgreSQL 17, MySQL 8.4 LTS, MongoDB 8 |
| Kafka | `apache/kafka` official image, KRaft, single broker |
| API docs | springdoc-openapi 3.x (the Boot 4 compatible line) |
| Mapping | MapStruct or hand-written mappers (hand-written is fine; keep them in `mapper`) |
| Tests | JUnit 5/6 per Boot BOM, Mockito, Spring Boot Test, `@MockitoBean` (not the removed `@MockBean`), Testcontainers (Boot-managed version) |
| Frontend | React 19 + TypeScript + Vite, React Router, Axios, plain CSS with CSS variables (no UI framework), Vitest + React Testing Library |
| Containers | Docker, Docker Compose v2 |

**Boot 4 migration notes for the implementer** (avoid Boot 2/3 tutorials):

- Boot 4 modularized starters; use `spring-boot-starter-webmvc` (or `spring-boot-starter-web` if still available), and the dedicated `spring-boot-starter-flyway`, `spring-boot-starter-kafka` etc. Let start.spring.io generate the POM if unsure.
- Boot 4 defaults to Jackson 3 (`tools.jackson.*` packages). Use the Boot-provided `JsonMapper`; for Kafka use Spring Kafka's Jackson 3 serializers (`JacksonJsonSerializer`/`JacksonJsonDeserializer`) or plain `StringSerializer` with manual mapping. String + manual mapping is the recommended, least fragile choice.
- `@MockBean` is gone; use `@MockitoBean`.
- Verify every version against start.spring.io and the Spring Cloud compatibility table at build time. If a library is unavailable, pick a maintained compatible alternative and record it in `IMPLEMENTATION_NOTES.md`.

All versions are centralized in `backend/pom.xml` `<properties>` and BOM imports. No version numbers in child POMs.

------------------------------------------------------------------------

# 5. Functional Requirements

## 5.1 User Service

### Responsibilities

- Register student and recruiter accounts.
- Authenticate users, hash passwords with BCrypt (strength 10).
- Issue signed JWT access tokens.
- Expose current-user details including academic or recruiter details.
- Admin user listing and activation/deactivation.
- Expose trusted academic profile to internal callers.

### Student registration fields

| Field | Validation |
|-------|------------|
| fullName | required, 2 to 100 chars |
| email | required, valid email, unique (case-insensitive, stored lowercase) |
| password | required, 8 to 72 chars, at least one letter and one digit |
| collegeName | required, max 150 |
| branch | required, one of `Branch` enum (Section 8.0) |
| graduationYear | required, 2020 to 2035 |
| cgpa | required, 0.00 to 10.00, two decimals |
| backlogCount | required, 0 to 50 |

### Recruiter registration fields

| Field | Validation |
|-------|------------|
| fullName | required |
| email (work email) | required, unique |
| password | as above |
| companyName | required (stored as recruiter detail; used to prefill the company profile form in Job Service) |
| designation | required |

Recruiter accounts default to `ACTIVE` for frictionless local demo. The `account_status` column supports `PENDING_APPROVAL`, `ACTIVE`, `DEACTIVATED`; config flag `placehub.recruiter.auto-approve=true` controls the default so admin approval can be enabled later without schema change.

### Endpoints

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| POST | `/api/auth/register/student` | Public | Register student, returns 201 + user DTO |
| POST | `/api/auth/register/recruiter` | Public | Register recruiter, returns 201 + user DTO |
| POST | `/api/auth/login` | Public | Returns `{ accessToken, tokenType, expiresIn, user }` |
| GET | `/api/users/me` | Authenticated | Current user with student or recruiter details |
| GET | `/api/admin/users` | ADMIN | Paginated list, filters `role`, `status`, `q` (name/email) |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | Body `{ "status": "ACTIVE" \| "DEACTIVATED" }`. Admin cannot deactivate self (409) |
| GET | `/internal/users/{id}` | Internal token | Minimal `{ id, fullName, email, role, status }` |
| GET | `/internal/users/{id}/academic-profile` | Internal token | `{ userId, fullName, email, branch, graduationYear, cgpa, backlogCount, status }`. 404 if not a student |

Note: v1 used `/api/users/register/*` and `/api/users/login`. v2 moves public auth to `/api/auth/**` so the gateway can whitelist one prefix as public. Both prefixes route to User Service.

Responses: 409 on duplicate email, 401 on bad credentials (same message for unknown email and wrong password), 403 on login of a `DEACTIVATED` or `PENDING_APPROVAL` account with a clear message. Never return password hashes.

### JWT

- Algorithm HS256, secret from `JWT_SECRET` (minimum 32 bytes, Base64 or raw; fail fast at startup if shorter).
- Claims: `sub` (user UUID), `email`, `role` (`STUDENT`/`RECRUITER`/`ADMIN`), `name`, `iss` (`placehub-user-service`), `iat`, `exp`.
- TTL from `JWT_TTL_MINUTES`, default 60.
- `.env.example` contains a clearly labelled local-only secret. README states asymmetric keys (RS256 + JWKS), refresh tokens, and key rotation are production hardening tasks.

------------------------------------------------------------------------

## 5.2 Job Service

### Responsibilities

- Manage company profiles (one per recruiter in v1).
- Create, edit, publish, close, and list job postings.
- Store eligibility criteria and validate students against them.
- Provide job summaries to Application Service.
- Students see only `PUBLISHED` jobs. Recruiters manage only their own. Admins see all.

### Company rules

- A recruiter must create a company profile before creating a job. `POST /api/jobs` without one returns 409 `"Create your company profile first"`.
- One company per recruiter (`unique(recruiter_owner_user_id)`). Second `POST /api/companies` returns 409.
- Company name is copied onto nothing; jobs reference `company_id`.

### Job rules

- New jobs are `DRAFT`.
- `PUT` allowed when status is `DRAFT` or `PUBLISHED`. `CLOSED` jobs are read-only (409). Edits to a published job's criteria do not affect existing applications (snapshots hold).
- Publish: `DRAFT -> PUBLISHED`, requires deadline in the future (400 otherwise).
- Close: `PUBLISHED -> CLOSED` or `DRAFT -> CLOSED`. Recruiter owner or ADMIN.
- Deadline stored as `Instant` (UTC). Frontend shows local time and sends ISO-8601 with offset.
- Students never see drafts: `GET /api/jobs/{id}` for a non-published job returns 404 to students and to non-owning recruiters.

### Job fields

`id`, `companyId`, `recruiterOwnerUserId`, `title` (required, max 150), `description` (required, max 5000), `location` (required), `employmentType` (`INTERNSHIP` | `FULL_TIME`), `ctc` (optional text, max 100), `minCgpa` (0 to 10, default 0), `maxBacklogs` (0 to 50, default 0), `eligibleBranches` (set of `Branch`; empty = all branches), `eligibleGraduationYears` (set of int; empty = all years), `deadline`, `status`, `createdAt`, `updatedAt`.

### Endpoints

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| POST | `/api/companies` | RECRUITER | Create own company profile |
| GET | `/api/companies/me` | RECRUITER | Own company (404 if none) |
| PUT | `/api/companies/me` | RECRUITER | Update own company |
| GET | `/api/companies/{id}` | Authenticated | Company details |
| POST | `/api/jobs` | RECRUITER | Create job (DRAFT) |
| PUT | `/api/jobs/{id}` | Owning RECRUITER | Update job |
| PATCH | `/api/jobs/{id}/publish` | Owning RECRUITER | Publish |
| PATCH | `/api/jobs/{id}/close` | Owning RECRUITER or ADMIN | Close |
| GET | `/api/jobs` | Authenticated | Browse PUBLISHED jobs. Filters: `q` (title/description/company), `location`, `employmentType`, `branch`. Sort: `deadline`, `createdAt` |
| GET | `/api/jobs/{id}` | Authenticated | Job details (visibility rules above) |
| GET | `/api/recruiter/jobs` | RECRUITER | Own jobs, any status, filter `status` |
| GET | `/api/admin/jobs` | ADMIN | All jobs, any status, filters `status`, `q` |
| GET | `/api/admin/companies` | ADMIN | All companies |
| GET | `/internal/jobs/{id}/summary` | Internal token | `{ jobId, title, companyId, companyName, recruiterOwnerUserId, status, deadline }` |
| POST | `/internal/jobs/{id}/validate-eligibility` | Internal token | Validate student attributes |

The student-facing eligibility preview on the job details page is served by Application Service (`GET /api/applications/eligibility?jobId=`), because Job Service never reads student data.

### Eligibility validation

Request:

```json
{ "branch": "CSE", "graduationYear": 2027, "cgpa": 8.4, "backlogCount": 0 }
```

Checks, all evaluated so the student sees every failing reason at once:

1. Job exists (else 404; this is the only non-200 outcome besides auth errors).
2. Status is `PUBLISHED` (reason: "This job is not open for applications").
3. `now < deadline` (reason: "The application deadline has passed").
4. `cgpa >= minCgpa` (reason: "Minimum CGPA required is 7.50; yours is 6.90").
5. `backlogCount <= maxBacklogs`.
6. `branch` in `eligibleBranches` if non-empty.
7. `graduationYear` in `eligibleGraduationYears` if non-empty.

Response is always **200**:

```json
{ "jobId": "uuid", "eligible": false, "reasons": ["Minimum CGPA required is 7.50; yours is 6.90"] }
```

Eligibility logic lives in a pure `EligibilityEvaluator` class (no Spring dependencies) so it is trivially unit-testable. Inject a `java.time.Clock` for deadline tests.

------------------------------------------------------------------------

## 5.3 Application Service

### Responsibilities

- Submit applications; prevent duplicates.
- Fetch trusted academic data from User Service and validate eligibility via Job Service.
- Store applications with academic snapshots.
- Students view own applications; recruiters view applicants for own jobs; admins view all.
- Validate and apply status transitions.
- Write outbox events transactionally and relay them to Kafka.
- Provide an eligibility preview for the UI.

### Statuses and transitions

```
APPLIED      -> UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN
UNDER_REVIEW -> SHORTLISTED, REJECTED, WITHDRAWN
SHORTLISTED  -> SELECTED, REJECTED, WITHDRAWN
SELECTED, REJECTED, WITHDRAWN: terminal
```

Change from v1: `SHORTLISTED -> WITHDRAWN` is allowed (a student must be able to withdraw before a terminal status, which v1 stated in prose but contradicted in the transition list).

- `WITHDRAWN` only by the owning student via `/withdraw`.
- `UNDER_REVIEW`, `SHORTLISTED`, `REJECTED`, `SELECTED` only by the owning recruiter or ADMIN via `/status`. Sending `WITHDRAWN` to `/status` returns 400.
- Same-status update returns 409. Invalid transition returns 409 with `"Cannot move from REJECTED to SHORTLISTED"`.
- Transition rules live in an `ApplicationStatus` enum method `canTransitionTo` (pure, unit-tested exhaustively).
- Applications are allowed to change status after the job closes (recruiters review after the deadline).
- Re-applying after `WITHDRAWN` is not allowed in v1 (409). Documented.

### Application fields

`id`, `jobId`, `studentUserId`, `recruiterOwnerUserId` (copied from job summary at apply time, so recruiter ownership checks for list/detail don't need a Feign call), `jobTitleSnapshot`, `companyNameSnapshot`, `studentNameSnapshot`, `studentEmailSnapshot`, `branchSnapshot`, `cgpaSnapshot`, `graduationYearSnapshot`, `backlogCountSnapshot`, `status`, `recruiterNote` (max 1000), `appliedAt`, `updatedAt`, `version` (`@Version` optimistic lock).

Ownership is checked against the stored `recruiterOwnerUserId`. Job ownership cannot change in v1, so this is safe; document it.

### Endpoints

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| POST | `/api/applications` | STUDENT | Body `{ "jobId": "uuid" }` only. Returns 201 |
| GET | `/api/applications/eligibility?jobId=` | STUDENT | Preview: fetches own academic profile + calls Job Service validation. Returns the eligibility result plus `alreadyApplied` boolean. No writes |
| GET | `/api/applications/me` | STUDENT | Own applications, paginated, filter `status` |
| GET | `/api/applications/{id}` | Owning student, owning recruiter, ADMIN | Details. Others get 404 (not 403) to avoid ID probing |
| PATCH | `/api/applications/{id}/withdraw` | Owning STUDENT | Withdraw |
| GET | `/api/applications/job/{jobId}` | Owning RECRUITER or ADMIN | Applicants for a job, paginated, filter `status`. Ownership: calls Job Service summary once; 403 if not owner |
| PATCH | `/api/applications/{id}/status` | Owning RECRUITER or ADMIN | Body `{ "status": "SHORTLISTED", "note": "optional" }` |
| GET | `/api/admin/applications` | ADMIN | All, paginated, filters `status`, `jobId` |
| GET | `/api/applications/stats/me` | STUDENT | Counts by status for dashboard cards |
| GET | `/api/applications/stats/recruiter` | RECRUITER | Counts by status across own jobs |

### Application creation workflow

1. Extract student ID from JWT `sub`. Reject non-STUDENT with 403.
2. Feign: `GET /internal/users/{id}/academic-profile`. If status not `ACTIVE`, 403.
3. Feign: `POST /internal/jobs/{jobId}/validate-eligibility`. Job 404 propagates as 404.
4. If `eligible == false`, return **422** with the error body plus a `details.reasons` array.
5. Feign: `GET /internal/jobs/{jobId}/summary` for snapshots and owner ID. (Steps 3 and 5 may be combined into one internal call if the implementer prefers; keep the validation endpoint as specified because it is a stated contract.)
6. In one `@Transactional` method: insert application with status `APPLIED`, insert outbox row for `APPLICATION_CREATED`.
7. Unique constraint `uq_app_job_student (job_id, student_user_id)` violation is caught (`DataIntegrityViolationException`) and mapped to **409** `"You have already applied to this job"`. Do not rely only on a pre-check.
8. Return 201 with the application DTO.

If User or Job Service is unavailable (timeout, circuit open, 5xx), return **503** `"Eligibility could not be verified. Try again shortly."` and create nothing.

### Status update workflow

1. Load application (404 if missing).
2. Caller must be ADMIN or `sub == recruiterOwnerUserId` (else 403).
3. Validate transition (409 if invalid).
4. In one transaction: update status/note/updatedAt and insert outbox row `APPLICATION_STATUS_CHANGED` with old and new status.
5. Return updated DTO.

Withdraw follows the same steps with the student ownership check and also writes a `APPLICATION_STATUS_CHANGED` event (newStatus `WITHDRAWN`).

### Transactional outbox (mandatory)

Table `outbox_events`: `id` (UUID, also used as `eventId`), `aggregate_id` (applicationId), `event_type`, `topic`, `payload` (JSON text), `status` (`PENDING`, `SENT`, `FAILED`), `attempts`, `last_error`, `created_at`, `sent_at`.

Relay: `@Scheduled(fixedDelay = 1000)` component that, in its own transaction, selects up to 50 `PENDING` rows ordered by `created_at` using `FOR UPDATE SKIP LOCKED`, sends each with `kafkaTemplate.send(topic, aggregateId, payload).get(5, SECONDS)`, marks `SENT` on success, increments `attempts` and records `last_error` on failure. After 10 attempts mark `FAILED` and log at ERROR. Producer config: `acks=all`, `enable.idempotence=true`.

Document delivery semantics in the README: at-least-once from outbox to Kafka (a crash between send and mark-sent causes a resend), deduplicated at the consumer via `eventId`. No exactly-once claim.

------------------------------------------------------------------------

## 5.4 Notification Service

### Responsibilities

- Consume both topics.
- Persist in-app notifications idempotently.
- Expose authenticated notification APIs.
- Log a simulated email via a `ConsoleEmailSender` implementing an `EmailSender` interface (so SMTP can be swapped in later).

### Recipients

| Event | Notify |
|-------|--------|
| `APPLICATION_CREATED` | Student ("Application submitted") and recruiter ("New applicant for {jobTitle}") |
| `APPLICATION_STATUS_CHANGED` with newStatus `WITHDRAWN` | Recruiter ("{studentName} withdrew from {jobTitle}") |
| `APPLICATION_STATUS_CHANGED` other | Student ("Your application for {jobTitle} is now {newStatus}") |

Because one event can produce two notifications, the idempotency key is the compound `(eventId, recipientUserId)` with a unique index. Insert with `insert` and catch `DuplicateKeyException` as "already processed".

### Notification fields

`id`, `eventId`, `recipientUserId`, `type`, `title`, `message`, `relatedApplicationId`, `relatedJobId`, `read` (default false), `createdAt`.

Indexes: unique `{eventId: 1, recipientUserId: 1}`; `{recipientUserId: 1, createdAt: -1}`; `{recipientUserId: 1, read: 1}`. Create them explicitly in a startup `IndexInitializer` (do not rely on `auto-index-creation`).

### Endpoints

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| GET | `/api/notifications` | Authenticated | Own notifications, newest first, paginated, filter `unreadOnly` |
| GET | `/api/notifications/unread-count` | Authenticated | `{ "count": n }` |
| PATCH | `/api/notifications/{id}/read` | Recipient only | 404 if not the recipient |
| PATCH | `/api/notifications/read-all` | Authenticated | Marks own as read, returns `{ "updated": n }` |

Recipient is always `sub` from the JWT. Queries always include `recipientUserId = sub`.

### Consumer error handling

- `DefaultErrorHandler` with `FixedBackOff(1000ms, 3 attempts)` then `DeadLetterPublishingRecoverer` to `<topic>.DLT`.
- Deserialization/validation errors (missing `eventId`, unknown `eventVersion`) are non-retryable and go straight to DLT.
- Manual ack not required; default `AckMode.BATCH` with idempotent writes is acceptable.

### Kafka event contracts (v1)

Application created:

```json
{
  "eventId": "uuid",
  "eventType": "APPLICATION_CREATED",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "applicationId": "uuid",
  "jobId": "uuid",
  "studentUserId": "uuid",
  "studentName": "Demo Student",
  "recruiterUserId": "uuid",
  "jobTitle": "Graduate Software Engineer",
  "companyName": "Northstar Technologies"
}
```

Application status changed:

```json
{
  "eventId": "uuid",
  "eventType": "APPLICATION_STATUS_CHANGED",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T11:00:00Z",
  "applicationId": "uuid",
  "jobId": "uuid",
  "studentUserId": "uuid",
  "studentName": "Demo Student",
  "recruiterUserId": "uuid",
  "jobTitle": "Graduate Software Engineer",
  "companyName": "Northstar Technologies",
  "oldStatus": "APPLIED",
  "newStatus": "SHORTLISTED",
  "changedByUserId": "uuid"
}
```

Changes from v1: `recipientUserId` removed (the producer should not decide UI recipients; the consumer applies the table above), `recruiterUserId` and `changedByUserId` added. Each service owns its own copy of these DTOs as Java records; do not share a jar. Add a contract test in each service that serializes/deserializes the same JSON fixture file stored at `contracts/events/*.json` at the repo root.

------------------------------------------------------------------------

# 6. Security Requirements

## 6.1 Authentication model

- User Service issues HS256 JWTs.
- Gateway validates signature and expiry for all routes except the public list (`/api/auth/**`, `OPTIONS` preflight, gateway health) and returns 401 early. This is defence in depth, not the authority.
- **Every business service independently validates the JWT** as an OAuth2 Resource Server with the same `JWT_SECRET`, maps the `role` claim to `ROLE_<role>` authority, and enforces authorization with `@PreAuthorize` and explicit ownership checks in the service layer.
- Gateway strips any incoming `X-User-*`, `X-Internal-Token` headers from client requests. Downstream services never read identity from custom headers.
- Deactivation takes effect at next login. Existing tokens remain valid until expiry (max 60 minutes by default). Application Service additionally refuses to create applications for non-`ACTIVE` students via the academic-profile call. Document this as a known limitation with token revocation listed as hardening.

## 6.2 Internal endpoints

- All `/internal/**` endpoints require header `X-Internal-Token` equal to `INTERNAL_SERVICE_TOKEN` env var, checked by a dedicated `SecurityFilterChain` ordered before the JWT chain. Missing or wrong token returns 401. Compare in constant time.
- Gateway returns 404 for any `/internal/**` request.
- Document that mTLS or OAuth2 client-credentials is the production replacement.

## 6.3 General

1. Spring Security in every backend service; stateless sessions; CSRF disabled (no cookies used).
2. BCrypt password hashing.
3. Never accept roles or user IDs from request bodies.
4. Never expose passwords, hashes, secrets, or stack traces. `server.error.include-stacktrace=never`; global `@RestControllerAdvice` returns the standard error body; unhandled exceptions log a server-side error with correlation ID and return a generic 500 message.
5. DTOs only; no JPA entities or Mongo documents in controller signatures.
6. Jakarta Bean Validation on all request bodies.
7. CORS configured **only at the gateway** for `FRONTEND_ORIGIN` (default `http://localhost:5173`), with `DedupeResponseHeader` for `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials`. Business services do not configure CORS.
8. Consistent 401 (missing/invalid token) and 403 (role/ownership) bodies via custom `AuthenticationEntryPoint` and `AccessDeniedHandler` in each service and the gateway.
9. Secrets and connection strings only via environment variables.
10. Basic login rate limiting is optional; if added, use the gateway `RequestRateLimiter` only if it does not require Redis. Otherwise skip and list as hardening.

------------------------------------------------------------------------

# 7. Frontend Requirements

React 19 + TypeScript + Vite, located at `frontend/placehub-web`.

## 7.1 Design direction

- Clean, restrained campus recruitment SaaS dashboard.
- Responsive: sidebar on desktop (>= 960px), collapsible top bar + drawer on mobile.
- Plain CSS with a single `tokens.css` (colors, spacing, radius, typography) and per-component CSS modules.
- Consistent loading (skeletons or spinners), empty, error (with retry), and success (toast) states.
- Accessible: labelled inputs, visible focus, `aria-live` region for toasts, semantic tables, keyboard-navigable status controls.
- Status badges with consistent color per application status and job status.
- No animation libraries, no component frameworks.

## 7.2 Routes

| Path | Role | Page |
|------|------|------|
| `/` | Public | Landing |
| `/login` | Public | Login (shows demo credentials hint in dev builds only, via `import.meta.env.DEV`) |
| `/register/student` | Public | Student registration (branch as select from the shared enum list) |
| `/register/recruiter` | Public | Recruiter registration |
| `/student` | STUDENT | Dashboard: cards (applied, under review, shortlisted, selected), recent notifications |
| `/student/jobs` | STUDENT | Browse jobs with search, location, type, branch filters; pagination |
| `/student/jobs/:id` | STUDENT | Job details + eligibility panel (calls `/api/applications/eligibility`), Apply button disabled with reasons when ineligible or already applied |
| `/student/applications` | STUDENT | My applications table with status badges and Withdraw action (confirm dialog) |
| `/student/profile` | STUDENT | Read-only academic profile |
| `/recruiter` | RECRUITER | Dashboard: job counts by status, applicant counts by status. If no company, a banner linking to company setup |
| `/recruiter/company` | RECRUITER | Create/edit company profile (prefilled with registration company name) |
| `/recruiter/jobs` | RECRUITER | My jobs with status filter and Publish/Close actions |
| `/recruiter/jobs/new`, `/recruiter/jobs/:id/edit` | RECRUITER | Job form with eligibility fields (branch multi-select checkboxes, graduation years, min CGPA, max backlogs, deadline) |
| `/recruiter/jobs/:id/applicants` | RECRUITER | Applicants table with filters |
| `/recruiter/applications/:id` | RECRUITER | Application details, snapshot data, note field, status buttons showing only valid next transitions |
| `/admin` | ADMIN | Dashboard: totals |
| `/admin/users` | ADMIN | Users table with role/status filters and Activate/Deactivate |
| `/admin/jobs` | ADMIN | All jobs with Close action |
| `/admin/applications` | ADMIN | All applications with status filter |
| `/notifications` | Any authenticated | Notification list, mark read, mark all read. Unread badge in sidebar polled every 30s |
| `*` | Any | 404 page |

## 7.3 Behaviour

- `VITE_API_BASE_URL` (default `http://localhost:8080`). No internal service URLs anywhere in the frontend.
- Token stored in `sessionStorage`. README notes HttpOnly secure cookies are preferable in production.
- Axios instance with request interceptor (Bearer token, `X-Correlation-Id` UUID per request) and response interceptor (401 clears session and redirects to `/login?expired=1`; 403 shows a "not allowed" toast).
- On app load, decode the token's `exp`; if expired, clear session.
- `ProtectedRoute` component checks auth and role; wrong role redirects to that role's home.
- Error bodies from the backend standard error model are displayed using `message`; 422 displays `details.reasons` as a list.
- No hardcoded jobs, applications, or users. All data comes from the backend.
- Shared `api/` layer with typed functions per endpoint and `types/` mirroring backend DTOs.

------------------------------------------------------------------------

# 8. Data Model

UUIDs everywhere. Each service owns its schema and Flyway migrations under `src/main/resources/db/migration`.

## 8.0 Shared enums (duplicated per service, values must match)

`Branch`: `CSE`, `ISE`, `AIML`, `ECE`, `EEE`, `ETE`, `ME`, `CIVIL`, `CHEM`, `BT`, `IEM`, `AERO`.
Frontend shows human labels (e.g. `CSE` -> "Computer Science & Engineering").

`EmploymentType`: `INTERNSHIP`, `FULL_TIME`.

`Role`: `STUDENT`, `RECRUITER`, `ADMIN`.

## 8.1 User Service (PostgreSQL)

- `users`: `id uuid pk`, `full_name`, `email` (unique, lowercase), `password_hash`, `role`, `account_status`, `created_at`, `updated_at`.
- `student_details`: `user_id uuid pk fk users(id)`, `college_name`, `branch`, `graduation_year`, `cgpa numeric(4,2)`, `backlog_count`.
- `recruiter_details`: `user_id uuid pk fk users(id)`, `company_name`, `designation`.

## 8.2 Job Service (MySQL)

Store UUIDs as `BINARY(16)` with Hibernate `@JdbcTypeCode(SqlTypes.BINARY)` or as `CHAR(36)`. Choose `CHAR(36)` for readability in a student project and document it.

- `companies`: `id`, `name`, `website`, `description`, `industry`, `recruiter_owner_user_id` (unique), `created_at`, `updated_at`.
- `jobs`: `id`, `company_id fk companies(id)`, `recruiter_owner_user_id`, `title`, `description TEXT`, `location`, `employment_type`, `ctc`, `min_cgpa DECIMAL(4,2)`, `max_backlogs`, `deadline DATETIME(6)` (UTC), `status`, `created_at`, `updated_at`. Indexes on `(status, deadline)`, `recruiter_owner_user_id`.
- `job_eligible_branches`: `job_id fk`, `branch`, pk `(job_id, branch)`.
- `job_eligible_graduation_years`: `job_id fk`, `graduation_year`, pk `(job_id, graduation_year)`.

Normalized child tables via `@ElementCollection` are chosen over JSON columns so branch filtering is a plain SQL join. Search uses `LIKE` on title/description/company name via a JPA Specification; full-text search is out of scope.

## 8.3 Application Service (PostgreSQL)

- `applications`: columns from 5.3, `unique (job_id, student_user_id)`, indexes on `student_user_id`, `job_id`, `recruiter_owner_user_id`.
- `outbox_events`: as in 5.3, index on `(status, created_at)`.

No foreign keys to other services. External IDs are plain UUID columns.

## 8.4 Notification Service (MongoDB)

Collection `notifications` with indexes as in 5.4.

------------------------------------------------------------------------

# 9. API Standards

- Public endpoints prefixed `/api`; internal endpoints `/internal`.
- JSON only. UUID path params (invalid UUID returns 400, not 500).
- Status codes: 200, 201 (with `Location` header), 204, 400, 401, 403, 404, 409, 422, 503 (dependency unavailable), 500.
- Error body:

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "You are not eligible for this job",
  "path": "/api/applications",
  "correlationId": "c0a8...",
  "details": { "reasons": ["Minimum CGPA required is 7.50; yours is 6.90"] }
}
```

Validation errors use `details.fieldErrors: { "email": "must be a well-formed email address" }`.

- Pagination: `page` (0-based), `size` (default 20, max 50, larger values clamped), `sort` (`field,asc|desc`, whitelisted fields only; unknown fields return 400). Response shape:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

Use a custom `PageResponse<T>` record rather than serializing Spring's `Page` directly.

- OpenAPI per service at `http://localhost:808x/swagger-ui.html` with a bearer auth scheme. Internal endpoints are tagged "internal".

------------------------------------------------------------------------

# 10. Reliability and Correctness

1. Feign timeouts: connect 2s, read 3s, configured per client.
2. Resilience4j circuit breaker on `user-service` and `job-service` clients (sliding window 10, failure rate 50%, open 10s). Fallback throws `DependencyUnavailableException` mapped to 503.
3. No application is created if eligibility cannot be verified.
4. DB unique constraint for duplicates, mapped to 409.
5. Status transitions validated in the domain enum; `@Version` prevents lost updates (concurrent update returns 409).
6. Kafka consumer idempotency via unique `(eventId, recipientUserId)`.
7. Outbox relay retry with attempt cap; consumer retry with DLT.
8. Actuator health with DB, Mongo, Kafka, and Eureka indicators. Compose health checks use `/actuator/health`.
9. Services start even if Kafka is briefly unavailable (producer retries via outbox; consumer reconnects). Application Service keeps accepting applications while Kafka is down; events are delivered when it recovers. This is a demo point: stop Kafka, apply, start Kafka, notification appears.
10. Never log passwords, tokens, or full request bodies on auth endpoints. Mask `Authorization` headers.

README must explain: Kafka gives at-least-once delivery here; duplicates are absorbed by the idempotent consumer; ordering is per application via the message key.

------------------------------------------------------------------------

# 11. Testing Requirements

Tests are part of the deliverable. `./mvnw verify` from `backend/` must pass. Integration tests use `@Testcontainers(disabledWithoutDocker = true)` so the build still passes (with skips reported) on machines without Docker.

## 11.1 Unit tests (always run, no Docker)

- User: registration validation, duplicate email, BCrypt hashing, login success, wrong password, deactivated login, JWT claims and expiry.
- Job: `EligibilityEvaluator` for each rule individually and combined (multiple reasons), empty branch/year lists mean "all", deadline boundary using a fixed `Clock`; ownership checks for update/publish/close; draft visibility.
- Application: `ApplicationStatus.canTransitionTo` for every pair (parameterized); apply flow with mocked Feign clients: eligible, ineligible (422), duplicate (409 via mocked constraint violation), dependency down (503), inactive student (403); status update ownership; withdraw rules; outbox row written in same transaction.
- Notification: event-to-notification mapping per recipient rule; duplicate event produces no second insert; read authorization.
- Gateway: `/internal/**` blocked; public routes reachable without token; protected routes 401 without token.

## 11.2 Integration tests (Testcontainers)

- User Service: `@SpringBootTest` + PostgreSQL container, register/login/me through MockMvc.
- Job Service: MySQL container, create company, create/publish job, browse, validate eligibility.
- Application Service: PostgreSQL + Kafka containers, Feign dependencies stubbed with WireMock (`org.wiremock:wiremock-standalone`) and Eureka disabled (`eureka.client.enabled=false`, static URLs via `spring.cloud.openfeign.client.config.<name>.url`). Verify apply -> outbox -> Kafka message on topic.
- Notification Service: MongoDB + Kafka containers, publish an event twice, assert one notification per recipient; read-authorization via MockMvc.
- Event contract tests against `contracts/events/*.json`.

## 11.3 End-to-end smoke script (required)

`scripts/e2e-smoke.sh` (bash + curl + jq) runs against the gateway after `docker compose up` and asserts every scenario below, printing PASS/FAIL per step and exiting non-zero on any failure. It must create its own fresh users with random emails so it is re-runnable, and use seeded data only where noted.

1. Student registers and logs in.
2. Recruiter registers and logs in.
3. Recruiter creates a company and creates + publishes a job (min CGPA 7.0, branches CSE/ISE).
4. Student browses jobs and sees the job.
5. Eligible student applies: 201.
6. Same student applies again: 409.
7. A second, ineligible student (CGPA 6.0) applies: 422 with reasons.
8. Within 15s (poll), student has an `APPLICATION_CREATED` notification.
9. Recruiter lists applicants for the job and sees the student.
10. Recruiter shortlists the application: 200.
11. Application status is `SHORTLISTED`.
12. Within 15s, student has a status-change notification.
13. Second student gets 404 on the first student's application and sees none of their notifications.
14. A second recruiter gets 403 editing the first recruiter's job and 403 updating its application.
15. Seeded admin logs in and can call `/api/admin/users`, `/api/admin/jobs`, `/api/admin/applications`; a student gets 403 on them.
16. Direct call to `http://localhost:8080/internal/users/<id>` returns 404.

Also provide `scripts/e2e-smoke.ps1` only if trivial; bash is sufficient (Windows users run it in Git Bash or WSL; document this).

## 11.4 Frontend tests

- `npm run build` and `npm run lint` must pass with no TypeScript errors.
- Vitest + React Testing Library: `ProtectedRoute` role redirect, login form validation, eligibility panel renders reasons, status control shows only valid transitions.

Run all tests and fix failures before declaring completion. Report actual results, including skipped tests and why.

------------------------------------------------------------------------

# 12. Local Development and Docker

## 12.1 Compose services

`docker-compose.yml` at the repo root:

| Compose service | Image | Notes |
|-----------------|-------|-------|
| `discovery-server` | built | healthcheck `/actuator/health` |
| `api-gateway` | built | depends on discovery healthy |
| `users-db` | `postgres:17` | `pg_isready` healthcheck, volume `users-db-data` |
| `jobs-db` | `mysql:8.4` | `mysqladmin ping` healthcheck, volume `jobs-db-data` |
| `applications-db` | `postgres:17` | healthcheck, volume `applications-db-data` |
| `notifications-db` | `mongo:8` | `mongosh --eval "db.adminCommand('ping')"` healthcheck, volume `notifications-db-data` |
| `kafka` | `apache/kafka:<current 4.x>` | KRaft combined mode, listeners `PLAINTEXT://kafka:9092` (internal) and `EXTERNAL://localhost:29092` (host), healthcheck using `/opt/kafka/bin/kafka-broker-api-versions.sh`, volume `kafka-data` |
| `user-service`, `job-service`, `application-service`, `notification-service` | built | `depends_on` with `condition: service_healthy` for their DB, Kafka (where used), and discovery |
| `frontend` | built (multi-stage: node build, nginx serve) | build arg `VITE_API_BASE_URL=http://localhost:8080`; nginx `try_files $uri /index.html` for SPA routing; port 5173 -> 80 |

Optional profile `observability` adds `prometheus` and `grafana` (`docker compose --profile observability up`), so the default stack stays lighter.

## 12.2 Dockerfiles

Backend: multi-stage. Stage 1 `eclipse-temurin:25-jdk` runs `./mvnw -pl <module> -am package -DskipTests` from `backend/` (build context is `backend/` so the parent POM is available). Stage 2 `eclipse-temurin:25-jre`, non-root user, `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"`. Use Docker layer caching for `~/.m2` via `--mount=type=cache`.

Each JVM service container: `mem_limit: 512m`. Total stack target: under 6 GB RAM. README lists 8 GB Docker memory as recommended.

## 12.3 Eureka in containers

`eureka.instance.prefer-ip-address=true`, `eureka.client.service-url.defaultZone=http://discovery-server:8761/eureka`, faster lease renewal in dev (`lease-renewal-interval-in-seconds=5`) so routing works quickly after startup. Gateway may return 503 for the first ~30s while services register; README says so and the smoke script waits for `/actuator/health` of gateway and a successful `POST /api/auth/login` before starting.

## 12.4 Files to provide

- `.env.example` with every variable (table in 12.5).
- `.gitignore` (Java, Node, IDE, `.env`, `target/`, `node_modules/`, `dist/`).
- Maven wrapper in `backend/`.
- `Makefile` (optional) or documented commands: `up`, `down`, `reset`, `test`, `smoke`.
- Clean reset: `docker compose down -v` (README warns it deletes all local data).

Default workflow:

```bash
cp .env.example .env
docker compose up --build
./scripts/e2e-smoke.sh
```

Staged startup for low-RAM machines (documented):

```bash
docker compose up -d users-db jobs-db applications-db notifications-db kafka discovery-server
docker compose up -d user-service job-service
docker compose up -d application-service notification-service api-gateway frontend
```

Running a service outside Docker: start infra with Compose, then `cd backend && ./mvnw -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev`. `application-dev.yml` points at `localhost` host ports (5433, 3307, 5434, 27018, 29092, 8761).

## 12.5 Environment variables

| Variable | Used by | Example (local only) |
|----------|---------|----------------------|
| `JWT_SECRET` | user, gateway, job, application, notification | `change-me-local-dev-secret-at-least-32-bytes!!` |
| `JWT_TTL_MINUTES` | user | `60` |
| `INTERNAL_SERVICE_TOKEN` | user, job, application | `change-me-local-internal-token` |
| `FRONTEND_ORIGIN` | gateway | `http://localhost:5173` |
| `USERS_DB_URL` / `_USER` / `_PASSWORD` | user | `jdbc:postgresql://users-db:5432/placehub_users`, `placehub_users`, `users_pw` |
| `JOBS_DB_URL` / `_USER` / `_PASSWORD` | job | `jdbc:mysql://jobs-db:3306/placehub_jobs`, `placehub_jobs`, `jobs_pw` |
| `APPLICATIONS_DB_URL` / `_USER` / `_PASSWORD` | application | `jdbc:postgresql://applications-db:5432/placehub_applications`, ... |
| `NOTIFICATIONS_MONGO_URI` | notification | `mongodb://placehub_notifications:notif_pw@notifications-db:27017/placehub_notifications?authSource=admin` |
| `KAFKA_BOOTSTRAP_SERVERS` | application, notification | `kafka:9092` |
| `EUREKA_URL` | all Spring apps | `http://discovery-server:8761/eureka` |
| `SPRING_PROFILES_ACTIVE` | all Spring apps | `dev` |
| `VITE_API_BASE_URL` | frontend build | `http://localhost:8080` |

Never commit `.env`.

------------------------------------------------------------------------

# 13. Seed Data and Demo Accounts

Seed only in the `dev` profile, idempotently, using a Flyway location `classpath:db/seed-dev` added only in `application-dev.yml` (repeatable migrations or `INSERT ... ON CONFLICT DO NOTHING` / `INSERT IGNORE`). Notification Service needs no seed (notifications come from events). Passwords are seeded as precomputed BCrypt hashes, generated once and committed in the seed SQL.

**Fixed UUIDs (must be identical across services):**

| Entity | UUID |
|--------|------|
| Admin user | `00000000-0000-0000-0000-000000000001` |
| Recruiter user | `00000000-0000-0000-0000-000000000002` |
| Student (eligible) | `00000000-0000-0000-0000-000000000003` |
| Student (ineligible) | `00000000-0000-0000-0000-000000000004` |
| Company Northstar Technologies | `10000000-0000-0000-0000-000000000001` |
| Job 1 | `20000000-0000-0000-0000-000000000001` |
| Job 2 | `20000000-0000-0000-0000-000000000002` |
| Job 3 | `20000000-0000-0000-0000-000000000003` |
| Job 4 (draft) | `20000000-0000-0000-0000-000000000004` |

**Accounts** (password `PlaceHub@123` for all, local demo only):

| Email | Role | Details |
|-------|------|---------|
| `admin@placehub.local` | ADMIN | |
| `recruiter@placehub.local` | RECRUITER | Northstar Technologies, Talent Acquisition Lead |
| `student@placehub.local` | STUDENT | CSE, 2027, CGPA 8.60, 0 backlogs |
| `student2@placehub.local` | STUDENT | ME, 2027, CGPA 6.40, 2 backlogs |

**Jobs** (owner = seeded recruiter, company = Northstar; deadlines computed relative to migration time, e.g. `NOW() + INTERVAL 60 DAY`, so the demo never expires):

| Job | Status | Criteria | Demonstrates |
|-----|--------|----------|--------------|
| Graduate Software Engineer | PUBLISHED | CGPA >= 7.5, 0 backlogs, CSE/ISE/AIML, 2027 | student eligible, student2 ineligible (3 reasons) |
| Data Analyst Intern | PUBLISHED | CGPA >= 6.0, max 2 backlogs, all branches, 2027/2028 | both eligible |
| Embedded Systems Engineer | PUBLISHED | CGPA >= 7.0, ECE/EEE/ETE | student ineligible on branch only |
| Platform Engineer (draft) | DRAFT | any | hidden from students |

No seeded applications, so the demo starts clean. Seed SQL must never run outside `dev`.

------------------------------------------------------------------------

# 14. Observability and Developer Experience

- Actuator: expose `health`, `info`, `prometheus` only. `health.show-details=when-authorized` outside dev, `always` in dev.
- Gateway generates `X-Correlation-Id` if absent and forwards it; services put it in MDC via a servlet filter; Feign interceptor forwards it; outbox payload headers include it so the consumer can log it.
- Log pattern includes `service`, `correlationId`, level, logger. JSON structured logs are optional (Boot's built-in structured logging is fine).
- Startup log line: service name, active profiles, port.
- Prometheus scrape config and one Grafana dashboard JSON (JVM + HTTP request rates per service) under `infrastructure/`, behind the `observability` Compose profile. Do this last; skip if it delays core work.

------------------------------------------------------------------------

# 15. Repository Structure

```text
placehub-lite/
├── README.md
├── BUILD_SPEC.md                 (this file)
├── IMPLEMENTATION_NOTES.md
├── .env.example
├── .gitignore
├── docker-compose.yml
├── contracts/
│   └── events/
│       ├── application-created.v1.json
│       └── application-status-changed.v1.json
├── scripts/
│   └── e2e-smoke.sh
├── infrastructure/
│   ├── prometheus/prometheus.yml
│   └── grafana/ (provisioning + dashboard json)
├── backend/
│   ├── pom.xml                   (parent, dependency management)
│   ├── mvnw, .mvn/
│   ├── discovery-server/
│   ├── api-gateway/
│   ├── user-service/
│   ├── job-service/
│   ├── application-service/
│   └── notification-service/
└── frontend/
    └── placehub-web/
```

Package layout per service under `com.placehub.<service>`: `config`, `controller`, `dto`, `entity` / `document`, `repository`, `service`, `security`, `exception`, `client` (Feign), `messaging` (producers, consumers, outbox), `mapper`. Business logic in services; controllers stay thin.

------------------------------------------------------------------------

# 16. Implementation Plan

Execute in order without stopping for approval. Commit (if git is present) at the end of each phase with a descriptive message.

**Phase 0: Inspect and plan.** Inspect repository, preserve existing work, create `IMPLEMENTATION_NOTES.md` with decisions and a checklist mirroring Section 17. Verify Boot/Cloud versions on start.spring.io.

**Phase 1: Foundation.** Parent POM, Maven wrapper, Eureka, Gateway with route table and `/internal/**` block, Compose with all infra containers and health checks. Verify: Eureka UI shows gateway.

**Phase 2: User Service.** Entities, Flyway, registration, login, JWT, `/me`, admin endpoints, internal endpoints with token filter, seed users, error handling, tests. Verify login through the gateway.

**Phase 3: Job Service.** Companies, jobs, ownership, visibility, filters, `EligibilityEvaluator`, internal endpoints, seed company/jobs with fixed UUIDs, tests. Verify student browse through the gateway with a seeded student token.

**Phase 4: Application Service.** Feign clients with timeouts, circuit breakers, internal token interceptor; apply, eligibility preview, duplicate handling, lists, status/withdraw, outbox table (relay can be a no-op stub until Phase 5 only within this phase), tests.

**Phase 5: Kafka + Notification Service.** Topics, outbox relay, consumer with DLT, idempotent persistence, console email, notification APIs, tests, contract fixtures. Verify a notification appears after applying.

**Phase 6: Frontend.** All pages in 7.2, API layer, interceptors, protected routes, states, tests, Dockerfile with nginx.

**Phase 7: Integration and hardening.** `./mvnw verify`, `npm run build && npm test`, `docker compose up --build`, `scripts/e2e-smoke.sh` all green. Authorization boundary review against the permission matrix. OpenAPI pages load. Observability profile last.

**Phase 8: Documentation and handoff.** README (Section 18), update `IMPLEMENTATION_NOTES.md`, final report.

------------------------------------------------------------------------

# 17. Definition of Done

- [ ] Four business services, Eureka, and Gateway are separate deployable Spring Boot apps and each starts independently.
- [ ] Each business service uses its own database container and credentials; no cross-service DB access.
- [ ] Eureka shows all five clients registered.
- [ ] Gateway routes match Section 3.4 and blocks `/internal/**`.
- [ ] Every business service validates JWTs itself; internal endpoints require the internal token.
- [ ] Student and recruiter registration/login work; admin cannot be self-registered.
- [ ] Recruiter can create a company, create and publish a job.
- [ ] Students see only published jobs.
- [ ] Eligibility is validated by Job Service with all reasons returned.
- [ ] Eligible student can apply; ineligible gets 422 with reasons; duplicate gets 409; dependency down gets 503.
- [ ] Recruiter can view applicants for own jobs and update status with transition validation.
- [ ] Student can withdraw; recruiter is notified.
- [ ] Events go through the outbox, reach Kafka, and are consumed idempotently.
- [ ] Users only ever see their own notifications.
- [ ] Frontend supports every page in Section 7.2 against live backend data.
- [ ] `docker compose up --build` starts the full system from a clean clone with `.env` copied from `.env.example`.
- [ ] Seed data loads in `dev` only, with fixed UUIDs, and supports the demo flow.
- [ ] `./mvnw verify` passes; frontend build, lint, and tests pass.
- [ ] `scripts/e2e-smoke.sh` passes all 16 checks.
- [ ] README is accurate; no real secrets committed; no TODOs in core workflows.

------------------------------------------------------------------------

# 18. README Contents (required sections)

1. What PlaceHub Lite is (3 to 5 lines).
2. Architecture diagram (Mermaid from 3.2) and service/port/database ownership table.
3. Prerequisites: Docker Desktop with 8 GB memory, or JDK 25 LTS + Node 22 LTS for local runs.
4. Quick start (three commands) and staged startup.
5. URLs: frontend, gateway, Eureka, each Swagger UI, Grafana/Prometheus (if enabled).
6. Demo credentials with the local-only warning.
7. Demo walkthrough (student applies to Graduate Software Engineer, student2 gets 422, recruiter shortlists, notifications appear; optional Kafka-down demo).
8. Kafka topics, outbox, delivery semantics, idempotency, DLT.
9. Security model: JWT flow, per-service validation, internal token, known limitations.
10. Environment variables table.
11. Running tests and the smoke script; what is skipped without Docker.
12. Reset command with data-loss warning.
13. Known limitations and production hardening: RS256 + JWKS, refresh tokens and revocation, mTLS/client-credentials for internal calls, HttpOnly cookies, rate limiting, recruiter approval flow, re-apply after withdrawal, TPO verification of academic data, Kubernetes, managed Kafka, real SMTP.
14. Troubleshooting: gateway 503 during registration warm-up, port conflicts, MySQL slow first start, Docker memory.

------------------------------------------------------------------------

# 19. Instructions to Claude Code

You are the autonomous senior full-stack engineer delivering PlaceHub Lite.

1. Inspect the current working directory first. Do not assume it is empty.
2. Read this entire specification, including the "What changed from v1" table, before making changes.
3. Track progress in `IMPLEMENTATION_NOTES.md` with a checklist.
4. Make routine engineering decisions yourself. Where this spec is explicit, follow it; where it is silent, choose the simplest maintainable option and record it.
5. Prefer working, maintainable code over abstraction.
6. Build incrementally; run builds and tests after each phase.
7. When a command fails, read the error, fix the root cause, retry. Never hide or skip failures silently.
8. No fake implementations, placeholder endpoints, hardcoded frontend data, or TODOs in core paths.
9. Do not claim a feature works unless you ran it or a test covers it.
10. Never weaken authorization to make a demo pass.
11. Never merge services into one Spring Boot app.
12. Never let a service touch another service's database.
13. Notifications only via Kafka events through the outbox, never synchronous REST.
14. Never execute user-submitted code.
15. Secrets only via environment variables; only local example values committed.
16. If Docker is unavailable: still implement everything, run unit tests and the frontend build, and document exactly what was not verified (Testcontainers suites, Compose, smoke script).
17. Use the console email adapter; in-app notifications must be fully functional.
18. Integrate with any existing frontend/backend instead of destroying it.
19. Keep docs in sync with decisions.
20. Final report must include: what was implemented, architecture and ports, how to start, demo credentials, every build/test command run with real results (pass/fail/skip counts), smoke script output, known limitations and incomplete work.

**Begin implementation now. Do not return only a plan. Continue through implementation, testing, and documentation until the Definition of Done is satisfied or a genuine environment blocker prevents further progress.**
