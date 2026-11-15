import type { ApplicationStatus, Branch, EmploymentType, JobStatus, Role } from './index';

/** Branch codes shared with the backend (spec 8.0), with human labels for display. */
export const BRANCH_LABELS: Record<Branch, string> = {
  CSE: 'Computer Science & Engineering',
  ISE: 'Information Science & Engineering',
  AIML: 'Artificial Intelligence & Machine Learning',
  ECE: 'Electronics & Communication Engineering',
  EEE: 'Electrical & Electronics Engineering',
  ETE: 'Electronics & Telecommunication Engineering',
  ME: 'Mechanical Engineering',
  CIVIL: 'Civil Engineering',
  CHEM: 'Chemical Engineering',
  BT: 'Biotechnology',
  IEM: 'Industrial Engineering & Management',
  AERO: 'Aerospace Engineering',
};

export const BRANCHES = Object.keys(BRANCH_LABELS) as Branch[];

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  INTERNSHIP: 'Internship',
  FULL_TIME: 'Full-time',
};

export const JOB_STATUSES: JobStatus[] = ['DRAFT', 'PUBLISHED', 'CLOSED'];

export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'SELECTED', 'REJECTED', 'WITHDRAWN',
];

export const STATUS_LABELS: Record<ApplicationStatus | JobStatus, string> = {
  APPLIED: 'Applied',
  UNDER_REVIEW: 'Under review',
  SHORTLISTED: 'Shortlisted',
  SELECTED: 'Selected',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  CLOSED: 'Closed',
};

/** Mirror of ApplicationStatus.canTransitionTo in Application Service. */
export const NEXT_STATUSES: Record<ApplicationStatus, ApplicationStatus[]> = {
  APPLIED: ['UNDER_REVIEW', 'SHORTLISTED', 'REJECTED', 'WITHDRAWN'],
  UNDER_REVIEW: ['SHORTLISTED', 'REJECTED', 'WITHDRAWN'],
  SHORTLISTED: ['SELECTED', 'REJECTED', 'WITHDRAWN'],
  SELECTED: [],
  REJECTED: [],
  WITHDRAWN: [],
};

/** Transitions a recruiter/admin may apply (withdrawal belongs to the student). */
export function recruiterTransitions(current: ApplicationStatus): ApplicationStatus[] {
  return NEXT_STATUSES[current].filter((s) => s !== 'WITHDRAWN');
}

export function canWithdraw(current: ApplicationStatus): boolean {
  return NEXT_STATUSES[current].includes('WITHDRAWN');
}

export const ROLE_HOME: Record<Role, string> = {
  STUDENT: '/student',
  RECRUITER: '/recruiter',
  ADMIN: '/admin',
};
