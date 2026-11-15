import type { ApplicationStatus } from '../types';
import { STATUS_LABELS, recruiterTransitions } from '../types/enums';

interface Props {
  current: ApplicationStatus;
  busy?: boolean;
  onChange: (status: ApplicationStatus) => void;
}

/** Buttons for the valid next statuses only (keyboard reachable, labelled group). */
export function StatusControl({ current, busy, onChange }: Props) {
  const options = recruiterTransitions(current);
  if (options.length === 0) {
    return <p className="muted">This application is {STATUS_LABELS[current].toLowerCase()}; no further changes are possible.</p>;
  }
  return (
    <div role="group" aria-label="Change application status" className="button-row">
      {options.map((status) => (
        <button
          key={status}
          type="button"
          className={`btn ${status === 'REJECTED' ? 'btn-danger' : 'btn-primary'}`}
          disabled={busy}
          onClick={() => onChange(status)}
        >
          Mark {STATUS_LABELS[status].toLowerCase()}
        </button>
      ))}
    </div>
  );
}
