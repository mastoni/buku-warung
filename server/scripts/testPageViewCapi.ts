import { config } from '../src/config/index.js';
import { MetaCapiService } from '../src/services/metaCapiService.js';

async function main() {
  const pixelId = config.meta.pixelId;
  const accessToken = config.meta.capiAccessToken;
  const testEventCode = process.argv[2] || config.meta.testEventCode;

  if (!pixelId || !accessToken) {
    console.log(JSON.stringify({
      success: false,
      reason: 'MISSING_CREDENTIALS',
      message: 'META_PIXEL_ID or META_CAPI_ACCESS_TOKEN is missing in .env.local'
    }, null, 2));
    process.exit(1);
  }

  // Controlled test instance with enabled: true
  const service = new MetaCapiService({
    pixelId,
    accessToken,
    apiVersion: config.meta.apiVersion || 'v19.0',
    testEventCode: testEventCode || undefined,
    enabled: true
  });

  const testEventId = `pv_HOTFIX_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;

  console.log(`[TEST-CAPI] Dispatching PageView with event_id: ${testEventId}`);
  console.log(`[TEST-CAPI] Dataset ID: ${pixelId}`);
  console.log(`[TEST-CAPI] Test Event Code: ${testEventCode || 'none'}`);

  const result = await service.sendPageView(testEventId, {
    ip: '103.168.147.243',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    eventSourceUrl: 'https://bukuwarung.skmnetwork.com/'
  });

  console.log('\n--- Actual Meta Response ---');
  console.log(JSON.stringify({
    success: result.success,
    sent: result.sent,
    httpStatus: result.httpStatus,
    eventsReceived: result.eventsReceived,
    fbtraceId: result.fbtraceId,
    eventId: result.eventId,
    messages: result.messages,
    error: result.error,
    hasTestCode: Boolean(testEventCode)
  }, null, 2));
}

main().catch(err => {
  console.error('Fatal error in testPageViewCapi:', err);
  process.exit(1);
});
