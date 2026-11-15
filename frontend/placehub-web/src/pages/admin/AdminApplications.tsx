import { useState } from 'react';
import { applicationApi } from '../../api/endpoints';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS } from '../../types/enums';
import { formatCgpa, formatDateTime } from '../../utils/format';

export function AdminApplications() {
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [page, setPage] = useState(0);
  const apps = useAsync(() => applicationApi.adminList({ status, page, size: 20 }), [status, page]);

  return (
    <div>
      <div className="page-header">
        <h1>All applications</h1>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as ApplicationStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </div>
      <AsyncView state={apps} empty={(p) => (p.content.length === 0 ? <EmptyState title="No applications" /> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th scope="col">Student</th><th scope="col">Job</th><th scope="col">Company</th><th scope="col">CGPA</th><th scope="col">Status</th><th scope="col">Applied</th></tr>
                </thead>
                <tbody>
                  {p.content.map((a) => (
                    <tr key={a.id}>
                      <td>{a.studentName}<div className="small muted">{a.studentEmail}</div></td>
                      <td>{a.jobTitle}</td>
                      <td>{a.companyName}</td>
                      <td>{formatCgpa(a.cgpa)}</td>
                      <td><StatusBadge status={a.status} /></td>
                      <td>{formatDateTime(a.appliedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
    </div>
  );
}
