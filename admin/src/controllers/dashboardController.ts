import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { LicenseClient } from '../services/licenseClient.js';
import { sessionAuthMiddleware } from '../middleware/sessionAuth.js';

export async function registerDashboardRoutes(fastify: FastifyInstance) {
  const licenseClient = new LicenseClient();

  fastify.get(
    '/api/dashboard/summary',
    { preHandler: [sessionAuthMiddleware] },
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const licensesRes = await licenseClient.listLicenses();
      if (licensesRes.status !== 200 || !licensesRes.data.success) {
        return reply.status(licensesRes.status).send(licensesRes.data);
      }

      const licenses = licensesRes.data.data || [];
      const totalLicenses = licenses.length;
      const activeLicenses = licenses.filter((l: any) => l.status === 'ACTIVE').length;
      const pendingLicenses = licenses.filter((l: any) => l.status === 'PENDING').length;
      const revokedLicenses = licenses.filter((l: any) => l.status === 'REVOKED').length;

      // Count active devices & pending recovery across licenses
      let activeDevices = 0;
      let pendingRecoveryRequests = 0;

      // For summary, fetch details of each license concurrently (or sample if large)
      await Promise.all(
        licenses.map(async (lic: any) => {
          const detailRes = await licenseClient.getLicenseDetail(lic.id);
          if (detailRes.status === 200 && detailRes.data.success) {
            const devices = detailRes.data.data.devices || [];
            const recoveries = detailRes.data.data.recoveryRequests || [];
            activeDevices += devices.filter((d: any) => d.status === 'ACTIVE').length;
            pendingRecoveryRequests += recoveries.filter((r: any) => r.status === 'PENDING').length;
          }
        })
      );

      return reply.status(200).send({
        success: true,
        data: {
          totalLicenses,
          activeLicenses,
          pendingLicenses,
          revokedLicenses,
          activeDevices,
          pendingRecoveryRequests
        }
      });
    }
  );
}
