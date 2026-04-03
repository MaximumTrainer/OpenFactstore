terraform {
  required_version = ">= 1.6"

  required_providers {
    factstore = {
      source  = "MaximumTrainer/factstore"
      version = "~> 1.0"
    }
  }
}

provider "factstore" {
  base_url  = var.factstore_url
  api_token = var.factstore_token
}
