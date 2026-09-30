import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import type { Claim, PageResponse } from '../types';
import { formatCurrency } from '../utils';

export function ClaimantDashboard() {
  const [data, setData] = useState<PageResponse<Claim> | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setError('');
    try { setData(await getMyClaims({ page: 0, size: 5, sort: 'claimDate,desc' })); }
    catch (requestError) { setError(errorMessage(requestError)); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const openClaims = data?.content.filter((claim) => !['REJECTED', 'SETTLED'].includes(claim.claimStatus)).length ?? 0;
  const visibleAmount = data?.content.reduce((sum, claim) => sum + Number(claim.claimAmount), 0) ?? 0;
  return (
    <div className="page-stack">
      <PageHeader title="Overview" description="Your latest claims and where each one stands." actions={<Link className="button button-primary" to="/claims/new">Create claim</Link>} />
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!data && !error ? <LoadingState label="Loading dashboard" /> : data && <>
        <section className="metric-strip" aria-label="Summary">
          <div className="metric"><span className="metric-label">Total claims</span><strong className="metric-value">{data.totalElements}</strong><small className="metric-note">All claims you have created</small></div>
          <div className="metric"><span className="metric-label">Open</span><strong className="metric-value">{openClaims}</strong><small className="metric-note">Draft or in progress, among your latest claims</small></div>
          <div className="metric"><span className="metric-label">Recent value</span><strong className="metric-value">{formatCurrency(visibleAmount)}</strong><small className="metric-note">Total amount of the claims listed below</small></div>
        </section>
        <section className="panel">
          <div className="panel-header"><div><h2>Recent claims</h2><p>Your five newest claims.</p></div>{data.totalElements > 0 && <Link className="panel-link" to="/claims">View all claims</Link>}</div>
          <ClaimTable claims={data.content} empty={{ title: 'No claims yet', message: 'Create your first claim to track it here.', action: <Link className="button button-primary button-small" to="/claims/new">Create claim</Link> }} />
        </section>
      </>}
    </div>
  );
}
