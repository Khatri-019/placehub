import { useEffect, useMemo, useState } from 'react';
import { Plus, Save, Trash2 } from 'lucide-react';
import { policyApi } from '../../api/endpoints';
import { parseError, type ParsedError } from '../../api/errors';
import { AsyncView, ErrorState } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { EmploymentType, Policy, PolicyAction, PolicyBand, PolicyDraft, PolicyEvaluation } from '../../types';
import { Badge, type Tone } from '../../ui/Badge';
import { Card } from '../../ui/Card';
import { Markdown } from '../../ui/Markdown';
import { Modal } from '../../ui/Modal';
import { Pagination } from '../../components/Pagination';
import { Skeleton } from '../../ui/Skeleton';
import { Tabs } from '../../ui/Tabs';
import { formatDateTime } from '../../utils/format';
import { bandLabel, policyDiff, renderConsent, toDraft, validateBands } from '../../utils/policy';

interface EditBand {
  from: string;
  to: string;
  action: PolicyAction;
  lock: boolean;
}

interface EditState {
  bands: EditBand[];
  appliesToInternships: boolean;
  maxActiveConsents: string;
  resumeRequired: boolean;
  withdrawOtherApplicationsOnLock: boolean;
  consentText: string;
}

const ACTION_LABELS: Record<PolicyAction, string> = { BLOCK: 'Block', REQUIRE_CONSENT: 'Require consent', ALLOW: 'Allow' };
const DECISION_TONE: Record<string, Tone> = { ALLOW: 'success', REQUIRE_CONSENT: 'warning', BLOCK: 'danger' };

function toEdit(p: PolicyDraft): EditState {
  return {
    bands: p.bands.map((b) => ({
      from: String(Number(b.fromMultiplier)), to: b.toMultiplier == null ? '' : String(Number(b.toMultiplier)),
      action: b.action, lock: b.lockOnPlacement,
    })),
    appliesToInternships: p.appliesToInternships,
    maxActiveConsents: p.maxActiveConsents == null ? '' : String(p.maxActiveConsents),
    resumeRequired: p.resumeRequired,
    withdrawOtherApplicationsOnLock: p.withdrawOtherApplicationsOnLock,
    consentText: p.consentText,
  };
}

function fromEdit(e: EditState): PolicyDraft {
  return {
    bands: e.bands.map((b): PolicyBand => ({
      fromMultiplier: Number(b.from), toMultiplier: b.to.trim() === '' ? null : Number(b.to), action: b.action, lockOnPlacement: b.lock,
    })),
    appliesToInternships: e.appliesToInternships,
    maxActiveConsents: e.maxActiveConsents.trim() === '' ? null : Number(e.maxActiveConsents),
    resumeRequired: e.resumeRequired,
    withdrawOtherApplicationsOnLock: e.withdrawOtherApplicationsOnLock,
    consentText: e.consentText,
  };
}

function issuesFor(e: EditState, draft: PolicyDraft) {
  const issues = validateBands(draft.bands);
  if (draft.bands.some((b) => b.action === 'REQUIRE_CONSENT') && !e.consentText.trim()) {
    issues.push({ row: null, message: 'Consent text is required when a band requires consent' });
  }
  if (e.maxActiveConsents.trim() !== '') {
    const n = Number(e.maxActiveConsents);
    if (!Number.isInteger(n) || n < 1) issues.push({ row: null, message: 'Max active consents must be a whole number ≥ 1 (empty = unlimited)' });
  }
  return issues;
}

function Simulator({ draft, valid }: { draft: PolicyDraft; valid: boolean }) {
  const [currentCtc, setCurrentCtc] = useState('12');
  const [jobCtc, setJobCtc] = useState('20');
  const [type, setType] = useState<EmploymentType>('FULL_TIME');
  const [locked, setLocked] = useState(false);
  const [result, setResult] = useState<PolicyEvaluation | null>(null);
  const [error, setError] = useState<ParsedError | null>(null);
  const key = JSON.stringify(draft);

  useEffect(() => {
    if (!valid) return undefined;
    const handle = window.setTimeout(() => {
      policyApi.simulate({
        currentCtc: currentCtc.trim() ? Number(currentCtc) : null,
        jobCtc: jobCtc.trim() ? Number(jobCtc) : null,
        employmentType: type, locked, policy: JSON.parse(key) as PolicyDraft,
      }).then((r) => { setResult(r); setError(null); }).catch((err) => setError(parseError(err)));
    }, 300);
    return () => window.clearTimeout(handle);
  }, [currentCtc, jobCtc, type, locked, key, valid]);

  return (
    <Card title="Simulator" actions={<span className="small muted">Uses the unsaved draft</span>}>
      <div className="stack">
        <div className="form-row">
          <div className="field">
            <label htmlFor="sim-current">Student current CTC (LPA)</label>
            <input id="sim-current" type="number" step="0.01" min={0.01} value={currentCtc} onChange={(e) => setCurrentCtc(e.target.value)} />
            <span className="field-hint">Empty = not placed</span>
          </div>
          <div className="field">
            <label htmlFor="sim-job">Job CTC (LPA)</label>
            <input id="sim-job" type="number" step="0.01" min={0.01} value={jobCtc} onChange={(e) => setJobCtc(e.target.value)} />
          </div>
        </div>
        <div className="row">
          <div className="field" style={{ minWidth: 160 }}>
            <label htmlFor="sim-type">Employment type</label>
            <select id="sim-type" value={type} onChange={(e) => setType(e.target.value as EmploymentType)}>
              <option value="FULL_TIME">Full-time</option>
              <option value="INTERNSHIP">Internship</option>
            </select>
          </div>
          <label className="check"><input type="checkbox" checked={locked} onChange={(e) => setLocked(e.target.checked)} />Student is locked</label>
        </div>
        <div aria-live="polite" className="stack-sm" data-testid="simulation-result">
          {!valid && <p className="small muted" style={{ margin: 0 }}>Fix the draft errors to simulate.</p>}
          {valid && error && <ErrorState error={error} />}
          {valid && result && (
            <>
              <div className="row">
                <Badge tone={DECISION_TONE[result.decision] ?? 'neutral'}>{result.decision.replace('_', ' ')}</Badge>
                {result.ratio != null && <span className="num small">Ratio {Number(result.ratio).toFixed(2)}×</span>}
                {result.matchedBand && <span className="small muted">Band {bandLabel(result.matchedBand)}</span>}
                {result.lockOnPlacement && <Badge tone="danger">Locks on selection</Badge>}
              </div>
              {result.reasons.map((r) => <p key={r.code + r.message} className="small" style={{ margin: 0 }}>{r.message}</p>)}
            </>
          )}
        </div>
      </div>
    </Card>
  );
}

function Editor({ active, onSaved }: { active: Policy; onSaved: () => void }) {
  const baseline = useMemo(() => toDraft(active), [active]);
  const [edit, setEdit] = useState<EditState>(() => toEdit(baseline));
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<ParsedError | null>(null);

  const draft = fromEdit(edit);
  const issues = issuesFor(edit, draft);
  const changes = policyDiff(baseline, draft);
  const rowIssues = new Set(issues.filter((i) => i.row != null).map((i) => i.row));
  const preview = renderConsent(edit.consentText, { companyName: 'Meridian Analytics', jobTitle: 'Senior Data Engineer', ctcLpa: 20, currentCtc: 12 });

  const setBand = (i: number, patch: Partial<EditBand>) =>
    setEdit((e) => ({ ...e, bands: e.bands.map((b, j) => (j === i ? { ...b, ...patch } : b)) }));
  const addBand = () => setEdit((e) => {
    const bands = [...e.bands];
    const last = bands[bands.length - 1];
    if (last) {
      const start = Number(last.from) + 0.5;
      bands[bands.length - 1] = { ...last, to: String(start) };
      bands.push({ from: String(start), to: '', action: 'ALLOW', lock: false });
    } else {
      bands.push({ from: '0', to: '', action: 'ALLOW', lock: false });
    }
    return { ...e, bands };
  });
  const removeBand = (i: number) => setEdit((e) => {
    const bands = e.bands.filter((_, j) => j !== i);
    if (bands.length) bands[bands.length - 1] = { ...bands[bands.length - 1], to: '' };
    return { ...e, bands };
  });

  const save = async () => {
    setBusy(true);
    setSaveError(null);
    try {
      const saved = await policyApi.save(draft, note.trim());
      emitToast({ kind: 'success', message: `Placement policy v${saved.version} is now active.` });
      setConfirming(false);
      setNote('');
      onSaved();
    } catch (err) {
      setSaveError(parseError(err));
    } finally {
      setBusy(false);
    }
  };

  const canSave = issues.length === 0 && changes.length > 0 && note.trim().length > 0;

  return (
    <div className="two-col">
      <div className="stack">
        <Card title="CTC bands" actions={<span className="small muted">Multiplier of the student&apos;s current CTC</span>}>
          <div className="table-wrap">
            <table className="table">
              <caption className="visually-hidden">Policy bands</caption>
              <thead>
                <tr><th scope="col">From ×</th><th scope="col">To ×</th><th scope="col">Action</th><th scope="col">Lock on selection</th><th scope="col"><span className="visually-hidden">Remove</span></th></tr>
              </thead>
              <tbody>
                {edit.bands.map((b, i) => (
                  <tr key={i} aria-invalid={rowIssues.has(i)} style={rowIssues.has(i) ? { background: 'var(--tone-danger-bg)' } : undefined}>
                    <td>
                      <input aria-label={`Band ${i + 1} from`} type="number" step="0.01" min={0} value={b.from} disabled={i === 0}
                        onChange={(e) => setBand(i, { from: e.target.value })} className="input" style={{ width: 104, minHeight: 36 }} />
                    </td>
                    <td>
                      <input aria-label={`Band ${i + 1} to`} type="number" step="0.01" min={0} value={b.to}
                        placeholder={i === edit.bands.length - 1 ? '∞' : ''} disabled={i === edit.bands.length - 1}
                        onChange={(e) => setBand(i, { to: e.target.value })} className="input" style={{ width: 104, minHeight: 36 }} />
                    </td>
                    <td>
                      <select aria-label={`Band ${i + 1} action`} className="input" style={{ minHeight: 36, width: 'auto' }} value={b.action} onChange={(e) => setBand(i, { action: e.target.value as PolicyAction })}>
                        {Object.entries(ACTION_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                      </select>
                    </td>
                    <td>
                      <input aria-label={`Band ${i + 1} locks on selection`} type="checkbox" checked={b.lock}
                        onChange={(e) => setBand(i, { lock: e.target.checked })} />
                    </td>
                    <td>
                      <button type="button" className="btn btn-ghost btn-sm btn-icon" aria-label={`Remove band ${i + 1}`}
                        disabled={edit.bands.length <= 1} onClick={() => removeBand(i)}><Trash2 size={16} aria-hidden="true" /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row-between" style={{ marginTop: 'var(--space-3)' }}>
            <button type="button" className="btn btn-secondary btn-sm" onClick={addBand}><Plus size={14} aria-hidden="true" />Add band</button>
            <span className="small muted">Last band is open-ended (∞)</span>
          </div>
          {issues.length > 0 && (
            <ul className="reasons small" role="alert" style={{ color: 'var(--tone-danger-fg)', marginTop: 'var(--space-3)' }}>
              {issues.map((i) => <li key={i.message}>{i.message}</li>)}
            </ul>
          )}
        </Card>

        <Card title="Rules">
          <div className="stack-sm">
            <label className="check"><input type="checkbox" checked={edit.appliesToInternships}
              onChange={(e) => setEdit({ ...edit, appliesToInternships: e.target.checked })} />Apply the policy to internships</label>
            <label className="check"><input type="checkbox" checked={edit.resumeRequired}
              onChange={(e) => setEdit({ ...edit, resumeRequired: e.target.checked })} />Require a resume to apply</label>
            <label className="check"><input type="checkbox" checked={edit.withdrawOtherApplicationsOnLock}
              onChange={(e) => setEdit({ ...edit, withdrawOtherApplicationsOnLock: e.target.checked })} />Withdraw other open applications when a student is locked</label>
            <div className="field" style={{ maxWidth: 260 }}>
              <label htmlFor="max-consents">Max active consents per student</label>
              <input id="max-consents" type="number" min={1} value={edit.maxActiveConsents} placeholder="Unlimited"
                onChange={(e) => setEdit({ ...edit, maxActiveConsents: e.target.value })} />
            </div>
          </div>
        </Card>

        <Card title="Consent text">
          <div className="form-row">
            <div className="field">
              <label htmlFor="consent-text">Markdown template</label>
              <textarea id="consent-text" rows={12} maxLength={20000} value={edit.consentText}
                onChange={(e) => setEdit({ ...edit, consentText: e.target.value })} />
              <span className="field-hint">Placeholders: {'{{companyName}} {{jobTitle}} {{ctcLpa}} {{currentCtc}}'}</span>
            </div>
            <div className="field">
              <span className="label" id="consent-preview-label">Live preview (sample job)</span>
              <div className="consent-scroll" aria-labelledby="consent-preview-label" tabIndex={0}><Markdown source={preview} /></div>
            </div>
          </div>
        </Card>
      </div>

      <aside className="stack sticky-aside" aria-label="Save and simulate">
        <Card title={`Changes vs active v${active.version}`}>
          {changes.length === 0 ? <p className="small muted" style={{ margin: 0 }}>No changes yet.</p> : (
            <ul className="reasons small">{changes.map((c) => <li key={c}>{c}</li>)}</ul>
          )}
          <div className="field" style={{ marginTop: 'var(--space-3)' }}>
            <label htmlFor="change-note">Change note (required)</label>
            <input id="change-note" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <div className="button-row" style={{ marginTop: 'var(--space-3)' }}>
            <button type="button" className="btn btn-primary" disabled={!canSave} onClick={() => setConfirming(true)}>
              <Save size={16} aria-hidden="true" />Publish new version
            </button>
            <button type="button" className="btn btn-ghost" disabled={changes.length === 0}
              onClick={() => setEdit(toEdit(baseline))}>Discard</button>
          </div>
        </Card>
        <Simulator draft={draft} valid={issues.length === 0} />
      </aside>

      <Modal open={confirming} onClose={() => setConfirming(false)} title={`Publish policy v${active.version + 1}?`}
        footer={(
          <div className="button-row">
            <button type="button" className="btn btn-secondary" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
            <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Publishing…' : 'Publish'}</button>
          </div>
        )}>
        <p>The new version applies to future applications immediately. Existing applications keep the policy snapshot they were made under.</p>
        <ul className="reasons small">{changes.map((c) => <li key={c}>{c}</li>)}</ul>
        {saveError && <ErrorState error={saveError} />}
      </Modal>
    </div>
  );
}

function History({ active }: { active: Policy }) {
  const [page, setPage] = useState(0);
  const versions = useAsync(() => policyApi.versions({ page, size: 10 }), [page]);
  return (
    <AsyncView state={versions} skeleton={<Skeleton lines={5} card />}>
      {(p) => (
        <div className="stack">
          {p.content.map((v, i) => {
            const previous = p.content[i + 1];
            const diff = previous ? policyDiff(toDraft(previous), toDraft(v)) : [];
            return (
              <Card key={v.id} title={(
                <span className="row">
                  <span>Version {v.version}</span>
                  {v.id === active.id && <Badge tone="success">Active</Badge>}
                </span>
              )} actions={<span className="small muted">{formatDateTime(v.effectiveFrom)}</span>}>
                <p style={{ margin: 0 }}>{v.changeNote}</p>
                <p className="small muted" style={{ margin: 'var(--space-2) 0' }}>{v.bands.map((b) => `${bandLabel(b)} ${ACTION_LABELS[b.action]}${b.lockOnPlacement ? ' + lock' : ''}`).join(' · ')}</p>
                {diff.length > 0 && <ul className="reasons small">{diff.map((d) => <li key={d}>{d}</li>)}</ul>}
              </Card>
            );
          })}
          <Pagination page={p} onChange={setPage} />
        </div>
      )}
    </AsyncView>
  );
}

function AuditLog() {
  const [page, setPage] = useState(0);
  const audit = useAsync(() => policyApi.audit({ page, size: 20 }), [page]);
  return (
    <AsyncView state={audit} skeleton={<Skeleton lines={6} card />}>
      {(p) => (
        <>
          <div className="table-wrap">
            <table className="table">
              <caption className="visually-hidden">Policy audit log</caption>
              <thead><tr><th scope="col">When</th><th scope="col">Action</th><th scope="col">Student</th><th scope="col">Reason</th></tr></thead>
              <tbody>
                {p.content.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                    <td><Badge tone="neutral">{a.action}</Badge></td>
                    <td className="small">{a.studentUserId ?? '—'}</td>
                    <td>{a.reason ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={p} onChange={setPage} />
        </>
      )}
    </AsyncView>
  );
}

export function AdminPolicy() {
  const active = useAsync(() => policyApi.active(), []);
  const [tab, setTab] = useState('editor');
  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Placement policy</h1>
          {active.data && (
            <p className="muted" style={{ margin: 0 }}>
              Active version {active.data.version} · effective {formatDateTime(active.data.effectiveFrom)}
            </p>
          )}
        </div>
      </div>
      <AsyncView state={active} skeleton={<Skeleton lines={8} card />}>
        {(p) => (
          <Tabs label="Policy sections" active={tab} onChange={setTab}
            tabs={[{ id: 'editor', label: 'Editor' }, { id: 'history', label: 'Version history' }, { id: 'audit', label: 'Audit log' }]}>
            {tab === 'editor' && <Editor key={p.id} active={p} onSaved={active.reload} />}
            {tab === 'history' && <History active={p} />}
            {tab === 'audit' && <AuditLog />}
          </Tabs>
        )}
      </AsyncView>
    </div>
  );
}
