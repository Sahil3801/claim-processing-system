import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage, errorStatus } from '../api/client';
import { ErrorAlert, Notice } from '../components/Feedback';
import { usePageTitle } from '../usePageTitle';

export function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const registered = (location.state as { registered?: boolean } | null)?.registered;
  usePageTitle('Sign in');

  if (session) return <Navigate to="/" replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true); setError('');
    try {
      const next = await login(username.trim(), password);
      navigate(next.role === 'CLAIMANT' ? '/dashboard' : '/officer', { replace: true });
    } catch (requestError) {
      setError(errorStatus(requestError) === 401 ? 'Incorrect username or password.' : errorMessage(requestError));
    } finally { setLoading(false); }
  }

  return (
    <main className="auth-page">
      <div className="auth-column">
        <div className="brand auth-brand"><span className="brand-mark" aria-hidden="true">CP</span><span className="brand-name">Claims Portal</span></div>
        <form className="auth-card" onSubmit={handleSubmit}>
          <div className="auth-heading"><h1>Sign in</h1><p>Use your claimant account, or the staff account your administrator set up.</p></div>
          {registered && <Notice tone="success">Account created. Sign in to continue.</Notice>}
          {error && <ErrorAlert message={error} />}
          <label className="field">Username<input autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} /></label>
          <label className="field">Password<input autoComplete="current-password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          <button className="button button-primary button-full" disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button>
        </form>
        <p className="auth-switch">New claimant? <Link to="/register">Create an account</Link></p>
      </div>
    </main>
  );
}
