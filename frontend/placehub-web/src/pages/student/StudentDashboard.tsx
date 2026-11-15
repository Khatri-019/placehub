import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { CheckCircle2, CircleDot, Clock3, Lock, Search, Star } from 'lucide-react';
import { applicationApi, notificationApi } from '../../api/endpoints';
import { useAuth } from '../../auth/useAuth';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS } from '../../types/enums';
import { StatCard } from '../../ui/Card';
import { EmptyState } from '../../ui/EmptyState';
import { SkeletonCards, Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';
import styles from './StudentDashboard.module.css';

const CARDS: { status: ApplicationStatus; icon: ReactNode; accent: 'orange' | 'raspberry' | 'navy' | 'green' }[] = [
  { status: 'APPLIED', icon: <CircleDot />, accent: 'navy' },
  { status: 'UNDER_REVIEW', icon: <Clock3 />, accent: 'orange' },
  { status: 'SHORTLISTED', icon: <Star />, accent: 'raspberry' },
  { status: 'SELECTED', icon: <CheckCircle2 />, accent: 'green' },
];

export function StudentDashboard() {
  const { user } = useAuth();
  const stats = useAsync(() => applicationApi.myStats(), []);
  const recent = useAsync(() => notificationApi.list({ page: 0, size: 5 }), []);
  const placement = useAsync(() => applicationApi.placementState(), []);

  return (
    <div className="stack-lg">
      <section className={styles.ribbon}>
        <div>
          <p className={styles.kicker}>Welcome back</p>
          <h1 className={styles.hello}>Welcome, {user?.fullName}</h1>
          <p className={styles.sub}>Here is how your applications are progressing.</p>
        </div>
        <Link to="/student/jobs" className={`btn ${styles.cta}`}><Search aria-hidden="true" /> Browse jobs</Link>
      </section>

      <section id="placement-state" aria-labelledby="placement-title">
        <h2 id="placement-title" className="visually-hidden">Placement status</h2>
        <AsyncView state={placement} skeleton={<Skeleton lines={2} card />}>
          {(p) => (
            <div className={`banner ${p.locked ? 'banner-danger' : p.placed ? '' : 'banner-info'}`} role="status">
              {p.locked && <Lock aria-hidden="true" />}
              <div className="stack-sm">
                <strong>{p.summary}</strong>
                {p.placed && p.bands.length > 0 && (
                  <ul className="reasons small">{p.bands.map((b) => <li key={b.fromCtc}>{b.description}</li>)}</ul>
                )}
                <span className="small">Placement policy v{p.policyVersion}</span>
              </div>
            </div>
          )}
        </AsyncView>
      </section>

      <AsyncView state={stats} skeleton={<SkeletonCards count={4} />}>
        {(s) => (
          <>
            <div className="grid-cards">
              {CARDS.map((c) => (
                <StatCard key={c.status} label={STATUS_LABELS[c.status]} value={s.byStatus[c.status] ?? 0}
                  icon={c.icon} accent={c.accent} to={`/student/applications?status=${c.status}`} />
              ))}
            </div>
            <section className="card" aria-labelledby="pipeline-title">
              <h2 id="pipeline-title">Application pipeline</h2>
              {s.total === 0 ? <p className="muted">No applications yet.</p> : (
                <div className={styles.pipeline} role="img"
                  aria-label={APPLICATION_STATUSES.map((st) => `${STATUS_LABELS[st]} ${s.byStatus[st] ?? 0}`).join(', ')}>
                  {APPLICATION_STATUSES.filter((st) => (s.byStatus[st] ?? 0) > 0).map((st) => (
                    <span key={st} className={`${styles.segment} ${styles[st.toLowerCase()]}`}
                      style={{ flexGrow: s.byStatus[st] ?? 0 }} title={`${STATUS_LABELS[st]}: ${s.byStatus[st]}`} />
                  ))}
                </div>
              )}
              <ul className={styles.legend}>
                {APPLICATION_STATUSES.map((st) => (
                  <li key={st}><span className={`${styles.swatch} ${styles[st.toLowerCase()]}`} aria-hidden="true" />
                    {STATUS_LABELS[st]} <span className="num muted">{s.byStatus[st] ?? 0}</span></li>
                ))}
              </ul>
            </section>
          </>
        )}
      </AsyncView>

      <section className="card" aria-labelledby="activity-title">
        <div className="row-between" style={{ marginBottom: 'var(--space-3)' }}>
          <h2 id="activity-title" style={{ margin: 0 }}>Recent activity</h2>
          <Link to="/notifications" className="small">View all</Link>
        </div>
        <AsyncView state={recent} skeleton={<Skeleton lines={4} />}
          empty={(p) => (p.content.length === 0 ? <EmptyState title="No notifications yet" illustration="bell" /> : null)}>
          {(p) => (
            <ul className={styles.activity}>
              {p.content.map((n) => (
                <li key={n.id}>
                  <span className={styles.activityDot} aria-hidden="true" />
                  <div>
                    <div className={styles.activityTitle}>{n.title}</div>
                    <div className="small muted">{formatDateTime(n.createdAt)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </AsyncView>
      </section>
    </div>
  );
}
