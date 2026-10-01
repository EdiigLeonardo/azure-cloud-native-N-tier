terraform {
  required_version = ">= 1.6.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }

    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {}
}

locals {
  project_slug    = lower(var.project_name)
  resource_suffix = "${var.environment_name}-${local.project_slug}"
  acr_name        = substr(replace("acr${local.resource_suffix}", "-", ""), 0, 50)
  postgres_name   = substr(replace("pg-${local.resource_suffix}", "-", ""), 0, 63)
  backend_name    = "${local.project_slug}-backend"
  proxy_name      = "${local.project_slug}-proxy"
  log_workspace   = "${local.project_slug}-law"
  container_env   = "${local.project_slug}-aca-env"
}

resource "azurerm_resource_group" "this" {
  name     = var.resource_group_name
  location = var.location
}

resource "azurerm_log_analytics_workspace" "this" {
  name                = local.log_workspace
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
}

resource "azurerm_container_app_environment" "this" {
  name                       = local.container_env
  location                   = azurerm_resource_group.this.location
  resource_group_name        = azurerm_resource_group.this.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.this.id
}

resource "azurerm_container_registry" "this" {
  name                = local.acr_name
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  sku                 = "Basic"
  admin_enabled       = true
}

resource "azurerm_postgresql_flexible_server" "db" {
  name                   = local.postgres_name
  resource_group_name    = azurerm_resource_group.this.name
  location               = azurerm_resource_group.this.location
  version                = "16"
  administrator_login    = var.postgres_admin_user
  administrator_password = var.postgres_admin_password
  zone                   = "1"
  storage_mb             = 32768
  sku_name               = "B_Standard_B1ms"

  backup_retention_days        = 7
  geo_redundant_backup_enabled = false
  public_network_access_enabled = true

  tags = {
    environment = var.environment_name
    project     = var.project_name
  }
}

resource "azurerm_postgresql_flexible_server_database" "backend" {
  name      = "backend"
  server_id = azurerm_postgresql_flexible_server.db.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_container_app" "backend" {
  name                         = local.backend_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = azurerm_container_registry.this.login_server
    username = azurerm_container_registry.this.admin_username
    password_secret_name = "acr-password"
  }

  secret {
    name  = "acr-password"
    value = azurerm_container_registry.this.admin_password
  }

  template {
    min_replicas = 1
    max_replicas = 2

    container {
      name   = "backend"
      image  = "${azurerm_container_registry.this.login_server}/backend:latest"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "JDBC_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.db.fqdn}:5432/${azurerm_postgresql_flexible_server_database.backend.name}?sslmode=require"
      }

      env {
        name  = "JDBC_DATABASE_USERNAME"
        value = var.postgres_admin_user
      }

      env {
        name  = "JDBC_DATABASE_PASSWORD"
        value = var.postgres_admin_password
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }

      liveness_probe {
        transport        = "TCP"
        port             = 8080
        interval_seconds = 30
        timeout          = 10
      }

      readiness_probe {
        transport        = "TCP"
        port             = 8080
        interval_seconds = 15
        timeout          = 10
      }
    }
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"

    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

resource "azurerm_container_app" "proxy" {
  name                         = local.proxy_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"

  registry {
    server   = azurerm_container_registry.this.login_server
    username = azurerm_container_registry.this.admin_username
    password_secret_name = "acr-password"
  }

  secret {
    name  = "acr-password"
    value = azurerm_container_registry.this.admin_password
  }

  template {
    min_replicas = 1
    max_replicas = 2

    container {
      name   = "proxy"
      image  = "${azurerm_container_registry.this.login_server}/proxy:latest"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "BACKEND_URL"
        value = "https://${azurerm_container_app.backend.latest_revision_fqdn}"
      }
    }
  }

  ingress {
    external_enabled = true
    target_port      = 80
    transport        = "auto"

    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}
