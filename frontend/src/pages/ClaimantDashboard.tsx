import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
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
      <header className="page-header"><div><p className="eyebrow">Claimant workspace</p><h1>Your claims at a glance</h1><p>Track recent activity or start a new claim.</p></div><Link className="button button-primary" to="/claims/new">Create claim</Link></header>
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!data && !error ? <LoadingState label="Loading your dashboard" /> : data && <>
        <section className="stat-grid">
          <article className="stat-card"><span>Total claims</span><strong>{data.totalElements}</strong><small>All submitted records</small></article>
          <article className="stat-card"><span>Active in recent list</span><strong>{openClaims}</strong><small>Draft or being processed</small></article>
          <article className="stat-card"><span>Recent claim value</span><strong>{formatCurrency(visibleAmount)}</strong><small>Across the claims shown below</small></article>
        </section>
        <section className="card"><div className="section-heading"><div><h2>Recent claims</h2><p>Your five most recently updated submissions.</p></div><Link to="/claims">View all</Link></div><ClaimTable claims={data.content} /></section>
      </>}
    </div>
  );
}
