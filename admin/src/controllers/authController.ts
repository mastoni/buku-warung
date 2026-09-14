import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import crypto from 'crypto';
import { config } from '../config/index.js';
import { createSessionToken, destroySessionToken, verifySessionToken } from '../middleware/sessionAuth.js';

function timingSafeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

export async function registerAuthRoutes(fastify: FastifyInstance) {
  // POST /api/auth/login
  fastify.post(
    '/api/auth/login',
    {
      schema: {
        body: {
          type: 'object',
          required: ['username', 'password'],
          properties: {
            username: { type: 'string' },
            password: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: { username: string; password: string } }>, reply: FastifyReply) => {
      const { username, password } = request.body;

      const isUserValid = timingSafeEqual(username, config.adminUsername);
      const isPassValid = timingSafeEqual(password, config.adminPassword);

      if (!isUserValid || !isPassValid) {
        return reply.status(401).send({
          success: false,
          error: {
            code: 'INVALID_CREDENTIALS',
            message: 'Username atau password admin salah.'
          }
        });
      }

      const token = createSessionToken(username);

      reply.setCookie('bw_admin_session', token, {
        path: '/',
        httpOnly: true,
        sameSite: 'strict',
        secure: config.nodeEnv === 'production',
        maxAge: 24 * 60 * 60 // 24 hours
      });

      return reply.status(200).send({
        success: true,
        message: 'Login berhasil.',
        data: {
          username
        }
      });
    }
  );

  // POST /api/auth/logout
  fastify.post('/api/auth/logout', async (request: FastifyRequest, reply: FastifyReply) => {
    const cookie = request.cookies['bw_admin_session'];
    if (cookie) {
      destroySessionToken(cookie);
    }

    reply.clearCookie('bw_admin_session', {
      path: '/',
      httpOnly: true,
      sameSite: 'strict',
      secure: config.nodeEnv === 'production'
    });

    return reply.status(200).send({
      success: true,
      message: 'Logout berhasil.'
    });
  });

  // GET /api/auth/me
  fastify.get('/api/auth/me', async (request: FastifyRequest, reply: FastifyReply) => {
    const cookie = request.cookies['bw_admin_session'];
    if (!cookie || !verifySessionToken(cookie)) {
      return reply.status(401).send({
        success: false,
        error: { code: 'UNAUTHORIZED', message: 'Not authenticated' }
      });
    }

    return reply.status(200).send({
      success: true,
      data: {
        username: config.adminUsername,
        authenticated: true
      }
    });
  });
}
