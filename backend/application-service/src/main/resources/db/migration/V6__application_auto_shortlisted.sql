-- v3 (spec 20.4): marks applications shortlisted by the system, for the recruiter's auto-shortlist indicator.
ALTER TABLE applications ADD COLUMN auto_shortlisted BOOLEAN NOT NULL DEFAULT FALSE;
