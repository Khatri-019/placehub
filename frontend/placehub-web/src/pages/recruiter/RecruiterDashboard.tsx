import { Link } from 'react-router';
import { applicationApi, companyApi, jobApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { JobStatus } from '../../types';
import { APPLICATION_STATUSES, JOB_STATUSES, STATUS_LABELS } from '../../types/enums';

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

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Recruiter dashboard</h1>
          {company.data && <p className="muted">{company.data.name}</p>}
        </div>
        <Link to="/recruiter/jobs/new" className="btn btn-primary">Post a job</Link>
      </div>
      {company.data === null && (
        <div className="banner" role="status">
          You need a company profile before posting jobs. <Link to="/recruiter/company">Set up your company</Link>.
        </div>
      )}
      <section>
        <h2>Jobs by status</h2>
        <AsyncView state={jobCounts}>
          {(counts) => (
            <div className="grid-cards">
              {JOB_STATUSES.map((s) => (
                <Link key={s} to={`/recruiter/jobs?status=${s}`} className="card stat-card" style={{ textDecoration: 'none', color: 'inherit' }}>
                  <div className="stat-value">{counts[s]}</div>
                  <div className="stat-label">{STATUS_LABELS[s]}</div>
                </Link>
              ))}
            </div>
          )}
        </AsyncView>
      </section>
      <section>
        <h2>Applicants by status</h2>
        <AsyncView state={appStats}>
          {(stats) => (
            <div className="grid-cards">
              {APPLICATION_STATUSES.map((s) => (
                <div key={s} className="card stat-card">
                  <div className="stat-value">{stats.byStatus[s] ?? 0}</div>
                  <div className="stat-label">{STATUS_LABELS[s]}</div>
                </div>
              ))}
            </div>
          )}
        </AsyncView>
      </section>
    </div>
  );
}
