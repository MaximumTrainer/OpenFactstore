ALTER TABLE deployment_policies ADD COLUMN IF NOT EXISTS wasm_module_content TEXT;
ALTER TABLE deployment_policies ADD COLUMN IF NOT EXISTS policy_evaluator VARCHAR(50) NOT NULL DEFAULT 'opa';
ALTER TABLE policies ADD COLUMN IF NOT EXISTS wasm_module_content TEXT;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS policy_evaluator VARCHAR(50) NOT NULL DEFAULT 'opa';
