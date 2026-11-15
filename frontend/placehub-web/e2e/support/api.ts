import { expect, request, type APIRequestContext } from '@playwright/test';

/** Direct gateway calls used to arrange test data, so every test owns fresh, isolated records. */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';
export const DEMO_PASSWORD = 'PlaceHub@123';
export const PASSWORD = 'Smoke12345';

export const SEEDED = {
  admin: 'admin@placehub.local',
  recruiter: 'recruiter@placehub.local',
  recruiter2: 'recruiter2@placehub.local',
  student: 'student@placehub.local',
  student2: 'student2@placehub.local',
};

export function uniqueSuffix(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
}

let shared: APIRequestContext | undefined;
export async function api(): Promise<APIRequestContext> {
  shared ??= await request.newContext({ baseURL: API_URL });
  return shared;
}

export interface Account {
  id: string;
  email: string;
  password: string;
  token: string;
  fullName: string;
}

export async function login(email: string, password: string): Promise<{ token: string; id: string }> {
  const res = await (await api()).post('/api/auth/login', { data: { email, password } });
  expect(res.status(), `login ${email}`).toBe(200);
  const body = await res.json();
  return { token: body.accessToken, id: body.user.id };
}

export function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function registerStudent(opts: { cgpa?: number; branch?: string; name?: string } = {}): Promise<Account> {
  const suffix = uniqueSuffix();
  const email = `e2e.student.${suffix}@example.com`;
  const fullName = opts.name ?? `E2E Student ${suffix}`;
  const res = await (await api()).post('/api/auth/register/student', {
    data: {
      fullName, email, password: PASSWORD, collegeName: 'E2E Institute', branch: opts.branch ?? 'CSE',
      graduationYear: 2027, cgpa: opts.cgpa ?? 8.5, backlogCount: 0,
    },
  });
  expect(res.status(), 'register student').toBe(201);
  const { token, id } = await login(email, PASSWORD);
  return { id, email, password: PASSWORD, token, fullName };
}

export async function registerRecruiter(): Promise<Account> {
  const suffix = uniqueSuffix();
  const email = `e2e.recruiter.${suffix}@example.com`;
  const fullName = `E2E Recruiter ${suffix}`;
  const res = await (await api()).post('/api/auth/register/recruiter', {
    data: { fullName, email, password: PASSWORD, companyName: `E2E Corp ${suffix}`, designation: 'Hiring Lead' },
  });
  expect(res.status(), 'register recruiter').toBe(201);
  const { token, id } = await login(email, PASSWORD);
  return { id, email, password: PASSWORD, token, fullName };
}

export async function createCompany(recruiter: Account): Promise<void> {
  const res = await (await api()).post('/api/companies', {
    headers: auth(recruiter.token),
    data: { name: `E2E Corp ${uniqueSuffix()}`, industry: 'Software' },
  });
  expect(res.status(), 'create company').toBe(201);
}

export interface JobOptions {
  title?: string;
  ctcLpa?: number;
  employmentType?: 'FULL_TIME' | 'INTERNSHIP';
  screeningMode?: 'MANUAL' | 'AUTO_SHORTLIST';
  minCgpa?: number;
}

export async function createPublishedJob(recruiter: Account, opts: JobOptions = {}): Promise<{ id: string; title: string }> {
  const title = opts.title ?? `E2E Engineer ${uniqueSuffix()}`;
  const deadline = new Date(Date.now() + 7 * 86_400_000).toISOString();
  const ctx = await api();
  const created = await ctx.post('/api/jobs', {
    headers: auth(recruiter.token),
    data: {
      title, description: 'End-to-end test job', location: 'Pune', employmentType: opts.employmentType ?? 'FULL_TIME',
      ctc: '10 LPA', ctcLpa: opts.ctcLpa ?? 10, minCgpa: opts.minCgpa ?? 7.0, maxBacklogs: 0,
      eligibleBranches: ['CSE', 'ISE'], eligibleGraduationYears: [], deadline,
      screeningMode: opts.screeningMode,
    },
  });
  expect(created.status(), 'create job').toBe(201);
  const job = await created.json();
  const published = await ctx.patch(`/api/jobs/${job.id}/publish`, { headers: auth(recruiter.token) });
  expect(published.status(), 'publish job').toBe(200);
  return { id: job.id, title };
}

/** A tiny but valid PDF (starts with the %PDF- magic bytes). */
export function samplePdf(label = 'E2E resume'): Buffer {
  return Buffer.from(
    `%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n` +
      `3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 100]>>endobj\n% ${label}\ntrailer<</Root 1 0 R>>\n%%EOF\n`,
  );
}

/** Uploads a resume through the API (no-op on stacks without the v3 resume endpoint). */
export async function uploadResume(student: Account): Promise<number> {
  const res = await (await api()).post('/api/users/me/resume', {
    headers: auth(student.token),
    multipart: { file: { name: 'resume.pdf', mimeType: 'application/pdf', buffer: samplePdf(student.fullName) } },
  });
  return res.status();
}

export async function apply(student: Account, jobId: string, consent?: { accepted: boolean; signedName: string }) {
  return (await api()).post('/api/applications', { headers: auth(student.token), data: { jobId, consent } });
}
