-- v3 (spec 20.1): numeric CTC in lakhs per annum. Required to publish FULL_TIME jobs (enforced in the service);
-- optional for internships. The free-text `ctc` column stays as a display label.
ALTER TABLE jobs ADD COLUMN ctc_lpa DECIMAL(6, 2) NULL AFTER ctc;
