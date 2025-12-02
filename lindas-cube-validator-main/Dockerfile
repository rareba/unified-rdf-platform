FROM docker.io/library/node:22-alpine AS builder

# Set the working directory
WORKDIR /app

# First, install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Then, copy the rest of the files
COPY . .
RUN npm run build

# Create the final image with the built files
FROM docker.io/library/nginx:1.26-alpine

RUN mkdir -p /app

WORKDIR /app
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /app/dist/cube-validator/browser/ .

EXPOSE 80
