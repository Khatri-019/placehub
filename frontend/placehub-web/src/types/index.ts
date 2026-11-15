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
  /** Numeric CTC in lakhs per annum (required to publish FULL_TIME jobs). */
  ctcLpa?: number | null;
  minCgpa: number;
  maxBacklogs: number;
  eligibleBranches: Branch[];
  eligibleGraduationYears: number[];
  deadline: string;
  status: JobStatus;
  createdAt: string;
  updatedAt: string;
  screeningMode?: ScreeningMode;
  autoShortlistMinCgpa?: number | null;
  autoShortlistMaxBacklogs?: number | null;
}

export interface JobRequest {
  title: string;
  description: string;
  location: string;
  employmentType: EmploymentType;
  ctc?: string;
  ctcLpa?: number;
  minCgpa?: number;
  maxBacklogs?: number;
  eligibleBranches: Branch[];
  eligibleGraduationYears: number[];
  deadline: string;
  screeningMode?: ScreeningMode;
  autoShortlistMinCgpa?: number;
  autoShortlistMaxBacklogs?: number;
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
  ctcLpa?: number | null;
  employmentType?: EmploymentType | null;
  hasResume?: boolean;
  policyDecision?: PolicyDecision | null;
  autoShortlisted?: boolean;
}

export type ViolationCode =
  | 'NOT_ELIGIBLE' | 'RESUME_REQUIRED' | 'POLICY_BLOCKED' | 'CONSENT_REQUIRED' | 'STUDENT_LOCKED'
  | 'CONSENT_LIMIT_REACHED';

export interface Violation {
  code: ViolationCode;
  message: string;
}

export interface EligibilityPreview {
  jobId: string;
  /** Job Service criteria only (v2 meaning). */
  eligible: boolean;
  reasons: string[];
  alreadyApplied: boolean;
  resumeRequired?: boolean;
  hasResume?: boolean;
  /** True when every requirement (criteria, resume, policy) is met and the student has not applied yet. */
  canApply?: boolean;
  violations?: Violation[];
  policy?: PolicyEvaluation;
  consent?: { renderedText: string; policyVersion: number; band: string | null; ratio: number | null } | null;
  placementState?: { currentCtc: number | null; placed: boolean; locked: boolean; summary: string };
}

export interface ResumeMetadata {
  id: string;
  filename: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: string;
  current: boolean;
  referenced: boolean;
}

export interface ResumeOverview {
  current: ResumeMetadata;
  versions: ResumeMetadata[];
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

// ---------------------------------------------------------------- v3 (BUILD_SPEC section 20)

export type ScreeningMode = 'MANUAL' | 'AUTO_SHORTLIST';
export type PolicyAction = 'BLOCK' | 'REQUIRE_CONSENT' | 'ALLOW';
export type PolicyDecision = 'ALLOW' | 'REQUIRE_CONSENT' | 'BLOCK';

export interface HistoryEntry {
  fromStatus?: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  actorType: 'USER' | 'SYSTEM';
  actorUserId?: string | null;
  reason?: string | null;
  changedAt: string;
}

export interface PolicyBand {
  fromMultiplier: number;
  toMultiplier: number | null;
  action: PolicyAction;
  lockOnPlacement: boolean;
}

export interface Policy {
  id: string;
  version: number;
  active: boolean;
  bands: PolicyBand[];
  appliesToInternships: boolean;
  maxActiveConsents: number | null;
  resumeRequired: boolean;
  withdrawOtherApplicationsOnLock: boolean;
  consentText: string;
  effectiveFrom: string;
  changedByUserId?: string | null;
  changeNote: string;
  createdAt: string;
}

export interface PolicyDraft {
  bands: PolicyBand[];
  appliesToInternships: boolean;
  maxActiveConsents: number | null;
  resumeRequired: boolean;
  withdrawOtherApplicationsOnLock: boolean;
  consentText: string;
}

export interface PolicyEvaluation {
  decision: PolicyDecision;
  reasons: { code: string; message: string }[];
  matchedBand?: PolicyBand | null;
  matchedBandLabel?: string | null;
  lockOnPlacement: boolean;
  policyVersion: number;
  ratio?: number | null;
}

export interface Placement {
  id: string;
  studentUserId: string;
  studentName: string;
  applicationId?: string | null;
  companyName: string;
  ctcLpa?: number | null;
  band?: string | null;
  locked: boolean;
  source: 'ON_CAMPUS' | 'OFF_CAMPUS';
  recordedAt: string;
  recordedBy?: string | null;
  studentCurrentlyLocked: boolean;
}

export interface BandExplanation {
  fromCtc: number;
  toCtc: number | null;
  action: PolicyAction;
  lockOnPlacement: boolean;
  description: string;
}

export interface PlacementStateView {
  currentCtc: number | null;
  placed: boolean;
  locked: boolean;
  lockReason?: string | null;
  placements: Placement[];
  policyVersion: number;
  policyEffectiveFrom: string;
  appliesToInternships: boolean;
  bands: BandExplanation[];
  summary: string;
}

export interface ConsentRecord {
  id: string;
  applicationId: string;
  studentUserId: string;
  jobId: string;
  policyVersion: number;
  band: string;
  consentTextHash: string;
  renderedText: string;
  signedName: string;
  acceptedAt: string;
  clientIp?: string | null;
  userAgent?: string | null;
}

export interface AuditEntry {
  id: string;
  actorUserId?: string | null;
  action: string;
  studentUserId?: string | null;
  before?: string | null;
  after?: string | null;
  reason?: string | null;
  createdAt: string;
}

export interface BulkResult {
  results: { applicationId: string; success: boolean; status?: ApplicationStatus; code?: string; message?: string }[];
  succeeded: number;
  failed: number;
}
