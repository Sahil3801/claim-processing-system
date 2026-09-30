import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createClaim } from '../api/claims';
import { errorMessage } from '../api/client';
import { ErrorAlert } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';

// Suggestions only: claim type stays free text, exactly as the API accepts it.
const typeSuggestions = ['Medical', 'Auto', 'Home', 'Travel', 'Life'];
const DESCRIPTION_MAX = 2000;

export function CreateClaimPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ claimAmount: '', claimType: '', description: '', emailId: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault(); setLoading(true); setError('');
    try {
      const claim = await createClaim({
        claimAmount: Number(form.claimAmount), claimType: form.claimType.trim(),
        description: form.description.trim(), emailId: form.emailId.trim() || undefined,
      });
      navigate(`/claims/${claim.claimId}`, { state: { created: true } });
    } catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }

  return (
    <div className="page-stack narrow-page">
      <PageHeader crumbs={[{ label: 'My claims', to: '/claims' }, { label: 'New claim' }]} title="New claim" description="Your claim is saved as a draft first, so you can check it before you submit it." />
      <form className="panel form-panel" onSubmit={handleSubmit} noValidate={false}>
        {error && <ErrorAlert message={error} />}
        <div className="form-section"><fieldset className="form-fieldset">
          <legend>Claim</legend>
          <div className="form-grid">
            <div className="field">
              <label className="field-label" htmlFor="claim-type">Claim type<span className="required-mark" aria-hidden="true">*</span></label>
              <input id="claim-type" required maxLength={100} list="claim-type-suggestions" autoComplete="off" aria-describedby="claim-type-help" value={form.claimType} onChange={(e) => setForm({ ...form, claimType: e.target.value })} />
              <span className="field-help" id="claim-type-help">Such as Medical, Auto, or Home.</span>
            </div>
            <datalist id="claim-type-suggestions">{typeSuggestions.map((type) => <option key={type} value={type} />)}</datalist>
            <div className="field">
              <label className="field-label" htmlFor="claim-amount">Claim amount<span className="required-mark" aria-hidden="true">*</span></label>
              <span className="input-affix"><span className="input-prefix" aria-hidden="true">$</span><input id="claim-amount" required min="0.01" step="0.01" type="number" inputMode="decimal" placeholder="0.00" aria-describedby="claim-amount-help" value={form.claimAmount} onChange={(e) => setForm({ ...form, claimAmount: e.target.value })} /></span>
              <span className="field-help" id="claim-amount-help">In US dollars, up to two decimal places.</span>
            </div>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="claim-description">Description<span className="required-mark" aria-hidden="true">*</span></label>
            <textarea id="claim-description" required maxLength={DESCRIPTION_MAX} rows={7} placeholder="Describe what happened, when it happened, and what it cost. Include any reference numbers." aria-describedby="claim-description-count" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <span className={`field-count${form.description.length > DESCRIPTION_MAX - 100 ? ' field-count-near' : ''}`} id="claim-description-count">{form.description.length}/{DESCRIPTION_MAX} characters</span>
          </div>
        </fieldset></div>
        <div className="form-section"><fieldset className="form-fieldset">
          <legend>Contact</legend>
          <div className="field field-half">
            <label className="field-label" htmlFor="claim-email">Email for updates<span className="field-optional">Optional</span></label>
            <input id="claim-email" type="email" maxLength={255} autoComplete="email" aria-describedby="claim-email-help" value={form.emailId} onChange={(e) => setForm({ ...form, emailId: e.target.value })} />
            <span className="field-help" id="claim-email-help">Leave blank to use your account email address.</span>
          </div>
        </fieldset></div>
        <div className="form-footer">
          <span className="field-help"><span aria-hidden="true">*</span> Required</span>
          <div className="form-actions"><Link className="button button-secondary" to="/claims">Cancel</Link><button className="button button-primary" disabled={loading}>{loading ? 'Saving draft…' : 'Save draft'}</button></div>
        </div>
      </form>
    </div>
  );
}
