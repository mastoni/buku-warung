import { FastifyRequest, FastifyReply } from 'fastify';
import crypto from 'crypto';
import { config } from '../config/index.js';

// In-memory active session store (for MVP)
const activeSessions = new Set<string>();

export function createSessionToken(username: string): string {
  const sessionId = crypto.randomUUID();
  const timestamp = Date.now();
  const payload = `${username}:${sessionId}:${timestamp}`;
  const signature = crypto.createHmac('sha256', config.adminSessionSecret).update(payload).digest('hex');
  const token = `${Buffer.from(payload).toString('base64url')}.${signature}`;
  activeSessions.add(token);
  return token;
}

export function verifySessionToken(token: string): boolean {
  if (!token || !activeSessions.has(token)) {
    return false;
  }

  const parts = token.split('.');
  if (parts.length !== 2) {
    return false;
  }

  const [encodedPayload, signature] = parts;
  try {
    const payload = Buffer.from(encodedPayload, 'base64url').toString('utf8');
    const expectedSignature = crypto.createHmac('sha256', config.adminSessionSecret).update(payload).digest('hex');
    
    const bufA = Buffer.from(signature);
    const bufB = Buffer.from(expectedSignature);
    if (bufA.length !== bufB.length || !crypto.timingSafeEqual(bufA, bufB)) {
      return false;
    }

    const [, , timestampStr] = payload.split(':');
    const timestamp = parseInt(timestampStr, 10);
    // 24-hour expiry
    if (Date.now() - timestamp > 24 * 60 * 60 * 1000) {
      activeSessions.delete(token);
      return false;
    }

    return true;
  } catch {
    return false;
  }
}

export function destroySessionToken(token: string): void {
  if (token) {
    activeSessions.delete(token);
  }
}

export async function sessionAuthMiddleware(request: FastifyRequest, reply: FastifyReply) {
  const cookie = request.cookies['bw_admin_session'];

  if (!cookie || !verifySessionToken(cookie)) {
    return reply.status(401).send({
      success: false,
      error: {
        code: 'UNAUTHORIZED',
        message: 'Admin session is invalid or expired. Please login.'
      }
    });
  }
}
