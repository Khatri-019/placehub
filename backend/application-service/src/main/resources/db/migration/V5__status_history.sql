-- v3 (spec 20.4): one row per status transition, including creation (from_status NULL), withdrawals and SYSTEM
-- transitions (policy withdrawals, auto-shortlisting). seq gives a stable order within one transaction.
CREATE TABLE application_status_history (
    id             UUID PRIMARY KEY,
    application_id UUID          NOT NULL REFERENCES applications (id),
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)   NOT NULL,
    actor_type     VARCHAR(10)   NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    actor_user_id  UUID,
    reason         VARCHAR(1000),
    changed_at     TIMESTAMPTZ   NOT NULL,
    seq            BIGSERIAL
);
CREATE INDEX ix_status_history_application ON application_status_history (application_id, seq);
