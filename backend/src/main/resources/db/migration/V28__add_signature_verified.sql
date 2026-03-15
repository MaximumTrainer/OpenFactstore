ALTER TABLE deployment_gate_results ADD COLUMN IF NOT EXISTS signature_verified BOOLEAN;
ALTER TABLE deployment_policies ADD COLUMN IF NOT EXISTS require_signature BOOLEAN NOT NULL DEFAULT FALSE;
