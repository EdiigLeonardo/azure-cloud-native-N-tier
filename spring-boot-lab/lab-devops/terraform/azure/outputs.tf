output "resource_group" {
  value = azurerm_resource_group.lab.name
}

output "acr_login_server" {
  value = azurerm_container_registry.acr.login_server
}

output "aks_get_credentials" {
  value = "az aks get-credentials -g ${azurerm_resource_group.lab.name} -n ${azurerm_kubernetes_cluster.aks.name}"
}
