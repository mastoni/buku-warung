import Fastify, { FastifyInstance } from 'fastify';
import helmet from '@fastify/helmet';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import { config } from './config/index.js';
import { registerHealthRoutes } from './controllers/healthController.js';
import { registerLicenseRoutes } from './controllers/licenseController.js';
import { registerAdminRoutes } from './controllers/adminController.js';
import { registerLandingRoutes } from './controllers/landingController.js';
import { registerDownloadRoutes } from './controllers/downloadController.js';
import { registerPublicOrderRoutes } from './controllers/publicOrderController.js';

export function buildApp(): FastifyInstance {
  const app = Fastify({
    logger: config.nodeEnv !== 'test'
      ? {
          level: 'info',
          redact: ['req.headers.authorization', 'body.licenseCode', 'body.ownerEmail', 'body.customerContact']
        }
      : false,
    bodyLimit: 10240,
    routerOptions: {
      maxParamLength: 500
    }
  });

  // Security Plugins
  app.register(helmet, { global: true });
  app.register(cors, {
    origin: (origin, cb) => {
      if (!origin) return cb(null, true);
      if (config.nodeEnv !== 'production') return cb(null, true);
      const allowedOrigins = [
        'https://skmnetwork.com',
        'https://www.skmnetwork.com',
        'https://license.skmnetwork.com'
      ];
      if (allowedOrigins.includes(origin)) {
        return cb(null, true);
      }
      return cb(new Error('Not allowed by CORS'), false);
    }
  });

  // Rate Limiter
  if (config.nodeEnv !== 'test') {
    app.register(rateLimit, {
      max: config.rateLimitMax,
      timeWindow: config.rateLimitWindowMs
    });
  }

  // Register Routes
  app.register(registerHealthRoutes);
  app.register(registerLicenseRoutes);
  app.register(registerAdminRoutes);
  app.register(registerLandingRoutes);
  app.register(registerDownloadRoutes);
  app.register(registerPublicOrderRoutes);

  // Error Handler
  app.setErrorHandler((error: any, _request, reply) => {
    if (error.validation) {
      return reply.status(400).send({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: error.message
        }
      });
    }

    if (error.statusCode === 429) {
      return reply.status(429).send({
        success: false,
        error: {
          code: 'RATE_LIMIT_EXCEEDED',
          message: 'Too many requests. Please slow down.'
        }
      });
    }

    app.log.error(error);
    return reply.status(500).send({
      success: false,
      error: {
        code: 'INTERNAL_SERVER_ERROR',
        message: 'An unexpected error occurred.'
      }
    });
  });

  return app;
}
