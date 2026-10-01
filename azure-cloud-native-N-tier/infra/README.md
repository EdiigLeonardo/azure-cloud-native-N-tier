# Azure deployment notes

This Terraform setup follows the same-domain proxy pattern described in the roadmap:

- one public Container App exposes the frontend and handles reverse proxying
- requests under `/api/*` are forwarded to the backend Container App
- the frontend static bundle is served from the same public endpoint, so there is no direct `localhost` dependency in production
- PostgreSQL Flexible Server is used for the Spring Boot datasource

## Files

- `terraform/main.tf` — Azure resources for resource group, ACR, Container Apps Environment, backend, proxy, and PostgreSQL
- `terraform/variables.tf` — customizable parameters
- `terraform/outputs.tf` — public URLs and connection details
- `terraform/terraform.tfvars.example` — example local values

## Expected flow

1. Build and push the backend image to ACR.
2. Build and push the proxy image to the same registry.
3. Run `terraform init`, `terraform plan` and `terraform apply`.
4. Access the public proxy URL and validate that `/` serves the frontend and `/api/items` reaches the backend.

## Example commands

```bash
cp infra/terraform/terraform.tfvars.example infra/terraform/terraform.tfvars
cd infra/terraform
terraform init
terraform plan
terraform apply
```

> Keep the PostgreSQL password and any production secrets in a secure store such as Azure Key Vault or a CI/CD secret manager. Do not commit real secrets to Git.
