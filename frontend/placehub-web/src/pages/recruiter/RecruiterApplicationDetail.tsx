import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, FileText } from 'lucide-react';
import { applicationApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { AsyncView } from '../../components/States';
import { StatusControl } from '../../components/StatusControl';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { BRANCH_LABELS, STATUS_LABELS } from '../../types/enums';
import { AutoBadge, StatusBadge } from '../../ui/Badge';
import { Card } from '../../ui/Card';
import { Skeleton } from '../../ui/Skeleton';
import { Stepper, Timeline } from '../../ui/Stepper';
import { formatCgpa, formatDateTime } from '../../utils/format';
import { openPdfInNewTab } from '../../utils/openBlob';
import { money } from '../../utils/policy';

export function RecruiterApplicationDetail() {
  const { id = '' } = useParams();
  const app = useAsync(() => applicationApi.get(id), [id]);
  const history = useAsync(() => applicationApi.history(id), [id]);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (app.data) setNote(app.data.recruiterNote ?? '');
  }, [app.data]);

  const change = async (status: ApplicationStatus) => {
    setBusy(true);
    try {
      const updated = await applicationApi.updateStatus(id, status, note);
      app.setData(updated);
      history.reload();
      emitToast({ kind: 'success', message: `Application marked ${STATUS_LABELS[status].toLowerCase()}.` });
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
      app.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <AsyncView state={app} skeleton={<div className="two-col"><Skeleton lines={8} card /><Skeleton lines={5} card /></div>}>
      {(a) => (
        <div className="stack">
          <p className="small" style={{ margin: 0 }}>
            <Link to={`/recruiter/jobs/${a.jobId}/applicants`} className="row" style={{ gap: 4, display: 'inline-flex' }}>
              <ArrowLeft size={14} aria-hidden="true" />Back to applicants
            </Link>
          </p>
          <div className="page-header">
            <div>
              <h1>{a.studentName}</h1>
              <p className="muted row" style={{ margin: 0 }}>
                <span>{a.jobTitle}</span><StatusBadge status={a.status} />{a.autoShortlisted && <AutoBadge />}
              </p>
            </div>
            {a.hasResume && (
              <button type="button" className="btn btn-secondary"
                onClick={() => openPdfInNewTab(() => applicationApi.resumeBlob(a.id))
                  .catch((err) => emitToast({ kind: 'error', message: parseError(err).message }))}>
                <FileText size={16} aria-hidden="true" /> View resume
              </button>
            )}
          </div>
          <Card><Stepper status={a.status} reached={history.data?.map((h) => h.toStatus)} /></Card>
          <div className="two-col">
            <div className="stack">
              <Card title="Snapshot at application time">
                <dl className="details">
                  <dt>Email</dt><dd>{a.studentEmail}</dd>
                  <dt>Branch</dt><dd>{BRANCH_LABELS[a.branch] ?? a.branch}</dd>
                  <dt>CGPA</dt><dd className="num">{formatCgpa(a.cgpa)}</dd>
                  <dt>Graduation year</dt><dd className="num">{a.graduationYear}</dd>
                  <dt>Backlogs</dt><dd className="num">{a.backlogCount}</dd>
                  <dt>CTC offered</dt><dd className="num">{a.ctcLpa != null ? `${money(a.ctcLpa)} LPA` : '—'}</dd>
                  <dt>Applied</dt><dd>{formatDateTime(a.appliedAt)}</dd>
                  <dt>Last updated</dt><dd>{formatDateTime(a.updatedAt)}</dd>
                </dl>
                {!a.hasResume && <p className="muted small">No resume was attached to this application.</p>}
              </Card>
              <Card title="Timeline">
                <AsyncView state={history} skeleton={<Skeleton lines={3} />}>{(entries) => <Timeline entries={entries} />}</AsyncView>
              </Card>
            </div>
            <Card title="Review" className="sticky-aside">
              <div className="stack">
                <div className="field">
                  <label htmlFor="note">Recruiter note (internal, never shown to the student)</label>
                  <textarea id="note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
                </div>
                <StatusControl current={a.status} busy={busy} onChange={change} />
              </div>
            </Card>
          </div>
        </div>
      )}
    </AsyncView>
  );
}
