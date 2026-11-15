import { Link } from 'react-router';
import { applicationApi, notificationApi } from '../../api/endpoints';
import { useAuth } from '../../auth/useAuth';
import { AsyncView, EmptyState } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { STATUS_LABELS } from '../../types/enums';
import { formatDateTime } from '../../utils/format';

const CARDS: ApplicationStatus[] = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'SELECTED'];

export function StudentDashboard() {
  const { user } = useAuth();
  const stats = useAsync(() => applicationApi.myStats(), []);
  const recent = useAsync(() => notificationApi.list({ page: 0, size: 5 }), []);

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Welcome, {user?.fullName}</h1>
          <p className="muted">Here is how your applications are progressing.</p>
        </div>
        <Link to="/student/jobs" className="btn btn-primary">Browse jobs</Link>
      </div>
      <AsyncView state={stats}>
        {(s) => (
          <div className="grid-cards">
            {CARDS.map((status) => (
              <div key={status} className="card stat-card">
                <div className="stat-value">{s.byStatus[status] ?? 0}</div>
                <div className="stat-label">{STATUS_LABELS[status]}</div>
              </div>
            ))}
          </div>
        )}
      </AsyncView>
      <section className="card">
        <div className="page-header">
          <h2>Recent notifications</h2>
          <Link to="/notifications" className="small">View all</Link>
        </div>
        <AsyncView state={recent} empty={(p) => (p.content.length === 0 ? <EmptyState title="No notifications yet" /> : null)}>
          {(p) => (
            <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {p.content.map((n) => (
                <li key={n.id}>
                  <strong>{n.title}</strong>
                  <div className="small muted">{formatDateTime(n.createdAt)}</div>
                </li>
              ))}
            </ul>
          )}
        </AsyncView>
      </section>
    </div>
  );
}
