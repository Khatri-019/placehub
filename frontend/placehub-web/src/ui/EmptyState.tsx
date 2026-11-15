import type { ReactNode } from 'react';
import styles from './EmptyState.module.css';

export type Illustration = 'inbox' | 'search' | 'briefcase' | 'bell';

/** Small inline SVG illustrations (no image requests), drawn with currentColor and tokens. */
function Art({ kind }: { kind: Illustration }) {
  const common = { width: 96, height: 72, viewBox: '0 0 96 72', fill: 'none', 'aria-hidden': true } as const;
  switch (kind) {
    case 'search':
      return (
        <svg {...common}>
          <rect x="8" y="14" width="56" height="44" rx="8" className={styles.paper} />
          <rect x="16" y="24" width="30" height="5" rx="2.5" className={styles.line} />
          <rect x="16" y="34" width="22" height="5" rx="2.5" className={styles.line} />
          <circle cx="66" cy="44" r="14" className={styles.accentStroke} strokeWidth="5" />
          <path d="M76 54l10 10" className={styles.accentStroke} strokeWidth="6" strokeLinecap="round" />
        </svg>
      );
    case 'briefcase':
      return (
        <svg {...common}>
          <rect x="14" y="22" width="68" height="40" rx="8" className={styles.paper} />
          <path d="M36 22v-6a4 4 0 014-4h16a4 4 0 014 4v6" className={styles.lineStroke} strokeWidth="4" />
          <rect x="14" y="36" width="68" height="6" className={styles.accent} />
        </svg>
      );
    case 'bell':
      return (
        <svg {...common}>
          <path d="M48 12c-11 0-18 8-18 19v11l-6 8h48l-6-8V31c0-11-7-19-18-19z" className={styles.paper} />
          <circle cx="48" cy="58" r="6" className={styles.accent} />
          <circle cx="66" cy="18" r="6" className={styles.accent} />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <path d="M12 40l12-24h48l12 24v18a6 6 0 01-6 6H18a6 6 0 01-6-6V40z" className={styles.paper} />
          <path d="M12 40h22l4 8h20l4-8h22" className={styles.lineStroke} strokeWidth="4" strokeLinejoin="round" />
          <rect x="38" y="24" width="20" height="5" rx="2.5" className={styles.accent} />
        </svg>
      );
  }
}

export function EmptyState({ title, children, illustration = 'inbox' }: {
  title: string;
  children?: ReactNode;
  illustration?: Illustration;
}) {
  return (
    <div className={styles.empty}>
      <Art kind={illustration} />
      <p className={styles.title}>{title}</p>
      {children && <div className={styles.body}>{children}</div>}
    </div>
  );
}
