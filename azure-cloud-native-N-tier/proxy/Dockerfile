FROM node:20 AS frontend-build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci --silent || true

COPY frontend ./frontend
RUN cd frontend && npm run build

FROM nginx:stable-alpine
COPY proxy/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=frontend-build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
CMD ["/bin/sh", "-c", "sed -e \"s|\\${BACKEND_URL}|${BACKEND_URL:-http://backend}|g\" /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;' "]
