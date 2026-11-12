-- #193: an environment's risk tier decides how its deployment gate falls back when the evidence
-- cannot be evaluated. TIER1 fails closed and is the default; STANDARD fails open with an audit alert.
ALTER TABLE environments ADD COLUMN tier VARCHAR(16) DEFAULT 'TIER1' NOT NULL;
