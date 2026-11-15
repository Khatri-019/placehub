import { Lock } from 'lucide-react';
import { applicationApi, userApi } from '../../api/endpoints';
import { ResumeCard } from '../../components/ResumeCard';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { BRANCH_LABELS } from '../../types/enums';
import { Badge, StatusBadge } from '../../ui/Badge';
import { Card } from '../../ui/Card';
import { Skeleton } from '../../ui/Skeleton';
import { formatCgpa, formatDate } from '../../utils/format';
import { money } from '../../utils/policy';

export function StudentProfile() {
  const me = useAsync(() => userApi.me(), []);
  const placement = useAsync(() => applicationApi.placementState(), []);
  return (
    <div className="two-col">
      <div className="stack">
        <Card title="Academic profile" actions={<span className="small muted">Read-only · contact the placement office to correct it</span>}>
          <AsyncView state={me} skeleton={<Skeleton lines={6} />}>
            {(u) => (
              <dl className="details">
                <dt>Name</dt><dd>{u.fullName}</dd>
                <dt>Email</dt><dd>{u.email}</dd>
                <dt>Account</dt><dd><StatusBadge status={u.status} /></dd>
                <dt>College</dt><dd>{u.studentDetails?.collegeName ?? '—'}</dd>
                <dt>Branch</dt><dd>{u.studentDetails ? `${BRANCH_LABELS[u.studentDetails.branch]} (${u.studentDetails.branch})` : '—'}</dd>
                <dt>Graduation year</dt><dd className="num">{u.studentDetails?.graduationYear ?? '—'}</dd>
                <dt>CGPA</dt><dd className="num">{formatCgpa(u.studentDetails?.cgpa)}</dd>
                <dt>Active backlogs</dt><dd className="num">{u.studentDetails?.backlogCount ?? '—'}</dd>
                <dt>Member since</dt><dd>{formatDate(u.createdAt)}</dd>
              </dl>
            )}
          </AsyncView>
        </Card>
        <ResumeCard />
      </div>
      <Card title="Placement status">
        <AsyncView state={placement} skeleton={<Skeleton lines={4} />}>
          {(p) => (
            <div className="stack">
              <div className="row">
                {p.locked ? <Badge tone="danger" icon={<Lock aria-hidden="true" />}>Locked</Badge>
                  : p.placed ? <Badge tone="success">Placed</Badge> : <Badge tone="info">Not placed</Badge>}
                {p.placed && <span className="num">Current CTC {money(p.currentCtc)} LPA</span>}
              </div>
              <p style={{ margin: 0 }}>{p.summary}</p>
              {p.lockReason && <p className="small muted" style={{ margin: 0 }}>Reason: {p.lockReason}</p>}
              {p.bands.length > 0 && (
                <ul className="reasons small">{p.bands.map((b) => <li key={b.fromCtc}>{b.description}</li>)}</ul>
              )}
              {p.placements.length > 0 && (
                <div>
                  <h3 className="label">Placements</h3>
                  <ul className="stack-sm" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                    {p.placements.map((pl) => (
                      <li key={pl.id} className="row-between small">
                        <span>{pl.companyName} <span className="muted">· {pl.source === 'OFF_CAMPUS' ? 'off-campus' : 'on campus'}</span></span>
                        <span className="num">{money(pl.ctcLpa)} LPA</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="small muted" style={{ margin: 0 }}>Placement policy v{p.policyVersion}, effective {formatDate(p.policyEffectiveFrom)}</p>
            </div>
          )}
        </AsyncView>
      </Card>
    </div>
  );
}
