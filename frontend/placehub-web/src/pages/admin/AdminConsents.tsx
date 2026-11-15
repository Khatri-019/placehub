import { useState, type FormEvent } from 'react';
import { Download } from 'lucide-react';
import { consentApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Pagination } from '../../components/Pagination';
import { AsyncView } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { ConsentRecord } from '../../types';
import { DataTable, type Column } from '../../ui/DataTable';
import { EmptyState } from '../../ui/EmptyState';
import { Markdown } from '../../ui/Markdown';
import { Modal } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function AdminConsents() {
  const [jobDraft, setJobDraft] = useState('');
  const [studentDraft, setStudentDraft] = useState('');
  const [filters, setFilters] = useState<{ jobId?: string; studentUserId?: string }>({});
  const [page, setPage] = useState(0);
  const [viewing, setViewing] = useState<ConsentRecord | null>(null);
  const [filterError, setFilterError] = useState<string | null>(null);
  const consents = useAsync(() => consentApi.list({ ...filters, page, size: 20 }), [filters.jobId, filters.studentUserId, page]);

  const apply = (e: FormEvent) => {
    e.preventDefault();
    const jobId = jobDraft.trim();
    const studentUserId = studentDraft.trim();
    if ((jobId && !UUID.test(jobId)) || (studentUserId && !UUID.test(studentUserId))) {
      setFilterError('Filters must be full IDs (UUID format).');
      return;
    }
    setFilterError(null);
    setFilters({ jobId: jobId || undefined, studentUserId: studentUserId || undefined });
    setPage(0);
  };

  const exportCsv = async () => {
    try {
      const blob = await consentApi.exportCsv(filters);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'consents.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    }
  };

  const columns: Column<ConsentRecord>[] = [
    { key: 'accepted', header: 'Accepted', render: (c) => formatDateTime(c.acceptedAt) },
    { key: 'signed', header: 'Signed name', render: (c) => <span style={{ fontWeight: 600 }}>{c.signedName}</span> },
    { key: 'band', header: 'Band', render: (c) => c.band },
    { key: 'version', header: 'Policy', align: 'right', render: (c) => <span className="num">v{c.policyVersion}</span> },
    { key: 'ip', header: 'Client IP', render: (c) => <span className="small">{c.clientIp ?? '—'}</span> },
    { key: 'actions', header: 'Actions', hideLabelOnMobile: true, render: (c) => (
      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setViewing(c)}>View text</button>
    ) },
  ];

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Consents</h1>
          <p className="muted" style={{ margin: 0 }}>Every consent stores the exact text the student accepted and its hash.</p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={exportCsv}><Download size={16} aria-hidden="true" />Export CSV</button>
      </div>
      <form className="filters" onSubmit={apply} role="search">
        <div className="field">
          <label htmlFor="c-job">Job ID</label>
          <input id="c-job" value={jobDraft} onChange={(e) => setJobDraft(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="c-student">Student ID</label>
          <input id="c-student" value={studentDraft} onChange={(e) => setStudentDraft(e.target.value)} />
        </div>
        <button type="submit" className="btn btn-primary">Filter</button>
      </form>
      {filterError && <p className="field-error" role="alert">{filterError}</p>}
      <AsyncView state={consents} skeleton={<Skeleton lines={6} card />}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="No consents recorded" illustration="inbox" /> : null)}>
        {(p) => (
          <>
            <DataTable caption="Consent records" columns={columns} rows={p.content} rowKey={(c) => c.id} />
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
      {viewing && (
        <Modal open onClose={() => setViewing(null)} title={`Consent by ${viewing.signedName}`} size="lg">
          <p className="small muted">Policy v{viewing.policyVersion} · band {viewing.band} · SHA-256 <span className="num">{viewing.consentTextHash}</span></p>
          <div className="consent-scroll" tabIndex={0} aria-label="Accepted consent text"><Markdown source={viewing.renderedText} /></div>
        </Modal>
      )}
    </div>
  );
}
