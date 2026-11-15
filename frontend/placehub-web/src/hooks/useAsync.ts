import { useCallback, useEffect, useRef, useState } from 'react';
import { parseError, type ParsedError } from '../api/errors';

export interface AsyncState<T> {
  data: T | undefined;
  loading: boolean;
  error: ParsedError | null;
  reload: () => void;
  setData: (data: T) => void;
}

/**
 * Loads data whenever `deps` change; exposes loading / error / reload for consistent page states.
 * Stale responses from earlier requests are ignored.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ParsedError | null>(null);
  const [tick, setTick] = useState(0);
  const requestId = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps);

  useEffect(() => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    load()
      .then((result) => {
        if (id === requestId.current) setData(result);
      })
      .catch((err: unknown) => {
        if (id === requestId.current) setError(parseError(err));
      })
      .finally(() => {
        if (id === requestId.current) setLoading(false);
      });
  }, [load, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, loading, error, reload, setData };
}
