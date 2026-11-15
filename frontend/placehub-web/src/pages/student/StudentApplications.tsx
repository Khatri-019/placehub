import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { applicationApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Pagination } from '../../components/Pagination';
import { AsyncView } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Application, ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS, canWithdraw } from '../../types/enums';
import { StatusBadge } from '../../ui/Badge';
import { DataTable, type Column, type SortState } from '../../ui/DataTable';
import { EmptyState } from '../../ui/EmptyState';
import { ConfirmDialog, Modal } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { Stepper, Timeline } from '../../ui/Stepper';
import { formatDateTime } from '../../utils/format';
import { money } from '../../utils/policy';

function TrackDialog({ app, onClose }: { app: Application; onClose: () => void }) {
  const history = useAsync(() => applicationApi.history(app.id), [app.id]);
  return (
    <Modal open title={`${app.jobTitle} · ${app.companyName}`} onClose={onClose} size="lg">
      <Stepper status={app.status} reached={history.data?.map((h) => h.toStatus)} />
      <h3>Timeline</h3>
      <AsyncView state={history} skeleton={<Skeleton lines={3} />}>{(entries) => <Timeline entries={entries} />}</AsyncView>
    </Modal>
  );
}

export function StudentApplications() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as ApplicationStatus | '';
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: 'appliedAt', dir: 'desc' });
  const apps = useAsync(() => applicationApi.mine({ status, page, size: 20, sort: `${sort.field},${sort.dir}` }),
    [status, page, sort.field, sort.dir]);
  const [target, setTarget] = useState<Application | null>(null);
  const [tracking, setTracking] = useState<Application | null>(null);
  const [busy, setBusy] = useState(false);

  const withdraw = async () => {
    if (!target) return;
    setBusy(true);
    try {
      await applicationApi.withdraw(target.id);
      emitToast({ kind: 'success', message: `Withdrawn from ${target.jobTitle}.` });
      setTarget(null);
      apps.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusy(false);
    }
  };

  const columns: Column<Application>[] = [
    { key: 'job', header: 'Job', sortField: 'jobTitle', render: (a) => <span style={{ fontWeight: 600 }}>{a.jobTitle}</span> },
    { key: 'company', header: 'Company', render: (a) => a.companyName },
    { key: 'ctc', header: 'CTC (LPA)', align: 'right', render: (a) => <span className="num">{money(a.ctcLpa)}</span> },
    { key: 'status', header: 'Status', sortField: 'status', render: (a) => <StatusBadge status={a.status} /> },
    { key: 'applied', header: 'Applied', sortField: 'appliedAt', render: (a) => formatDateTime(a.appliedAt) },
    { key: 'actions', header: 'Actions', hideLabelOnMobile: true, render: (a) => (
      <div className="button-row">
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => setTracking(a)}>Track</button>
        {canWithdraw(a.status) && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setTarget(a)}>Withdraw</button>
        )}
      </div>
    ) },
  ];

  return (
    <div className="stack">
      <div className="page-header">
        <p className="muted">Withdrawing is final: re-applying to the same job is not supported.</p>
        <div className="field" style={{ minWidth: 200 }}>
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setParams(e.target.value ? { status: e.target.value } : {}); setPage(0); }}>
            <option value="">All</option>
            {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </div>
      <AsyncView state={apps} skeleton={<Skeleton lines={6} card />}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="No applications yet" illustration="briefcase" /> : null)}>
        {(p) => (
          <>
            <DataTable caption="Your applications" columns={columns} rows={p.content} rowKey={(a) => a.id}
              sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
      <ConfirmDialog open={!!target} title="Withdraw application?" danger busy={busy}
        message={target ? `You will be withdrawn from ${target.jobTitle} at ${target.companyName}. This cannot be undone.` : ''}
        confirmLabel="Withdraw" onConfirm={withdraw} onCancel={() => setTarget(null)} />
      {tracking && <TrackDialog app={tracking} onClose={() => setTracking(null)} />}
    </div>
  );
}
