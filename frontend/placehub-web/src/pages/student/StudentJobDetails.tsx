import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { AlertTriangle, CalendarClock, MapPin, Wallet } from 'lucide-react';
import { applicationApi, jobApi } from '../../api/endpoints';
import { parseError, type ParsedError } from '../../api/errors';
import { useAuth } from '../../auth/useAuth';
import { ConsentModal } from '../../components/ConsentModal';
import { EligibilityPanel } from '../../components/EligibilityPanel';
import { AsyncView, ErrorState } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import { BRANCH_LABELS, EMPLOYMENT_TYPE_LABELS } from '../../types/enums';
import { PolicyBadge, type PolicyKind } from '../../ui/Badge';
import { Skeleton } from '../../ui/Skeleton';
import { formatDateTime } from '../../utils/format';
import { deadlineLabel } from './jobFormat';

export function StudentJobDetails() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const job = useAsync(() => jobApi.get(id), [id]);
  const eligibility = useAsync(() => applicationApi.eligibility(id), [id]);
  const [busy, setBusy] = useState(false);
  const [applyError, setApplyError] = useState<ParsedError | null>(null);
  const [consentOpen, setConsentOpen] = useState(false);
  const [consentError, setConsentError] = useState<string | null>(null);

  const preview = eligibility.data;
  const canApply = !!preview && (preview.canApply ?? (preview.eligible && !preview.alreadyApplied));
  const missingResume = !!preview && preview.resumeRequired && !preview.hasResume && !preview.alreadyApplied;
  const decision = preview?.policy?.decision;
  const policyKind: PolicyKind | null = !preview?.policy ? null
    : preview.placementState?.locked ? 'LOCKED'
      : decision === 'ALLOW' ? 'OPEN' : decision === 'REQUIRE_CONSENT' ? 'CONSENT' : 'BLOCKED';

  const submit = async (signedName?: string) => {
    setBusy(true);
    setApplyError(null);
    setConsentError(null);
    try {
      await applicationApi.apply(id, signedName ? { accepted: true, signedName } : undefined);
      emitToast({ kind: 'success', message: 'Application submitted.' });
      setConsentOpen(false);
      eligibility.reload();
    } catch (err) {
      const parsed = parseError(err);
      if (signedName) setConsentError(parsed.message);
      else setApplyError(parsed);
      eligibility.reload();
    } finally {
      setBusy(false);
    }
  };

  const onApply = () => (decision === 'REQUIRE_CONSENT' ? setConsentOpen(true) : submit());

  return (
    <AsyncView state={job} skeleton={<div className="two-col"><Skeleton lines={8} card /><Skeleton lines={5} card /></div>}>
      {(j) => {
        const due = deadlineLabel(j.deadline);
        return (
          <div className="stack-lg">
            <header className="page-header">
              <div>
                <h1>{j.title}</h1>
                <div className="row muted small">
                  <span>{j.companyName}</span><span aria-hidden="true">·</span><span>{EMPLOYMENT_TYPE_LABELS[j.employmentType]}</span>
                  <span aria-hidden="true">·</span>
                  <span className="row" style={{ gap: 4 }}><MapPin size={14} aria-hidden="true" />{j.location}</span>
                </div>
              </div>
              {policyKind && <PolicyBadge kind={policyKind} />}
            </header>
            <div className="two-col">
              <section className="card stack">
                <div className="grid-cards">
                  <div>
                    <div className="label">CTC</div>
                    <div className="row num" style={{ fontWeight: 600, gap: 6 }}>
                      <Wallet size={16} aria-hidden="true" />
                      {j.ctcLpa != null ? `${Number(j.ctcLpa).toFixed(2)} LPA` : 'Not specified'}{j.ctc ? ` · ${j.ctc}` : ''}
                    </div>
                  </div>
                  <div>
                    <div className="label">Deadline</div>
                    <div className="row" style={{ fontWeight: 600, gap: 6 }}>
                      <CalendarClock size={16} aria-hidden="true" />{formatDateTime(j.deadline)}
                    </div>
                    <div className="small muted">{due.text}</div>
                  </div>
                </div>
                <div>
                  <h2>About the role</h2>
                  <p className="pre-wrap">{j.description}</p>
                </div>
                <div>
                  <h2>Eligibility criteria</h2>
                  <dl className="details">
                    <dt>Minimum CGPA</dt><dd className="num">{Number(j.minCgpa).toFixed(2)}</dd>
                    <dt>Maximum backlogs</dt><dd className="num">{j.maxBacklogs}</dd>
                    <dt>Branches</dt>
                    <dd>{j.eligibleBranches.length ? j.eligibleBranches.map((b) => BRANCH_LABELS[b]).join(', ') : 'All branches'}</dd>
                    <dt>Graduation years</dt>
                    <dd>{j.eligibleGraduationYears.length ? j.eligibleGraduationYears.join(', ') : 'All years'}</dd>
                  </dl>
                </div>
              </section>
              <aside className="stack sticky-aside" aria-label="Apply">
                <EligibilityPanel preview={preview} loading={eligibility.loading} error={eligibility.error} onRetry={eligibility.reload} />
                {preview?.policy && (
                  <section className="card card-tight stack-sm" aria-labelledby="policy-title">
                    <h2 id="policy-title" className="label" style={{ margin: 0 }}>Placement policy</h2>
                    {decision === 'ALLOW' && <p style={{ margin: 0 }}>No placement-policy restrictions apply to this job.</p>}
                    {decision === 'REQUIRE_CONSENT' && (
                      <p style={{ margin: 0 }}>This job pays <span className="num">{preview.policy.ratio}×</span> your current CTC.
                        Applying needs your consent{preview.policy.lockOnPlacement ? ', and if you are selected you will be locked.' : '.'}</p>
                    )}
                    {preview.policy.reasons.map((r) => <p key={r.message} style={{ margin: 0 }}>{r.message}</p>)}
                    {preview.placementState && <p className="small muted" style={{ margin: 0 }}>{preview.placementState.summary}</p>}
                  </section>
                )}
                {missingResume && (
                  <div className="banner" role="status">
                    <AlertTriangle aria-hidden="true" />
                    <span>Upload your resume before applying. <Link to="/student/profile">Go to your profile</Link></span>
                  </div>
                )}
                {applyError && <ErrorState error={applyError} />}
                <button type="button" className="btn btn-primary" disabled={!canApply || busy} onClick={onApply}
                  aria-describedby="apply-help">
                  {busy ? 'Submitting…' : preview?.alreadyApplied ? 'Already applied'
                    : decision === 'REQUIRE_CONSENT' ? 'Apply with consent' : 'Apply now'}
                </button>
                <p id="apply-help" className="small muted">Your verified academic profile and current resume are used.</p>
              </aside>
            </div>
            <ConsentModal open={consentOpen} renderedText={preview?.consent?.renderedText ?? ''} band={preview?.consent?.band ?? null}
              fullName={user?.fullName ?? ''} busy={busy} error={consentError}
              onCancel={() => setConsentOpen(false)} onAccept={(name) => submit(name)} />
          </div>
        );
      }}
    </AsyncView>
  );
}
