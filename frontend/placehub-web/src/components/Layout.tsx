import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, Outlet, matchPath, useLocation, useNavigate } from 'react-router';
import {
  Bell, Briefcase, Building2, ChevronDown, ClipboardList, FileCheck2, FileText, Gauge, Landmark, LogOut, Menu,
  Scale, Search, Users, UserRound,
} from 'lucide-react';
import { notificationApi } from '../api/endpoints';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../types';
import { Drawer } from '../ui/Modal';
import { PolicyRibbon } from './PolicyRibbon';
import { ThemeToggle } from './ThemeToggle';
import styles from './Layout.module.css';

interface NavItem { to: string; label: string; icon: ReactNode; end?: boolean }
interface NavGroup { label: string; items: NavItem[] }

const NAV: Record<Role, NavGroup[]> = {
  STUDENT: [
    { label: 'Overview', items: [{ to: '/student', label: 'Dashboard', icon: <Gauge />, end: true }] },
    { label: 'Placements', items: [
      { to: '/student/jobs', label: 'Browse jobs', icon: <Search /> },
      { to: '/student/applications', label: 'My applications', icon: <ClipboardList /> },
    ] },
    { label: 'Account', items: [{ to: '/student/profile', label: 'Profile & resume', icon: <UserRound /> }] },
  ],
  RECRUITER: [
    { label: 'Overview', items: [{ to: '/recruiter', label: 'Dashboard', icon: <Gauge />, end: true }] },
    { label: 'Hiring', items: [
      { to: '/recruiter/jobs', label: 'My jobs', icon: <Briefcase /> },
      { to: '/recruiter/company', label: 'Company', icon: <Building2 /> },
    ] },
  ],
  ADMIN: [
    { label: 'Overview', items: [{ to: '/admin', label: 'Dashboard', icon: <Gauge />, end: true }] },
    { label: 'Placement office', items: [
      { to: '/admin/policy', label: 'Placement policy', icon: <Scale /> },
      { to: '/admin/placements', label: 'Placements', icon: <Landmark /> },
      { to: '/admin/consents', label: 'Consents', icon: <FileCheck2 /> },
    ] },
    { label: 'Records', items: [
      { to: '/admin/users', label: 'Users', icon: <Users /> },
      { to: '/admin/jobs', label: 'Jobs', icon: <Briefcase /> },
      { to: '/admin/applications', label: 'Applications', icon: <FileText /> },
    ] },
  ],
};

/** Page title and breadcrumb trail per route (first match wins). */
const TITLES: { pattern: string; title: string; crumbs?: { label: string; to: string }[] }[] = [
  { pattern: '/student', title: 'Dashboard' },
  { pattern: '/student/jobs', title: 'Browse jobs' },
  { pattern: '/student/jobs/:id', title: 'Job details', crumbs: [{ label: 'Browse jobs', to: '/student/jobs' }] },
  { pattern: '/student/applications', title: 'My applications' },
  { pattern: '/student/profile', title: 'Profile & resume' },
  { pattern: '/recruiter', title: 'Dashboard' },
  { pattern: '/recruiter/company', title: 'Company profile' },
  { pattern: '/recruiter/jobs', title: 'My jobs' },
  { pattern: '/recruiter/jobs/new', title: 'Post a job', crumbs: [{ label: 'My jobs', to: '/recruiter/jobs' }] },
  { pattern: '/recruiter/jobs/:id/edit', title: 'Edit job', crumbs: [{ label: 'My jobs', to: '/recruiter/jobs' }] },
  { pattern: '/recruiter/jobs/:id/applicants', title: 'Applicants', crumbs: [{ label: 'My jobs', to: '/recruiter/jobs' }] },
  { pattern: '/recruiter/applications/:id', title: 'Application review', crumbs: [{ label: 'My jobs', to: '/recruiter/jobs' }] },
  { pattern: '/admin', title: 'Dashboard' },
  { pattern: '/admin/policy', title: 'Placement policy' },
  { pattern: '/admin/placements', title: 'Placements' },
  { pattern: '/admin/consents', title: 'Consents' },
  { pattern: '/admin/users', title: 'Users' },
  { pattern: '/admin/jobs', title: 'Jobs' },
  { pattern: '/admin/applications', title: 'Applications' },
  { pattern: '/notifications', title: 'Notifications' },
];

const UNREAD_POLL_MS = 30_000;
const ROLE_LABEL: Record<Role, string> = { STUDENT: 'Student', RECRUITER: 'Recruiter', ADMIN: 'Placement office' };

export function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => { setDrawerOpen(false); setMenuOpen(false); }, [location.pathname]);

  useEffect(() => {
    let cancelled = false;
    const refresh = () => notificationApi.unreadCount().then((n) => !cancelled && setUnread(n)).catch(() => undefined);
    refresh();
    const timer = window.setInterval(refresh, UNREAD_POLL_MS);
    window.addEventListener('placehub:notifications-changed', refresh);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('placehub:notifications-changed', refresh);
    };
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', close); };
  }, [menuOpen]);

  const page = useMemo(() => TITLES.find((t) => matchPath(t.pattern, location.pathname)), [location.pathname]);
  useEffect(() => { document.title = page ? `${page.title} · PlaceHub Lite` : 'PlaceHub Lite'; }, [page]);

  if (!user) return null;

  const logout = () => {
    signOut();
    navigate(`/login/${user.role.toLowerCase()}`);
  };

  const nav = (
    <nav aria-label="Main" className={styles.nav}>
      {NAV[user.role].map((group) => (
        <div key={group.label} className={styles.group}>
          <div className={styles.groupLabel}>{group.label}</div>
          <ul className={styles.navList}>
            {group.items.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
                  <span aria-hidden="true" className={styles.navIcon}>{item.icon}</span>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
      <div className={styles.group}>
        <ul className={styles.navList}>
          <li>
            <NavLink to="/notifications" className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
              <span aria-hidden="true" className={styles.navIcon}><Bell /></span>
              Notifications
              {unread > 0 && <span className={styles.unread} aria-label={`${unread} unread`}>{unread}</span>}
            </NavLink>
          </li>
        </ul>
      </div>
    </nav>
  );

  return (
    <div className={styles.shell}>
      <a href="#main-content" className="skip-link">Skip to content</a>
      <aside className={styles.sidebar}>
        <Link to="/" className={`brand-mark ${styles.brand}`}><span className="dot" aria-hidden="true">P</span>PlaceHub <span className="lite">Lite</span></Link>
        {nav}
      </aside>
      <Drawer open={drawerOpen} title="Menu" onClose={() => setDrawerOpen(false)}>
        <div className={styles.drawerNav}>{nav}</div>
      </Drawer>
      <div className={styles.main}>
        {user.role === 'STUDENT' && <PolicyRibbon />}
        <header className={styles.topbar}>
          <button type="button" className={`btn btn-ghost btn-icon ${styles.menuButton}`} aria-label="Open navigation"
            aria-expanded={drawerOpen} onClick={() => setDrawerOpen(true)}>
            <Menu aria-hidden="true" />
          </button>
          <div className={styles.titleBlock}>
            {page?.crumbs && (
              <nav aria-label="Breadcrumb">
                <ol className={styles.crumbs}>
                  {page.crumbs.map((c) => <li key={c.to}><Link to={c.to}>{c.label}</Link></li>)}
                  <li aria-current="page">{page.title}</li>
                </ol>
              </nav>
            )}
            <div className={styles.pageTitle}>{page?.title ?? 'PlaceHub Lite'}</div>
          </div>
          <div className={styles.actions}>
            <ThemeToggle />
            <Link to="/notifications" className={`btn btn-ghost btn-icon ${styles.bell}`} aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
              <Bell aria-hidden="true" />
              {unread > 0 && <span className={styles.bellDot} aria-hidden="true" />}
            </Link>
            <div className={styles.userMenu} ref={menuRef}>
              <button type="button" className={styles.userButton} aria-haspopup="menu" aria-expanded={menuOpen}
                aria-label={`Account menu for ${user.fullName}`}
                onClick={() => setMenuOpen((o) => !o)}>
                <span className={styles.avatar} aria-hidden="true">{user.fullName.slice(0, 1).toUpperCase()}</span>
                <span className={styles.userText}>
                  <span className={styles.userName}>{user.fullName}</span>
                  <span className={styles.userRole}>{ROLE_LABEL[user.role]}</span>
                </span>
                <ChevronDown aria-hidden="true" className={styles.chev} />
              </button>
              {menuOpen && (
                <div role="menu" className={styles.menu}>
                  <div className={styles.menuHead}>
                    <div className={styles.userName}>{user.fullName}</div>
                    <div className="small muted">{user.email}</div>
                  </div>
                  <button type="button" role="menuitem" className={styles.menuItem} onClick={logout}>
                    <LogOut aria-hidden="true" /> Sign out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className={styles.content} id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
