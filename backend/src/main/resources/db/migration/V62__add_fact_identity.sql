-- #191: the identity verified at ingestion, bound to each fact by the server.
-- Facts ingested before this change carry no recorded lineage.
ALTER TABLE facts ADD COLUMN identity_json TEXT NOT NULL DEFAULT '{"kind":"unrecorded","subject":"unrecorded"}';
