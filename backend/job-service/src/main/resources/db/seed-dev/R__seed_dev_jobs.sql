-- DEV PROFILE ONLY (location added only in application-dev.yml). Idempotent via INSERT IGNORE.
-- Owner is the seeded recruiter 00000000-0000-0000-0000-000000000002 (User Service). Deadlines are relative to
-- migration time so the demo never expires.
INSERT IGNORE INTO companies (id, name, website, description, industry, recruiter_owner_user_id, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'Northstar Technologies', 'https://northstar.example',
        'Product engineering company building logistics and analytics platforms.', 'Software',
        '00000000-0000-0000-0000-000000000002', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT IGNORE INTO jobs (id, company_id, recruiter_owner_user_id, title, description, location, employment_type, ctc,
                         min_cgpa, max_backlogs, deadline, status, created_at, updated_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002', 'Graduate Software Engineer',
   'Build and operate backend services in Java and Spring. Strong fundamentals in data structures expected.',
   'Bengaluru', 'FULL_TIME', '12 LPA', 7.50, 0, UTC_TIMESTAMP(6) + INTERVAL 60 DAY, 'PUBLISHED',
   UTC_TIMESTAMP(6) - INTERVAL 4 SECOND, UTC_TIMESTAMP(6)),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002', 'Data Analyst Intern',
   'Six-month internship analysing operational data with SQL and Python dashboards.',
   'Pune (Hybrid)', 'INTERNSHIP', '35k per month', 6.00, 2, UTC_TIMESTAMP(6) + INTERVAL 45 DAY, 'PUBLISHED',
   UTC_TIMESTAMP(6) - INTERVAL 3 SECOND, UTC_TIMESTAMP(6)),
  ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002', 'Embedded Systems Engineer',
   'Firmware development for IoT telemetry devices using C and an RTOS.',
   'Hyderabad', 'FULL_TIME', '10 LPA', 7.00, 0, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, 'PUBLISHED',
   UTC_TIMESTAMP(6) - INTERVAL 2 SECOND, UTC_TIMESTAMP(6)),
  ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002', 'Platform Engineer (draft)',
   'Kubernetes and CI/CD platform work. Draft posting, not visible to students.',
   'Remote', 'FULL_TIME', NULL, 0.00, 0, UTC_TIMESTAMP(6) + INTERVAL 90 DAY, 'DRAFT',
   UTC_TIMESTAMP(6) - INTERVAL 1 SECOND, UTC_TIMESTAMP(6));

INSERT IGNORE INTO job_eligible_branches (job_id, branch) VALUES
  ('20000000-0000-0000-0000-000000000001', 'CSE'),
  ('20000000-0000-0000-0000-000000000001', 'ISE'),
  ('20000000-0000-0000-0000-000000000001', 'AIML'),
  ('20000000-0000-0000-0000-000000000003', 'ECE'),
  ('20000000-0000-0000-0000-000000000003', 'EEE'),
  ('20000000-0000-0000-0000-000000000003', 'ETE');

INSERT IGNORE INTO job_eligible_graduation_years (job_id, graduation_year) VALUES
  ('20000000-0000-0000-0000-000000000001', 2027),
  ('20000000-0000-0000-0000-000000000002', 2027),
  ('20000000-0000-0000-0000-000000000002', 2028);
