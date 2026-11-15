-- v3 (spec 20.2): the resume version captured at apply time (User Service id; no cross-service foreign key).
ALTER TABLE applications ADD COLUMN resume_id_snapshot UUID;
