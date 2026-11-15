import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { ThemeToggle } from './ThemeToggle';

/** Two-column auth layout: navy brand panel (with the gradient band) and the form panel. */
export function AuthLayout({ heading, blurb, children, asideFooter }: {
  heading: string;
  blurb: string;
  children: ReactNode;
  asideFooter?: ReactNode;
}) {
  return (
    <div className="auth-page">
      <aside className="auth-aside">
        <Link to="/" className="brand-mark"><span className="dot" aria-hidden="true">P</span>PlaceHub <span className="lite">Lite</span></Link>
        <div>
          <p className="label" style={{ color: 'var(--color-sidebar-label)' }}>Campus placements</p>
          <h1>{heading}</h1>
          <p>{blurb}</p>
        </div>
        <div className="small">{asideFooter}</div>
      </aside>
      <main className="auth-main" id="main-content">
        <div className="auth-card">
          <div className="row-between" style={{ marginBottom: 'var(--space-4)' }}>
            <Link to="/" className="small">← Home</Link>
            <ThemeToggle />
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}
