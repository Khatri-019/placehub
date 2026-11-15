-- DEV PROFILE ONLY (location added only in application-dev.yml). Idempotent.
-- Password for every account: PlaceHub@123 (BCrypt strength 10, precomputed). Local demo only.
INSERT INTO users (id, full_name, email, password_hash, role, account_status, created_at, updated_at) VALUES
  ('00000000-0000-0000-0000-000000000001', 'Platform Admin', 'admin@placehub.local',
   '$2a$10$4txQ8RjbqmLHIX/BM1kQdOFAwZ4z0v3nRkCglq6GRJYkLJB49Goei', 'ADMIN', 'ACTIVE', NOW(), NOW()),
  ('00000000-0000-0000-0000-000000000002', 'Riya Recruiter', 'recruiter@placehub.local',
   '$2a$10$4txQ8RjbqmLHIX/BM1kQdOFAwZ4z0v3nRkCglq6GRJYkLJB49Goei', 'RECRUITER', 'ACTIVE', NOW(), NOW()),
  ('00000000-0000-0000-0000-000000000003', 'Demo Student', 'student@placehub.local',
   '$2a$10$4txQ8RjbqmLHIX/BM1kQdOFAwZ4z0v3nRkCglq6GRJYkLJB49Goei', 'STUDENT', 'ACTIVE', NOW(), NOW()),
  ('00000000-0000-0000-0000-000000000004', 'Second Student', 'student2@placehub.local',
   '$2a$10$4txQ8RjbqmLHIX/BM1kQdOFAwZ4z0v3nRkCglq6GRJYkLJB49Goei', 'STUDENT', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO recruiter_details (user_id, company_name, designation) VALUES
  ('00000000-0000-0000-0000-000000000002', 'Northstar Technologies', 'Talent Acquisition Lead')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO student_details (user_id, college_name, branch, graduation_year, cgpa, backlog_count) VALUES
  ('00000000-0000-0000-0000-000000000003', 'PlaceHub Institute of Technology', 'CSE', 2027, 8.60, 0),
  ('00000000-0000-0000-0000-000000000004', 'PlaceHub Institute of Technology', 'ME', 2027, 6.40, 2)
ON CONFLICT (user_id) DO NOTHING;
