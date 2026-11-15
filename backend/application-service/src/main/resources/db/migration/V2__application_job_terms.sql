-- v3 (spec 20.1): job compensation snapshots taken at apply time. Nullable: applications created before v3 have none.
ALTER TABLE applications ADD COLUMN ctc_lpa_snapshot NUMERIC(6, 2);
ALTER TABLE applications ADD COLUMN employment_type_snapshot VARCHAR(20);
