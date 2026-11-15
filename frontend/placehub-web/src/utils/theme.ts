export type Theme = 'light' | 'dark';

const KEY = 'placehub.theme';

/** Saved preference, or null to follow prefers-color-scheme. localStorage can throw (privacy modes), so guard it. */
export function savedTheme(): Theme | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch {
    return null;
  }
}

export function effectiveTheme(): Theme {
  const saved = savedTheme();
  if (saved) return saved;
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(theme: Theme | null): void {
  const root = document.documentElement;
  if (theme) root.setAttribute('data-theme', theme);
  else root.removeAttribute('data-theme');
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    // Not persisted (e.g. storage disabled); the choice still applies for this page view.
  }
  applyTheme(theme);
}

export function readFlag(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeFlag(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore: dismissal simply is not remembered
  }
}
