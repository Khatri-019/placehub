import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { createCompany, createPublishedJob, DEMO_PASSWORD, registerRecruiter, registerStudent, SEEDED, uploadResume, apply } from './support/api';
import { signIn, type Portal } from './support/ui';

/**
 * Accessibility (axe, WCAG 2.1 A/AA) on the key pages in both themes, plus the screenshots used in the README.
 * Screenshots land in docs/screenshots/ (repo root).
 */
const SHOTS = new URL('../../../docs/screenshots/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
mkdirSync(SHOTS, { recursive: true });

async function setTheme(page: Page, theme: 'light' | 'dark') {
  await page.addInitScript((t) => { try { localStorage.setItem('placehub.theme', t); } catch { /* ignore */ } }, theme);
}

async function check(page: Page, name: string, theme: string) {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  const results = await new AxeBuilder({ page: page as unknown as ConstructorParameters<typeof AxeBuilder>[0]['page'] }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(', ')}`);
  expect(summary, `${name} [${theme}] axe violations`).toEqual([]);
  await page.screenshot({ path: `${SHOTS}${name}-${theme}.png`, fullPage: false });
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`${theme} theme`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: 1366, height: 860 });
      await setTheme(page, theme);
    });

    test('public pages', async ({ page }) => {
      await page.goto('/');
      await check(page, 'landing', theme);
      for (const portal of ['student', 'recruiter', 'admin'] as Portal[]) {
        await page.goto(`/login/${portal}`);
        await check(page, `login-${portal}`, theme);
      }
    });

    test('student pages', async ({ page }) => {
      await signIn(page, SEEDED.student, DEMO_PASSWORD, 'student');
      await check(page, 'student-dashboard', theme);
      await page.goto('/student/jobs');
      await check(page, 'student-jobs', theme);
      // Test runs add many jobs; search so the seeded one is on page 1.
      await page.getByLabel('Search', { exact: true }).fill('Senior Data Engineer');
      await page.getByRole('button', { name: /apply filters/i }).click();
      await page.getByRole('link', { name: 'Senior Data Engineer' }).first().click();
      await expect(page.getByRole('heading', { level: 1, name: 'Senior Data Engineer' })).toBeVisible();
      await check(page, 'student-job-details', theme);
    });

    test('recruiter applicants', async ({ page }) => {
      const recruiter = await registerRecruiter();
      await createCompany(recruiter);
      const job = await createPublishedJob(recruiter);
      for (let i = 0; i < 2; i += 1) {
        const s = await registerStudent();
        await uploadResume(s);
        await apply(s, job.id);
      }
      await signIn(page, recruiter.email, recruiter.password, 'recruiter');
      await page.goto(`/recruiter/jobs/${job.id}/applicants`);
      await expect(page.getByRole('table')).toBeVisible();
      await check(page, 'recruiter-applicants', theme);
    });

    test('admin policy', async ({ page }) => {
      await signIn(page, SEEDED.admin, DEMO_PASSWORD, 'admin');
      await page.goto('/admin/policy');
      await expect(page.getByTestId('simulation-result').getByText(/REQUIRE CONSENT|ALLOW|BLOCK/)).toBeVisible();
      await check(page, 'admin-policy', theme);
    });
  });
}
