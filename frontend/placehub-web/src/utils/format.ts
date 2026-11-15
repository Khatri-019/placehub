/** Formats an ISO instant in the viewer's local time zone. */
export function formatDateTime(iso: string | undefined | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(iso: string | undefined | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

export function formatCgpa(value: number | undefined | null): string {
  return value === undefined || value === null ? '—' : Number(value).toFixed(2);
}

/** Value for <input type="datetime-local"> in local time. */
export function toLocalInput(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** datetime-local (local time, no zone) to ISO-8601 with offset (UTC "Z"). */
export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}
