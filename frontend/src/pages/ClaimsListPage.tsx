import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { claimStatuses, getClaims, getMyClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { Pagination } from '../components/Pagination';
import { readableStatus } from '../components/StatusBadge';
import type { Claim, ClaimStatus, PageResponse } from '../types';

export function ClaimsListPage({ pendingOnly = false }: { pendingOnly?: boolean }) {
  const { session } = useAuth();
  const isClaimant = session?.role === 'CLAIMANT';
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ClaimStatus | ''>(pendingOnly ? 'SUBMITTED' : '');
  const [claimType, setClaimType] = useState('');
  const [userId, setUserId] = useState('');
  const [data, setData] = useState<PageResponse<Claim> | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    const filters = { page, size: 10, status, claimType: claimType.trim() || undefined, userId: userId ? Number(userId) : undefined, sort: 'claimDate,desc' };
    try { setData(isClaimant ? await getMyClaims(filters) : await getClaims(filters)); }
    catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }, [page, status, claimType, userId, isClaimant]);
  useEffect(() => { void load(); }, [load]);

  return (
    <div className="page-stack">
      <header className="page-header"><div><p className="eyebrow">{isClaimant ? 'Claim history' : 'Operations queue'}</p><h1>{pendingOnly ? 'Pending claims' : isClaimant ? 'My claims' : 'All claims'}</h1><p>Filter and open a claim to see the full record.</p></div></header>
      {!isClaimant && <section className="card filters" aria-label="Claim filters">
        <label>Status<select value={status} onChange={(e) => { setStatus(e.target.value as ClaimStatus | ''); setPage(0); }}><option value="">All statuses</option>{claimStatuses.map((item) => <option key={item} value={item}>{readableStatus(item)}</option>)}</select></label>
        <label>Claim type<input placeholder="Filter by type" value={claimType} onChange={(e) => { setClaimType(e.target.value); setPage(0); }} /></label>
        <label>Claimant ID<input min="1" type="number" placeholder="Any claimant" value={userId} onChange={(e) => { setUserId(e.target.value); setPage(0); }} /></label>
        <button className="button button-secondary filter-reset" type="button" onClick={() => { setStatus(pendingOnly ? 'SUBMITTED' : ''); setClaimType(''); setUserId(''); setPage(0); }}>Reset</button>
      </section>}
      {error && <ErrorAlert message={error} onRetry={load} />}
      <section className="card">
        {loading ? <LoadingState label="Loading claims" /> : data && <><div className="section-heading"><div><h2>{data.totalElements} claim{data.totalElements === 1 ? '' : 's'}</h2><p>Showing up to {data.size} results per page.</p></div></div><ClaimTable claims={data.content} showClaimant={!isClaimant} /><Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} /></>}
      </section>
    </div>
  );
}
