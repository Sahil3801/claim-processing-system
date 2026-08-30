import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert } from '../components/Feedback';

export function RegisterPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  if (session) return <Navigate to="/" replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (form.password !== form.confirmPassword) { setError('Passwords do not match.'); return; }
    setLoading(true);
    try {
      await register(form.username.trim(), form.email.trim(), form.password);
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel auth-intro">
        <div className="brand brand-light"><span className="brand-mark">CP</span><span>Claims Portal</span></div>
        <div><p className="eyebrow">Start with confidence</p><h1>Your claims.<br />One clear view.</h1><p>Create an account to submit new claims and follow each decision.</p></div>
        <p className="auth-footnote">Claimant registration</p>
      </section>
      <section className="auth-panel auth-form-panel">
        <form className="auth-form" onSubmit={handleSubmit}>
          <div><p className="eyebrow">Create account</p><h2>Register as a claimant</h2><p className="muted">All fields are required.</p></div>
          {error && <ErrorAlert message={error} />}
          <label>Username<input required maxLength={100} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
          <label>Email address<input required type="email" maxLength={255} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <label>Password<input required type="password" minLength={8} maxLength={72} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
          <label>Confirm password<input required type="password" value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} /></label>
          <button className="button button-primary button-full" disabled={loading}>{loading ? 'Creating account…' : 'Create account'}</button>
          <p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
        </form>
      </section>
    </main>
  );
}
