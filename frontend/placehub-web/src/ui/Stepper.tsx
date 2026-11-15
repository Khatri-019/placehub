import { Check, LogOut, X } from 'lucide-react';
import type { ApplicationStatus } from '../types';
import { STATUS_LABELS } from '../types/enums';
import type { HistoryEntry } from '../types';
import { formatDateTime } from '../utils/format';
import styles from './Stepper.module.css';

const PIPELINE: ApplicationStatus[] = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'SELECTED'];

/**
 * Applied → Under review → Shortlisted → Selected. Rejected and Withdrawn are shown as a distinct final step after
 * the last stage the application reached.
 */
export function Stepper({ status, reached }: { status: ApplicationStatus; reached?: ApplicationStatus[] }) {
  const terminalBad = status === 'REJECTED' || status === 'WITHDRAWN';
  const seen = new Set<ApplicationStatus>(reached ?? []);
  seen.add('APPLIED');
  if (!terminalBad) {
    PIPELINE.slice(0, PIPELINE.indexOf(status) + 1).forEach((s) => seen.add(s));
  }
  const lastReached = [...PIPELINE].reverse().find((s) => seen.has(s)) ?? 'APPLIED';
  const steps = terminalBad ? [...PIPELINE.slice(0, PIPELINE.indexOf(lastReached) + 1), status] : PIPELINE;

  return (
    <ol className={styles.steps} aria-label={`Application progress: ${STATUS_LABELS[status]}`}>
      {steps.map((s) => {
        const isCurrent = s === status;
        const done = seen.has(s) && !isCurrent;
        const bad = (s === 'REJECTED' || s === 'WITHDRAWN') && isCurrent;
        return (
          <li key={s} className={`${styles.step} ${done ? styles.done : ''} ${isCurrent ? styles.current : ''} ${bad ? styles.bad : ''}`}
            aria-current={isCurrent ? 'step' : undefined}>
            <span className={styles.dot} aria-hidden="true">
              {bad ? (s === 'REJECTED' ? <X /> : <LogOut />) : done || (isCurrent && s === 'SELECTED') ? <Check /> : null}
            </span>
            <span className={styles.text}>{STATUS_LABELS[s]}</span>
          </li>
        );
      })}
    </ol>
  );
}

export function Timeline({ entries }: { entries: HistoryEntry[] }) {
  if (entries.length === 0) return <p className="muted small">No status changes recorded yet.</p>;
  return (
    <ol className={styles.timeline}>
      {entries.map((e, i) => (
        <li key={`${e.changedAt}-${i}`} className={styles.event}>
          <span className={styles.marker} aria-hidden="true" />
          <div>
            <div className={styles.eventTitle}>
              {e.fromStatus ? `${STATUS_LABELS[e.fromStatus]} → ${STATUS_LABELS[e.toStatus]}` : `${STATUS_LABELS[e.toStatus]}`}
              {e.actorType === 'SYSTEM' && <span className={styles.system}>system</span>}
            </div>
            <div className="small muted">{formatDateTime(e.changedAt)}</div>
            {e.reason && <div className="small">{e.reason}</div>}
          </div>
        </li>
      ))}
    </ol>
  );
}
