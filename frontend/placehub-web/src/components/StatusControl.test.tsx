import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ApplicationStatus } from '../types';
import { StatusControl } from './StatusControl';

function labels(current: ApplicationStatus) {
  render(<StatusControl current={current} onChange={() => undefined} />);
  return screen.queryAllByRole('button').map((b) => b.textContent);
}

describe('StatusControl', () => {
  it('offers only valid next transitions (never withdraw)', () => {
    expect(labels('APPLIED')).toEqual(['Mark under review', 'Mark shortlisted', 'Mark rejected']);
  });

  it('offers select/reject from shortlisted', () => {
    expect(labels('SHORTLISTED')).toEqual(['Mark selected', 'Mark rejected']);
  });

  it('offers shortlist/reject from under review', () => {
    expect(labels('UNDER_REVIEW')).toEqual(['Mark shortlisted', 'Mark rejected']);
  });

  it.each<ApplicationStatus>(['SELECTED', 'REJECTED', 'WITHDRAWN'])('shows no actions for terminal %s', (status) => {
    expect(labels(status)).toEqual([]);
    expect(screen.getByText(/no further changes/)).toBeInTheDocument();
  });

  it('reports the chosen status and is keyboard operable', async () => {
    const onChange = vi.fn();
    render(<StatusControl current="APPLIED" onChange={onChange} />);
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Mark under review' })).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    expect(onChange).toHaveBeenCalledWith('UNDER_REVIEW');
  });
});
