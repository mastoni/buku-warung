import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fs from 'fs';
import path from 'path';

/**
 * Serves the admin console shell for Tester Management.
 *
 * The page itself contains NO tester data - it is a static shell whose every value is fetched at
 * runtime through the authenticated /v1/admin/* API using a Bearer token typed into the login
 * box. That is why this route is intentionally not behind adminAuthMiddleware: a browser cannot
 * attach an Authorization header to a top-level navigation, and gating the HTML while the data
 * fetches are separately gated would give no real protection.
 *
 * The same URL must never be served publicly by the reverse proxy.
 */
export async function registerAdminConsoleRoutes(fastify: FastifyInstance) {
  const pagePath = path.resolve(__dirname, '../../public/admin/testers.html');

  fastify.get('/admin/testers', async (_request: FastifyRequest, reply: FastifyReply) => {
    try {
      const html = fs.readFileSync(pagePath, 'utf8');
      return reply.status(200).type('text/html; charset=utf-8').send(html);
    } catch {
      return reply.status(404).send({ success: false, error: { code: 'NOT_FOUND', message: 'Admin console unavailable.' } });
    }
  });
}
