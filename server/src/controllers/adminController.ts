import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { AdminService } from '../services/adminService.js';
import { PricingService } from '../services/pricingService.js';
import { adminAuthMiddleware } from '../middleware/auth.js';
import {
  AdminCreateLicenseRequest,
  AdminRebindRequest,
  CreateOrderRequest,
  VerifyPaymentRequest,
  MarkDeliveredRequest,
  UpdatePromotionRequest
} from '../types/index.js';

export async function registerAdminRoutes(fastify: FastifyInstance) {
  const adminService = new AdminService();
  const pricingService = new PricingService();

  // Protect all /v1/admin/* routes with adminAuthMiddleware
  fastify.addHook('preHandler', async (request, reply) => {
    if (request.url.startsWith('/v1/admin')) {
      await adminAuthMiddleware(request, reply);
    }
  });

  // POST /v1/admin/licenses
  fastify.post(
    '/v1/admin/licenses',
    {
      schema: {
        body: {
          type: 'object',
          required: ['ownerEmail'],
          properties: {
            ownerEmail: { type: 'string', minLength: 3 },
            product: { type: 'string' },
            price: { type: 'number' },
            customerName: { type: 'string' },
            customerContact: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: AdminCreateLicenseRequest }>, reply: FastifyReply) => {
      const result = adminService.createLicense(request.body, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }
      return reply.status(201).send(result);
    }
  );

  // GET /v1/admin/licenses
  fastify.get('/v1/admin/licenses', async (_request: FastifyRequest, reply: FastifyReply) => {
    const licenses = adminService.listLicenses();
    return reply.status(200).send({
      success: true,
      data: licenses
    });
  });

  // GET /v1/admin/licenses/:id
  fastify.get(
    '/v1/admin/licenses/:id',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'License ID must be a number.' } });
      }

      const detail = adminService.getLicenseDetail(id);
      if (!detail.license) {
        return reply.status(404).send({ success: false, error: { code: 'NOT_FOUND', message: 'License not found.' } });
      }

      return reply.status(200).send({
        success: true,
        data: detail
      });
    }
  );

  // POST /v1/admin/licenses/:id/revoke
  fastify.post(
    '/v1/admin/licenses/:id/revoke',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: { reason?: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'License ID must be a number.' } });
      }

      const reason = request.body?.reason || 'Revoked via Admin API';
      const result = adminService.revokeLicense(id, 'ADMIN_API', reason);
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        message: `License #${id} revoked successfully.`
      });
    }
  );

  // POST /v1/admin/devices/:id/revoke
  fastify.post(
    '/v1/admin/devices/:id/revoke',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: { reason?: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Device ID must be a number.' } });
      }

      const reason = request.body?.reason || 'Device binding revoked via Admin API';
      const result = adminService.revokeDevice(id, 'ADMIN_API', reason);
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        message: `Device #${id} revoked successfully.`
      });
    }
  );

  // POST /v1/admin/license/rebind
  fastify.post(
    '/v1/admin/license/rebind',
    {
      schema: {
        body: {
          type: 'object',
          required: ['licenseId', 'newDeviceBinding'],
          properties: {
            licenseId: { type: 'number' },
            newDeviceBinding: { type: 'string', minLength: 1 },
            reason: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: AdminRebindRequest }>, reply: FastifyReply) => {
      const { licenseId, newDeviceBinding, reason } = request.body;
      const result = adminService.rebindLicense(licenseId, newDeviceBinding, 'ADMIN_API', reason);
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        message: `License #${licenseId} rebound to new device binding successfully.`
      });
    }
  );

  // GET /v1/admin/audit-logs
  fastify.get(
    '/v1/admin/audit-logs',
    async (request: FastifyRequest<{ Querystring: { licenseId?: string } }>, reply: FastifyReply) => {
      const licenseId = request.query.licenseId ? parseInt(request.query.licenseId, 10) : undefined;
      const logs = adminService.getAuditLogs(licenseId);
      return reply.status(200).send({
        success: true,
        data: logs
      });
    }
  );

  /* =========================================================================
   * SALES & ORDER ENDPOINTS (C.10.1)
   * ========================================================================= */

  function formatOrderResponse(order: any) {
    if (!order) return null;
    return {
      ...order,
      id: order.id,
      orderId: order.id,
      orderNumber: order.order_number,
      orderCode: order.order_number,
      customerName: order.customer_name,
      customerContact: order.customer_contact,
      ownerEmail: order.owner_email,
      product: order.product,
      amount: order.amount,
      status: order.effective_status || order.status,
      paymentStatus: order.payment_status,
      paymentMethod: order.payment_method,
      paymentReference: order.payment_reference,
      licenseId: order.license_id,
       notes: order.notes,
       verifiedAt: order.verified_at,
       verifiedBy: order.verified_by,
       deliveredAt: order.delivered_at,
       deliveredBy: order.delivered_by,
       createdAt: order.created_at,
       updatedAt: order.updated_at,
       leadToken: order.lead_token,
       utmSource: order.utm_source,
       utmMedium: order.utm_medium,
       utmCampaign: order.utm_campaign,
       utmContent: order.utm_content,
       order: order
    };
  }

  // POST /v1/admin/orders
  fastify.post(
    '/v1/admin/orders',
    {
      schema: {
        body: {
          type: 'object',
          required: ['customerName', 'ownerEmail'],
          properties: {
            customerName: { type: 'string', minLength: 1 },
            customerContact: { type: 'string' },
            customerWhatsapp: { type: 'string' },
             ownerEmail: { type: 'string', minLength: 3 },
             product: { type: 'string' },
             amount: { type: 'number' },
             notes: { type: 'string' },
             leadToken: { type: 'string' },
             utm_source: { type: 'string' },
             utm_medium: { type: 'string' },
             utm_campaign: { type: 'string' },
             utm_content: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: CreateOrderRequest & { customerWhatsapp?: string } }>, reply: FastifyReply) => {
      const body = request.body || ({} as any);
      const payload: CreateOrderRequest = {
        customerName: body.customerName,
        customerContact: body.customerContact || body.customerWhatsapp || '',
        ownerEmail: body.ownerEmail,
        product: body.product || 'BUKU_WARUNG',
        amount: body.amount || 50000,
        notes: body.notes,
        leadToken: body.leadToken || undefined,
        utm_source: body.utm_source || undefined,
        utm_medium: body.utm_medium || undefined,
        utm_campaign: body.utm_campaign || undefined,
        utm_content: body.utm_content || undefined
      };

      const result = adminService.createOrder(payload, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(201).send({
        success: true,
        data: formatOrderResponse(result.data)
      });
    }
  );

  // GET /v1/admin/orders
  fastify.get(
    '/v1/admin/orders',
    async (request: FastifyRequest<{ Querystring: { filter?: string; search?: string } }>, reply: FastifyReply) => {
      const { filter, search } = request.query;
      const result = adminService.listOrders(filter, search);
      const orders = result.orders.map((o) => formatOrderResponse(o));
      return reply.status(200).send({
        success: true,
        data: orders,
        metrics: result.metrics
      });
    }
  );

  // GET /v1/admin/orders/:id
  fastify.get(
    '/v1/admin/orders/:id',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const result = adminService.getOrderDetail(id);
      if (!result.order) {
        return reply.status(404).send({ success: false, error: { code: 'NOT_FOUND', message: 'Order not found.' } });
      }

      return reply.status(200).send({
        success: true,
        data: {
          ...result,
          order: formatOrderResponse(result.order)
        }
      });
    }
  );

  // POST /v1/admin/orders/:id/verify-payment
  fastify.post(
    '/v1/admin/orders/:id/verify-payment',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: VerifyPaymentRequest }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const result = adminService.verifyOrderPayment(id, request.body || {}, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        data: formatOrderResponse(result.data)
      });
    }
  );

  // POST /v1/admin/orders/:id/generate-license
  fastify.post(
    '/v1/admin/orders/:id/generate-license',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const result = adminService.generateLicenseForOrder(id, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        data: {
          ...result.data!,
          status: result.data!.order.status,
          licenseId: result.data!.licenseId,
          licenseCode: result.data!.licenseCode,
          licenseUuid: result.data!.licenseUuid,
          ownerEmail: result.data!.ownerEmail,
          order: formatOrderResponse(result.data!.order)
        }
      });
    }
  );

  // POST /v1/admin/orders/:id/mark-delivered
  fastify.post(
    '/v1/admin/orders/:id/mark-delivered',
    async (request: FastifyRequest<{ Params: { id: string }; Body?: MarkDeliveredRequest }>, reply: FastifyReply) => {
      const id = parseInt(request.params.id, 10);
      if (isNaN(id)) {
        return reply.status(400).send({ success: false, error: { code: 'INVALID_ID', message: 'Order ID must be a number.' } });
      }

      const result = adminService.markOrderDelivered(id, request.body || {}, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send({
        success: true,
        data: formatOrderResponse(result.data)
      });
    }
  );

  /* =========================================================================
   * PROMOTION & COMMERCIAL PRICING ENDPOINTS
   * ========================================================================= */

  // GET /v1/admin/promotions
  fastify.get('/v1/admin/promotions', async (_request: FastifyRequest, reply: FastifyReply) => {
    const promotions = pricingService.listPromotions();
    const now = Date.now();
    const resolvedPromotions = promotions.map((p) => pricingService.resolvePrice(p.product, now));
    return reply.status(200).send({
      success: true,
      data: resolvedPromotions,
      raw: promotions
    });
  });

  // GET /v1/admin/promotions/:product
  fastify.get(
    '/v1/admin/promotions/:product',
    async (request: FastifyRequest<{ Params: { product: string } }>, reply: FastifyReply) => {
      const { product } = request.params;
      const promo = pricingService.getPromotion(product);
      if (!promo) {
        return reply.status(404).send({
          success: false,
          error: { code: 'PROMOTION_NOT_FOUND', message: `Promotion for product '${product}' not found.` }
        });
      }

      const resolved = pricingService.resolvePrice(product, Date.now());
      return reply.status(200).send({
        success: true,
        data: resolved,
        raw: promo
      });
    }
  );

  // PUT /v1/admin/promotions/:product
  fastify.put(
    '/v1/admin/promotions/:product',
    {
      schema: {
        body: {
          type: 'object',
          properties: {
            name: { type: 'string', minLength: 1 },
            enabled: { anyOf: [{ type: 'boolean' }, { type: 'integer' }] },
            normalPrice: { type: 'number' },
            promoPrice: { type: 'number' },
            startsAt: { anyOf: [{ type: 'number' }, { type: 'string' }] },
            expiresAt: { anyOf: [{ type: 'number' }, { type: 'string' }] },
            timezone: { type: 'string' },
            showCountdown: { anyOf: [{ type: 'boolean' }, { type: 'integer' }] }
          }
        }
      }
    },
    async (
      request: FastifyRequest<{ Params: { product: string }; Body: UpdatePromotionRequest }>,
      reply: FastifyReply
    ) => {
      const { product } = request.params;
      const result = pricingService.updatePromotion(product, request.body || {}, 'ADMIN_API');
      if (!result.success) {
        return reply.status(400).send(result);
      }

      return reply.status(200).send(result);
    }
  );
}

