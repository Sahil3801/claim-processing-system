import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createClaim } from '../api/claims';
import { errorMessage } from '../api/client';
import { ErrorAlert } from '../components/Feedback';

export function CreateClaimPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ userId: '', claimAmount: '', claimType: '', description: '', emailId: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault(); setLoading(true); setError('');
    try {
      const claim = await createClaim({
        userId: Number(form.userId), claimAmount: Number(form.claimAmount), claimType: form.claimType.trim(),
        description: form.description.trim(), emailId: form.emailId.trim() || undefined,
      });
      navigate(`/claims/${claim.claimId}`, { state: { created: true } });
    } catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }

  return (
    <div className="page-stack narrow-page">
      <header className="page-header"><div><p className="eyebrow">New claim</p><h1>Tell us what happened</h1><p>Create a draft now. You can review it before submission.</p></div></header>
      <form className="card form-grid" onSubmit={handleSubmit}>
        {error && <div className="form-span"><ErrorAlert message={error} /></div>}
        <label>Claimant ID<span className="field-help">Your numeric user ID</span><input required min="1" type="number" value={form.userId} onChange={(e) => setForm({ ...form, userId: e.target.value })} /></label>
        <label>Claim type<input required maxLength={100} placeholder="e.g. Medical" value={form.claimType} onChange={(e) => setForm({ ...form, claimType: e.target.value })} /></label>
        <label>Claim amount<input required min="0.01" step="0.01" type="number" placeholder="0.00" value={form.claimAmount} onChange={(e) => setForm({ ...form, claimAmount: e.target.value })} /></label>
        <label>Email for updates<span className="field-help">Optional</span><input type="email" maxLength={255} value={form.emailId} onChange={(e) => setForm({ ...form, emailId: e.target.value })} /></label>
        <label className="form-span">Description<textarea required maxLength={2000} rows={6} placeholder="Describe the incident and relevant details…" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /><span className="field-help field-count">{form.description.length}/2000</span></label>
        <div className="form-actions form-span"><Link className="button button-secondary" to="/claims">Cancel</Link><button className="button button-primary" disabled={loading}>{loading ? 'Creating…' : 'Create draft'}</button></div>
      </form>
    </div>
  );
}
