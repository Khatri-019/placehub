import { expect, test } from '@playwright/test';
import {
  api, apply, auth, createCompany, createPublishedJob, DEMO_PASSWORD, login, registerRecruiter, registerStudent, SEEDED,
  uploadResume, type Account,
} from './support/api';
import { signIn } from './support/ui';

/** v3 journeys (BUILD_SPEC 20.8): portals, consent, policy editor, bulk actions, theme + ribbon, mobile browse. */

async function setStatus(recruiter: Account, appId: string, status: string) {
  const res = await (await api()).patch(`/api/applications/${appId}/status`, { headers: auth(recruiter.token), data: { status } });
  expect(res.status(), `status ${status}`).toBe(200);
}

test('the three login portals: chooser, per-role pages and the role-mismatch message', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible();
  for (const [link, heading] of [[/student/i, 'Student sign in'], [/recruiter/i, 'Recruiter sign in'],
    [/placement office/i, 'Placement office sign in']] as const) {
    await page.goto('/login');
    await page.getByRole('link', { name: link }).first().click();
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
  }
  await page.goto('/login/recruiter');
  await page.getByLabel('Email').fill(SEEDED.student);
  await page.getByLabel('Password', { exact: true }).fill(DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toContainText(/not registered as a recruiter.*student login/i);
  await expect(page).toHaveURL(/\/login\/recruiter$/);
});

test('a placed student applies to a consent-band job through the consent modal', async ({ page }) => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const first = await createPublishedJob(recruiter, { ctcLpa: 10 });
  const consentJob = await createPublishedJob(recruiter, { ctcLpa: 17 }); // 1.7x: consent band in the seeded policy
  const student = await registerStudent();
  await uploadResume(student);
  const placed = await (await apply(student, first.id)).json();
  await setStatus(recruiter, placed.id, 'SHORTLISTED');
  await setStatus(recruiter, placed.id, 'SELECTED');

  await signIn(page, student.email, student.password, 'student');
  await page.goto(`/student/jobs/${consentJob.id}`);
  await expect(page.getByText('Consent needed').first()).toBeVisible();
  await page.getByRole('button', { name: 'Apply with consent' }).click();
  const dialog = page.getByRole('dialog', { name: /consent required/i });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Agree and apply' })).toBeDisabled();
  await dialog.getByTestId('consent-text').evaluate((el) => { el.scrollTop = el.scrollHeight; });
  await dialog.getByLabel(/I have read and agree/).check();
  await dialog.getByLabel('Type your full name to sign').fill('Someone Else');
  await expect(dialog.getByRole('button', { name: 'Agree and apply' })).toBeDisabled();
  await dialog.getByLabel('Type your full name to sign').fill(student.fullName);
  await dialog.getByRole('button', { name: 'Agree and apply' }).click();
  await expect(page.getByText(/application submitted/i).first()).toBeVisible();
  await expect(page.getByText('You have already applied')).toBeVisible();
});

test('admin policy editor validates bands live and simulates the unsaved draft', async ({ page }) => {
  await signIn(page, SEEDED.admin, DEMO_PASSWORD, 'admin');
  await page.goto('/admin/policy');
  const result = page.getByTestId('simulation-result');
  await page.getByLabel('Student current CTC (LPA)').fill('12');
  await page.getByLabel('Job CTC (LPA)').fill('28');
  await expect(result).toContainText('ALLOW'); // 2.33x is above the seeded 2.0 consent ceiling

  await page.getByLabel('Band 2 to').fill('2.5');
  await expect(page.getByRole('alert').filter({ hasText: 'overlap' })).toBeVisible();
  await expect(result).toContainText('Fix the draft errors');
  await page.getByLabel('Band 3 from').fill('2.5');
  await expect(page.getByRole('alert').filter({ hasText: 'overlap' })).toHaveCount(0);
  await expect(result).toContainText('REQUIRE CONSENT');
  await expect(page.getByText(/Added band 1\.50× – 2\.50×/).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Publish new version' })).toBeDisabled(); // change note missing
  await page.getByRole('button', { name: 'Discard' }).click();
  await expect(page.getByText('No changes yet.')).toBeVisible();
});

test('recruiter bulk-shortlists selected applicants', async ({ page }) => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const job = await createPublishedJob(recruiter);
  for (let i = 0; i < 3; i += 1) {
    const s = await registerStudent();
    await uploadResume(s);
    expect((await apply(s, job.id)).status()).toBe(201);
  }
  await signIn(page, recruiter.email, recruiter.password, 'recruiter');
  await page.goto(`/recruiter/jobs/${job.id}/applicants`);
  await page.getByLabel('Select all rows').check();
  await expect(page.getByRole('region', { name: 'Bulk actions' })).toContainText('3 selected');
  await page.getByRole('region', { name: 'Bulk actions' }).getByRole('button', { name: 'Mark shortlisted' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('3 applicants marked shortlisted.').first()).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Shortlisted' })).toHaveCount(3);
});

test('dark theme toggle persists and the policy ribbon can be dismissed', async ({ page }) => {
  // A new policy version (same rules) makes the ribbon show for students for 7 days.
  const admin = await login(SEEDED.admin, DEMO_PASSWORD);
  const ctx = await api();
  const active = await (await ctx.get('/api/admin/policy', { headers: auth(admin.token) })).json();
  const { bands, appliesToInternships, maxActiveConsents, resumeRequired, withdrawOtherApplicationsOnLock, consentText } = active;
  const saved = await ctx.put('/api/admin/policy', {
    headers: auth(admin.token),
    data: { bands, appliesToInternships, maxActiveConsents, resumeRequired, withdrawOtherApplicationsOnLock, consentText,
      changeNote: 'E2E: republish for the ribbon check' },
  });
  expect(saved.status()).toBe(200);

  const student = await registerStudent();
  await signIn(page, student.email, student.password, 'student');
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

  const ribbon = page.getByRole('region', { name: 'Placement policy update' });
  await expect(ribbon).toBeVisible();
  await ribbon.getByRole('button', { name: 'Dismiss policy update notice' }).click();
  await expect(ribbon).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(ribbon).toHaveCount(0);
  await page.getByRole('button', { name: 'Switch to light theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
});

test.describe('mobile', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });

  test('student browses jobs with the filter sheet and opens a job', async ({ page }) => {
    await signIn(page, SEEDED.student, DEMO_PASSWORD, 'student');
    await page.goto('/student/jobs');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, 'no horizontal scroll').toBeLessThanOrEqual(0);
    await page.getByRole('button', { name: 'Filters' }).click();
    const sheet = page.getByRole('dialog', { name: 'Filter jobs' });
    await sheet.getByLabel('Search', { exact: true }).fill('Quant Developer');
    await sheet.getByRole('button', { name: 'Apply filters' }).click();
    await expect(sheet).toHaveCount(0);
    await page.getByRole('link', { name: 'Quant Developer' }).first().click();
    await expect(page.getByRole('heading', { level: 1, name: 'Quant Developer' })).toBeVisible();
    await page.getByRole('button', { name: 'Open navigation' }).click();
    await expect(page.getByRole('dialog', { name: 'Menu' }).getByRole('link', { name: 'My applications' })).toBeVisible();
  });
});
