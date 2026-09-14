import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';

export async function registerHealthRoutes(fastify: FastifyInstance) {
  fastify.get('/health', async (_request: FastifyRequest, reply: FastifyReply) => {
    return reply.status(200).send({
      status: 'ok',
      service: 'bukuwarung-license-server',
      version: '1.0.0',
      timestamp: Date.now()
    });
  });
}
