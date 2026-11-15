import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { notificationApi } from '../api/endpoints';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../types';
import styles from './Layout.module.css';

const NAV: Record<Role, { to: string; label: string; end?: boolean }[]> = {
  STUDENT: [
    { to: '/student', label: 'Dashboard', end: true },
    { to: '/student/jobs', label: 'Browse jobs' },
    { to: '/student/applications', label: 'My applications' },
    { to: '/student/profile', label: 'Profile' },
  ],
  RECRUITER: [
    { to: '/recruiter', label: 'Dashboard', end: true },
    { to: '/recruiter/company', label: 'Company' },
    { to: '/recruiter/jobs', label: 'My jobs' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', end: true },
    { to: '/admin/users', label: 'Users' },
    { to: '/admin/jobs', label: 'Jobs' },
    { to: '/admin/applications', label: 'Applications' },
  ],
};

const UNREAD_POLL_MS = 30_000;

/** Sidebar on desktop (>= 960px); top bar with a drawer on smaller screens. */
export function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  useEffect(() => setDrawerOpen(false), [location.pathname]);

  useEffect(() => {
    let cancelled = false;
    const refresh = () =>
      notificationApi.unreadCount().then((n) => !cancelled && setUnread(n)).catch(() => undefined);
    refresh();
    const timer = window.setInterval(refresh, UNREAD_POLL_MS);
    window.addEventListener('placehub:notifications-changed', refresh);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('placehub:notifications-changed', refresh);
    };
  }, []);

  if (!user) return null;

  const logout = () => {
    signOut();
    navigate('/login');
  };

  const nav = (
    <nav aria-label="Main">
      <ul className={styles.navList}>
        {NAV[user.role].map((item) => (
          <li key={item.to}>
            <NavLink to={item.to} end={item.end}
              className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
              {item.label}
            </NavLink>
          </li>
        ))}
        <li>
          <NavLink to="/notifications" className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
            Notifications
            {unread > 0 && <span className={styles.unread} aria-label={`${unread} unread`}>{unread}</span>}
          </NavLink>
        </li>
      </ul>
    </nav>
  );

  return (
    <div className={styles.shell}>
      <aside className={`${styles.sidebar} ${drawerOpen ? styles.open : ''}`} id="app-drawer">
        <div className={styles.brand}>PlaceHub <span>Lite</span></div>
        {nav}
        <div className={styles.userBox}>
          <div className={styles.userName}>{user.fullName}</div>
          <div className={styles.userRole}>{user.role.toLowerCase()}</div>
          <button type="button" className="btn btn-secondary btn-sm" onClick={logout}>Sign out</button>
        </div>
      </aside>
      {drawerOpen && <div className={styles.backdrop} onClick={() => setDrawerOpen(false)} aria-hidden="true" />}
      <div className={styles.main}>
        <header className={styles.topbar}>
          <button type="button" className={styles.menuButton} aria-controls="app-drawer" aria-expanded={drawerOpen}
            aria-label="Open navigation" onClick={() => setDrawerOpen((o) => !o)}>
            ☰
          </button>
          <span className={styles.brand}>PlaceHub <span>Lite</span></span>
          {unread > 0 && <span className={styles.unread}>{unread}</span>}
        </header>
        <main className={styles.content} id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
