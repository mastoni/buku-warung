import Fastify, { FastifyInstance } from 'fastify';
import path from 'path';
import { fileURLToPath } from 'url';
import fastifyCookie from '@fastify/cookie';
import fastifyHelmet from '@fastify/helmet';
import fastifyCors from '@fastify/cors';
import fastifyRateLimit from '@fastify/rate-limit';
import fastifyStatic from '@fastify/static';
import { config } from './config/index.js';
import { registerAuthRoutes } from './controllers/authController.js';
import { registerDashboardRoutes } from './controllers/dashboardController.js';
import { registerLicenseProxyRoutes } from './controllers/licenseProxyController.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export function buildAdminApp(): FastifyInstance {
  const app = Fastify({
    logger: config.nodeEnv !== 'test' ? { level: 'info' } : false
  });

  // Security Plugins
  app.register(fastifyHelmet, {
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'", 'https://fonts.googleapis.com'],
        fontSrc: ["'self'", 'https://fonts.gstatic.com'],
        scriptSrc: ["'self'", "'unsafe-inline'"],
        imgSrc: ["'self'", 'data:']
      }
    }
  });

  app.register(fastifyCors, {
    origin: true,
    credentials: true
  });

  app.register(fastifyCookie, {
    secret: config.adminSessionSecret
  });

  if (config.nodeEnv !== 'test') {
    app.register(fastifyRateLimit, {
      max: 100,
      timeWindow: 60000
    });
  }

  // Static Assets
  const publicPath = path.resolve(__dirname, '../../public');
  app.register(fastifyStatic, {
    root: publicPath,
    prefix: '/'
  });

  // Health Check
  app.get('/health', async (_req, reply) => {
    return reply.status(200).send({
      status: 'ok',
      service: 'bukuwarung-license-admin',
      version: '1.0.0',
      timestamp: Date.now()
    });
  });

  // API Routes
  app.register(registerAuthRoutes);
  app.register(registerDashboardRoutes);
  app.register(registerLicenseProxyRoutes);

  // Fallback to index.html for SPA
  app.setNotFoundHandler((_req, reply) => {
    return reply.sendFile('index.html');
  });

  return app;
}
