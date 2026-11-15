import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, FileText, Sparkles } from 'lucide-react';
import { applicationApi, jobApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Pagination } from '../../components/Pagination';
import { AsyncView } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { Application, ApplicationStatus } from '../../types';
import { APPLICATION_STATUSES, STATUS_LABELS, recruiterTransitions } from '../../types/enums';
import { AutoBadge, StatusBadge } from '../../ui/Badge';
import { DataTable, type Column, type SortState } from '../../ui/DataTable';
import { EmptyState } from '../../ui/EmptyState';
import { Modal } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { formatCgpa, formatDateTime } from '../../utils/format';
import { openPdfInNewTab } from '../../utils/openBlob';

const BULK_TARGETS: ApplicationStatus[] = ['UNDER_REVIEW', 'SHORTLISTED', 'REJECTED'];

export function RecruiterApplicants() {
  const { id = '' } = useParams();
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: 'appliedAt', dir: 'desc' });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkTarget, setBulkTarget] = useState<ApplicationStatus | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const job = useAsync(() => jobApi.get(id), [id]);
  const applicants = useAsync(() => applicationApi.forJob(id, { status, page, size: 20, sort: `${sort.field},${sort.dir}` }),
    [id, status, page, sort.field, sort.dir]);

  const resetSelection = () => setSelected(new Set());

  const runBulk = async () => {
    if (!bulkTarget) return;
    setBusy(true);
    try {
      const result = await applicationApi.bulkStatus([...selected], bulkTarget, note.trim() || undefined);
      emitToast({
        kind: result.failed ? 'error' : 'success',
        message: result.failed
          ? `${result.succeeded} updated, ${result.failed} skipped (invalid transition or not yours).`
          : `${result.succeeded} applicant${result.succeeded === 1 ? '' : 's'} marked ${STATUS_LABELS[bulkTarget].toLowerCase()}.`,
      });
      setBulkTarget(null);
      setNote('');
      resetSelection();
      applicants.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusy(false);
    }
  };

  const autoScreen = async () => {
    setBusy(true);
    try {
      const r = await applicationApi.autoScreen(id);
      emitToast({ kind: 'success', message: `Screened ${r.evaluated} applicant${r.evaluated === 1 ? '' : 's'}; ${r.shortlisted} shortlisted.` });
      applicants.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusy(false);
    }
  };

  const viewResume = (a: Application) =>
    openPdfInNewTab(() => applicationApi.resumeBlob(a.id))
      .catch((err) => emitToast({ kind: 'error', message: parseError(err).message }));

  const columns: Column<Application>[] = [
    { key: 'student', header: 'Student', sortField: 'studentName', render: (a) => (
      <div>
        <Link to={`/recruiter/applications/${a.id}`} style={{ fontWeight: 600 }}>{a.studentName}</Link>
        <div className="small muted">{a.studentEmail}</div>
      </div>
    ) },
    { key: 'branch', header: 'Branch', render: (a) => a.branch },
    { key: 'cgpa', header: 'CGPA', align: 'right', sortField: 'cgpa', render: (a) => <span className="num">{formatCgpa(a.cgpa)}</span> },
    { key: 'backlogs', header: 'Backlogs', align: 'right', render: (a) => <span className="num">{a.backlogCount}</span> },
    { key: 'status', header: 'Status', sortField: 'status', render: (a) => (
      <div className="row" style={{ gap: 6 }}><StatusBadge status={a.status} />{a.autoShortlisted && <AutoBadge />}</div>
    ) },
    { key: 'applied', header: 'Applied', sortField: 'appliedAt', render: (a) => formatDateTime(a.appliedAt) },
    { key: 'actions', header: 'Actions', hideLabelOnMobile: true, render: (a) => (
      <div className="button-row">
        <Link className="btn btn-secondary btn-sm" to={`/recruiter/applications/${a.id}`}>Review</Link>
        {a.hasResume && (
          <button type="button" className="btn btn-ghost btn-sm" aria-label={`View resume of ${a.studentName}`} onClick={() => viewResume(a)}>
            <FileText size={14} aria-hidden="true" /> Resume
          </button>
        )}
      </div>
    ) },
  ];

  const autoMode = job.data?.screeningMode === 'AUTO_SHORTLIST';

  return (
    <div className="stack">
      <p className="small" style={{ margin: 0 }}>
        <Link to="/recruiter/jobs" className="row" style={{ gap: 4, display: 'inline-flex' }}><ArrowLeft size={14} aria-hidden="true" />Back to my jobs</Link>
      </p>
      <div className="page-header">
        <div>
          <h1>Applicants</h1>
          {/* Space is reserved so the table below does not shift when the job loads (CLS). */}
          <p className="muted row" style={{ margin: 0, height: 28, flexWrap: 'nowrap', overflow: 'hidden' }}>
            {job.data && (
              <>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}>{job.data.title}</span><StatusBadge status={job.data.status} />
                {autoMode && <span className="small nowrap">Auto-shortlist on</span>}
              </>
            )}
          </p>
        </div>
        <div className="row">
          {autoMode && (
            <button type="button" className="btn btn-secondary" disabled={busy} onClick={autoScreen}>
              <Sparkles size={16} aria-hidden="true" /> Apply to existing applicants
            </button>
          )}
          <div className="field" style={{ minWidth: 180 }}>
            <label htmlFor="status">Status</label>
            <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as ApplicationStatus | ''); setPage(0); resetSelection(); }}>
              <option value="">All</option>
              {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
            </select>
          </div>
        </div>
      </div>

      {selected.size > 0 && (
        <div className="banner banner-info row-between" role="region" aria-label="Bulk actions">
          <span><strong className="num">{selected.size}</strong> selected</span>
          <div className="button-row">
            {BULK_TARGETS.map((t) => (
              <button key={t} type="button" className={`btn btn-sm ${t === 'REJECTED' ? 'btn-danger' : 'btn-primary'}`}
                onClick={() => setBulkTarget(t)}>
                Mark {STATUS_LABELS[t].toLowerCase()}
              </button>
            ))}
            <button type="button" className="btn btn-ghost btn-sm" onClick={resetSelection}>Clear</button>
          </div>
        </div>
      )}

      <AsyncView state={applicants} skeleton={<Skeleton lines={6} card />}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="No applicants yet" illustration="inbox" /> : null)}>
        {(p) => (
          <>
            <DataTable caption="Applicants" columns={columns} rows={p.content} rowKey={(a) => a.id}
              sort={sort} onSort={(s) => { setSort(s); setPage(0); }}
              selected={selected} onSelectedChange={setSelected} rowLabel={(a) => a.studentName}
              isSelectable={(a) => recruiterTransitions(a.status).length > 0} />
            <Pagination page={p} onChange={(n) => { setPage(n); resetSelection(); }} />
          </>
        )}
      </AsyncView>

      <Modal open={!!bulkTarget} onClose={() => setBulkTarget(null)}
        title={bulkTarget ? `Mark ${selected.size} as ${STATUS_LABELS[bulkTarget].toLowerCase()}?` : ''}
        footer={(
          <div className="button-row">
            <button type="button" className="btn btn-secondary" onClick={() => setBulkTarget(null)} disabled={busy}>Cancel</button>
            <button type="button" className={`btn ${bulkTarget === 'REJECTED' ? 'btn-danger' : 'btn-primary'}`} onClick={runBulk} disabled={busy}>
              {busy ? 'Updating…' : 'Confirm'}
            </button>
          </div>
        )}>
        <p>Applicants whose current status cannot move to this status are skipped and reported.</p>
        <div className="field">
          <label htmlFor="bulk-note">Internal note (optional)</label>
          <textarea id="bulk-note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </Modal>
    </div>
  );
}
