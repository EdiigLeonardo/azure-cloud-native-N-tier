variable "subscription_id" {
  description = "ID da subscrição Azure (az account show --query id -o tsv)"
  type        = string
}

variable "location" {
  type    = string
  default = "westeurope"
}

variable "node_vm_size" {
  description = "VM dos nós do AKS. B2s é das mais baratas; se não houver quota, experimenta Standard_D2s_v5"
  type        = string
  default     = "Standard_B2s"
}

variable "node_count" {
  type    = number
  default = 1
}
