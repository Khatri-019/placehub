import type { Page } from '../types';

export function Pagination<T>({ page, onChange }: { page: Page<T>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn btn-secondary btn-sm" disabled={page.page === 0}
        onClick={() => onChange(page.page - 1)}>
        Previous
      </button>
      <span>
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} total
      </span>
      <button type="button" className="btn btn-secondary btn-sm" disabled={page.page + 1 >= page.totalPages}
        onClick={() => onChange(page.page + 1)}>
        Next
      </button>
    </nav>
  );
}
