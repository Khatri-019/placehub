import type { ReactNode } from 'react';

interface Props {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: (a11y: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}

/** Label + control + error/hint wiring so every input is labelled and errors are announced. */
export function Field({ id, label, error, hint, children }: Props) {
  const describedBy = [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(' ');
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': !!error, 'aria-describedby': describedBy || undefined })}
      {hint && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  );
}
