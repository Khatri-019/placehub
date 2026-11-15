import { userApi } from '../../api/endpoints';
import { ResumeCard } from '../../components/ResumeCard';
import { AsyncView } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useAsync } from '../../hooks/useAsync';
import { BRANCH_LABELS } from '../../types/enums';
import { formatCgpa, formatDate } from '../../utils/format';

export function StudentProfile() {
  const me = useAsync(() => userApi.me(), []);
  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Academic profile</h1>
          <p className="muted">Read-only. Contact your placement office to correct academic data.</p>
        </div>
      </div>
      <AsyncView state={me}>
        {(u) => (
          <section className="card">
            <dl className="details">
              <dt>Name</dt><dd>{u.fullName}</dd>
              <dt>Email</dt><dd>{u.email}</dd>
              <dt>Account</dt><dd><StatusBadge status={u.status} /></dd>
              <dt>College</dt><dd>{u.studentDetails?.collegeName ?? '—'}</dd>
              <dt>Branch</dt><dd>{u.studentDetails ? `${BRANCH_LABELS[u.studentDetails.branch]} (${u.studentDetails.branch})` : '—'}</dd>
              <dt>Graduation year</dt><dd>{u.studentDetails?.graduationYear ?? '—'}</dd>
              <dt>CGPA</dt><dd>{formatCgpa(u.studentDetails?.cgpa)}</dd>
              <dt>Active backlogs</dt><dd>{u.studentDetails?.backlogCount ?? '—'}</dd>
              <dt>Member since</dt><dd>{formatDate(u.createdAt)}</dd>
            </dl>
          </section>
        )}
      </AsyncView>
      <div style={{ marginTop: 'var(--space-5)' }}>
        <ResumeCard />
      </div>
    </div>
  );
}
