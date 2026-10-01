# Run with Docker Compose (local dev)

Build and run the full stack (Postgres, Backend, Frontend, Nginx proxy):

```bash
docker compose build --pull --no-cache
docker compose up --remove-orphans
```

Services:
- Proxy: http://localhost (port 80) — routes `/api/*` to backend and other requests to frontend
- Frontend: served by its own nginx on container port 80 (mapped to host 3000)
- Backend: Spring Boot on port 8080 (proxied by `proxy`)
- Postgres: internal `db` service

Quick security scan (local images):

```bash
# after building images
trivy image $(docker images --format "{{.Repository}}:{{.Tag}}" | grep backend | head -n1)
trivy image $(docker images --format "{{.Repository}}:{{.Tag}}" | grep frontend | head -n1)
```
