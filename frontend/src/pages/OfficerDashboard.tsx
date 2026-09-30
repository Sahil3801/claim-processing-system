import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
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
      <PageHeader title="Officer dashboard" description="Review new submissions oldest first and keep active claims moving." actions={<Link className="button button-primary" to="/officer/claims">Open claims queue</Link>} />
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!data && !error ? <LoadingState label="Loading dashboard" /> : data && <>
        <section className="metric-strip" aria-label="Queue summary">
          <div className="metric metric-emphasis"><span className="metric-label">Awaiting review</span><strong className="metric-value">{data.submitted}</strong><small className="metric-note">Submitted and not yet picked up</small></div>
          <div className="metric"><span className="metric-label">Under review</span><strong className="metric-value">{data.review}</strong><small className="metric-note">Waiting for a decision</small></div>
          <div className="metric"><span className="metric-label">Ready to settle</span><strong className="metric-value">{data.approved}</strong><small className="metric-note">Approved and not yet settled</small></div>
        </section>
        <section className="panel">
          <div className="panel-header"><div><h2>Oldest new submissions</h2><p>Submitted claims that have waited longest for review.</p></div><Link className="panel-link" to="/officer/claims">View full queue</Link></div>
          <ClaimTable claims={data.recent} showClaimant empty={{ title: 'Queue is clear', message: 'No submitted claims are waiting for review.' }} />
        </section>
      </>}
    </div>
  );
}
