import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const navClass = ({ isActive }: { isActive: boolean }) => isActive ? 'nav-link active' : 'nav-link';

export function AppShell() {
  const { session, logout } = useAuth();
  if (!session) return null;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">CP</span><span>Claims Portal</span></div>
        <nav className="sidebar-nav">
          {session.role === 'CLAIMANT' && <>
            <NavLink className={navClass} to="/dashboard">Overview</NavLink>
            <NavLink className={navClass} to="/claims">My claims</NavLink>
            <NavLink className={navClass} to="/claims/new">Create claim</NavLink>
          </>}
          {(session.role === 'CLAIMS_OFFICER' || session.role === 'ADMIN') && <>
            <NavLink className={navClass} to="/officer">Officer dashboard</NavLink>
            <NavLink className={navClass} to="/officer/claims">Claims queue</NavLink>
          </>}
          {session.role === 'ADMIN' && <NavLink className={navClass} to="/reports">Reporting</NavLink>}
        </nav>
        <div className="account-block">
          <span className="account-avatar">{session.username.slice(0, 1).toUpperCase()}</span>
          <span><strong>{session.username}</strong><small>{session.role.replace('_', ' ')}</small></span>
          <button className="link-button" type="button" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main-content"><Outlet /></main>
    </div>
  );
}
