import { Link } from 'react-router';
import { Briefcase, CheckCircle2, FileEdit, Inbox, Plus, Star, Users } from 'lucide-react';
import { applicationApi, companyApi, jobApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { JobStatus } from '../../types';
import { JOB_STATUSES } from '../../types/enums';
import { StatusBadge } from '../../ui/Badge';
import { Card, StatCard } from '../../ui/Card';
import { EmptyState } from '../../ui/EmptyState';
import { SkeletonCards, Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';

/** Resolves to null when the recruiter has no company yet (404), so the page can show the setup banner. */
async function loadCompany() {
  try {
    return await companyApi.mine();
  } catch (err) {
    if (parseError(err).status === 404) return null;
    throw err;
  }
}

async function loadJobCounts(): Promise<Record<JobStatus, number>> {
  const pages = await Promise.all(JOB_STATUSES.map((status) => jobApi.mine({ status, page: 0, size: 1 })));
  return Object.fromEntries(JOB_STATUSES.map((s, i) => [s, pages[i].totalElements])) as Record<JobStatus, number>;
}

export function RecruiterDashboard() {
  const company = useAsync(loadCompany, []);
  const jobCounts = useAsync(loadJobCounts, []);
  const appStats = useAsync(() => applicationApi.recruiterStats(), []);
  const recent = useAsync(() => jobApi.mine({ page: 0, size: 5 }), []);

  return (
    <div className="stack-lg">
      <div className="page-header">
        <div>
          <h1>Recruiter dashboard</h1>
          {company.data && <p className="muted" style={{ margin: 0 }}>{company.data.name}</p>}
        </div>
        <Link to="/recruiter/jobs/new" className="btn btn-primary"><Plus size={16} aria-hidden="true" />Post a job</Link>
      </div>
      {company.data === null && (
        <div className="banner" role="status">
          <Briefcase aria-hidden="true" />
          <span>You need a company profile before posting jobs. <Link to="/recruiter/company">Set up your company</Link>.</span>
        </div>
      )}
      <section aria-labelledby="jobs-heading" className="stack-sm">
        <h2 id="jobs-heading">Jobs</h2>
        <AsyncView state={jobCounts} skeleton={<SkeletonCards count={3} />}>
          {(counts) => (
            <div className="grid-cards">
              <StatCard label="Published" value={counts.PUBLISHED} icon={<CheckCircle2 />} accent="green" to="/recruiter/jobs?status=PUBLISHED" />
              <StatCard label="Drafts" value={counts.DRAFT} icon={<FileEdit />} accent="navy" to="/recruiter/jobs?status=DRAFT" />
              <StatCard label="Closed" value={counts.CLOSED} icon={<Briefcase />} accent="raspberry" to="/recruiter/jobs?status=CLOSED" />
            </div>
          )}
        </AsyncView>
      </section>
      <section aria-labelledby="apps-heading" className="stack-sm">
        <h2 id="apps-heading">Applicants</h2>
        <AsyncView state={appStats} skeleton={<SkeletonCards count={4} />}>
          {(stats) => (
            <div className="grid-cards">
              <StatCard label="Total" value={stats.total} icon={<Users />} accent="navy" />
              <StatCard label="New" value={stats.byStatus.APPLIED ?? 0} icon={<Inbox />} accent="orange" hint="Waiting for review" />
              <StatCard label="Shortlisted" value={stats.byStatus.SHORTLISTED ?? 0} icon={<Star />} accent="raspberry" />
              <StatCard label="Selected" value={stats.byStatus.SELECTED ?? 0} icon={<CheckCircle2 />} accent="green" />
            </div>
          )}
        </AsyncView>
      </section>
      <Card title="Recent jobs" actions={<Link to="/recruiter/jobs" className="small">View all</Link>}>
        <AsyncView state={recent} skeleton={<Skeleton lines={4} />}
          empty={(p) => (p.content.length === 0 ? (
            <EmptyState title="No jobs yet" illustration="briefcase"><Link to="/recruiter/jobs/new">Post your first job</Link></EmptyState>
          ) : null)}>
          {(p) => (
            <ul className="stack-sm" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {p.content.map((j) => (
                <li key={j.id} className="row-between">
                  <span>
                    <Link to={`/recruiter/jobs/${j.id}/applicants`} style={{ fontWeight: 600 }}>{j.title}</Link>
                    <span className="small muted"> · closes {formatDateTime(j.deadline)}</span>
                  </span>
                  <StatusBadge status={j.status} />
                </li>
              ))}
            </ul>
          )}
        </AsyncView>
      </Card>
    </div>
  );
}
