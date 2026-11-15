import { api } from './client';
import type {
  AccountStatus, Application, ApplicationStatus, Branch, Company, CompanyRequest, EligibilityPreview,
  EmploymentType, Job, JobRequest, JobStatus, LoginResponse, Notification, Page, RecruiterRegistration, Role,
  ResumeMetadata, ResumeOverview, StatusStats, StudentRegistration, User,
} from '../types';

type Params = Record<string, string | number | boolean | undefined>;

/** Drops empty values so filters are only sent when set. */
function clean(params: Params): Params {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== '')) as Params;
}

// ---------------------------------------------------------------- auth & users (user-service)
export const authApi = {
  login: (email: string, password: string) =>
    api.post<LoginResponse>('/api/auth/login', { email, password }).then((r) => r.data),
  registerStudent: (body: StudentRegistration) =>
    api.post<User>('/api/auth/register/student', body).then((r) => r.data),
  registerRecruiter: (body: RecruiterRegistration) =>
    api.post<User>('/api/auth/register/recruiter', body).then((r) => r.data),
};

export const userApi = {
  me: () => api.get<User>('/api/users/me').then((r) => r.data),
  adminList: (p: { role?: Role | ''; status?: AccountStatus | ''; q?: string; page?: number; size?: number }) =>
    api.get<Page<User>>('/api/admin/users', { params: clean(p) }).then((r) => r.data),
  setStatus: (id: string, status: 'ACTIVE' | 'DEACTIVATED') =>
    api.patch<User>(`/api/admin/users/${id}/status`, { status }).then((r) => r.data),
};

export const resumeApi = {
  /** Resolves to null when the student has not uploaded a resume yet. */
  get: () =>
    api.get<ResumeOverview>('/api/users/me/resume').then((r) => r.data).catch((err) => {
      if (err?.response?.status === 404) return null;
      throw err;
    }),
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post<ResumeMetadata>('/api/users/me/resume', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data);
  },
  remove: (id: string) => api.delete(`/api/users/me/resume/${id}`),
  /** PDF bytes fetched with the bearer header; never put the token in a URL. */
  currentBlob: () => api.get<Blob>('/api/users/me/resume/file', { responseType: 'blob' }).then((r) => r.data),
  versionBlob: (id: string) => api.get<Blob>(`/api/users/me/resume/${id}/file`, { responseType: 'blob' }).then((r) => r.data),
};

// ---------------------------------------------------------------- companies & jobs (job-service)
export const companyApi = {
  mine: () => api.get<Company>('/api/companies/me').then((r) => r.data),
  create: (body: CompanyRequest) => api.post<Company>('/api/companies', body).then((r) => r.data),
  updateMine: (body: CompanyRequest) => api.put<Company>('/api/companies/me', body).then((r) => r.data),
  adminList: (p: { page?: number; size?: number }) =>
    api.get<Page<Company>>('/api/admin/companies', { params: clean(p) }).then((r) => r.data),
};

export interface JobBrowseFilters {
  q?: string;
  location?: string;
  employmentType?: EmploymentType | '';
  branch?: Branch | '';
  page?: number;
  size?: number;
  sort?: string;
}

export const jobApi = {
  browse: (f: JobBrowseFilters) => api.get<Page<Job>>('/api/jobs', { params: clean({ ...f }) }).then((r) => r.data),
  get: (id: string) => api.get<Job>(`/api/jobs/${id}`).then((r) => r.data),
  create: (body: JobRequest) => api.post<Job>('/api/jobs', body).then((r) => r.data),
  update: (id: string, body: JobRequest) => api.put<Job>(`/api/jobs/${id}`, body).then((r) => r.data),
  publish: (id: string) => api.patch<Job>(`/api/jobs/${id}/publish`).then((r) => r.data),
  close: (id: string) => api.patch<Job>(`/api/jobs/${id}/close`).then((r) => r.data),
  mine: (p: { status?: JobStatus | ''; page?: number; size?: number }) =>
    api.get<Page<Job>>('/api/recruiter/jobs', { params: clean(p) }).then((r) => r.data),
  adminList: (p: { status?: JobStatus | ''; q?: string; page?: number; size?: number }) =>
    api.get<Page<Job>>('/api/admin/jobs', { params: clean(p) }).then((r) => r.data),
};

// ---------------------------------------------------------------- applications (application-service)
export const applicationApi = {
  apply: (jobId: string) => api.post<Application>('/api/applications', { jobId }).then((r) => r.data),
  eligibility: (jobId: string) =>
    api.get<EligibilityPreview>('/api/applications/eligibility', { params: { jobId } }).then((r) => r.data),
  mine: (p: { status?: ApplicationStatus | ''; page?: number; size?: number }) =>
    api.get<Page<Application>>('/api/applications/me', { params: clean(p) }).then((r) => r.data),
  get: (id: string) => api.get<Application>(`/api/applications/${id}`).then((r) => r.data),
  resumeBlob: (id: string) =>
    api.get<Blob>(`/api/applications/${id}/resume`, { responseType: 'blob' }).then((r) => r.data),
  withdraw: (id: string) => api.patch<Application>(`/api/applications/${id}/withdraw`).then((r) => r.data),
  forJob: (jobId: string, p: { status?: ApplicationStatus | ''; page?: number; size?: number }) =>
    api.get<Page<Application>>(`/api/applications/job/${jobId}`, { params: clean(p) }).then((r) => r.data),
  updateStatus: (id: string, status: ApplicationStatus, note?: string) =>
    api.patch<Application>(`/api/applications/${id}/status`, { status, note }).then((r) => r.data),
  adminList: (p: { status?: ApplicationStatus | ''; jobId?: string; page?: number; size?: number }) =>
    api.get<Page<Application>>('/api/admin/applications', { params: clean(p) }).then((r) => r.data),
  myStats: () => api.get<StatusStats>('/api/applications/stats/me').then((r) => r.data),
  recruiterStats: () => api.get<StatusStats>('/api/applications/stats/recruiter').then((r) => r.data),
};

// ---------------------------------------------------------------- notifications (notification-service)
export const notificationApi = {
  list: (p: { unreadOnly?: boolean; page?: number; size?: number }) =>
    api.get<Page<Notification>>('/api/notifications', { params: clean(p) }).then((r) => r.data),
  unreadCount: () => api.get<{ count: number }>('/api/notifications/unread-count').then((r) => r.data.count),
  markRead: (id: string) => api.patch<Notification>(`/api/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.patch<{ updated: number }>('/api/notifications/read-all').then((r) => r.data.updated),
};
