-- UUIDs are stored as CHAR(36) for readability (spec section 8.2). All timestamps are UTC.
CREATE TABLE companies (
    id                      CHAR(36)      NOT NULL PRIMARY KEY,
    name                    VARCHAR(150)  NOT NULL,
    website                 VARCHAR(255)  NULL,
    description             VARCHAR(2000) NULL,
    industry                VARCHAR(100)  NULL,
    recruiter_owner_user_id CHAR(36)      NOT NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    CONSTRAINT uq_companies_recruiter UNIQUE (recruiter_owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE jobs (
    id                      CHAR(36)      NOT NULL PRIMARY KEY,
    company_id              CHAR(36)      NOT NULL,
    recruiter_owner_user_id CHAR(36)      NOT NULL,
    title                   VARCHAR(150)  NOT NULL,
    description             TEXT          NOT NULL,
    location                VARCHAR(150)  NOT NULL,
    employment_type         VARCHAR(20)   NOT NULL,
    ctc                     VARCHAR(100)  NULL,
    min_cgpa                DECIMAL(4, 2) NOT NULL DEFAULT 0,
    max_backlogs            INT           NOT NULL DEFAULT 0,
    deadline                DATETIME(6)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_jobs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_jobs_type CHECK (employment_type IN ('INTERNSHIP', 'FULL_TIME')),
    INDEX ix_jobs_status_deadline (status, deadline),
    INDEX ix_jobs_recruiter (recruiter_owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE job_eligible_branches (
    job_id CHAR(36)    NOT NULL,
    branch VARCHAR(10) NOT NULL,
    PRIMARY KEY (job_id, branch),
    CONSTRAINT fk_branches_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE job_eligible_graduation_years (
    job_id          CHAR(36) NOT NULL,
    graduation_year INT      NOT NULL,
    PRIMARY KEY (job_id, graduation_year),
    CONSTRAINT fk_years_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
