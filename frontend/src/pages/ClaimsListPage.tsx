import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { claimStatuses, getClaims, getMyClaims } from '../api/claims';
import { errorMessage } from '../api/client';
import { ClaimTable } from '../components/ClaimTable';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
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

  const filtersChanged = status !== (pendingOnly ? 'SUBMITTED' : '') || claimType !== '' || userId !== '';
  return (
    <div className="page-stack">
      <PageHeader
        title={pendingOnly ? 'Claims queue' : isClaimant ? 'My claims' : 'All claims'}
        description={isClaimant ? 'Every claim you have created, newest first.' : 'Open a claim to review its details and move it to the next step.'}
        actions={isClaimant ? <Link className="button button-primary" to="/claims/new">Create claim</Link> : undefined}
      />
      {error && <ErrorAlert message={error} onRetry={load} />}
      <section className="panel" aria-label="Claims">
        {!isClaimant && <div className="toolbar" role="search" aria-label="Filter claims">
          <label className="toolbar-field">Status<select value={status} onChange={(e) => { setStatus(e.target.value as ClaimStatus | ''); setPage(0); }}><option value="">All statuses</option>{claimStatuses.map((item) => <option key={item} value={item}>{readableStatus(item)}</option>)}</select></label>
          <label className="toolbar-field">Claim type<input placeholder="Any type" value={claimType} onChange={(e) => { setClaimType(e.target.value); setPage(0); }} /></label>
          <label className="toolbar-field toolbar-field-narrow">Claimant ID<input min="1" type="number" inputMode="numeric" placeholder="Any" value={userId} onChange={(e) => { setUserId(e.target.value); setPage(0); }} /></label>
          <button className="button button-ghost button-small toolbar-reset" type="button" disabled={!filtersChanged} onClick={() => { setStatus(pendingOnly ? 'SUBMITTED' : ''); setClaimType(''); setUserId(''); setPage(0); }}>Reset filters</button>
        </div>}
        <div className="panel-subheader" aria-live="polite">
          {data && !loading ? <span><strong>{data.totalElements}</strong> claim{data.totalElements === 1 ? '' : 's'}{!isClaimant && filtersChanged ? ' match these filters' : ''}</span> : <span>&nbsp;</span>}
        </div>
        {loading ? <LoadingState label="Loading claims" /> : data && <>
          <ClaimTable
            claims={data.content}
            showClaimant={!isClaimant}
            empty={isClaimant
              ? { title: 'No claims yet', message: 'Claims you create will appear here.', action: <Link className="button button-primary button-small" to="/claims/new">Create claim</Link> }
              : { title: 'No claims match', message: filtersChanged ? 'Try a different status or clear the filters.' : 'There is nothing waiting in this queue.' }}
          />
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} pageSize={data.size} totalElements={data.totalElements} />
        </>}
      </section>
    </div>
  );
}
