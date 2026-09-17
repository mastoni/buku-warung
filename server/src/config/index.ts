import dotenv from 'dotenv';
import path from 'path';

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
};

if (!config.serverPepper) {
  throw new Error('SERVER_PEPPER is required. Set it in the environment before starting the server.');
}
