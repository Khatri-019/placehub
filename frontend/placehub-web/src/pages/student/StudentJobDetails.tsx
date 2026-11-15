import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { applicationApi, jobApi } from '../../api/endpoints';
import { parseError, type ParsedError } from '../../api/errors';
import { EligibilityPanel } from '../../components/EligibilityPanel';
import { AsyncView, ErrorState } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import { BRANCH_LABELS, EMPLOYMENT_TYPE_LABELS } from '../../types/enums';
import { formatDateTime } from '../../utils/format';

export function StudentJobDetails() {
  const { id = '' } = useParams();
  const job = useAsync(() => jobApi.get(id), [id]);
  const eligibility = useAsync(() => applicationApi.eligibility(id), [id]);
  const [busy, setBusy] = useState(false);
  const [applyError, setApplyError] = useState<ParsedError | null>(null);

  const preview = eligibility.data;
  const canApply = !!preview && (preview.canApply ?? (preview.eligible && !preview.alreadyApplied));
  const missingResume = !!preview && preview.resumeRequired && !preview.hasResume && !preview.alreadyApplied;

  const onApply = async () => {
    setBusy(true);
    setApplyError(null);
    try {
      await applicationApi.apply(id);
      emitToast({ kind: 'success', message: 'Application submitted.' });
      eligibility.reload();
    } catch (err) {
      setApplyError(parseError(err));
      eligibility.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <AsyncView state={job}>
      {(j) => (
        <div>
          <p className="small"><Link to="/student/jobs">← Back to jobs</Link></p>
          <div className="page-header">
            <div>
              <h1>{j.title}</h1>
              <p className="muted">{j.companyName} · {j.location} · {EMPLOYMENT_TYPE_LABELS[j.employmentType]}</p>
            </div>
          </div>
          <div className="two-col">
            <section className="card stack">
              <h2>About the role</h2>
              <p className="pre-wrap">{j.description}</p>
              <h2>Eligibility criteria</h2>
              <dl className="details">
                <dt>Minimum CGPA</dt><dd>{Number(j.minCgpa).toFixed(2)}</dd>
                <dt>Maximum backlogs</dt><dd>{j.maxBacklogs}</dd>
                <dt>Branches</dt>
                <dd>{j.eligibleBranches.length ? j.eligibleBranches.map((b) => BRANCH_LABELS[b]).join(', ') : 'All branches'}</dd>
                <dt>Graduation years</dt>
                <dd>{j.eligibleGraduationYears.length ? j.eligibleGraduationYears.join(', ') : 'All years'}</dd>
                <dt>CTC</dt>
                <dd>{j.ctcLpa != null ? `${Number(j.ctcLpa).toFixed(2)} LPA` : 'Not specified'}{j.ctc ? ` (${j.ctc})` : ''}</dd>
                <dt>Deadline</dt><dd>{formatDateTime(j.deadline)}</dd>
              </dl>
            </section>
            <aside className="stack">
              <EligibilityPanel preview={eligibility.data} loading={eligibility.loading} error={eligibility.error}
                onRetry={eligibility.reload} />
              {missingResume && (
                <div className="banner" role="status">
                  Upload your resume before applying. <Link to="/student/profile">Go to your profile</Link>
                </div>
              )}
              {applyError && <ErrorState error={applyError} />}
              <button type="button" className="btn btn-primary" disabled={!canApply || busy} onClick={onApply}
                aria-describedby="apply-help">
                {busy ? 'Submitting…' : eligibility.data?.alreadyApplied ? 'Already applied' : 'Apply now'}
              </button>
              <p id="apply-help" className="small muted">
                Your verified academic profile is used; you cannot edit it here.
              </p>
            </aside>
          </div>
        </div>
      )}
    </AsyncView>
  );
}
