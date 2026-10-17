import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';

// Prioritize .env.local if present, then fallback to .env
const envLocalCandidates = [
  path.resolve(process.cwd(), '.env.local'),
  path.resolve(process.cwd(), 'server/.env.local')
];
for (const envPath of envLocalCandidates) {
  if (fs.existsSync(envPath)) {
    dotenv.config({ path: envPath });
    break;
  }
}
dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '3000', 10),
  host: process.env.HOST || '0.0.0.0',
  nodeEnv: process.env.NODE_ENV || 'development',
  databasePath: process.env.DATABASE_PATH || path.resolve(process.cwd(), 'bukuwarung_license.db'),
  adminApiKey: process.env.ADMIN_API_KEY || 'default_admin_secret_key_12345',
  serverPepper: process.env.SERVER_PEPPER,
  rateLimitMax: parseInt(process.env.RATE_LIMIT_MAX || '60', 10),
  rateLimitWindowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  meta: {
    pixelId: (process.env.META_PIXEL_ID || '').trim(),
    capiAccessToken: (process.env.META_CAPI_ACCESS_TOKEN || '').trim(),
    apiVersion: (process.env.META_CAPI_API_VERSION || 'v19.0').trim(),
    testEventCode: (process.env.META_CAPI_TEST_EVENT_CODE || '').trim(),
    enabled: process.env.META_CAPI_ENABLED === 'true'
  }
};

if (!config.serverPepper) {
  throw new Error('SERVER_PEPPER is required. Set it in the environment before starting the server.');
}
