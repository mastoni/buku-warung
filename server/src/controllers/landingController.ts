import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { AttributionService } from '../services/attributionService.js';
import { LandingTrackRequest } from '../types/index.js';
import { validateUtm, validateFunnelEvent, ALLOWED_UTM_PARAMS } from '../utils/attribution.js';
import { adminAuthMiddleware } from '../middleware/auth.js';

export async function registerLandingRoutes(fastify: FastifyInstance) {
  const attributionService = new AttributionService();

  const ALLOWED_FIELDS = new Set(['eventType', 'leadToken', ...ALLOWED_UTM_PARAMS]);

  const LANDING_TRACK_SCHEMA = {
    type: 'object',
    properties: {
      eventType: { type: 'string', minLength: 1, maxLength: 50 },
      leadToken: { type: 'string', pattern: '^LW-[2-9A-HJ-NP-Z]{6}$' },
      utm_source: { type: 'string', maxLength: 100 },
      utm_medium: { type: 'string', maxLength: 100 },
      utm_campaign: { type: 'string', maxLength: 100 },
      utm_content: { type: 'string', maxLength: 100 }
    },
    required: ['eventType']
  };

  fastify.post(
    '/v1/landing/track',
    {
      schema: {
        body: LANDING_TRACK_SCHEMA
      }
    },
    async (request: FastifyRequest<{ Body: LandingTrackRequest }>, reply: FastifyReply) => {
      const body = request.body as unknown as Record<string, unknown>;

      const bodyKeys = Object.keys(body || {});
      for (const key of bodyKeys) {
        if (!ALLOWED_FIELDS.has(key)) {
          return reply.status(400).send({
            success: false,
            error: {
              code: 'UNEXPECTED_FIELD',
              message: `Unexpected field: ${key}.`
            }
          });
        }
      }

      const eventType = body.eventType as string;
      if (!validateFunnelEvent(eventType)) {
        return reply.status(400).send({
          success: false,
          error: {
            code: 'INVALID_EVENT_TYPE',
            message: 'Invalid or unknown event type.'
          }
        });
      }

      for (const param of ALLOWED_UTM_PARAMS) {
        const value = body[param];
        if (value !== undefined && value !== null && value !== '' && !validateUtm(String(value))) {
          return reply.status(400).send({
            success: false,
            error: {
              code: 'INVALID_UTM_VALUE',
              message: `Invalid value for ${param}.`
            }
          });
        }
      }

      const ipAddress = request.ip || (request.headers['x-forwarded-for'] as string | undefined)?.split(',')[0]?.trim();
      const userAgent = request.headers['user-agent'] as string | undefined;

      try {
        const result = attributionService.processLandingTrack(
          {
            eventType,
            leadToken: body.leadToken as string | undefined,
            utm_source: body.utm_source as string | undefined,
            utm_medium: body.utm_medium as string | undefined,
            utm_campaign: body.utm_campaign as string | undefined,
            utm_content: body.utm_content as string | undefined
          },
          ipAddress,
          userAgent
        );

        if (!result.success) {
          return reply.status(400).send({
            success: false,
            error: {
              code: 'TRACKING_FAILED',
              message: 'Failed to record landing track event.'
            }
          });
        }

        return reply.status(200).send(result);
      } catch {
        return reply.status(500).send({
          success: false,
          error: {
            code: 'INTERNAL_ERROR',
            message: 'An unexpected error occurred during tracking.'
          }
        });
      }
    }
  );

  fastify.get(
    '/v1/landing/metrics',
    {
      schema: {
        querystring: {
          type: 'object',
          properties: {
            range: { type: 'string', enum: ['today', '7d', '30d', 'all'] },
            start: { type: 'string' },
            end: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Querystring: { range?: string; start?: string; end?: string } }>, reply: FastifyReply) => {
      await adminAuthMiddleware(request, reply);
      if (reply.sent) return;

      const now = Date.now();
      const msPerDay = 24 * 60 * 60 * 1000;
      let dateStart: number | undefined;
      let dateEnd: number | undefined;

      if (request.query.range) {
        switch (request.query.range) {
          case 'today': {
            const today = new Date();
            dateStart = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
            dateEnd = dateStart + msPerDay - 1;
            break;
          }
          case '7d':
            dateStart = now - 7 * msPerDay;
            dateEnd = now;
            break;
          case '30d':
            dateStart = now - 30 * msPerDay;
            dateEnd = now;
            break;
          case 'all':
            dateStart = undefined;
            dateEnd = undefined;
            break;
        }
      }

      if (request.query.start) {
        const parsed = parseInt(request.query.start, 10);
        if (!isNaN(parsed) && parsed > 0) dateStart = parsed;
      }
      if (request.query.end) {
        const parsed = parseInt(request.query.end, 10);
        if (!isNaN(parsed) && parsed > 0) dateEnd = parsed;
      }

      const metrics = attributionService.getAttributionMetrics();
      const funnelAnalytics = attributionService.getFunnelAnalytics(dateStart, dateEnd);

      return reply.status(200).send({
        success: true,
        data: {
          attribution: metrics,
          funnel: funnelAnalytics
        }
      });
    }
  );
}
