import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import {
  LicenseClient,
  UpdateTesterPayload
} from '../services/licenseClient.js';
import { sessionAuthMiddleware } from '../middleware/sessionAuth.js';

/**
 * Session-BFF proxy for Tester Management ("Test Dulu" closed-testing campaign).
 *
 * Browser  ->  /api/testers*  (Admin App session cookie)
 *          ->  /v1/admin/testers*  (ADMIN_API_KEY injected server-side by LicenseClient)
 *          ->  adminAuthMiddleware in the License Server
 *
 * The ADMIN_API_KEY stays inside this process: the browser authenticates with the Admin App
 * session cookie and never sees, sends, or receives the license-server key.
 */
export async function registerTesterProxyRoutes(fastify: FastifyInstance) {
  const licenseClient = new LicenseClient();

  // Same protection pattern as the existing /api/licenses, /api/orders and /api/promotions
  // routes: everything under /api/testers requires a valid Admin App session.
  fastify.addHook('preHandler', async (request, reply) => {
    if (request.url.startsWith('/api/testers')) {
      await sessionAuthMiddleware(request, reply);
    }
  });

  // GET /api/testers/meta/statuses
  // Registered before /api/testers/:id so "meta" is not parsed as an id.
  fastify.get('/api/testers/meta/statuses', async (_request: FastifyRequest, reply: FastifyReply) => {
    const res = await licenseClient.getTesterStatuses();
    return reply.status(res.status).send(res.data);
  });

  // GET /api/testers/summary
  fastify.get('/api/testers/summary', async (_request: FastifyRequest, reply: FastifyReply) => {
    const res = await licenseClient.getTestersSummary();
    return reply.status(res.status).send(res.data);
  });

  // GET /api/testers?status=&search=
  fastify.get(
    '/api/testers',
    async (request: FastifyRequest<{ Querystring: { status?: string; search?: string } }>, reply: FastifyReply) => {
      const { status, search } = request.query ?? {};
      const res = await licenseClient.listTesters(status, search);
      return reply.status(res.status).send(res.data);
    }
  );

  // GET /api/testers/:id
  fastify.get(
    '/api/testers/:id',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Tester ID must be a number.' } });
      }

      const res = await licenseClient.getTesterDetail(id);
      return reply.status(res.status).send(res.data);
    }
  );

  // PUT /api/testers/:id
  fastify.put(
    '/api/testers/:id',
    async (
      request: FastifyRequest<{ Params: { id: string }; Body: UpdateTesterPayload }>,
      reply: FastifyReply
    ) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Tester ID must be a number.' } });
      }

      const res = await licenseClient.updateTester(id, request.body || {});
      return reply.status(res.status).send(res.data);
    }
  );
}
