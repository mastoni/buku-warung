import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import {
  LicenseClient,
  CreateLicensePayload,
  RebindPayload,
  CreateOrderPayload,
  VerifyPaymentPayload,
  MarkDeliveredPayload
} from '../services/licenseClient.js';
import { sessionAuthMiddleware } from '../middleware/sessionAuth.js';

export async function registerLicenseProxyRoutes(fastify: FastifyInstance) {
  const licenseClient = new LicenseClient();

  // Protect all /api/licenses*, /api/devices*, /api/license/rebind, /api/audit-logs, /api/orders* routes
  fastify.addHook('preHandler', async (request, reply) => {
    if (
      request.url.startsWith('/api/licenses') ||
      request.url.startsWith('/api/devices') ||
      request.url.startsWith('/api/license/rebind') ||
      request.url.startsWith('/api/audit-logs') ||
      request.url.startsWith('/api/orders')
    ) {
      await sessionAuthMiddleware(request, reply);
    }
  });

  // POST /api/licenses
  fastify.post(
    '/api/licenses',
    async (request: FastifyRequest<{ Body: CreateLicensePayload }>, reply: FastifyReply) => {
      const res = await licenseClient.createLicense(request.body);
      return reply.status(res.status).send(res.data);
    }
  );

  // GET /api/licenses
  fastify.get('/api/licenses', async (_request: FastifyRequest, reply: FastifyReply) => {
    const res = await licenseClient.listLicenses();
    return reply.status(res.status).send(res.data);
  });

  // GET /api/licenses/:id
  fastify.get(
    '/api/licenses/:id',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'License ID must be a number.' } });
      }

      const res = await licenseClient.getLicenseDetail(id);
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/licenses/:id/revoke
  fastify.post(
    '/api/licenses/:id/revoke',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: { reason?: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'License ID must be a number.' } });
      }

      const reason = request.body?.reason || 'Revoked via Admin Dashboard';
      const res = await licenseClient.revokeLicense(id, reason);
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/devices/:id/revoke
  fastify.post(
    '/api/devices/:id/revoke',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: { reason?: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Device ID must be a number.' } });
      }

      const reason = request.body?.reason || 'Device revoked via Admin Dashboard';
      const res = await licenseClient.revokeDevice(id, reason);
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/license/rebind
  fastify.post(
    '/api/license/rebind',
    async (request: FastifyRequest<{ Body: RebindPayload }>, reply: FastifyReply) => {
      const res = await licenseClient.rebindDevice(request.body);
      return reply.status(res.status).send(res.data);
    }
  );

  // GET /api/audit-logs
  fastify.get(
    '/api/audit-logs',
    async (request: FastifyRequest<{ Querystring: { licenseId?: string } }>, reply: FastifyReply) => {
      const licenseId = request.query.licenseId ? parseInt(request.query.licenseId, 10) : undefined;
      const res = await licenseClient.getAuditLogs(licenseId);
      return reply.status(res.status).send(res.data);
    }
  );

  /* =========================================================================
   * SALES & ORDER PROXY ROUTES (C.10.1)
   * ========================================================================= */

  // POST /api/orders
  fastify.post(
    '/api/orders',
    async (request: FastifyRequest<{ Body: CreateOrderPayload }>, reply: FastifyReply) => {
      const res = await licenseClient.createOrder(request.body);
      return reply.status(res.status).send(res.data);
    }
  );

  // GET /api/orders
  fastify.get(
    '/api/orders',
    async (request: FastifyRequest<{ Querystring: { filter?: string; search?: string } }>, reply: FastifyReply) => {
      const { filter, search } = request.query;
      const res = await licenseClient.listOrders(filter, search);
      return reply.status(res.status).send(res.data);
    }
  );

  // GET /api/orders/:id
  fastify.get(
    '/api/orders/:id',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const res = await licenseClient.getOrderDetail(id);
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/orders/:id/verify-payment
  fastify.post(
    '/api/orders/:id/verify-payment',
    async (request: FastifyRequest<{ Params: { id: string }; Body: VerifyPaymentPayload }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const res = await licenseClient.verifyOrderPayment(id, request.body || {});
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/orders/:id/generate-license
  fastify.post(
    '/api/orders/:id/generate-license',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const res = await licenseClient.generateLicenseForOrder(id);
      return reply.status(res.status).send(res.data);
    }
  );

  // POST /api/orders/:id/mark-delivered
  fastify.post(
    '/api/orders/:id/mark-delivered',
    async (request: FastifyRequest<{ Params: { id: string }; Body: MarkDeliveredPayload }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const res = await licenseClient.markOrderDelivered(id, request.body || {});
      return reply.status(res.status).send(res.data);
    }
  );
}
