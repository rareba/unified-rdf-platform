# Stage 1: Build the application
FROM node:20-alpine AS builder

# Build configuration argument (production, offline, online)
ARG BUILD_CONFIG=production

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --legacy-peer-deps

COPY . .

# Build with the specified configuration
RUN npm run build -- --configuration ${BUILD_CONFIG}

# Stage 2: Serve the application with Nginx
FROM nginx:stable-alpine

COPY --from=builder /app/dist/rdf-forge-ui/browser /usr/share/nginx/html

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]