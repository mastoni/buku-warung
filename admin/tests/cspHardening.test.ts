import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildAdminApp } from '../src/app.js';

describe('CSP Hardening (C.14.6.1)', () => {
  let adminApp: ReturnType<typeof buildAdminApp>;

  beforeAll(async () => {
    adminApp = buildAdminApp();
    await adminApp.ready();
  });

  afterAll(async () => {
    await adminApp.close();
  });

  it('CSP script-src must NOT contain unsafe-inline', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/',
    });

    const csp = res.headers['content-security-policy'] || '';
    const scriptSrcMatch = csp.match(/script-src\s+([^;]+)/);
    expect(scriptSrcMatch, 'script-src directive not found in CSP').toBeTruthy();

    const scriptSrcValue = scriptSrcMatch![1];
    expect(scriptSrcValue).not.toContain('unsafe-inline');
    expect(scriptSrcValue).not.toContain('unsafe-eval');
  });

  it('CSP script-src must contain self', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/',
    });

    const csp = res.headers['content-security-policy'] || '';
    const scriptSrcMatch = csp.match(/script-src\s+([^;]+)/);
    expect(scriptSrcMatch, 'script-src directive not found in CSP').toBeTruthy();

    const scriptSrcValue = scriptSrcMatch![1];
    expect(scriptSrcValue).toContain("'self'");
  });

  it('CSP script-src-attr must be none', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/',
    });

    const csp = res.headers['content-security-policy'] || '';
    expect(csp).toContain("script-src-attr 'none'");
  });

  it('CSP style-src may contain unsafe-inline (out of scope for this gate)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/',
    });

    const csp = res.headers['content-security-policy'] || '';
    const styleSrcMatch = csp.match(/style-src\s+([^;]+)/);
    expect(styleSrcMatch, 'style-src directive not found in CSP').toBeTruthy();
    const styleSrcValue = styleSrcMatch![1];
    expect(styleSrcValue).toContain("'unsafe-inline'");
  });

  it('CSP must NOT contain unsafe-eval anywhere', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/',
    });

    const csp = res.headers['content-security-policy'] || '';
    expect(csp).not.toContain('unsafe-eval');
  });

  it('Static HTML has ZERO inline event handlers', () => {
    const fs = require('fs');
    const path = require('path');
    const publicDir = path.resolve(__dirname, '../../admin/public');
    const files = fs.readdirSync(publicDir);

    const inlineHandlers = [
      'onclick', 'onerror', 'onload', 'onmouseover', 'onmousedown',
      'onmouseup', 'onchange', 'onsubmit', 'onkeydown', 'onkeyup',
    ];

    for (const file of files) {
      const fullPath = path.join(publicDir, file);
      if (fs.statSync(fullPath).isFile()) {
        const content = fs.readFileSync(fullPath, 'utf8');
        for (const handler of inlineHandlers) {
          const regex = new RegExp(`\\b${handler}\\s*=`, 'i');
          expect(content, `${file} contains ${handler} inline handler`).not.toMatch(regex);
        }
        expect(content, `${file} contains javascript: URI`).not.toContain('javascript:');
        expect(content, `${file} contains eval(`).not.toContain('eval(');
        expect(content, `${file} contains new Function`).not.toContain('new Function');
      }
    }
  });
});
