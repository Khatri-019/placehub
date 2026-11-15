import type { ReactNode } from 'react';
import {
  Ban, CheckCircle2, Circle, CircleDot, Clock3, FileEdit, Lock, LogOut, ShieldAlert, Sparkles, Star, XCircle,
} from 'lucide-react';
import type { AccountStatus, ApplicationStatus, JobStatus } from '../types';
import { STATUS_LABELS } from '../types/enums';
import styles from './Badge.module.css';

export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'brand';

export function Badge({ tone = 'neutral', icon, children }: { tone?: Tone; icon?: ReactNode; children: ReactNode }) {
  return (
    <span className={`${styles.badge} ${styles[tone]}`}>
      {icon}
      {children}
    </span>
  );
}

type AnyStatus = ApplicationStatus | JobStatus | AccountStatus;

const STATUS_TONE: Record<AnyStatus, Tone> = {
  APPLIED: 'info',
  UNDER_REVIEW: 'warning',
  SHORTLISTED: 'brand',
  SELECTED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
  DRAFT: 'neutral',
  PUBLISHED: 'success',
  CLOSED: 'danger',
  ACTIVE: 'success',
  DEACTIVATED: 'danger',
  PENDING_APPROVAL: 'warning',
};

const ACCOUNT_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  DEACTIVATED: 'Deactivated',
  PENDING_APPROVAL: 'Pending approval',
};

const STATUS_ICON: Record<AnyStatus, ReactNode> = {
  APPLIED: <CircleDot aria-hidden="true" />,
  UNDER_REVIEW: <Clock3 aria-hidden="true" />,
  SHORTLISTED: <Star aria-hidden="true" />,
  SELECTED: <CheckCircle2 aria-hidden="true" />,
  REJECTED: <XCircle aria-hidden="true" />,
  WITHDRAWN: <LogOut aria-hidden="true" />,
  DRAFT: <FileEdit aria-hidden="true" />,
  PUBLISHED: <CheckCircle2 aria-hidden="true" />,
  CLOSED: <Ban aria-hidden="true" />,
  ACTIVE: <CheckCircle2 aria-hidden="true" />,
  DEACTIVATED: <Ban aria-hidden="true" />,
  PENDING_APPROVAL: <Clock3 aria-hidden="true" />,
};

/** Status badge: semantic colour plus icon plus text, so status is never conveyed by colour alone. */
export function StatusBadge({ status }: { status: AnyStatus }) {
  const label = (STATUS_LABELS as Record<string, string>)[status] ?? ACCOUNT_LABELS[status as AccountStatus] ?? status;
  return <Badge tone={STATUS_TONE[status] ?? 'neutral'} icon={STATUS_ICON[status] ?? <Circle aria-hidden="true" />}>{label}</Badge>;
}

export type PolicyKind = 'OPEN' | 'CONSENT' | 'BLOCKED' | 'LOCKED';

const POLICY: Record<PolicyKind, { label: string; icon: ReactNode }> = {
  OPEN: { label: 'Open', icon: <CheckCircle2 aria-hidden="true" /> },
  CONSENT: { label: 'Consent needed', icon: <ShieldAlert aria-hidden="true" /> },
  BLOCKED: { label: 'Blocked', icon: <Ban aria-hidden="true" /> },
  LOCKED: { label: 'Locked', icon: <Lock aria-hidden="true" /> },
};

export function PolicyBadge({ kind }: { kind: PolicyKind }) {
  return (
    <span className={`${styles.badge} ${styles[`policy${kind}`]}`} title={`Placement policy: ${POLICY[kind].label}`}>
      {POLICY[kind].icon}
      <span className="visually-hidden">Placement policy: </span>
      {POLICY[kind].label}
    </span>
  );
}

export function AutoBadge() {
  return <Badge tone="brand" icon={<Sparkles aria-hidden="true" />}>Auto-shortlisted</Badge>;
}
