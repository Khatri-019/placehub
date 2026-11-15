// TypeScript mirrors of the backend DTOs. Keep in sync with each service's dto package.

export type Role = 'STUDENT' | 'RECRUITER' | 'ADMIN';
export type AccountStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'DEACTIVATED';
export type Branch =
  | 'CSE' | 'ISE' | 'AIML' | 'ECE' | 'EEE' | 'ETE' | 'ME' | 'CIVIL' | 'CHEM' | 'BT' | 'IEM' | 'AERO';
export type EmploymentType = 'INTERNSHIP' | 'FULL_TIME';
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type ApplicationStatus =
  | 'APPLIED' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'SELECTED' | 'REJECTED' | 'WITHDRAWN';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  details?: { reasons?: string[]; fieldErrors?: Record<string, string> };
}

export interface StudentDetails {
  collegeName: string;
  branch: Branch;
  graduationYear: number;
  cgpa: number;
  backlogCount: number;
}

export interface RecruiterDetails {
  companyName: string;
  designation: string;
}

export interface User {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  status: AccountStatus;
  createdAt: string;
  studentDetails?: StudentDetails;
  recruiterDetails?: RecruiterDetails;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface StudentRegistration {
  fullName: string;
  email: string;
  password: string;
  collegeName: string;
  branch: Branch | '';
  graduationYear: number | '';
  cgpa: number | '';
  backlogCount: number | '';
}

export interface RecruiterRegistration {
  fullName: string;
  email: string;
  password: string;
  companyName: string;
  designation: string;
}

export interface Company {
  id: string;
  name: string;
  website?: string | null;
  description?: string | null;
  industry?: string | null;
  recruiterOwnerUserId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyRequest {
  name: string;
  website?: string;
  description?: string;
  industry?: string;
}

export interface Job {
  id: string;
  companyId: string;
  companyName: string;
  recruiterOwnerUserId: string;
  title: string;
  description: string;
  location: string;
  employmentType: EmploymentType;
  ctc?: string | null;
  minCgpa: number;
  maxBacklogs: number;
  eligibleBranches: Branch[];
  eligibleGraduationYears: number[];
  deadline: string;
  status: JobStatus;
  createdAt: string;
  updatedAt: string;
}

export interface JobRequest {
  title: string;
  description: string;
  location: string;
  employmentType: EmploymentType;
  ctc?: string;
  minCgpa?: number;
  maxBacklogs?: number;
  eligibleBranches: Branch[];
  eligibleGraduationYears: number[];
  deadline: string;
}

export interface Application {
  id: string;
  jobId: string;
  studentUserId: string;
  recruiterOwnerUserId: string;
  jobTitle: string;
  companyName: string;
  studentName: string;
  studentEmail: string;
  branch: Branch;
  cgpa: number;
  graduationYear: number;
  backlogCount: number;
  status: ApplicationStatus;
  recruiterNote?: string | null;
  appliedAt: string;
  updatedAt: string;
}

export interface EligibilityPreview {
  jobId: string;
  eligible: boolean;
  reasons: string[];
  alreadyApplied: boolean;
}

export interface StatusStats {
  total: number;
  byStatus: Record<ApplicationStatus, number>;
}

export interface Notification {
  id: string;
  eventId: string;
  recipientUserId: string;
  type: string;
  title: string;
  message: string;
  relatedApplicationId?: string;
  relatedJobId?: string;
  read: boolean;
  createdAt: string;
}
