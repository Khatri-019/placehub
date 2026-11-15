import { expect, test } from '@playwright/test';
import {
  auth, api, createCompany, createPublishedJob, DEMO_PASSWORD, registerRecruiter, registerStudent, SEEDED,
  uploadResume,
} from './support/api';
import { signIn } from './support/ui';

/** Baseline coverage of the v2 user journeys (Phase 9.0). */

test.describe('login per role', () => {
  for (const [portal, email, heading] of [
    ['student', SEEDED.student, /Welcome, Demo Student/],
    ['recruiter', SEEDED.recruiter, /Recruiter dashboard/i],
    ['admin', SEEDED.admin, /Placement office dashboard/i],
  ] as const) {
    test(`${portal} lands on their dashboard`, async ({ page }) => {
      await signIn(page, email, DEMO_PASSWORD, portal);
      await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
    });
  }

  test('wrong password shows the generic error', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(SEEDED.student);
    await page.getByLabel('Password', { exact: true }).fill('not-the-password1');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toContainText('Invalid email or password');
  });
});

test('student browses, applies, gets shortlisted, sees notifications and withdraws', async ({ page, browser }) => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const job = await createPublishedJob(recruiter, { ctcLpa: 10 });
  const student = await registerStudent({ cgpa: 8.5 });
  await uploadResume(student);

  // Browse and apply through the UI.
  await signIn(page, student.email, student.password, 'student');
  await page.goto('/student/jobs');
  await page.getByLabel('Search', { exact: true }).fill(job.title);
  await page.getByRole('button', { name: /apply filters|search/i }).click();
  await page.getByRole('link', { name: job.title }).click();
  await expect(page.getByRole('heading', { level: 1, name: job.title })).toBeVisible();
  await expect(page.getByText('You are eligible')).toBeVisible();
  await page.getByRole('button', { name: /apply now/i }).click();
  await expect(page.getByText(/application submitted/i).first()).toBeVisible();
  await expect(page.getByText('You have already applied')).toBeVisible();

  // Recruiter shortlists in a separate browser context.
  const recruiterContext = await browser.newContext();
  const rp = await recruiterContext.newPage();
  await signIn(rp, recruiter.email, recruiter.password, 'recruiter');
  await rp.goto('/recruiter/jobs');
  await rp.getByRole('row', { name: new RegExp(job.title) }).getByRole('link', { name: 'Applicants' }).click();
  await rp.getByRole('row', { name: new RegExp(student.fullName) }).getByRole('link', { name: /review/i }).click();
  await rp.getByRole('button', { name: /mark shortlisted/i }).click();
  await expect(rp.getByText(/marked shortlisted/i).first()).toBeVisible();
  await recruiterContext.close();

  // Status-change notification reaches the student (via outbox -> Kafka -> consumer).
  await expect(async () => {
    await page.goto('/notifications');
    await expect(page.getByText(new RegExp(`${job.title} is now SHORTLISTED`)).first()).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 20_000 });

  // Withdraw with confirmation.
  await page.goto('/student/applications');
  await page.getByRole('row', { name: new RegExp(job.title) }).getByRole('button', { name: 'Withdraw' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Withdraw' }).click();
  await expect(page.getByRole('row', { name: new RegExp(job.title) }).getByText('Withdrawn')).toBeVisible();
});

test('ineligible student sees every failing reason and cannot apply', async ({ page }) => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const job = await createPublishedJob(recruiter, { minCgpa: 9.0 });
  const student = await registerStudent({ cgpa: 7.0, branch: 'ME' });
  await uploadResume(student);
  await signIn(page, student.email, student.password, 'student');
  await page.goto(`/student/jobs/${job.id}`);
  await expect(page.getByText('Minimum CGPA required is 9.00; yours is 7.00')).toBeVisible();
  await expect(page.getByText(/Your branch \(ME\) is not eligible/)).toBeVisible();
  await expect(page.getByRole('button', { name: /apply now/i })).toBeDisabled();
});

test('admin pages load with live data', async ({ page }) => {
  await signIn(page, SEEDED.admin, DEMO_PASSWORD, 'admin');
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { level: 1, name: 'Users' })).toBeVisible();
  await expect(page.getByRole('cell', { name: SEEDED.student, exact: true })).toBeVisible();
  await page.goto('/admin/jobs');
  await expect(page.getByRole('cell', { name: 'Graduate Software Engineer' }).first()).toBeVisible();
  await page.goto('/admin/applications');
  await expect(page.getByRole('heading', { level: 1, name: /applications/i })).toBeVisible();
});

test('roles cannot open other role areas', async ({ page }) => {
  await signIn(page, SEEDED.student, DEMO_PASSWORD, 'student');
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/student$/);
  const ctx = await api();
  const { token } = await (await ctx.post('/api/auth/login', {
    data: { email: SEEDED.student, password: DEMO_PASSWORD },
  })).json().then((b) => ({ token: b.accessToken as string }));
  expect((await ctx.get('/api/admin/users', { headers: auth(token) })).status()).toBe(403);
});
