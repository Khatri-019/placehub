import type { ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import type { ParsedError } from '../api/errors';
import { EmptyState as Empty, type Illustration } from '../ui/EmptyState';
import { Skeleton } from '../ui/Skeleton';

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return <Skeleton lines={3} label={label} />;
}

export function EmptyState({ title, children, illustration }: { title: string; children?: ReactNode; illustration?: Illustration }) {
  return <Empty title={title} illustration={illustration}>{children}</Empty>;
}

export function ErrorState({ error, onRetry }: { error: ParsedError; onRetry?: () => void }) {
  return (
    <div className="banner banner-danger" role="alert">
      <AlertTriangle aria-hidden="true" />
      <div className="stack-sm">
        <strong>{error.message}</strong>
        {error.reasons.length > 0 && (
          <ul className="reasons">{error.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
        )}
        {onRetry && (
          <div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={onRetry}>
              <RotateCcw aria-hidden="true" /> Try again
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

/** Renders the right state for an async load (skeleton, error with retry, empty), or the children when ready. */
export function AsyncView<T>({
  state,
  children,
  empty,
  skeleton,
}: {
  state: { data: T | undefined; loading: boolean; error: ParsedError | null; reload: () => void };
  children: (data: T) => ReactNode;
  empty?: (data: T) => ReactNode | null;
  skeleton?: ReactNode;
}) {
  if (state.loading && state.data === undefined) return <>{skeleton ?? <Skeleton lines={4} card />}</>;
  if (state.error) return <ErrorState error={state.error} onRetry={state.reload} />;
  if (state.data === undefined) return <>{skeleton ?? <Skeleton lines={4} card />}</>;
  const emptyView = empty?.(state.data);
  if (emptyView) return <>{emptyView}</>;
  return <>{children(state.data)}</>;
}
