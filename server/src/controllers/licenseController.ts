import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { LicenseService } from '../services/licenseService.js';
import { ActivateRequest, ValidateRequest, RecoverRequest } from '../types/index.js';

export async function registerLicenseRoutes(fastify: FastifyInstance) {
  const licenseService = new LicenseService();

  // POST /v1/license/activate
  fastify.post(
    '/v1/license/activate',
    {
      schema: {
        body: {
          type: 'object',
          required: ['licenseCode', 'ownerEmail', 'deviceBinding'],
          properties: {
            licenseCode: { type: 'string', minLength: 5 },
            ownerEmail: { type: 'string', minLength: 3 },
            deviceBinding: { type: 'string', minLength: 1 }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: ActivateRequest }>, reply: FastifyReply) => {
      const result = licenseService.activateLicense(request.body);
      if (!result.success) {
        return reply.status(400).send(result);
      }
      return reply.status(200).send(result);
    }
  );

  // POST /v1/license/validate
  fastify.post(
    '/v1/license/validate',
    {
      schema: {
        body: {
          type: 'object',
          required: ['licenseCode', 'ownerEmail', 'deviceBinding'],
          properties: {
            licenseCode: { type: 'string', minLength: 5 },
            ownerEmail: { type: 'string', minLength: 3 },
            deviceBinding: { type: 'string', minLength: 1 }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: ValidateRequest }>, reply: FastifyReply) => {
      const result = licenseService.validateLicense(request.body);
      return reply.status(200).send({
        success: result.valid,
        status: result.status,
        message: result.message
      });
    }
  );

  // POST /v1/license/recover
  fastify.post(
    '/v1/license/recover',
    {
      schema: {
        body: {
          type: 'object',
          required: ['licenseCode', 'ownerEmail', 'newDeviceBinding'],
          properties: {
            licenseCode: { type: 'string', minLength: 5 },
            ownerEmail: { type: 'string', minLength: 3 },
            newDeviceBinding: { type: 'string', minLength: 1 },
            reason: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: RecoverRequest }>, reply: FastifyReply) => {
      const result = licenseService.requestRecovery(request.body);
      if (!result.success) {
        return reply.status(400).send(result);
      }
      return reply.status(200).send(result);
    }
  );

  // GET /v1/license/status
  fastify.get(
    '/v1/license/status',
    {
      schema: {
        querystring: {
          type: 'object',
          required: ['licenseCode', 'ownerEmail'],
          properties: {
            licenseCode: { type: 'string', minLength: 5 },
            ownerEmail: { type: 'string', minLength: 3 }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Querystring: { licenseCode: string; ownerEmail: string } }>, reply: FastifyReply) => {
      const { licenseCode, ownerEmail } = request.query;
      const result = licenseService.getLicenseStatus(licenseCode, ownerEmail);
      if (!result.success) {
        return reply.status(404).send(result);
      }
      return reply.status(200).send(result);
    }
  );
}
