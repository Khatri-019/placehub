import type { AccountStatus, ApplicationStatus, JobStatus } from '../types';
import { STATUS_LABELS } from '../types/enums';
import styles from './StatusBadge.module.css';

type AnyStatus = ApplicationStatus | JobStatus | AccountStatus;

const ACCOUNT_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  DEACTIVATED: 'Deactivated',
  PENDING_APPROVAL: 'Pending approval',
};

/** One consistent colour per status, for applications, jobs and accounts. */
export function StatusBadge({ status }: { status: AnyStatus }) {
  const label = (STATUS_LABELS as Record<string, string>)[status] ?? ACCOUNT_LABELS[status as AccountStatus];
  return <span className={`${styles.badge} ${styles[status.toLowerCase()] ?? ''}`}>{label ?? status}</span>;
}
