import { Link } from 'react-router';
import { applicationApi, companyApi, jobApi, userApi } from '../../api/endpoints';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';

async function loadTotals() {
  const [users, students, recruiters, jobs, published, companies, applications] = await Promise.all([
    userApi.adminList({ size: 1 }),
    userApi.adminList({ role: 'STUDENT', size: 1 }),
    userApi.adminList({ role: 'RECRUITER', size: 1 }),
    jobApi.adminList({ size: 1 }),
    jobApi.adminList({ status: 'PUBLISHED', size: 1 }),
    companyApi.adminList({ size: 1 }),
    applicationApi.adminList({ size: 1 }),
  ]);
  return [
    { label: 'Users', value: users.totalElements, to: '/admin/users' },
    { label: 'Students', value: students.totalElements, to: '/admin/users' },
    { label: 'Recruiters', value: recruiters.totalElements, to: '/admin/users' },
    { label: 'Companies', value: companies.totalElements, to: '/admin/jobs' },
    { label: 'Jobs', value: jobs.totalElements, to: '/admin/jobs' },
    { label: 'Published jobs', value: published.totalElements, to: '/admin/jobs' },
    { label: 'Applications', value: applications.totalElements, to: '/admin/applications' },
  ];
}

export function AdminDashboard() {
  const totals = useAsync(loadTotals, []);
  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Placement office dashboard</h1>
          <p className="muted">Platform-wide totals.</p>
        </div>
      </div>
      <AsyncView state={totals}>
        {(items) => (
          <div className="grid-cards">
            {items.map((t) => (
              <Link key={t.label} to={t.to} className="card stat-card" style={{ textDecoration: 'none', color: 'inherit' }}>
                <div className="stat-value">{t.value}</div>
                <div className="stat-label">{t.label}</div>
              </Link>
            ))}
          </div>
        )}
      </AsyncView>
    </div>
  );
}
