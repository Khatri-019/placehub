import { useState } from 'react';
import { Moon, Sun } from 'lucide-react';
import { effectiveTheme, saveTheme, type Theme } from '../utils/theme';

/** Light/dark toggle; the choice is saved in localStorage (guarded) and applied via data-theme on <html>. */
export function ThemeToggle({ className = 'btn btn-ghost btn-icon' }: { className?: string }) {
  const [theme, setTheme] = useState<Theme>(() => effectiveTheme());
  const next: Theme = theme === 'dark' ? 'light' : 'dark';
  return (
    <button type="button" className={className} aria-label={`Switch to ${next} theme`} aria-pressed={theme === 'dark'}
      title={`Switch to ${next} theme`}
      onClick={() => { saveTheme(next); setTheme(next); }}>
      {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
    </button>
  );
}
