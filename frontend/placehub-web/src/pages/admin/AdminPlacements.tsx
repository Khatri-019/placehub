import { useState, type FormEvent } from 'react';
import { Lock, LockOpen, Plus } from 'lucide-react';
import { placementApi, userApi } from '../../api/endpoints';
import { parseError, type ParsedError } from '../../api/errors';
import { Pagination } from '../../components/Pagination';
import { AsyncView, ErrorState } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Placement, User } from '../../types';
import { Badge } from '../../ui/Badge';
import { DataTable, type Column } from '../../ui/DataTable';
import { EmptyState } from '../../ui/EmptyState';
import { Modal } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';
import { money } from '../../utils/policy';

type LockAction = { placement: Placement; action: 'lock' | 'unlock' };

function LockDialog({ target, onClose, onDone }: { target: LockAction | null; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ParsedError | null>(null);
  if (!target) return null;
  const { placement, action } = target;
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await (action === 'lock' ? placementApi.lock(placement.studentUserId, reason.trim())
        : placementApi.unlock(placement.studentUserId, reason.trim()));
      emitToast({ kind: 'success', message: `${placement.studentName} ${action === 'lock' ? 'locked' : 'unlocked'}.` });
      setReason('');
      onDone();
    } catch (err) {
      setError(parseError(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal open onClose={onClose} title={`${action === 'lock' ? 'Lock' : 'Unlock'} ${placement.studentName}?`}
      description="The reason is recorded in the policy audit log."
      footer={(
        <div className="button-row">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="button" className={`btn ${action === 'lock' ? 'btn-danger' : 'btn-primary'}`} onClick={submit}
            disabled={busy || reason.trim().length < 3}>{busy ? 'Saving…' : action === 'lock' ? 'Lock student' : 'Unlock student'}</button>
        </div>
      )}>
      <div className="field">
        <label htmlFor="lock-reason">Reason (required)</label>
        <textarea id="lock-reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
      {error && <ErrorState error={error} />}
    </Modal>
  );
}

function OffCampusDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const [q, setQ] = useState('');
  const [matches, setMatches] = useState<User[] | null>(null);
  const [student, setStudent] = useState<User | null>(null);
  const [company, setCompany] = useState('');
  const [ctc, setCtc] = useState('');
  const [lock, setLock] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ParsedError | null>(null);

  const search = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const page = await userApi.adminList({ role: 'STUDENT', q: q.trim(), size: 8 });
      setMatches(page.content);
    } catch (err) {
      setError(parseError(err));
    }
  };

  const ctcValue = Number(ctc);
  const valid = !!student && company.trim().length > 0 && ctc.trim() !== '' && ctcValue > 0 && ctcValue <= 9999.99;

  const submit = async () => {
    if (!student) return;
    setBusy(true);
    setError(null);
    try {
      await placementApi.offCampus({ studentUserId: student.id, companyName: company.trim(), ctcLpa: Math.round(ctcValue * 100) / 100, lock });
      emitToast({ kind: 'success', message: `Off-campus placement recorded for ${student.fullName}.` });
      setStudent(null); setMatches(null); setQ(''); setCompany(''); setCtc(''); setLock(false);
      onDone();
    } catch (err) {
      setError(parseError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Record off-campus placement" size="lg"
      footer={(
        <div className="button-row">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={submit} disabled={!valid || busy}>{busy ? 'Saving…' : 'Record placement'}</button>
        </div>
      )}>
      <div className="stack">
        <form className="row" onSubmit={search} role="search" style={{ alignItems: 'flex-end' }}>
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="student-q">Find student (name or email)</label>
            <input id="student-q" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-secondary">Find</button>
        </form>
        {matches && (matches.length === 0 ? <p className="small muted" style={{ margin: 0 }}>No students match.</p> : (
          <fieldset className="field">
            <legend>Student</legend>
            <div className="stack-sm">
              {matches.map((m) => (
                <label key={m.id} className="check">
                  <input type="radio" name="student" checked={student?.id === m.id} onChange={() => setStudent(m)} />
                  <span>{m.fullName} <span className="muted">· {m.email}</span></span>
                </label>
              ))}
            </div>
          </fieldset>
        ))}
        <div className="form-row">
          <div className="field">
            <label htmlFor="oc-company">Company</label>
            <input id="oc-company" maxLength={150} value={company} onChange={(e) => setCompany(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="oc-ctc">CTC (LPA)</label>
            <input id="oc-ctc" type="number" step="0.01" min={0.01} value={ctc} onChange={(e) => setCtc(e.target.value)} />
          </div>
        </div>
        <label className="check"><input type="checkbox" checked={lock} onChange={(e) => setLock(e.target.checked)} />Lock the student</label>
        {error && <ErrorState error={error} />}
      </div>
    </Modal>
  );
}

export function AdminPlacements() {
  const [locked, setLocked] = useState<'' | 'true' | 'false'>('');
  const [source, setSource] = useState('');
  const [page, setPage] = useState(0);
  const placements = useAsync(() => placementApi.list({ locked: locked === '' ? '' : locked === 'true', source, page, size: 20 }),
    [locked, source, page]);
  const [lockTarget, setLockTarget] = useState<LockAction | null>(null);
  const [offCampus, setOffCampus] = useState(false);

  const columns: Column<Placement>[] = [
    { key: 'student', header: 'Student', render: (p) => <span style={{ fontWeight: 600 }}>{p.studentName}</span> },
    { key: 'company', header: 'Company', render: (p) => p.companyName },
    { key: 'ctc', header: 'CTC (LPA)', align: 'right', render: (p) => <span className="num">{money(p.ctcLpa)}</span> },
    { key: 'band', header: 'Band', render: (p) => p.band ?? '—' },
    { key: 'source', header: 'Source', render: (p) => <Badge tone={p.source === 'OFF_CAMPUS' ? 'info' : 'brand'}>{p.source === 'OFF_CAMPUS' ? 'Off-campus' : 'On campus'}</Badge> },
    { key: 'state', header: 'Student', render: (p) => (p.studentCurrentlyLocked
      ? <Badge tone="danger" icon={<Lock aria-hidden="true" />}>Locked</Badge> : <Badge tone="success">Unlocked</Badge>) },
    { key: 'recorded', header: 'Recorded', render: (p) => formatDateTime(p.recordedAt) },
    { key: 'actions', header: 'Actions', hideLabelOnMobile: true, render: (p) => (p.studentCurrentlyLocked ? (
      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setLockTarget({ placement: p, action: 'unlock' })}>
        <LockOpen size={14} aria-hidden="true" />Unlock
      </button>
    ) : (
      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setLockTarget({ placement: p, action: 'lock' })}>
        <Lock size={14} aria-hidden="true" />Lock
      </button>
    )) },
  ];

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Placements</h1>
          <p className="muted" style={{ margin: 0 }}>On-campus selections are recorded automatically; lock and unlock actions are audited.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setOffCampus(true)}><Plus size={16} aria-hidden="true" />Record off-campus</button>
      </div>
      <div className="filters">
        <div className="field">
          <label htmlFor="f-locked">Lock state</label>
          <select id="f-locked" value={locked} onChange={(e) => { setLocked(e.target.value as '' | 'true' | 'false'); setPage(0); }}>
            <option value="">All</option><option value="true">Locked</option><option value="false">Unlocked</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="f-source">Source</label>
          <select id="f-source" value={source} onChange={(e) => { setSource(e.target.value); setPage(0); }}>
            <option value="">All</option><option value="ON_CAMPUS">On campus</option><option value="OFF_CAMPUS">Off-campus</option>
          </select>
        </div>
      </div>
      <AsyncView state={placements} skeleton={<Skeleton lines={6} card />}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="No placements recorded" illustration="briefcase" /> : null)}>
        {(p) => (
          <>
            <DataTable caption="Placements" columns={columns} rows={p.content} rowKey={(x) => x.id} />
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
      <LockDialog target={lockTarget} onClose={() => setLockTarget(null)} onDone={() => { setLockTarget(null); placements.reload(); }} />
      <OffCampusDialog open={offCampus} onClose={() => setOffCampus(false)} onDone={() => { setOffCampus(false); placements.reload(); }} />
    </div>
  );
}
