/**
 * Opens a PDF fetched with the bearer header in a new tab via a short-lived blob URL, so tokens never appear in
 * URLs, browser history or server logs. The tab is opened synchronously (before the fetch) so pop-up blockers
 * treat it as user-initiated.
 */
export async function openPdfInNewTab(load: () => Promise<Blob>): Promise<void> {
  const tab = window.open('', '_blank');
  try {
    const blob = await load();
    const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
    if (tab) {
      tab.location.href = url;
    } else {
      window.location.assign(url);
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (err) {
    tab?.close();
    throw err;
  }
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}
