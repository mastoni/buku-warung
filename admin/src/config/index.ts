import dotenv from 'dotenv';

dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '3001', 10),
  host: process.env.HOST || '0.0.0.0',
  nodeEnv: process.env.NODE_ENV || 'development',
  licenseServerUrl: (process.env.LICENSE_SERVER_URL || 'http://localhost:3000').replace(/\/$/, ''),
  adminApiKey: process.env.ADMIN_API_KEY || 'default_admin_secret_key_12345',
  adminSessionSecret: process.env.ADMIN_SESSION_SECRET || 'bukuwarung_super_secure_session_secret_cookie_key_32bytes',
  adminUsername: process.env.ADMIN_USERNAME || 'admin',
  adminPassword: process.env.ADMIN_PASSWORD || 'bukuwarung_admin_secret_password',
};
