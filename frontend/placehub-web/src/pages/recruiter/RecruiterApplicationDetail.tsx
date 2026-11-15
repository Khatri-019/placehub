import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { applicationApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { AsyncView } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { StatusControl } from '../../components/StatusControl';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { BRANCH_LABELS, STATUS_LABELS } from '../../types/enums';
import { formatCgpa, formatDateTime } from '../../utils/format';

export function RecruiterApplicationDetail() {
  const { id = '' } = useParams();
  const app = useAsync(() => applicationApi.get(id), [id]);
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
      emitToast({ kind: 'success', message: `Application marked ${STATUS_LABELS[status].toLowerCase()}.` });
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
      app.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <AsyncView state={app}>
      {(a) => (
        <div>
          <p className="small"><Link to={`/recruiter/jobs/${a.jobId}/applicants`}>← Back to applicants</Link></p>
          <div className="page-header">
            <div>
              <h1>{a.studentName}</h1>
              <p className="muted">{a.jobTitle} · <StatusBadge status={a.status} /></p>
            </div>
          </div>
          <div className="two-col">
            <section className="card">
              <h2>Snapshot at application time</h2>
              <dl className="details">
                <dt>Email</dt><dd>{a.studentEmail}</dd>
                <dt>Branch</dt><dd>{BRANCH_LABELS[a.branch] ?? a.branch}</dd>
                <dt>CGPA</dt><dd>{formatCgpa(a.cgpa)}</dd>
                <dt>Graduation year</dt><dd>{a.graduationYear}</dd>
                <dt>Backlogs</dt><dd>{a.backlogCount}</dd>
                <dt>Applied</dt><dd>{formatDateTime(a.appliedAt)}</dd>
                <dt>Last updated</dt><dd>{formatDateTime(a.updatedAt)}</dd>
              </dl>
            </section>
            <section className="card stack">
              <h2>Review</h2>
              <div className="field">
                <label htmlFor="note">Recruiter note (internal)</label>
                <textarea id="note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
              </div>
              <StatusControl current={a.status} busy={busy} onChange={change} />
            </section>
          </div>
        </div>
      )}
    </AsyncView>
  );
}
