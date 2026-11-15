CREATE TABLE users (
    id             UUID PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'RECRUITER', 'ADMIN')),
    account_status VARCHAR(20)  NOT NULL CHECK (account_status IN ('PENDING_APPROVAL', 'ACTIVE', 'DEACTIVATED')),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = LOWER(email))
);

CREATE INDEX ix_users_role_status ON users (role, account_status);

CREATE TABLE student_details (
    user_id         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    college_name    VARCHAR(150)  NOT NULL,
    branch          VARCHAR(10)   NOT NULL,
    graduation_year INTEGER       NOT NULL CHECK (graduation_year BETWEEN 2020 AND 2035),
    cgpa            NUMERIC(4, 2) NOT NULL CHECK (cgpa BETWEEN 0 AND 10),
    backlog_count   INTEGER       NOT NULL CHECK (backlog_count BETWEEN 0 AND 50)
);

CREATE TABLE recruiter_details (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    company_name VARCHAR(150) NOT NULL,
    designation  VARCHAR(100) NOT NULL
);
