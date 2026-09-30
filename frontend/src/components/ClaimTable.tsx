import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { Claim } from '../types';
import { formatClaimType, formatCurrency, formatDay, formatTime } from '../utils';
import { EmptyState } from './Feedback';
import { StatusBadge } from './StatusBadge';

export function ClaimTable({ claims, showClaimant = false, empty }: {
  claims: Claim[];
  showClaimant?: boolean;
  empty?: { title: string; message: string; action?: ReactNode };
}) {
  if (!claims.length) {
    return <EmptyState title={empty?.title ?? 'No claims found'} message={empty?.message ?? 'No claims match the filters.'} action={empty?.action} />;
  }
  return (
    <div className="table-wrap">
      <table className="data-table data-table-claims">
        <thead>
          <tr>
            <th scope="col">Claim</th>
            {showClaimant && <th scope="col">Claimant</th>}
            <th scope="col">Type</th>
            <th scope="col" className="num">Amount</th>
            <th scope="col">Created</th>
            <th scope="col">Status</th>
            <th scope="col"><span className="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {claims.map((claim) => (
            <tr key={claim.claimId}>
              <td><Link className="claim-id" to={`/claims/${claim.claimId}`}>#{claim.claimId}</Link></td>
              {showClaimant && <td className="muted-cell">User {claim.userId}</td>}
              <td className="truncate" title={claim.claimType}>{formatClaimType(claim.claimType)}</td>
              <td className="num">{formatCurrency(claim.claimAmount)}</td>
              <td><span className="date-cell">{formatDay(claim.claimDate)}<small>{formatTime(claim.claimDate)}</small></span></td>
              <td><StatusBadge status={claim.claimStatus} /></td>
              <td className="row-action"><Link to={`/claims/${claim.claimId}`} aria-label={`View claim ${claim.claimId}`}>View</Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
