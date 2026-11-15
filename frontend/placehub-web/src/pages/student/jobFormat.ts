/** "Closes in 3 days" style countdown (client-side, informational). */
export function deadlineLabel(iso: string, now = Date.now()): { text: string; urgent: boolean } {
  const ms = new Date(iso).getTime() - now;
  if (ms <= 0) return { text: 'Closed', urgent: true };
  const hours = Math.floor(ms / 3_600_000);
  if (hours < 24) return { text: `Closes in ${Math.max(hours, 1)}h`, urgent: true };
  const days = Math.floor(hours / 24);
  return { text: `Closes in ${days} day${days === 1 ? '' : 's'}`, urgent: days <= 3 };
}
