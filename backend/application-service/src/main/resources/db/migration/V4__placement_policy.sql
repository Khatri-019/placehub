-- v3 (spec 20.3): placement policy engine, owned by Application Service.

CREATE TABLE placement_policies (
    id                        UUID PRIMARY KEY,
    version                   INTEGER      NOT NULL UNIQUE,
    active                    BOOLEAN      NOT NULL DEFAULT FALSE,
    bands_json                TEXT         NOT NULL,
    applies_to_internships    BOOLEAN      NOT NULL,
    max_active_consents       INTEGER      CHECK (max_active_consents IS NULL OR max_active_consents >= 1),
    resume_required           BOOLEAN      NOT NULL,
    withdraw_others_on_lock   BOOLEAN      NOT NULL,
    consent_text              TEXT         NOT NULL,
    effective_from            TIMESTAMPTZ  NOT NULL,
    changed_by_user_id        UUID,
    change_note               VARCHAR(500) NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL
);
-- Exactly one active version.
CREATE UNIQUE INDEX uq_placement_policies_active ON placement_policies (active) WHERE active;

-- Default policy v1 (all profiles, not dev-only seed data).
INSERT INTO placement_policies (id, version, active, bands_json, applies_to_internships, max_active_consents,
                                resume_required, withdraw_others_on_lock, consent_text, effective_from,
                                changed_by_user_id, change_note, created_at)
VALUES ('30000000-0000-0000-0000-000000000001', 1, TRUE,
        '[{"fromMultiplier":0,"toMultiplier":1.5,"action":"BLOCK","lockOnPlacement":false},'
            || '{"fromMultiplier":1.5,"toMultiplier":2.0,"action":"REQUIRE_CONSENT","lockOnPlacement":true},'
            || '{"fromMultiplier":2.0,"toMultiplier":null,"action":"ALLOW","lockOnPlacement":false}]',
        FALSE, NULL, TRUE, TRUE,
        E'## Placement policy consent\n\n'
            || E'I am applying to **{{jobTitle}}** at **{{companyName}}**, which offers **{{ctcLpa}} LPA**. '
            || E'My current placement is **{{currentCtc}} LPA**.\n\n'
            || E'I understand that if I am selected for this role I will be **locked** under the placement policy: '
            || E'I will not be able to apply to any other job, and my other open applications will be withdrawn.',
        NOW(), NULL, 'Default policy (v1)', NOW());

-- Per-student lock state. Current CTC is derived from placements (highest ctc_lpa).
CREATE TABLE student_placement_states (
    student_user_id UUID PRIMARY KEY,
    locked          BOOLEAN       NOT NULL DEFAULT FALSE,
    lock_reason     VARCHAR(1000),
    locked_at       TIMESTAMPTZ,
    updated_by      UUID,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE placements (
    id              UUID PRIMARY KEY,
    student_user_id UUID          NOT NULL,
    student_name    VARCHAR(100)  NOT NULL,
    application_id  UUID          REFERENCES applications (id),
    company_name    VARCHAR(150)  NOT NULL,
    ctc_lpa         NUMERIC(6, 2),
    band            VARCHAR(120),
    locked          BOOLEAN       NOT NULL,
    source          VARCHAR(20)   NOT NULL CHECK (source IN ('ON_CAMPUS', 'OFF_CAMPUS')),
    recorded_at     TIMESTAMPTZ   NOT NULL,
    recorded_by     UUID
);
CREATE INDEX ix_placements_student ON placements (student_user_id);
CREATE UNIQUE INDEX uq_placements_application ON placements (application_id) WHERE application_id IS NOT NULL;

-- Immutable consent records, written in the same transaction as the application.
CREATE TABLE consents (
    id                UUID PRIMARY KEY,
    application_id    UUID          NOT NULL UNIQUE REFERENCES applications (id),
    student_user_id   UUID          NOT NULL,
    job_id            UUID          NOT NULL,
    policy_version    INTEGER       NOT NULL,
    band              VARCHAR(120)  NOT NULL,
    consent_text_hash VARCHAR(64)   NOT NULL,
    rendered_text     TEXT          NOT NULL,
    signed_name       VARCHAR(100)  NOT NULL,
    accepted_at       TIMESTAMPTZ   NOT NULL,
    client_ip         VARCHAR(64),
    user_agent        VARCHAR(512)
);
CREATE INDEX ix_consents_job ON consents (job_id);
CREATE INDEX ix_consents_student ON consents (student_user_id);

CREATE TABLE policy_audit_log (
    id            UUID PRIMARY KEY,
    actor_user_id UUID,
    action        VARCHAR(40)   NOT NULL,
    student_user_id UUID,
    before_json   TEXT,
    after_json    TEXT,
    reason        VARCHAR(1000),
    created_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_policy_audit_created ON policy_audit_log (created_at DESC);

-- Policy snapshots on applications: later policy changes never alter existing applications.
ALTER TABLE applications ADD COLUMN policy_version_snapshot INTEGER;
ALTER TABLE applications ADD COLUMN policy_decision_snapshot VARCHAR(20);
ALTER TABLE applications ADD COLUMN ratio_snapshot NUMERIC(8, 2);
ALTER TABLE applications ADD COLUMN policy_band_snapshot VARCHAR(120);
ALTER TABLE applications ADD COLUMN lock_on_placement_snapshot BOOLEAN NOT NULL DEFAULT FALSE;

-- Outbox rows are relayed in insertion order (selection + auto-withdrawal and auto-shortlist write several events in
-- one transaction with the same timestamp).
ALTER TABLE outbox_events ADD COLUMN seq BIGSERIAL;
CREATE INDEX ix_outbox_status_seq ON outbox_events (status, seq);
