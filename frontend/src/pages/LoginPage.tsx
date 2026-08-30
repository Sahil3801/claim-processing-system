import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage } from '../api/client';
import { ErrorAlert } from '../components/Feedback';

export function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const registered = (location.state as { registered?: boolean } | null)?.registered;

  if (session) return <Navigate to="/" replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true); setError('');
    try {
      const next = await login(username.trim(), password);
      navigate(next.role === 'CLAIMANT' ? '/dashboard' : '/officer', { replace: true });
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally { setLoading(false); }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel auth-intro">
        <div className="brand brand-light"><span className="brand-mark">CP</span><span>Claims Portal</span></div>
        <div><p className="eyebrow">Claims, without the clutter</p><h1>Clear decisions.<br />Visible progress.</h1><p>Submit, review, and track insurance claims from one focused workspace.</p></div>
        <p className="auth-footnote">Secure role-based claims processing</p>
      </section>
      <section className="auth-panel auth-form-panel">
        <form className="auth-form" onSubmit={handleSubmit}>
          <div><p className="eyebrow">Welcome back</p><h2>Sign in to your account</h2><p className="muted">Enter your credentials to continue.</p></div>
          {registered && <div className="alert alert-success">Registration complete. You can sign in now.</div>}
          {error && <ErrorAlert message={error} />}
          <label>Username<input autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} /></label>
          <label>Password<input autoComplete="current-password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          <button className="button button-primary button-full" disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button>
          <p className="auth-switch">New claimant? <Link to="/register">Create an account</Link></p>
        </form>
      </section>
    </main>
  );
}
