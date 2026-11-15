import { Link } from 'react-router';
import { Briefcase, Building2, FileCheck2, GraduationCap, Lock, ScrollText, ShieldCheck, Users } from 'lucide-react';
import { applicationApi, companyApi, jobApi, placementApi, policyApi, userApi } from '../../api/endpoints';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { Badge } from '../../ui/Badge';
import { Card, StatCard } from '../../ui/Card';
import { Skeleton, SkeletonCards } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';
import { bandLabel } from '../../utils/policy';

async function loadTotals() {
  const [students, recruiters, companies, published, applications, placements, locked] = await Promise.all([
    userApi.adminList({ role: 'STUDENT', size: 1 }),
    userApi.adminList({ role: 'RECRUITER', size: 1 }),
    companyApi.adminList({ size: 1 }),
    jobApi.adminList({ status: 'PUBLISHED', size: 1 }),
    applicationApi.adminList({ size: 1 }),
    placementApi.list({ size: 1 }),
    placementApi.list({ locked: true, size: 1 }),
  ]);
  return {
    students: students.totalElements, recruiters: recruiters.totalElements, companies: companies.totalElements,
    published: published.totalElements, applications: applications.totalElements,
    placements: placements.totalElements, locked: locked.totalElements,
  };
}

const ACTION_TONE = { ALLOW: 'success', REQUIRE_CONSENT: 'warning', BLOCK: 'danger' } as const;

export function AdminDashboard() {
  const totals = useAsync(loadTotals, []);
  const policy = useAsync(() => policyApi.active(), []);
  return (
    <div className="stack-lg">
      <div className="page-header">
        <div>
          <h1>Placement office dashboard</h1>
          <p className="muted" style={{ margin: 0 }}>Platform-wide totals and the active placement policy.</p>
        </div>
      </div>
      <AsyncView state={totals} skeleton={<SkeletonCards count={7} />}>
        {(t) => (
          <div className="grid-cards">
            <StatCard label="Students" value={t.students} icon={<GraduationCap />} accent="navy" to="/admin/users" />
            <StatCard label="Recruiters" value={t.recruiters} icon={<Users />} accent="orange" to="/admin/users" />
            <StatCard label="Companies" value={t.companies} icon={<Building2 />} accent="raspberry" to="/admin/jobs" />
            <StatCard label="Published jobs" value={t.published} icon={<Briefcase />} accent="green" to="/admin/jobs" />
            <StatCard label="Applications" value={t.applications} icon={<FileCheck2 />} accent="navy" to="/admin/applications" />
            <StatCard label="Placements" value={t.placements} icon={<ShieldCheck />} accent="green" to="/admin/placements" />
            <StatCard label="Locked students" value={t.locked} icon={<Lock />} accent="raspberry" to="/admin/placements" />
          </div>
        )}
      </AsyncView>
      <Card title="Active placement policy" actions={<Link to="/admin/policy" className="small">Edit policy</Link>}>
        <AsyncView state={policy} skeleton={<Skeleton lines={4} />}>
          {(p) => (
            <div className="stack-sm">
              <p className="small muted row" style={{ margin: 0, gap: 6 }}>
                <ScrollText size={14} aria-hidden="true" />
                <span>Version {p.version} · effective {formatDateTime(p.effectiveFrom)} · {p.changeNote}</span>
              </p>
              <ul className="stack-sm" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {p.bands.map((b) => (
                  <li key={b.fromMultiplier} className="row">
                    <span className="num" style={{ minWidth: 140 }}>{bandLabel(b)}</span>
                    <Badge tone={ACTION_TONE[b.action]}>{b.action.replace('_', ' ')}</Badge>
                    {b.lockOnPlacement && <Badge tone="danger" icon={<Lock aria-hidden="true" />}>Locks</Badge>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </AsyncView>
      </Card>
    </div>
  );
}
