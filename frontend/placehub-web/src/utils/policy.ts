import type { Job, PlacementStateView, PolicyBand, PolicyDraft } from '../types';
import type { PolicyKind } from '../ui/Badge';

/**
 * Policy badge for a job card, computed from the student's placement state (one request for the whole list). The
 * job details page always shows the authoritative server evaluation.
 */
export function policyBadgeFor(job: Pick<Job, 'ctcLpa' | 'employmentType'>, state: PlacementStateView | undefined): PolicyKind | null {
  if (!state) return null;
  if (state.locked) return 'LOCKED';
  const internship = job.employmentType === 'INTERNSHIP';
  if (internship && !state.appliesToInternships) return 'OPEN';
  if (job.ctcLpa == null) return 'BLOCKED';
  if (!state.placed) return 'OPEN';
  const ctc = Number(job.ctcLpa);
  const band = state.bands.find((b) => ctc >= Number(b.fromCtc) && (b.toCtc == null || ctc < Number(b.toCtc)));
  if (!band) return 'BLOCKED';
  return band.action === 'ALLOW' ? 'OPEN' : band.action === 'REQUIRE_CONSENT' ? 'CONSENT' : 'BLOCKED';
}

export interface BandIssue {
  row: number | null;
  message: string;
}

const twoDecimals = (n: number) => Math.round(n * 100) === n * 100 || Math.abs(Math.round(n * 100) - n * 100) < 1e-9;

/** Client mirror of the server's PolicyValidator, for live highlighting in the band editor. */
export function validateBands(bands: PolicyBand[]): BandIssue[] {
  const issues: BandIssue[] = [];
  if (bands.length === 0) return [{ row: null, message: 'At least one band is required' }];
  bands.forEach((b, i) => {
    const last = i === bands.length - 1;
    const from = Number(b.fromMultiplier);
    if (Number.isNaN(from)) issues.push({ row: i, message: `Band ${i + 1}: "from" must be a number` });
    if (i === 0 && from !== 0) issues.push({ row: i, message: 'Band 1 must start at 0' });
    if (i > 0 && (from <= 0 || !twoDecimals(from))) issues.push({ row: i, message: `Band ${i + 1}: "from" must be > 0 with at most 2 decimals` });
    if (b.toMultiplier == null) {
      if (!last) issues.push({ row: i, message: `Band ${i + 1}: only the last band may be open-ended` });
    } else {
      const to = Number(b.toMultiplier);
      if (to <= from) issues.push({ row: i, message: `Band ${i + 1}: "to" must be greater than "from"` });
      if (!twoDecimals(to) || to > 100) issues.push({ row: i, message: `Band ${i + 1}: "to" must have at most 2 decimals and be ≤ 100` });
      if (last) issues.push({ row: i, message: `Band ${i + 1}: the last band must be open-ended` });
      const next = bands[i + 1];
      if (next) {
        const nf = Number(next.fromMultiplier);
        if (to < nf) issues.push({ row: i + 1, message: `Gap between band ${i + 1} and band ${i + 2} (${to} to ${nf})` });
        if (to > nf) issues.push({ row: i + 1, message: `Band ${i + 1} and band ${i + 2} overlap` });
      }
    }
  });
  return issues;
}

export function renderConsent(template: string, vars: { companyName: string; jobTitle: string; ctcLpa: number | null; currentCtc: number | null }): string {
  const money = (v: number | null) => (v == null ? '-' : Number(v).toFixed(2));
  return template
    .split('{{companyName}}').join(vars.companyName)
    .split('{{jobTitle}}').join(vars.jobTitle)
    .split('{{ctcLpa}}').join(money(vars.ctcLpa))
    .split('{{currentCtc}}').join(money(vars.currentCtc));
}

export function bandLabel(b: PolicyBand): string {
  const to = b.toMultiplier == null ? '∞' : `${Number(b.toMultiplier).toFixed(2)}×`;
  return `${Number(b.fromMultiplier).toFixed(2)}× – ${to}`;
}

export function money(v: number | null | undefined): string {
  return v == null ? '—' : Number(v).toFixed(2);
}

const RIBBON_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

/** Students see the policy-change ribbon for 7 days after a new policy version (v2+), until they dismiss it. */
export function shouldShowRibbon(state: { policyVersion: number; policyEffectiveFrom: string } | null,
                                 now = Date.now(), dismissed: string | null = null): boolean {
  if (!state || state.policyVersion <= 1) return false;
  const since = new Date(state.policyEffectiveFrom).getTime();
  if (Number.isNaN(since) || now - since > RIBBON_DAYS_MS || now < since - 60_000) return false;
  return dismissed !== String(state.policyVersion);
}

/** Human-readable differences between two policy drafts (used for "diff vs active" and version history). */
export function policyDiff(from: PolicyDraft, to: PolicyDraft): string[] {
  const out: string[] = [];
  const bandsText = (bs: PolicyBand[]) => bs.map((b) => `${bandLabel(b)} ${b.action}${b.lockOnPlacement ? ' + lock' : ''}`);
  const a = bandsText(from.bands);
  const b = bandsText(to.bands);
  if (a.join('|') !== b.join('|')) {
    a.filter((x) => !b.includes(x)).forEach((x) => out.push(`Removed band ${x}`));
    b.filter((x) => !a.includes(x)).forEach((x) => out.push(`Added band ${x}`));
  }
  const flag = (label: string, x: boolean, y: boolean) => { if (x !== y) out.push(`${label}: ${x ? 'on' : 'off'} → ${y ? 'on' : 'off'}`); };
  flag('Applies to internships', from.appliesToInternships, to.appliesToInternships);
  flag('Resume required', from.resumeRequired, to.resumeRequired);
  flag('Withdraw other applications on lock', from.withdrawOtherApplicationsOnLock, to.withdrawOtherApplicationsOnLock);
  if ((from.maxActiveConsents ?? null) !== (to.maxActiveConsents ?? null)) {
    out.push(`Max active consents: ${from.maxActiveConsents ?? 'unlimited'} → ${to.maxActiveConsents ?? 'unlimited'}`);
  }
  if (from.consentText !== to.consentText) out.push('Consent text changed');
  return out;
}

export function toDraft(p: PolicyDraft): PolicyDraft {
  return {
    bands: p.bands.map((b) => ({
      fromMultiplier: Number(b.fromMultiplier),
      toMultiplier: b.toMultiplier == null ? null : Number(b.toMultiplier),
      action: b.action,
      lockOnPlacement: b.lockOnPlacement,
    })),
    appliesToInternships: p.appliesToInternships,
    maxActiveConsents: p.maxActiveConsents ?? null,
    resumeRequired: p.resumeRequired,
    withdrawOtherApplicationsOnLock: p.withdrawOtherApplicationsOnLock,
    consentText: p.consentText,
  };
}
