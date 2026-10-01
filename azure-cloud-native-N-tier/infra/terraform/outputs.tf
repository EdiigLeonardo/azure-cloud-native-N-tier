output "resource_group_name" {
  value = azurerm_resource_group.this.name
}

output "proxy_url" {
  description = "Public URL for the single-domain proxy that serves the frontend and routes /api to the backend."
  value       = "https://${azurerm_container_app.proxy.latest_revision_fqdn}"
}

output "backend_url" {
  description = "Backend container app URL."
  value       = "https://${azurerm_container_app.backend.latest_revision_fqdn}"
}

output "database_fqdn" {
  description = "PostgreSQL Flexible Server hostname."
  value       = azurerm_postgresql_flexible_server.db.fqdn
}
