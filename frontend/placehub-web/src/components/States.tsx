import type { ReactNode } from 'react';
import type { ParsedError } from '../api/errors';

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state state-empty">
      <p className="state-title">{title}</p>
      {children}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: ParsedError; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">{error.message}</p>
      {error.reasons.length > 0 && (
        <ul className="reasons">
          {error.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      )}
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

/** Renders the right state for an async load, or the children when data is ready. */
export function AsyncView<T>({
  state,
  children,
  empty,
}: {
  state: { data: T | undefined; loading: boolean; error: ParsedError | null; reload: () => void };
  children: (data: T) => ReactNode;
  empty?: (data: T) => ReactNode | null;
}) {
  if (state.loading && state.data === undefined) return <Spinner />;
  if (state.error) return <ErrorState error={state.error} onRetry={state.reload} />;
  if (state.data === undefined) return <Spinner />;
  const emptyView = empty?.(state.data);
  if (emptyView) return <>{emptyView}</>;
  return <>{children(state.data)}</>;
}
