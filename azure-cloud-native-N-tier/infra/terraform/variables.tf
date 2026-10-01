variable "location" {
  description = "Azure region for the lab resources. This subscription enforces a restricted set of regions; use one of the allowed values: francecentral, spaincentral, austriaeast, italynorth, norwayeast."
  type        = string
  default     = "francecentral"
}

variable "resource_group_name" {
  description = "Resource group for the entire application stack."
  type        = string
  default     = "rg-devsecops-lab"
}

variable "project_name" {
  description = "Project slug used for naming Azure resources."
  type        = string
  default     = "cloud-native-n-tier"
}

variable "environment_name" {
  description = "Environment label (dev, stage, prod)."
  type        = string
  default     = "dev"
}

variable "postgres_admin_user" {
  description = "PostgreSQL administrator username for the Flexible Server."
  type        = string
  default     = "postgresadmin"
}

variable "postgres_admin_password" {
  description = "Strong PostgreSQL admin password. Keep this in a secret store instead of source control."
  type        = string
  sensitive   = true
  default     = "ChangeMe!123456"
}
