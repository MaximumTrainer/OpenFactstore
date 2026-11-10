# Factstore Provider

The Factstore Terraform provider allows teams to manage [Factstore](https://github.com/MaximumTrainer/OpenFactstore) resources as Infrastructure-as-Code — enabling version-controlled, code-reviewed, reproducible configuration of compliance pipelines.

## Getting Started

```hcl
terraform {
  required_providers {
    factstore = {
      source  = "MaximumTrainer/factstore"
      version = "~> 1.0"
    }
  }
}

provider "factstore" {
  base_url  = "https://factstore.example.com"
  api_token = var.factstore_api_token
}
```

## Authentication

The provider authenticates using a Factstore API token. You can supply it via:

- The `api_token` provider argument
- The `FACTSTORE_API_TOKEN` environment variable (recommended for CI/CD)

## Provider Configuration

| Argument    | Type   | Required | Description |
|-------------|--------|----------|-------------|
| `base_url`  | string | yes      | Base URL of the Factstore API. Also settable via `FACTSTORE_BASE_URL`. |
| `api_token` | string | no       | API token for authentication. Also settable via `FACTSTORE_API_TOKEN`. Required in practice: authentication is enforced by default, and managing flows, policies and environments needs an `admin`-scoped key. |

## Resources

| Resource                        | Description |
|---------------------------------|-------------|
| `factstore_flow`                | Manages a compliance flow |
| `factstore_environment`         | Manages a deployment environment |
| `factstore_policy`              | Manages a compliance policy |
| `factstore_policy_attachment`   | Attaches a policy to an environment |
| `factstore_logical_environment` | Manages a logical environment |
| `factstore_organisation`        | Manages an organisation |

## Data Sources

| Data Source                 | Description |
|-----------------------------|-------------|
| `data.factstore_flow`        | Reads an existing flow by ID |
| `data.factstore_environment` | Reads an existing environment by ID |

## Example: Full Compliance Setup

```hcl
resource "factstore_organisation" "acme" {
  slug        = "acme"
  name        = "ACME Corp"
  description = "ACME Corporation"
}

resource "factstore_flow" "backend_ci" {
  name        = "backend-ci"
  description = "CI pipeline for the backend service"
  required_attestation_types = ["junit", "snyk", "pull-request"]
}

resource "factstore_environment" "production" {
  name        = "production"
  type        = "K8S"
  description = "Production Kubernetes cluster"
}

resource "factstore_policy" "prod_policy" {
  name                     = "prod-requirements"
  enforce_provenance       = true
  enforce_trail_compliance = true
  required_attestation_types = ["snyk", "junit"]
}

resource "factstore_policy_attachment" "prod" {
  policy_id      = factstore_policy.prod_policy.id
  environment_id = factstore_environment.production.id
}
```

## Import

All resources support `terraform import` using their Factstore UUID:

```sh
terraform import factstore_flow.backend_ci <flow-uuid>
terraform import factstore_environment.production <environment-uuid>
terraform import factstore_policy.prod_policy <policy-uuid>
terraform import factstore_policy_attachment.prod <attachment-uuid>
terraform import factstore_logical_environment.prod_group <logical-env-uuid>
terraform import factstore_organisation.acme <organisation-uuid>
```
