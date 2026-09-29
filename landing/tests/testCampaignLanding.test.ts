import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { TEST_CAMPAIGN_CONFIG, LANDING_CONFIG } from '../src/data/landingData';
import { TEST_CAMPAIGN_UTM } from '../src/tracking';

const read = (relative: string) => fs.readFileSync(path.resolve(process.cwd(), relative), 'utf8');

describe('Landing closed-testing campaign', () => {
  /* =========================================================================
   * 8. FULL state is reflected by the landing UI (from server state only)
   * ========================================================================= */
  describe('FULL state rendering', () => {
    const section = read('src/components/TestProgramSection.tsx');

    it('renders the required FULL-state copy', () => {
      expect(section).toContain('TEST_CAMPAIGN_CONFIG.fullTitle');
      expect(section).toContain('TEST_CAMPAIGN_CONFIG.fullBody');
      expect(TEST_CAMPAIGN_CONFIG.fullTitle).toBe('Batch Test 1 Sudah Penuh');
      expect(TEST_CAMPAIGN_CONFIG.fullBody).toBe('Kuota 50 usaha untuk batch testing ini telah terpenuhi.');
    });

    it('offers the waiting-list consultation CTA when full', () => {
      expect(section).toContain('TEST_CAMPAIGN_CONFIG.waitingListCta');
      expect(section).toContain('LANDING_CONFIG.whatsappConsultationUrl');
      expect(TEST_CAMPAIGN_CONFIG.waitingListCta).toBe('DAFTAR WAITING LIST');
    });

    it('gates the full state on the server status, not on any local counter', () => {
      const hook = read('src/hooks/useTestCampaign.ts');
      // FULL is decided only by the status the server returned.
      expect(hook).toContain("export type TestCampaignStatus = 'OPEN' | 'FULL'");
      expect(hook).toContain("next.status === 'FULL'");
      expect(hook).toContain("isFull: state.status === 'FULL'");
      // No capacity number may ever be persisted. The only stored value is a static boolean
      // flag meaning "this device registered before", which is a UX hint and never a count.
      expect(hook).not.toMatch(/localStorage\.setItem\([^)]*remaining/);
      expect(hook).not.toMatch(/localStorage\.setItem\([^)]*capacity/);
      const persisted = hook.match(/localStorage\.setItem\(([^)]*)\)/g) ?? [];
      for (const call of persisted) {
        expect(call).toMatch(/'lw_test_registered',\s*'1'/);
      }
    });

    it('keeps the OPEN-state availability copy driven by server values', () => {
      expect(section).toContain('{state.registered}');
      expect(section).toContain('{state.capacity}');
      expect(section).toContain('{state.remaining}');
      // No fabricated scarcity, countdown or testimonials anywhere in the section.
      expect(section).not.toMatch(/countdown|hitung mundur|segera berakhir|terbatas!|testimoni/i);
    });
  });

  /* =========================================================================
   * 9. BUY DIRECT still resolves to the existing Public Order
   * ========================================================================= */
  describe('BUY DIRECT path', () => {
    it('keeps the existing public order URL as the buy target', () => {
      expect(LANDING_CONFIG.publicOrderUrl).toContain('https://license.skmnetwork.com/beli/buku-warung');
      expect(LANDING_CONFIG.publicOrderUrl).toContain('utm_campaign=launch_v020');
    });

    it('renders the buy CTA through the section and the hero', () => {
      const section = read('src/components/TestProgramSection.tsx');
      const hero = read('src/components/Hero.tsx');
      expect(section).toContain('getOrderUrl(LANDING_CONFIG.publicOrderUrl)');
      expect(hero).toContain('getOrderUrl(LANDING_CONFIG.publicOrderUrl)');
    });

    it('does not route purchase through WhatsApp', () => {
      const section = read('src/components/TestProgramSection.tsx');
      // The buy anchor must be the order URL, never the wa.me consultation link.
      expect(section).not.toMatch(/href=\{LANDING_CONFIG\.whatsappConsultationUrl\}[\s\S]{0,200}secondaryCta/);
    });

    it('keeps the WhatsApp support CTA available', () => {
      const section = read('src/components/TestProgramSection.tsx');
      const hero = read('src/components/Hero.tsx');
      expect(section).toContain('trackWhatsAppClick');
      expect(hero).toContain('trackWhatsAppClick');
    });
  });

  /* =========================================================================
   * Campaign attribution
   * ========================================================================= */
  describe('attribution', () => {
    it('uses a dedicated campaign value without disturbing launch_v020', () => {
      expect(TEST_CAMPAIGN_UTM.utm_campaign).toBe('bukuwarung_test_batch_1');
      expect(LANDING_CONFIG.publicOrderUrl).toContain('utm_campaign=launch_v020');
      expect(LANDING_CONFIG.publicOrderStickyUrl).toContain('utm_campaign=launch_v020');
      expect(LANDING_CONFIG.publicOrderPricingUrl).toContain('utm_campaign=launch_v020');
    });

    it('emits only events that exist in the server allowlist', () => {
      const tracking = read('src/tracking.ts');
      const serverAllowlist = read('../server/src/utils/attribution.ts');
      for (const event of [
        'TEST_PROGRAM_VIEW',
        'TEST_REGISTRATION_STARTED',
        'TEST_REGISTRATION_SUBMITTED',
        'TEST_PROGRAM_FULL'
      ]) {
        expect(tracking).toContain(event);
        expect(serverAllowlist).toContain(`'${event}'`);
      }
    });
  });

  /* =========================================================================
   * No PII in analytics
   * ========================================================================= */
  it('never posts registration PII to the tracking endpoint', () => {
    const tracking = read('src/tracking.ts');
    for (const piiField of ['name', 'whatsapp', 'googlePlayEmail', 'businessType', 'consent']) {
      expect(tracking).not.toMatch(new RegExp(`sendTrackEvent\\([^)]*${piiField}`));
    }
  });
});
