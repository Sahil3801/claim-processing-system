import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { getClaim, submitClaim, transitionClaim } from '../api/claims';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { readableStatus, StatusBadge } from '../components/StatusBadge';
import type { Claim, ClaimStatus } from '../types';
import { formatCurrency, formatDate } from '../utils';

const standardPath: ClaimStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'SETTLED'];

function StatusTimeline({ claim }: { claim: Claim }) {
  const path = claim.claimStatus === 'REJECTED'
    ? ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'REJECTED'] as ClaimStatus[]
    : standardPath;
  const current = path.indexOf(claim.claimStatus);
  return (
    <ol className="timeline">
      {path.map((status, index) => (
        <li key={status} className={index < current ? 'complete' : index === current ? 'current' : ''}>
          <span className="timeline-dot" />
          <div><strong>{readableStatus(status)}</strong><small>{index === 0 ? `Created ${formatDate(claim.claimDate)}` : index === current ? `Current as of ${formatDate(claim.lastUpdated)}` : index < current ? 'Completed' : 'Next step'}</small></div>
        </li>
      ))}
    </ol>
  );
}

export function ClaimDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const { session } = useAuth();
  const [claim, setClaim] = useState<Claim | null>(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [showReject, setShowReject] = useState(false);
  const [reason, setReason] = useState('');
  const created = (location.state as { created?: boolean } | null)?.created;

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setClaim(await getClaim(Number(id))); }
    catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }, [id]);
  useEffect(() => { void load(); }, [load]);

  async function runAction(action: 'submit' | 'review' | 'approve' | 'reject' | 'settle') {
    if (!claim) return;
    setActing(true); setActionError('');
    try {
      const updated = action === 'submit'
        ? await submitClaim(claim.claimId)
        : await transitionClaim(claim.claimId, action, action === 'reject' ? reason.trim() : undefined);
      setClaim(updated); setShowReject(false); setReason('');
    } catch (requestError) { setActionError(errorMessage(requestError)); }
    finally { setActing(false); }
  }

  const isClaimant = session?.role === 'CLAIMANT';
  return (
    <div className="page-stack">
      <header className="page-header"><div><p className="eyebrow">Claim record</p><h1>Claim #{id}</h1><p>Review the submitted details and current processing state.</p></div><Link className="button button-secondary" to={isClaimant ? '/claims' : '/officer/claims'}>Back to claims</Link></header>
      {created && <div className="alert alert-success">Draft created successfully. Submit it when the details are ready.</div>}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading ? <LoadingState label="Loading claim" /> : claim && <div className="detail-grid">
        <section className="card claim-main">
          <div className="section-heading"><div><h2>Claim details</h2><p>Submitted information</p></div><StatusBadge status={claim.claimStatus} /></div>
          <dl className="detail-list">
            <div><dt>Claim amount</dt><dd>{formatCurrency(claim.claimAmount)}</dd></div>
            <div><dt>Claim type</dt><dd>{claim.claimType}</dd></div>
            <div><dt>Claimant ID</dt><dd>{claim.userId}</dd></div>
            <div><dt>Contact email</dt><dd>{claim.emailId || 'Not provided'}</dd></div>
            <div><dt>Created</dt><dd>{formatDate(claim.claimDate)}</dd></div>
            <div><dt>Last updated</dt><dd>{formatDate(claim.lastUpdated)}</dd></div>
          </dl>
          <div className="description-block"><h3>Description</h3><p>{claim.description}</p></div>
          {actionError && <ErrorAlert message={actionError} />}
          <div className="action-bar">
            {isClaimant && claim.claimStatus === 'DRAFT' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('submit')}>{acting ? 'Submitting…' : 'Submit claim'}</button>}
            {!isClaimant && claim.claimStatus === 'SUBMITTED' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('review')}>{acting ? 'Updating…' : 'Start review'}</button>}
            {!isClaimant && claim.claimStatus === 'UNDER_REVIEW' && <><button className="button button-primary" disabled={acting} onClick={() => void runAction('approve')}>Approve</button><button className="button button-danger" disabled={acting} onClick={() => setShowReject(true)}>Reject</button></>}
            {!isClaimant && claim.claimStatus === 'APPROVED' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('settle')}>{acting ? 'Updating…' : 'Mark settled'}</button>}
            {!((isClaimant && claim.claimStatus === 'DRAFT') || (!isClaimant && ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(claim.claimStatus))) && <span className="muted">No actions are available at this stage.</span>}
          </div>
        </section>
        <aside className="card timeline-card"><h2>Status timeline</h2><p className="muted">Progress through the controlled claim lifecycle.</p><StatusTimeline claim={claim} /><p className="timeline-note">The API currently supplies created and latest-update timestamps; intermediate dates are shown as completed states.</p></aside>
      </div>}
      {showReject && <div className="modal-backdrop" role="presentation"><div className="modal" role="dialog" aria-modal="true" aria-labelledby="reject-title"><h2 id="reject-title">Reject claim #{claim?.claimId}</h2><p>Provide a clear reason. This is required and the decision cannot be advanced afterward.</p><label>Rejection reason<textarea autoFocus required maxLength={1000} rows={5} value={reason} onChange={(e) => setReason(e.target.value)} /></label><div className="form-actions"><button className="button button-secondary" onClick={() => setShowReject(false)}>Cancel</button><button className="button button-danger" disabled={acting || !reason.trim()} onClick={() => void runAction('reject')}>{acting ? 'Rejecting…' : 'Confirm rejection'}</button></div></div></div>}
    </div>
  );
}
