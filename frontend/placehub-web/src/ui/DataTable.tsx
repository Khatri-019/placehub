import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import styles from './DataTable.module.css';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  /** API sort field; makes the header a sort button. */
  sortField?: string;
  align?: 'left' | 'right';
  /** Hide the label in the mobile card layout (e.g. action columns). */
  hideLabelOnMobile?: boolean;
}

export interface SortState {
  field: string;
  dir: 'asc' | 'desc';
}

interface Props<T> {
  caption: string;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  sort?: SortState | null;
  onSort?: (sort: SortState) => void;
  selected?: Set<string>;
  onSelectedChange?: (selected: Set<string>) => void;
  rowLabel?: (row: T) => string;
  isSelectable?: (row: T) => boolean;
}

/**
 * Semantic table with a sticky header, sortable columns and optional row selection for bulk actions. Below 720px
 * each row becomes a labelled card (CSS only).
 */
export function DataTable<T>({
  caption, columns, rows, rowKey, sort, onSort, selected, onSelectedChange, rowLabel, isSelectable,
}: Props<T>) {
  const selectable = !!selected && !!onSelectedChange;
  const selectableRows = rows.filter((r) => !isSelectable || isSelectable(r));
  const allSelected = selectable && selectableRows.length > 0 && selectableRows.every((r) => selected.has(rowKey(r)));

  const toggleAll = () => {
    if (!selectable) return;
    const next = new Set(selected);
    if (allSelected) selectableRows.forEach((r) => next.delete(rowKey(r)));
    else selectableRows.forEach((r) => next.add(rowKey(r)));
    onSelectedChange(next);
  };

  const toggle = (id: string) => {
    if (!selectable) return;
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onSelectedChange(next);
  };

  const sortButton = (c: Column<T>) => {
    if (!c.sortField || !onSort) return c.header;
    const activeSort = sort?.field === c.sortField ? sort.dir : null;
    const nextDir = activeSort === 'asc' ? 'desc' : 'asc';
    return (
      <button type="button" className={styles.sort} onClick={() => onSort({ field: c.sortField!, dir: nextDir })}>
        {c.header}
        {activeSort === 'asc' ? <ArrowUp aria-hidden="true" /> : activeSort === 'desc' ? <ArrowDown aria-hidden="true" />
          : <ArrowUpDown aria-hidden="true" />}
      </button>
    );
  };

  return (
    <div className={`table-wrap ${styles.wrap}`}>
      <table className={`table ${styles.table}`}>
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            {selectable && (
              <th scope="col" className={styles.checkCell}>
                <input type="checkbox" aria-label="Select all rows" checked={allSelected} onChange={toggleAll}
                  disabled={selectableRows.length === 0} />
              </th>
            )}
            {columns.map((c) => (
              <th key={c.key} scope="col" style={{ textAlign: c.align ?? 'left' }}
                aria-sort={sort && c.sortField && sort.field === c.sortField ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                {sortButton(c)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const id = rowKey(row);
            const canSelect = !isSelectable || isSelectable(row);
            return (
              <tr key={id} className={selected?.has(id) ? styles.selected : undefined}>
                {selectable && (
                  <td className={styles.checkCell}>
                    <input type="checkbox" aria-label={`Select ${rowLabel ? rowLabel(row) : id}`}
                      checked={selected.has(id)} onChange={() => toggle(id)} disabled={!canSelect} />
                  </td>
                )}
                {columns.map((c) => (
                  <td key={c.key} data-label={c.hideLabelOnMobile ? undefined : c.header}
                    style={{ textAlign: c.align ?? 'left' }}>
                    {c.render(row)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
