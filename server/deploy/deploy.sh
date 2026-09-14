#!/usr/bin/env bash
set -euo pipefail

# Buku Warung Commercial License Server Deployment Script
# Target: https://license.skmnetwork.com
# Port: 127.0.0.1:3100 (Isolated from Docker port 3000)

APP_DIR="/var/www/bukuwarung-license"
DATA_DIR="/var/data/bukuwarung-license"
INTERNAL_PORT="3100"

echo "=== [1/6] Preparing Isolated Directories ==="
sudo mkdir -p "$APP_DIR"
sudo mkdir -p "$DATA_DIR"

# Set permissions for persistent SQLite data
sudo chown -R www-data:www-data "$DATA_DIR"
sudo chmod 750 "$DATA_DIR"

echo "=== [2/6] Building Production Server ==="
cd "$APP_DIR"
npm ci --production=false
npm run build
npm prune --production

echo "=== [3/6] Setting Up Nginx Configuration ==="
if [ -f "$APP_DIR/deploy/nginx/license.skmnetwork.com.conf" ]; then
    sudo cp "$APP_DIR/deploy/nginx/license.skmnetwork.com.conf" /etc/nginx/sites-available/license.skmnetwork.com.conf
    sudo ln -sf /etc/nginx/sites-available/license.skmnetwork.com.conf /etc/nginx/sites-enabled/
    sudo nginx -t
    sudo systemctl reload nginx
fi

echo "=== [4/6] Starting Fastify License Server with PM2 ==="
if command -v pm2 &> /dev/null; then
    pm2 startOrRestart "$APP_DIR/deploy/pm2/ecosystem.config.cjs" --env production
    pm2 save
else
    echo "PM2 not found. Please install PM2 (npm install -g pm2) or start with node dist/server.js"
fi

echo "=== [5/6] Local Loopback Health Check Verification ==="
sleep 2
curl -sSf "http://127.0.0.1:${INTERNAL_PORT}/health" || {
    echo "ERROR: Local health check on port ${INTERNAL_PORT} failed!"
    exit 1
}

echo "=== [6/6] SSL Certificate Provisioning ==="
echo "If certificate is not yet issued, run:"
echo "sudo certbot --nginx -d license.skmnetwork.com"

echo "=== Deployment Ready: https://license.skmnetwork.com ==="
