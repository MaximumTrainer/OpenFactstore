# ── Organisation ─────────────────────────────────────────────────────────────

resource "factstore_organisation" "main" {
  slug        = var.org_slug
  name        = var.org_name
  description = "Root organisation managed by Terraform."
}

# ── Logical environment group ─────────────────────────────────────────────────

resource "factstore_logical_environment" "production_group" {
  name        = "production-group"
  description = "Logical grouping for all production-tier environments."
}

# ── Physical environments ─────────────────────────────────────────────────────

resource "factstore_environment" "staging" {
  name        = "staging"
  type        = "K8S"
  description = "Staging Kubernetes cluster. Deployments require baseline attestations."
}

resource "factstore_environment" "production" {
  name        = "production"
  type        = "K8S"
  description = "Production Kubernetes cluster. Deployments require full compliance attestations."
}

# ── Deployment policies ───────────────────────────────────────────────────────

resource "factstore_policy" "baseline" {
  name                       = "baseline-requirements"
  enforce_provenance         = true
  enforce_trail_compliance   = false
  required_attestation_types = ["junit", "snyk"]
}

resource "factstore_policy" "production" {
  name                       = "production-requirements"
  enforce_provenance         = true
  enforce_trail_compliance   = true
  required_attestation_types = ["junit", "snyk", "pull-request"]
}

# ── Policy attachments ────────────────────────────────────────────────────────

resource "factstore_policy_attachment" "staging" {
  policy_id      = factstore_policy.baseline.id
  environment_id = factstore_environment.staging.id
}

resource "factstore_policy_attachment" "production" {
  policy_id      = factstore_policy.production.id
  environment_id = factstore_environment.production.id
}

# ── Flows ─────────────────────────────────────────────────────────────────────

resource "factstore_flow" "backend_ci" {
  name                       = "backend-ci"
  description                = "CI pipeline for the backend service. Requires unit tests and security scan."
  required_attestation_types = ["junit", "snyk"]
}

resource "factstore_flow" "frontend_ci" {
  name                       = "frontend-ci"
  description                = "CI pipeline for the frontend application. Requires build verification and security scan."
  required_attestation_types = ["build", "snyk"]
}
