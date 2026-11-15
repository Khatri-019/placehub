import type { ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './Card.module.css';

export function Card({ title, actions, children, className = '' }: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className={styles.head}>
          {title && <h2 className={styles.title}>{title}</h2>}
          {actions && <div className="button-row">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/** Dashboard metric. When `to` is given the whole card is a link. */
export function StatCard({ label, value, icon, hint, to, accent = 'orange' }: {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  hint?: ReactNode;
  to?: string;
  accent?: 'orange' | 'raspberry' | 'navy' | 'green';
}) {
  const body = (
    <>
      <div className={styles.statTop}>
        <span className="label">{label}</span>
        {icon && <span className={`${styles.statIcon} ${styles[accent]}`} aria-hidden="true">{icon}</span>}
      </div>
      <div className={`${styles.statValue} num`}>{value}</div>
      {hint && <div className="small muted">{hint}</div>}
    </>
  );
  return to ? <Link to={to} className={`card ${styles.stat} ${styles.statLink}`}>{body}</Link>
    : <div className={`card ${styles.stat}`}>{body}</div>;
}
