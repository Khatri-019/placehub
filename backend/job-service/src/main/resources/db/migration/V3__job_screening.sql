-- v3 (spec 20.4): optional auto-shortlisting. Every existing job stays MANUAL.
ALTER TABLE jobs ADD COLUMN screening_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER status;
ALTER TABLE jobs ADD COLUMN auto_shortlist_min_cgpa DECIMAL(4, 2) NULL AFTER screening_mode;
ALTER TABLE jobs ADD COLUMN auto_shortlist_max_backlogs INT NULL AFTER auto_shortlist_min_cgpa;
ALTER TABLE jobs ADD CONSTRAINT ck_jobs_screening CHECK (screening_mode IN ('MANUAL', 'AUTO_SHORTLIST'));
