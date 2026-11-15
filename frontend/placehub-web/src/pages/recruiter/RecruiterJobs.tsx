import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Plus } from 'lucide-react';
import { jobApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Pagination } from '../../components/Pagination';
import { AsyncView } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Job, JobStatus } from '../../types';
import { EMPLOYMENT_TYPE_LABELS, JOB_STATUSES, STATUS_LABELS } from '../../types/enums';
import { Badge, StatusBadge } from '../../ui/Badge';
import { DataTable, type Column } from '../../ui/DataTable';
import { EmptyState } from '../../ui/EmptyState';
import { ConfirmDialog } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';
import { money } from '../../utils/policy';

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

  const columns: Column<Job>[] = [
    { key: 'title', header: 'Title', render: (j) => (
      <div>
        <span style={{ fontWeight: 600 }}>{j.title}</span>
        <div className="small muted">{EMPLOYMENT_TYPE_LABELS[j.employmentType]} · {j.location}</div>
      </div>
    ) },
    { key: 'ctc', header: 'CTC (LPA)', align: 'right', render: (j) => <span className="num">{money(j.ctcLpa)}</span> },
    { key: 'status', header: 'Status', render: (j) => (
      <div className="row" style={{ gap: 6 }}>
        <StatusBadge status={j.status} />
        {j.screeningMode === 'AUTO_SHORTLIST' && <Badge tone="brand">Auto-shortlist</Badge>}
      </div>
    ) },
    { key: 'deadline', header: 'Deadline', render: (j) => formatDateTime(j.deadline) },
    { key: 'actions', header: 'Actions', hideLabelOnMobile: true, render: (job) => (
      <div className="button-row">
        <Link className="btn btn-secondary btn-sm" to={`/recruiter/jobs/${job.id}/applicants`}>Applicants</Link>
        {job.status !== 'CLOSED' && <Link className="btn btn-ghost btn-sm" to={`/recruiter/jobs/${job.id}/edit`}>Edit</Link>}
        {job.status === 'DRAFT' && (
          <button type="button" className="btn btn-primary btn-sm" disabled={busyId === job.id} onClick={() => run(job, 'publish')}>Publish</button>
        )}
        {job.status !== 'CLOSED' && (
          <button type="button" className="btn btn-danger btn-sm" disabled={busyId === job.id} onClick={() => setClosing(job)}>Close</button>
        )}
      </div>
    ) },
  ];

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>My jobs</h1>
          <p className="muted" style={{ margin: 0 }}>Drafts are hidden from students until you publish them.</p>
        </div>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <div className="field" style={{ minWidth: 160 }}>
            <label htmlFor="status">Status</label>
            <select id="status" value={status} onChange={(e) => { setParams(e.target.value ? { status: e.target.value } : {}); setPage(0); }}>
              <option value="">All</option>
              {JOB_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
            </select>
          </div>
          <Link to="/recruiter/jobs/new" className="btn btn-primary"><Plus size={16} aria-hidden="true" />Post a job</Link>
        </div>
      </div>
      <AsyncView state={jobs} skeleton={<Skeleton lines={6} card />}
        empty={(p) => (p.content.length === 0 ? (
          <EmptyState title="No jobs yet" illustration="briefcase"><Link to="/recruiter/jobs/new">Post your first job</Link></EmptyState>
        ) : null)}>
        {(p) => (
          <>
            <DataTable caption="My jobs" columns={columns} rows={p.content} rowKey={(j) => j.id} />
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
