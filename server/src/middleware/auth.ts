import { FastifyRequest, FastifyReply } from 'fastify';
import { config } from '../config/index.js';
import { timingSafeCompare } from '../utils/crypto.js';

export async function adminAuthMiddleware(request: FastifyRequest, reply: FastifyReply) {
  const authHeader = request.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return reply.status(401).send({
      success: false,
      error: {
        code: 'UNAUTHORIZED',
        message: 'Admin authorization token required in Bearer format.'
      }
    });
  }

  const token = authHeader.substring(7).trim();
  const expectedKey = config.adminApiKey;

  if (!timingSafeCompare(token, expectedKey)) {
    return reply.status(403).send({
      success: false,
      error: {
        code: 'FORBIDDEN',
        message: 'Invalid admin authorization token.'
      }
    });
  }
}
