// Lighthouse user flow against the compose stack (production nginx build), default mobile emulation + throttling.
// Usage: cd scripts/lighthouse && npm install && node flow.mjs <path-to-chrome> <report.html>
// (Chrome from Playwright: ~/AppData/Local/ms-playwright/chromium-*/chrome-win/chrome.exe.) The session token is kept
// in per-tab sessionStorage, so one tab signs in and then navigates (a plain CLI run could not see it).
import { writeFileSync } from 'node:fs';
import { startFlow } from 'lighthouse';
import puppeteer from 'puppeteer-core';

const BASE = 'http://localhost:5173';
const CHROME = process.argv[2];
const OUT = process.argv[3];
const PASSWORD = 'PlaceHub@123';

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
const flow = await startFlow(page, { flags: { disableStorageReset: true } });

async function signIn(portal, email) {
  await page.goto(`${BASE}/login/${portal}`, { waitUntil: 'networkidle0' });
  await page.type('#email', email);
  await page.type('#password', PASSWORD);
  await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0' }), page.click('button[type="submit"]')]);
}

async function signOut() {
  await page.evaluate(() => sessionStorage.clear());
}

await flow.navigate(`${BASE}/`, { name: 'Landing' });
await flow.navigate(`${BASE}/login/student`, { name: 'Login (student)' });
await signIn('student', 'student@placehub.local');
await flow.navigate(`${BASE}/student/jobs`, { name: 'Student jobs' });
await signOut();
await signIn('recruiter', 'recruiter@placehub.local');
await flow.navigate(`${BASE}/recruiter/jobs/20000000-0000-0000-0000-000000000001/applicants`, { name: 'Recruiter applicants' });

const result = await flow.createFlowResult();
const rows = result.steps.map((s) => {
  const c = s.lhr.categories;
  const pct = (k) => Math.round((c[k]?.score ?? 0) * 100);
  const cls = s.lhr.audits['cumulative-layout-shift']?.numericValue;
  const lcp = s.lhr.audits['largest-contentful-paint']?.numericValue;
  return { page: s.name, performance: pct('performance'), accessibility: pct('accessibility'),
    bestPractices: pct('best-practices'), seo: pct('seo'), cls: cls?.toFixed(3), lcpMs: Math.round(lcp ?? 0),
    failing: Object.values(s.lhr.audits).filter((a) => a.score !== null && a.score < 0.9 && a.scoreDisplayMode !== 'informative'
      && a.scoreDisplayMode !== 'notApplicable' && a.scoreDisplayMode !== 'manual').map((a) => a.id).slice(0, 12) };
});
writeFileSync(OUT, await flow.generateReport());
console.log(JSON.stringify(rows, null, 1));
await browser.close();
