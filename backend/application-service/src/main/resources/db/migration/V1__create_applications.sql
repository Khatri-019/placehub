-- No foreign keys to other services: job, student and recruiter ids are plain UUID columns.
CREATE TABLE applications (
    id                       UUID PRIMARY KEY,
    job_id                   UUID          NOT NULL,
    student_user_id          UUID          NOT NULL,
    recruiter_owner_user_id  UUID          NOT NULL,
    job_title_snapshot       VARCHAR(150)  NOT NULL,
    company_name_snapshot    VARCHAR(150)  NOT NULL,
    student_name_snapshot    VARCHAR(100)  NOT NULL,
    student_email_snapshot   VARCHAR(254)  NOT NULL,
    branch_snapshot          VARCHAR(10)   NOT NULL,
    cgpa_snapshot            NUMERIC(4, 2) NOT NULL,
    graduation_year_snapshot INTEGER       NOT NULL,
    backlog_count_snapshot   INTEGER       NOT NULL,
    status                   VARCHAR(20)   NOT NULL CHECK (status IN
        ('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'SELECTED', 'REJECTED', 'WITHDRAWN')),
    recruiter_note           VARCHAR(1000),
    applied_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_job_student UNIQUE (job_id, student_user_id)
);

CREATE INDEX ix_applications_student ON applications (student_user_id);
CREATE INDEX ix_applications_job ON applications (job_id);
CREATE INDEX ix_applications_recruiter ON applications (recruiter_owner_user_id);

-- Transactional outbox. id is also the event's eventId.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ
);

CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);
