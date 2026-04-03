output "organisation_id" {
  description = "ID of the bootstrapped organisation."
  value       = factstore_organisation.main.id
}

output "staging_environment_id" {
  description = "ID of the staging environment."
  value       = factstore_environment.staging.id
}

output "production_environment_id" {
  description = "ID of the production environment."
  value       = factstore_environment.production.id
}

output "production_group_id" {
  description = "ID of the production logical environment group."
  value       = factstore_logical_environment.production_group.id
}

output "baseline_policy_id" {
  description = "ID of the baseline deployment policy (staging)."
  value       = factstore_policy.baseline.id
}

output "production_policy_id" {
  description = "ID of the production deployment policy."
  value       = factstore_policy.production.id
}

output "backend_flow_id" {
  description = "ID of the backend-ci flow."
  value       = factstore_flow.backend_ci.id
}

output "frontend_flow_id" {
  description = "ID of the frontend-ci flow."
  value       = factstore_flow.frontend_ci.id
}
