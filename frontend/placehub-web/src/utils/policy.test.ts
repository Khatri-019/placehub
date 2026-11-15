import { describe, expect, it } from 'vitest';
import type { PlacementStateView, PolicyBand, PolicyDraft } from '../types';
import { policyBadgeFor, policyDiff, renderConsent, shouldShowRibbon, validateBands } from './policy';
import { effectiveTheme, saveTheme, savedTheme } from './theme';

const V1: PolicyBand[] = [
  { fromMultiplier: 0, toMultiplier: 1.5, action: 'BLOCK', lockOnPlacement: false },
  { fromMultiplier: 1.5, toMultiplier: 2, action: 'REQUIRE_CONSENT', lockOnPlacement: true },
  { fromMultiplier: 2, toMultiplier: null, action: 'ALLOW', lockOnPlacement: false },
];

const draft = (over: Partial<PolicyDraft> = {}): PolicyDraft => ({
  bands: V1, appliesToInternships: false, maxActiveConsents: null, resumeRequired: true,
  withdrawOtherApplicationsOnLock: true, consentText: 'I accept {{jobTitle}} at {{companyName}}', ...over,
});

const state = (over: Partial<PlacementStateView> = {}): PlacementStateView => ({
  currentCtc: 12, placed: true, locked: false, placements: [], policyVersion: 1, policyEffectiveFrom: '2026-11-01T00:00:00Z',
  appliesToInternships: false, summary: '',
  bands: [
    { fromCtc: 0, toCtc: 18, action: 'BLOCK', lockOnPlacement: false, description: '' },
    { fromCtc: 18, toCtc: 24, action: 'REQUIRE_CONSENT', lockOnPlacement: true, description: '' },
    { fromCtc: 24, toCtc: null, action: 'ALLOW', lockOnPlacement: false, description: '' },
  ],
  ...over,
});

describe('validateBands', () => {
  it('accepts the seeded policy', () => {
    expect(validateBands(V1)).toEqual([]);
  });

  it('flags gaps, overlaps, a non-zero start and a closed last band', () => {
    const gap = [{ ...V1[0], toMultiplier: 1.4 }, V1[1], V1[2]];
    expect(validateBands(gap).map((i) => i.message)).toContain('Gap between band 1 and band 2 (1.4 to 1.5)');
    const overlap = [{ ...V1[0], toMultiplier: 1.6 }, V1[1], V1[2]];
    expect(validateBands(overlap).map((i) => i.message)).toContain('Band 1 and band 2 overlap');
    expect(validateBands([{ ...V1[0], fromMultiplier: 0.5, toMultiplier: null }])[0].message).toBe('Band 1 must start at 0');
    expect(validateBands([V1[0]]).map((i) => i.message)).toContain('Band 1: the last band must be open-ended');
    expect(validateBands([])).toHaveLength(1);
  });
});

describe('policyBadgeFor', () => {
  it('matches the band boundaries (lower bound inclusive, upper exclusive)', () => {
    expect(policyBadgeFor({ ctcLpa: 17.99, employmentType: 'FULL_TIME' }, state())).toBe('BLOCKED');
    expect(policyBadgeFor({ ctcLpa: 18, employmentType: 'FULL_TIME' }, state())).toBe('CONSENT');
    expect(policyBadgeFor({ ctcLpa: 24, employmentType: 'FULL_TIME' }, state())).toBe('OPEN');
  });

  it('handles locked, unplaced and internship cases', () => {
    expect(policyBadgeFor({ ctcLpa: 50, employmentType: 'FULL_TIME' }, state({ locked: true }))).toBe('LOCKED');
    expect(policyBadgeFor({ ctcLpa: 5, employmentType: 'FULL_TIME' }, state({ placed: false }))).toBe('OPEN');
    expect(policyBadgeFor({ ctcLpa: null, employmentType: 'INTERNSHIP' }, state())).toBe('OPEN');
    expect(policyBadgeFor({ ctcLpa: 5, employmentType: 'FULL_TIME' }, undefined)).toBeNull();
  });
});

describe('policyDiff and consent rendering', () => {
  it('lists changed bands and flags', () => {
    const next = draft({ bands: [V1[0], { ...V1[1], toMultiplier: 2.5 }, { ...V1[2], fromMultiplier: 2.5 }], maxActiveConsents: 2 });
    const diff = policyDiff(draft(), next);
    expect(diff).toContain('Max active consents: unlimited → 2');
    expect(diff.some((d) => d.startsWith('Added band 1.50× – 2.50×'))).toBe(true);
    expect(policyDiff(draft(), draft())).toEqual([]);
  });

  it('fills placeholders without interpreting markup', () => {
    expect(renderConsent(draft().consentText, { companyName: '<b>X</b>', jobTitle: 'SDE', ctcLpa: 20, currentCtc: 12 }))
      .toBe('I accept SDE at <b>X</b>');
  });
});

describe('policy ribbon', () => {
  const now = Date.parse('2026-11-05T00:00:00Z');
  it('shows for 7 days after a new version until dismissed', () => {
    const v2 = { policyVersion: 2, policyEffectiveFrom: '2026-11-01T00:00:00Z' };
    expect(shouldShowRibbon(v2, now)).toBe(true);
    expect(shouldShowRibbon(v2, now, '2')).toBe(false);
    expect(shouldShowRibbon(v2, Date.parse('2026-11-09T00:00:01Z'))).toBe(false);
    expect(shouldShowRibbon({ policyVersion: 1, policyEffectiveFrom: '2026-11-04T00:00:00Z' }, now)).toBe(false);
  });
});

describe('theme preference', () => {
  it('persists and applies the chosen theme', () => {
    saveTheme('dark');
    expect(savedTheme()).toBe('dark');
    expect(effectiveTheme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    saveTheme('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});
