-- #240: an attestation whose status is the caller's claim, which the server could not check, is
-- marked self-asserted. A flow can refuse to count such evidence, except for types it names.
ALTER TABLE attestations ADD COLUMN self_asserted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE flows ADD COLUMN reject_self_asserted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE flows ADD COLUMN accepted_self_asserted_types TEXT DEFAULT '' NOT NULL;
