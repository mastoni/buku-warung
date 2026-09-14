# Stage 1: Builder
FROM node:20-alpine AS builder

WORKDIR /app

# Install native dependencies required for better-sqlite3 compilation
RUN apk add --no-cache python3 make g++

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Remove development dependencies
RUN npm prune --omit=dev

# Stage 2: Production Runner
FROM node:20-alpine AS runner

WORKDIR /app

RUN apk add --no-cache wget ca-certificates

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

# Prepare persistent data directory with proper ownership
RUN mkdir -p /var/data/bukuwarung-license && chown -R node:node /var/data/bukuwarung-license /app

COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist

USER node

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:3000/health || exit 1

CMD ["node", "dist/server.js"]
