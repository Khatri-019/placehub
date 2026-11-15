import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EligibilityPanel } from './EligibilityPanel';

const base = { jobId: 'j1', alreadyApplied: false };

describe('EligibilityPanel', () => {
  it('lists every failing reason', () => {
    const reasons = [
      'Minimum CGPA required is 7.50; yours is 6.40',
      'Maximum backlogs allowed is 0; you have 2',
      'Your branch (ME) is not eligible; eligible branches: CSE, ISE, AIML',
    ];
    render(<EligibilityPanel preview={{ ...base, eligible: false, reasons }} loading={false} error={null} />);
    expect(screen.getByRole('heading', { name: 'You are not eligible' })).toBeInTheDocument();
    const items = within(screen.getByRole('list')).getAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual(reasons);
  });

  it('confirms eligibility', () => {
    render(<EligibilityPanel preview={{ ...base, eligible: true, reasons: [] }} loading={false} error={null} />);
    expect(screen.getByText('You are eligible')).toBeInTheDocument();
  });

  it('explains an existing application', () => {
    render(<EligibilityPanel preview={{ ...base, eligible: true, reasons: [], alreadyApplied: true }} loading={false} error={null} />);
    expect(screen.getByText('You have already applied')).toBeInTheDocument();
  });

  it('shows a dependency error', () => {
    render(<EligibilityPanel preview={undefined} loading={false}
      error={{ status: 503, message: 'Eligibility could not be verified. Try again shortly.', reasons: [], fieldErrors: {} }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Eligibility could not be verified');
  });
});
