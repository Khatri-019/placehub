import styles from './Skeleton.module.css';

/**
 * Loading placeholder with fixed dimensions, so the content that replaces it does not shift the layout (CLS).
 */
export function Skeleton({ lines = 3, height = 14, card = false, label = 'Loading' }: {
  lines?: number;
  height?: number;
  card?: boolean;
  label?: string;
}) {
  return (
    <div className={card ? `card ${styles.wrap}` : styles.wrap} role="status" aria-live="polite" aria-busy="true">
      <span className="visually-hidden">{label}…</span>
      {Array.from({ length: lines }, (_, i) => (
        <span key={i} className={styles.line} style={{ height, width: `${i === lines - 1 ? 60 : 100 - i * 6}%` }} />
      ))}
    </div>
  );
}

export function SkeletonCards({ count = 4, height = 118 }: { count?: number; height?: number }) {
  return (
    <div className="grid-cards" role="status" aria-busy="true">
      <span className="visually-hidden">Loading…</span>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className={`card ${styles.block}`} style={{ height }} />
      ))}
    </div>
  );
}
