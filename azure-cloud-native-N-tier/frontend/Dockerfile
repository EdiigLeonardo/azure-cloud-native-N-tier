# Frontend Dockerfile: build Vite app and serve with nginx
FROM node:20 AS build
WORKDIR /app
COPY package.json package-lock.json pnpm-lock.yaml* ./
RUN npm ci --silent || true
COPY . .
RUN npm run build

FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["/bin/sh","-c","nginx -g 'daemon off;'"]
