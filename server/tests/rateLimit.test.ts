import { describe, it, expect } from 'vitest';
import Database from 'better-sqlite3';

describe('M.3.2-G3 Rate Limit Regression', () => {
  it('returns HTTP 429 on POST /v1/public/orders after configured threshold', async () => {
    const originalNodeEnv = process.env.NODE_ENV;
    const originalPepper = process.env.SERVER_PEPPER;

    process.env.NODE_ENV = 'production';
    process.env.SERVER_PEPPER = 'test_pepper';

    // Force fresh module evaluation with production env so rate-limit plugin is registered
    // @ts-ignore
    const { buildApp } = await import('../src/app.js');
    // @ts-ignore
    const { getDatabase, closeDatabase } = await import('../src/db/database.js');

    const db = getDatabase(':memory:');
    const app = buildApp();
    await app.ready();

    try {
      const threshold = 5;
      for (let i = 0; i < threshold; i++) {
        const res = await app.inject({
          method: 'POST',
          url: '/v1/public/orders',
          payload: {
            customerName: `Rate Test User ${i}`,
            customerContact: '081234567890',
            ownerEmail: 'ratetest@warung.id'
          }
        });
        expect([200, 201]).toContain(res.statusCode);
      }

      const res429 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Rate Test Overflow',
          customerContact: '081234567891',
          ownerEmail: 'ratetest2@warung.id'
        }
      });
      expect(res429.statusCode).toBe(429);
      const body = JSON.parse(res429.body);
      expect(body.error.code).toBe('RATE_LIMIT_EXCEEDED');
    } finally {
      await app.close();
      closeDatabase();
      process.env.NODE_ENV = originalNodeEnv;
      process.env.SERVER_PEPPER = originalPepper;
    }
  });
});
