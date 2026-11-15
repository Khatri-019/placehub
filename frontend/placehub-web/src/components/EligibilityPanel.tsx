import type { ParsedError } from '../api/errors';
import type { EligibilityPreview } from '../types';
import styles from './EligibilityPanel.module.css';

interface Props {
  preview: EligibilityPreview | undefined;
  loading: boolean;
  error: ParsedError | null;
  onRetry?: () => void;
}

/** Shows whether the student can apply, listing every failing rule returned by Job Service. */
export function EligibilityPanel({ preview, loading, error, onRetry }: Props) {
  if (loading && !preview) {
    return <section className={styles.panel} aria-busy="true"><p>Checking your eligibility…</p></section>;
  }
  if (error) {
    return (
      <section className={`${styles.panel} ${styles.warn}`} role="alert">
        <h2 className={styles.title}>Eligibility unavailable</h2>
        <p>{error.message}</p>
        {onRetry && <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>Retry</button>}
      </section>
    );
  }
  if (!preview) return null;
  if (preview.alreadyApplied) {
    return (
      <section className={`${styles.panel} ${styles.info}`}>
        <h2 className={styles.title}>You have already applied</h2>
        <p>Track progress on your applications page. Re-applying after withdrawal is not supported.</p>
      </section>
    );
  }
  if (preview.eligible) {
    return (
      <section className={`${styles.panel} ${styles.ok}`}>
        <h2 className={styles.title}>You are eligible</h2>
        <p>Your academic profile meets every requirement for this job.</p>
      </section>
    );
  }
  return (
    <section className={`${styles.panel} ${styles.bad}`} aria-labelledby="eligibility-title">
      <h2 id="eligibility-title" className={styles.title}>You are not eligible</h2>
      <ul className={styles.reasons}>
        {preview.reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </section>
  );
}
