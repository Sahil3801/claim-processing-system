import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { getClaim, submitClaim, transitionClaim } from '../api/claims';
import { errorCode, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert, LoadingState, Notice } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { readableStatus, StatusBadge } from '../components/StatusBadge';
import type { Claim, ClaimStatus } from '../types';
import { formatClaimType, formatCurrency, formatDate } from '../utils';

// Another user changed the claim first: its status moved on, or a concurrent write won.
const staleClaimErrors = new Set(['CONCURRENT_CLAIM_UPDATE', 'INVALID_CLAIM_TRANSITION']);

// Matches the API's limit for a rejection reason.
const REASON_MAX = 500;

type ClaimAction = 'submit' | 'review' | 'approve' | 'reject' | 'settle';

const actionResults: Record<ClaimAction, string> = {
  submit: 'Claim submitted. The claims team will pick it up for review.',
  review: 'Review started. You can now approve or reject this claim.',
  approve: 'Claim approved. It is ready to be settled.',
  reject: 'Claim rejected. The reason has been recorded in its history.',
  settle: 'Claim marked as settled. No further actions are needed.',
};

const standardPath: ClaimStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'SETTLED'];

function StatusTimeline({ claim }: { claim: Claim }) {
  const path = claim.claimStatus === 'REJECTED'
    ? ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'REJECTED'] as ClaimStatus[]
    : standardPath;
  const current = path.indexOf(claim.claimStatus);
  const finished = claim.claimStatus === 'SETTLED' || claim.claimStatus === 'REJECTED';
  return (
    <ol className="timeline">
      {path.map((status, index) => (
        <li key={status} className={index < current ? 'complete' : index === current ? `current${finished ? ` final final-${status.toLowerCase()}` : ''}` : ''}>
          <span className="timeline-dot" aria-hidden="true" />
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
  const [staleNotice, setStaleNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [showReject, setShowReject] = useState(false);
  const [reason, setReason] = useState('');
  const [doneNotice, setDoneNotice] = useState('');
  const rejectButton = useRef<HTMLButtonElement>(null);
  const created = (location.state as { created?: boolean } | null)?.created;

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setClaim(await getClaim(Number(id))); }
    catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }, [id]);
  useEffect(() => { void load(); }, [load]);

  async function runAction(action: ClaimAction) {
    if (!claim) return;
    setActing(true); setActionError(''); setStaleNotice(''); setDoneNotice('');
    try {
      const updated = action === 'submit'
        ? await submitClaim(claim.claimId)
        : await transitionClaim(claim.claimId, action, action === 'reject' ? reason.trim() : undefined);
      setClaim(updated); setShowReject(false); setReason('');
      setDoneNotice(actionResults[action]);
    } catch (requestError) {
      if (staleClaimErrors.has(errorCode(requestError) ?? '')) await refreshAfterConflict(claim.claimId);
      else setActionError(errorMessage(requestError));
    }
    finally { setActing(false); }
  }

  async function refreshAfterConflict(claimId: number) {
    try {
      const latest = await getClaim(claimId);
      setClaim(latest); setShowReject(false);
      setStaleNotice(`Another user updated this claim while you were working. It is now ${readableStatus(latest.claimStatus)}; review the latest details before taking further action.`);
    } catch (requestError) { setActionError(errorMessage(requestError)); }
  }

  function closeReject() {
    setShowReject(false);
    rejectButton.current?.focus();
  }

  useEffect(() => {
    if (!showReject) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape' && !acting) closeReject(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [showReject, acting]);

  const isClaimant = session?.role === 'CLAIMANT';
  const hasAction = claim !== null && ((isClaimant && claim.claimStatus === 'DRAFT')
    || (!isClaimant && ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(claim.claimStatus)));
  const listCrumb = isClaimant ? { label: 'My claims', to: '/claims' } : { label: 'Claims queue', to: '/officer/claims' };
  return (
    <div className="page-stack">
      <PageHeader
        crumbs={[listCrumb, { label: `Claim #${id}` }]}
        title={`Claim #${id}`}
        meta={claim && <StatusBadge status={claim.claimStatus} />}
        description={claim ? <>{formatClaimType(claim.claimType)} · {formatCurrency(claim.claimAmount)} · Last updated {formatDate(claim.lastUpdated)}</> : undefined}
      />
      {created && <Notice tone="success">Draft created. Check the details, then submit it when you are ready.</Notice>}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading ? <LoadingState label="Loading claim" /> : claim && <div className="detail-grid">
        <div className="detail-main">
          <section className="panel panel-padded" aria-labelledby="details-title">
            <h2 id="details-title">Claim details</h2>
            <dl className="detail-list">
              <div><dt>Claim amount</dt><dd className="num-value">{formatCurrency(claim.claimAmount)}</dd></div>
              <div><dt>Claim type</dt><dd>{formatClaimType(claim.claimType)}</dd></div>
              <div><dt>Claimant ID</dt><dd>{claim.userId}</dd></div>
              <div className="detail-wide"><dt>Contact email</dt><dd>{claim.emailId ? <span className="break-anywhere">{claim.emailId}</span> : <span className="value-missing">Not provided</span>}</dd></div>
              <div><dt>Created</dt><dd>{formatDate(claim.claimDate)}</dd></div>
              <div><dt>Last updated</dt><dd>{formatDate(claim.lastUpdated)}</dd></div>
            </dl>
          </section>
          <section className="panel panel-padded" aria-labelledby="description-title">
            <h2 id="description-title">Description</h2>
            <p className="description-text">{claim.description}</p>
          </section>
        </div>
        <aside className="detail-side">
          <section className="panel panel-padded decision-panel" aria-labelledby="actions-title">
            <h2 id="actions-title">{isClaimant ? 'Next step' : 'Decision'}</h2>
            {doneNotice && <Notice tone="success">{doneNotice}</Notice>}
            {staleNotice && <div className="alert alert-warning" role="status">{staleNotice}</div>}
            {actionError && <ErrorAlert message={actionError} />}
            {hasAction && <p className="decision-help">
              {isClaimant && claim.claimStatus === 'DRAFT' && 'Submitting sends this claim to the claims team. It cannot be changed afterwards.'}
              {!isClaimant && claim.claimStatus === 'SUBMITTED' && 'Start a review to take this claim on. Approving or rejecting comes next.'}
              {!isClaimant && claim.claimStatus === 'UNDER_REVIEW' && 'Approve to move the claim toward settlement, or reject it with a reason. Rejection is final.'}
              {!isClaimant && claim.claimStatus === 'APPROVED' && 'Mark the claim settled once the payment has been made.'}
            </p>}
            <div className="action-bar">
              {isClaimant && claim.claimStatus === 'DRAFT' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('submit')}>{acting ? 'Submitting…' : 'Submit claim'}</button>}
              {!isClaimant && claim.claimStatus === 'SUBMITTED' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('review')}>{acting ? 'Updating…' : 'Start review'}</button>}
              {!isClaimant && claim.claimStatus === 'UNDER_REVIEW' && <><button className="button button-primary" disabled={acting} onClick={() => void runAction('approve')}>Approve</button><button ref={rejectButton} className="button button-danger-outline" disabled={acting} onClick={() => setShowReject(true)}>Reject</button></>}
              {!isClaimant && claim.claimStatus === 'APPROVED' && <button className="button button-primary" disabled={acting} onClick={() => void runAction('settle')}>{acting ? 'Updating…' : 'Mark settled'}</button>}
              {!hasAction && <p className="decision-help decision-none">No actions are available at this stage.{isClaimant && claim.claimStatus !== 'REJECTED' && claim.claimStatus !== 'SETTLED' ? ' The claims team is handling it.' : ''}</p>}
            </div>
          </section>
          <section className="panel panel-padded" aria-labelledby="timeline-title">
            <h2 id="timeline-title">Progress</h2>
            <StatusTimeline claim={claim} />
            <p className="timeline-note">Only the created and last-updated times are recorded here; earlier steps show as completed.</p>
          </section>
        </aside>
      </div>}
      {showReject && <div className="modal-backdrop" role="presentation">
        <div className="modal" role="dialog" aria-modal="true" aria-labelledby="reject-title" aria-describedby="reject-help">
          <h2 id="reject-title">Reject claim #{claim?.claimId}</h2>
          <p id="reject-help">The reason is saved with the claim's history. A rejected claim cannot be reopened.</p>
          <div className="field">
            <label className="field-label" htmlFor="reject-reason">Reason for rejection</label>
            <textarea id="reject-reason" autoFocus required maxLength={REASON_MAX} rows={5} aria-describedby="reject-reason-count" value={reason} onChange={(e) => setReason(e.target.value)} />
            <span className={`field-count${reason.length > REASON_MAX - 50 ? ' field-count-near' : ''}`} id="reject-reason-count">{reason.length}/{REASON_MAX} characters</span>
          </div>
          <div className="form-actions">
            <button className="button button-secondary" type="button" disabled={acting} onClick={closeReject}>Cancel</button>
            <button className="button button-danger" type="button" disabled={acting || !reason.trim()} onClick={() => void runAction('reject')}>{acting ? 'Rejecting…' : 'Reject claim'}</button>
          </div>
        </div>
      </div>}
    </div>
  );
}
