import { useState, type FormEvent } from 'react';
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

export function AdminJobs() {
  const [status, setStatus] = useState<JobStatus | ''>('');
  const [qDraft, setQDraft] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const jobs = useAsync(() => jobApi.adminList({ status, q, page, size: 20 }), [status, q, page]);
  const [closing, setClosing] = useState<Job | null>(null);
  const [busy, setBusy] = useState(false);

  const close = async () => {
    if (!closing) return;
    setBusy(true);
    try {
      await jobApi.close(closing.id);
      emitToast({ kind: 'success', message: `${closing.title} closed.` });
      setClosing(null);
      jobs.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusy(false);
    }
  };

  const search = (e: FormEvent) => {
    e.preventDefault();
    setQ(qDraft);
    setPage(0);
  };

  return (
    <div>
      <div className="page-header"><h1>All jobs</h1></div>
      <form className="filters" onSubmit={search} role="search">
        <div className="field">
          <label htmlFor="q">Search</label>
          <input id="q" value={qDraft} onChange={(e) => setQDraft(e.target.value)} placeholder="Title, description or company" />
        </div>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as JobStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {JOB_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
        <button type="submit" className="btn btn-primary">Search</button>
      </form>
      <AsyncView state={jobs} empty={(p) => (p.content.length === 0 ? <EmptyState title="No jobs match" /> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th scope="col">Title</th><th scope="col">Company</th><th scope="col">Status</th><th scope="col">Deadline</th><th scope="col">Actions</th></tr>
                </thead>
                <tbody>
                  {p.content.map((j) => (
                    <tr key={j.id}>
                      <td>{j.title}</td>
                      <td>{j.companyName}</td>
                      <td><StatusBadge status={j.status} /></td>
                      <td>{formatDateTime(j.deadline)}</td>
                      <td>
                        {j.status !== 'CLOSED' && (
                          <button type="button" className="btn btn-danger btn-sm" onClick={() => setClosing(j)}>Close</button>
                        )}
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
      <ConfirmDialog open={!!closing} title="Close this job?" danger busy={busy}
        message={closing ? `${closing.title} (${closing.companyName}) will stop accepting applications.` : ''}
        confirmLabel="Close job" onConfirm={close} onCancel={() => setClosing(null)} />
    </div>
  );
}
