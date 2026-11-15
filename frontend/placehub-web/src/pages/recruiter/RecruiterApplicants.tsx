import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { applicationApi, jobApi } from '../../api/endpoints';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS } from '../../types/enums';
import { formatCgpa, formatDateTime } from '../../utils/format';

export function RecruiterApplicants() {
  const { id = '' } = useParams();
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [page, setPage] = useState(0);
  const job = useAsync(() => jobApi.get(id), [id]);
  const applicants = useAsync(() => applicationApi.forJob(id, { status, page, size: 20 }), [id, status, page]);

  return (
    <div>
      <p className="small"><Link to="/recruiter/jobs">← Back to my jobs</Link></p>
      <div className="page-header">
        <div>
          <h1>Applicants</h1>
          {job.data && <p className="muted">{job.data.title} · <StatusBadge status={job.data.status} /></p>}
        </div>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as ApplicationStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </div>
      <AsyncView state={applicants} empty={(p) => (p.content.length === 0 ? <EmptyState title="No applicants yet" /> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th scope="col">Student</th><th scope="col">Branch</th><th scope="col">CGPA</th><th scope="col">Backlogs</th><th scope="col">Status</th><th scope="col">Applied</th><th scope="col"><span className="muted">Review</span></th></tr>
                </thead>
                <tbody>
                  {p.content.map((a) => (
                    <tr key={a.id}>
                      <td>{a.studentName}<div className="small muted">{a.studentEmail}</div></td>
                      <td>{a.branch}</td>
                      <td>{formatCgpa(a.cgpa)}</td>
                      <td>{a.backlogCount}</td>
                      <td><StatusBadge status={a.status} /></td>
                      <td>{formatDateTime(a.appliedAt)}</td>
                      <td><Link className="btn btn-secondary btn-sm" to={`/recruiter/applications/${a.id}`}>Review</Link></td>
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
