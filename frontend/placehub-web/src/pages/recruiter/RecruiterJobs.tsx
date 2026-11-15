import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { jobApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Job, JobStatus } from '../../types';
import { JOB_STATUSES, STATUS_LABELS } from '../../types/enums';
import { formatDateTime } from '../../utils/format';

export function RecruiterJobs() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as JobStatus | '';
  const [page, setPage] = useState(0);
  const jobs = useAsync(() => jobApi.mine({ status, page, size: 20 }), [status, page]);
  const [closing, setClosing] = useState<Job | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const run = async (job: Job, action: 'publish' | 'close') => {
    setBusyId(job.id);
    try {
      await (action === 'publish' ? jobApi.publish(job.id) : jobApi.close(job.id));
      emitToast({ kind: 'success', message: `${job.title} ${action === 'publish' ? 'published' : 'closed'}.` });
      setClosing(null);
      jobs.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>My jobs</h1>
          <p className="muted">Drafts are hidden from students until you publish them.</p>
        </div>
        <div className="button-row" style={{ alignItems: 'flex-end' }}>
          <div className="field">
            <label htmlFor="status">Status</label>
            <select id="status" value={status} onChange={(e) => { setParams(e.target.value ? { status: e.target.value } : {}); setPage(0); }}>
              <option value="">All</option>
              {JOB_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
            </select>
          </div>
          <Link to="/recruiter/jobs/new" className="btn btn-primary">Post a job</Link>
        </div>
      </div>
      <AsyncView state={jobs} empty={(p) => (p.content.length === 0 ? <EmptyState title="No jobs yet"><Link to="/recruiter/jobs/new">Post your first job</Link></EmptyState> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th scope="col">Title</th><th scope="col">Status</th><th scope="col">Deadline</th><th scope="col">Location</th><th scope="col">Actions</th></tr>
                </thead>
                <tbody>
                  {p.content.map((job) => (
                    <tr key={job.id}>
                      <td>{job.title}</td>
                      <td><StatusBadge status={job.status} /></td>
                      <td>{formatDateTime(job.deadline)}</td>
                      <td>{job.location}</td>
                      <td>
                        <div className="button-row">
                          <Link className="btn btn-secondary btn-sm" to={`/recruiter/jobs/${job.id}/applicants`}>Applicants</Link>
                          {job.status !== 'CLOSED' && (
                            <Link className="btn btn-secondary btn-sm" to={`/recruiter/jobs/${job.id}/edit`}>Edit</Link>
                          )}
                          {job.status === 'DRAFT' && (
                            <button type="button" className="btn btn-primary btn-sm" disabled={busyId === job.id}
                              onClick={() => run(job, 'publish')}>Publish</button>
                          )}
                          {job.status !== 'CLOSED' && (
                            <button type="button" className="btn btn-danger btn-sm" disabled={busyId === job.id}
                              onClick={() => setClosing(job)}>Close</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
      <ConfirmDialog open={!!closing} title="Close this job?" danger busy={!!busyId}
        message={closing ? `${closing.title} will stop accepting applications and become read-only.` : ''}
        confirmLabel="Close job" onConfirm={() => closing && run(closing, 'close')} onCancel={() => setClosing(null)} />
    </div>
  );
}
