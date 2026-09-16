# Stage 1: Builder
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
COPY public ./public
RUN npm run build

# Remove development dependencies
RUN npm prune --omit=dev

# Stage 2: Production Runner
FROM node:20-alpine AS runner

WORKDIR /app

RUN apk add --no-cache wget ca-certificates

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3001

COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/package-lock.json ./package-lock.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/public ./public

USER node

EXPOSE 3001

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:3001/health || exit 1

CMD ["node", "dist/server.js"]
