import { useEffect, useRef } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { UserRole } from '../types';

const navClass = ({ isActive }: { isActive: boolean }) => isActive ? 'nav-link active' : 'nav-link';

const roleLabels: Record<UserRole, string> = {
  CLAIMANT: 'Claimant',
  CLAIMS_OFFICER: 'Claims officer',
  ADMIN: 'Administrator',
};

export function AppShell() {
  const { session, logout } = useAuth();
  const { pathname } = useLocation();
  const navRef = useRef<HTMLElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  // On narrow screens the nav is a horizontal strip; keep the current page's tab visible.
  useEffect(() => {
    navRef.current?.querySelector<HTMLElement>('.nav-link.active')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
    // After navigation (including signing in), start keyboard and screen-reader users at the new page's
    // content. Scroll to the top ourselves: letting focus scroll would tuck the title under the sticky
    // mobile header.
    mainRef.current?.focus({ preventScroll: true });
    window.scrollTo?.(0, 0);
  }, [pathname]);
  if (!session) return null;
  const isStaff = session.role === 'CLAIMS_OFFICER' || session.role === 'ADMIN';

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark" aria-hidden="true">CP</span><span className="brand-name">Claims Portal</span></div>
        <nav className="sidebar-nav" aria-label="Main" ref={navRef}>
          {session.role === 'CLAIMANT' && <div className="nav-group">
            <span className="nav-group-label">My workspace</span>
            <NavLink className={navClass} to="/dashboard">Overview</NavLink>
            <NavLink className={navClass} to="/claims" end>My claims</NavLink>
            <NavLink className={navClass} to="/claims/new">New claim</NavLink>
          </div>}
          {isStaff && <div className="nav-group">
            <span className="nav-group-label">Operations</span>
            <NavLink className={navClass} to="/officer" end>Officer dashboard</NavLink>
            <NavLink className={navClass} to="/officer/claims">Claims queue</NavLink>
          </div>}
          {session.role === 'ADMIN' && <div className="nav-group">
            <span className="nav-group-label">Administration</span>
            <NavLink className={navClass} to="/reports">Reporting</NavLink>
          </div>}
        </nav>
        <div className="account-block">
          <span className="account-avatar" aria-hidden="true">{session.username.slice(0, 1).toUpperCase()}</span>
          <span className="account-text"><strong title={session.username}>{session.username}</strong><small>{roleLabels[session.role]}</small></span>
          <button className="button button-ghost button-small account-signout" type="button" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main-content" id="main-content" tabIndex={-1} ref={mainRef}><Outlet /></main>
    </div>
  );
}
