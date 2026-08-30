import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import type { Claim, ClaimStatus } from '../types';

interface QueueData { submitted: number; review: number; approved: number; recent: Claim[] }

export function OfficerDashboard() {
  const [data, setData] = useState<QueueData | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setError('');
    try {
      const fetchStatus = (status: ClaimStatus, size = 1) => getClaims({ status, page: 0, size, sort: 'claimDate,asc' });
      const [submitted, review, approved] = await Promise.all([fetchStatus('SUBMITTED', 5), fetchStatus('UNDER_REVIEW'), fetchStatus('APPROVED')]);
      setData({ submitted: submitted.totalElements, review: review.totalElements, approved: approved.totalElements, recent: submitted.content });
    } catch (requestError) { setError(errorMessage(requestError)); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  return (
    <div className="page-stack">
      <header className="page-header"><div><p className="eyebrow">Claims operations</p><h1>Review queue</h1><p>Prioritize new submissions and move active claims forward.</p></div><Link className="button button-primary" to="/officer/claims">Open full queue</Link></header>
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!data && !error ? <LoadingState label="Loading operations dashboard" /> : data && <>
        <section className="stat-grid"><article className="stat-card accent"><span>Awaiting review</span><strong>{data.submitted}</strong><small>New submissions</small></article><article className="stat-card"><span>Under review</span><strong>{data.review}</strong><small>Active assessments</small></article><article className="stat-card"><span>Ready to settle</span><strong>{data.approved}</strong><small>Approved claims</small></article></section>
        <section className="card"><div className="section-heading"><div><h2>Oldest new submissions</h2><p>Start with claims waiting longest.</p></div><Link to="/officer/claims">View queue</Link></div><ClaimTable claims={data.recent} showClaimant /></section>
      </>}
    </div>
  );
}
