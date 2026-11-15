import { useState } from 'react';
import { applicationApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Application, ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS, canWithdraw } from '../../types/enums';
import { formatDateTime } from '../../utils/format';

export function StudentApplications() {
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [page, setPage] = useState(0);
  const apps = useAsync(() => applicationApi.mine({ status, page, size: 20 }), [status, page]);
  const [target, setTarget] = useState<Application | null>(null);
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

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>My applications</h1>
          <p className="muted">Withdrawing is final: re-applying to the same job is not supported in v1.</p>
        </div>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as ApplicationStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </div>
      <AsyncView state={apps} empty={(p) => (p.content.length === 0 ? <EmptyState title="No applications yet" /> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <caption className="muted small" style={{ textAlign: 'left', padding: 'var(--space-3)' }}>Your applications</caption>
                <thead>
                  <tr><th scope="col">Job</th><th scope="col">Company</th><th scope="col">Status</th><th scope="col">Applied</th><th scope="col">Updated</th><th scope="col"><span className="muted">Actions</span></th></tr>
                </thead>
                <tbody>
                  {p.content.map((a) => (
                    <tr key={a.id}>
                      <td>{a.jobTitle}</td>
                      <td>{a.companyName}</td>
                      <td><StatusBadge status={a.status} /></td>
                      <td>{formatDateTime(a.appliedAt)}</td>
                      <td>{formatDateTime(a.updatedAt)}</td>
                      <td>
                        {canWithdraw(a.status) && (
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setTarget(a)}>Withdraw</button>
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
      <ConfirmDialog open={!!target} title="Withdraw application?" danger busy={busy}
        message={target ? `You will be withdrawn from ${target.jobTitle} at ${target.companyName}. This cannot be undone.` : ''}
        confirmLabel="Withdraw" onConfirm={withdraw} onCancel={() => setTarget(null)} />
    </div>
  );
}
