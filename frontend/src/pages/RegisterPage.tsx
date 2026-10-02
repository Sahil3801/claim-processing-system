import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import { errorCode, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert, Notice, useSlow, WAKE_MESSAGE } from '../components/Feedback';
import { usePageTitle } from '../usePageTitle';

export function RegisterPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const waking = useSlow(loading);
  usePageTitle('Create account');
  if (session) return <Navigate to="/" replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (form.password !== form.confirmPassword) { setError('Passwords do not match.'); return; }
    setLoading(true);
    try {
      await register(form.username.trim(), form.email.trim(), form.password);
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (requestError) {
      setError(errorCode(requestError) === 'DATA_INTEGRITY_CONFLICT'
        ? 'That username or email address is already registered. Sign in or use different details.'
        : errorMessage(requestError));
    }
    finally { setLoading(false); }
  }

  return (
    <main className="auth-page">
      <div className="auth-column">
        <div className="brand auth-brand"><span className="brand-mark" aria-hidden="true">CP</span><span className="brand-name">Claims Portal</span></div>
        <form className="auth-card" onSubmit={handleSubmit}>
          <div className="auth-heading"><h1>Create a claimant account</h1><p>Submit your own claims and track each decision. Staff accounts are set up by an administrator.</p></div>
          {error && <ErrorAlert message={error} />}
          {waking && <Notice tone="info">{WAKE_MESSAGE}</Notice>}
          <label className="field">Username<input required maxLength={100} autoComplete="username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
          <label className="field">Email address<input required type="email" maxLength={255} autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <div className="field"><label className="field-label" htmlFor="register-password">Password</label><input id="register-password" required type="password" minLength={8} maxLength={72} autoComplete="new-password" aria-describedby="password-help" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /><span className="field-help" id="password-help">At least 8 characters.</span></div>
          <label className="field">Confirm password<input required type="password" autoComplete="new-password" value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} /></label>
          <button className="button button-primary button-full" disabled={loading}>{waking ? 'Waking up the server…' : loading ? 'Creating account…' : 'Create account'}</button>
        </form>
        <p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
      </div>
    </main>
  );
}
