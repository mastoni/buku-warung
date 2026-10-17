import { config } from '../src/config/index.js';
import { MetaCapiService } from '../src/services/metaCapiService.js';

/**
 * Dual Verification Script for Meta PageView Deduplication
 * 
 * Simulates a single complete visit to Buku Warung landing page:
 * 1. Generates one shared unique event_id (pv_<timestamp>_<rand>)
 * 2. Emits Browser Pixel PageView event to https://www.facebook.com/tr/ with:
 *    - id: 2559501331198045
 *    - ev: PageView
 *    - eid: <event_id>
 *    - test_event_code: TEST2489
 * 3. Emits Server CAPI PageView event to https://graph.facebook.com/v19.0/2559501331198045/events with:
 *    - event_name: PageView
 *    - event_id: <same event_id>
 *    - test_event_code: TEST2489
 * 4. Displays full verification results, including status, headers, and deduplication tokens.
 */
async function main() {
  const pixelId = config.meta.pixelId || '2559501331198045';
  const accessToken = config.meta.capiAccessToken;
  const testEventCode = process.argv[2] || 'TEST2489';

  if (!pixelId || !accessToken) {
    console.error('Missing META_PIXEL_ID or META_CAPI_ACCESS_TOKEN');
    process.exit(1);
  }

  // Generate identical event_id for this single landing page visit
  const now = Date.now();
  const rand = Math.random().toString(36).substring(2, 8);
  const sharedEventId = `pv_HOTFIX_${now}_${rand}`;
  const clientIp = '103.168.147.243';
  const userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
  const sourceUrl = 'https://bukuwarung.skmnetwork.com/';

  console.log('================================================================');
  console.log('META CAPI + PIXEL DUAL PAGEVIEW VERIFICATION');
  console.log('================================================================');
  console.log(`Dataset ID        : ${pixelId}`);
  console.log(`Test Event Code   : ${testEventCode}`);
  console.log(`Shared Event ID   : ${sharedEventId}`);
  console.log(`Event Name        : PageView`);
  console.log(`Action Source     : website (${sourceUrl})`);
  console.log('----------------------------------------------------------------\n');

  // Step A: Dispatch Browser Pixel Event (simulating browser execution)
  console.log('[STEP 1/2] Emitting Browser Pixel PageView event...');
  const pixelParams = new URLSearchParams({
    id: pixelId,
    ev: 'PageView',
    dl: sourceUrl,
    rl: '',
    if: 'false',
    ts: String(now),
    eid: sharedEventId,
    test_event_code: testEventCode,
    v: '2.0',
    r: 'stable'
  });

  const pixelUrl = `https://www.facebook.com/tr/?${pixelParams.toString()}`;
  let pixelResult: { status: number; ok: boolean; contentType: string } | null = null;
  try {
    const pixelRes = await fetch(pixelUrl, {
      method: 'GET',
      headers: {
        'User-Agent': userAgent,
        'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
        'Referer': sourceUrl
      }
    });
    pixelResult = {
      status: pixelRes.status,
      ok: pixelRes.ok,
      contentType: pixelRes.headers.get('content-type') || 'unknown'
    };
    console.log(`[PIXEL-BROWSER] HTTP ${pixelRes.status} ${pixelRes.ok ? 'OK' : 'FAIL'} (Content-Type: ${pixelResult.contentType})`);
  } catch (err) {
    console.error('[PIXEL-BROWSER] Network error dispatching browser pixel event:', err);
  }

  // Step B: Dispatch Server CAPI Event (simulating backend execution)
  console.log('\n[STEP 2/2] Emitting Server CAPI PageView event...');
  const capiService = new MetaCapiService({
    pixelId,
    accessToken,
    apiVersion: config.meta.apiVersion || 'v19.0',
    testEventCode,
    enabled: true
  });

  const capiResult = await capiService.sendPageView(sharedEventId, {
    ip: clientIp,
    userAgent,
    eventSourceUrl: sourceUrl
  });

  console.log(`[CAPI-SERVER] HTTP ${capiResult.httpStatus} ${capiResult.success ? 'OK' : 'FAIL'}`);
  console.log(`[CAPI-SERVER] Events Received: ${capiResult.eventsReceived}`);
  console.log(`[CAPI-SERVER] Trace ID: ${capiResult.fbtraceId}`);
  if (capiResult.messages && capiResult.messages.length > 0) {
    console.log(`[CAPI-SERVER] Messages:`, JSON.stringify(capiResult.messages));
  } else {
    console.log(`[CAPI-SERVER] Messages: [] (Clean session match)`);
  }

  console.log('\n================================================================');
  console.log('DUAL DISPATCH SUMMARY & DEDUPLICATION AUDIT');
  console.log('================================================================');
  console.log(`Browser Pixel Event : ${pixelResult?.ok ? 'DISPATCHED' : 'FAILED'} (HTTP ${pixelResult?.status})`);
  console.log(`Server CAPI Event   : ${capiResult.success ? 'DISPATCHED' : 'FAILED'} (HTTP ${capiResult.httpStatus})`);
  console.log(`Matching Event ID   : ${sharedEventId}`);
  console.log(`Matching Event Name : PageView`);
  console.log(`Events Received     : ${capiResult.eventsReceived}`);
  console.log(`Meta FBTrace ID     : ${capiResult.fbtraceId}`);
  console.log('================================================================');
}

main().catch(err => {
  console.error('Fatal verification error:', err);
  process.exit(1);
});
