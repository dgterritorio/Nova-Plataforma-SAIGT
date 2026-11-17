FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public ./public
COPY src ./src
# `development` só para desenvolvimento local; as imagens a entregar usam `production`.
ARG BUILD_CONFIG=production
RUN npx ng build --configuration $BUILD_CONFIG

FROM nginxinc/nginx-unprivileged:alpine
COPY --from=builder /app/dist/saigt-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
