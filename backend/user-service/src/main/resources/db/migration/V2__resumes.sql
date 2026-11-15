-- v3 (spec 20.2): resume versions owned by User Service. The newest 3 versions per student are kept; older versions
-- that an application references (referenced = true) are never pruned or deletable.
CREATE TABLE resumes (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename VARCHAR(120) NOT NULL,
    size_bytes        INTEGER      NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 2097152),
    sha256            VARCHAR(64)  NOT NULL,
    content           BYTEA        NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL,
    is_current        BOOLEAN      NOT NULL DEFAULT FALSE,
    referenced        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_resumes_user_uploaded ON resumes (user_id, uploaded_at DESC);
CREATE UNIQUE INDEX uq_resumes_one_current ON resumes (user_id) WHERE is_current;
