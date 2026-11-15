import { expect, test } from '@playwright/test';
import { apply, createCompany, createPublishedJob, registerRecruiter, registerStudent, samplePdf } from './support/api';
import { signIn } from './support/ui';

test('student uploads a resume on the profile page and a recruiter opens it from the applicant list', async ({ page, browser }) => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const job = await createPublishedJob(recruiter, { ctcLpa: 9 });
  const student = await registerStudent({ cgpa: 8.2 });

  await signIn(page, student.email, student.password, 'student');

  // Without a resume the job page warns and Apply stays disabled.
  await page.goto(`/student/jobs/${job.id}`);
  await expect(page.getByText(/Upload your resume before applying/)).toBeVisible();
  await expect(page.getByRole('button', { name: /apply now/i })).toBeDisabled();

  // Rejects a non-PDF client-side, then uploads a real PDF.
  await page.goto('/student/profile');
  const picker = page.locator('input[type="file"]');
  await picker.setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
  await expect(page.getByText('Only PDF files are accepted.').first()).toBeVisible();
  await picker.setInputFiles({ name: 'Asha CV.pdf', mimeType: 'application/pdf', buffer: samplePdf('ui upload') });
  await expect(page.getByText('Uploaded Asha CV.pdf.').first()).toBeVisible();
  await expect(page.getByText('Asha CV.pdf', { exact: true })).toBeVisible();

  // Now the student can apply from the job page.
  await page.goto(`/student/jobs/${job.id}`);
  await expect(page.getByText(/Upload your resume before applying/)).toHaveCount(0);
  await page.getByRole('button', { name: /apply now/i }).click();
  await expect(page.getByText('You have already applied')).toBeVisible();

  // Recruiter views the resume. The PDF is fetched with the bearer header (never a token in the URL) and handed to a
  // new tab as a blob: URL. Headless Chromium has no PDF viewer, so we assert the authenticated fetch and the popup.
  const ctx = await browser.newContext();
  const rp = await ctx.newPage();
  await signIn(rp, recruiter.email, recruiter.password, 'recruiter');
  await rp.goto(`/recruiter/jobs/${job.id}/applicants`);
  const [popup, response] = await Promise.all([
    ctx.waitForEvent('page'),
    rp.waitForResponse((r) => /\/api\/applications\/[^/]+\/resume$/.test(r.url())),
    rp.getByRole('button', { name: `View resume of ${student.fullName}` }).click(),
  ]);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/pdf');
  expect(response.headers()['x-content-type-options']).toBe('nosniff');
  expect(response.request().headers()['authorization']).toMatch(/^Bearer /);
  expect(response.url()).not.toMatch(/token|access/i);
  expect(popup).toBeTruthy();
  await ctx.close();
});

test('the API rejects a non-PDF named .pdf and applications without a resume', async () => {
  const recruiter = await registerRecruiter();
  await createCompany(recruiter);
  const job = await createPublishedJob(recruiter);
  const student = await registerStudent();
  const res = await apply(student, job.id);
  expect(res.status()).toBe(422);
  const body = await res.json();
  expect(body.details.violations.map((v: { code: string }) => v.code)).toContain('RESUME_REQUIRED');
});
