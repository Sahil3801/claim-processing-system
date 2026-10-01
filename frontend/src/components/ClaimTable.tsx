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
    <>
    <div className="table-wrap table-wrap-claims">
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
    {/* Phones: one card per claim instead of a table that scrolls sideways and hides the status. */}
    <ul className="claim-cards" aria-label="Claims">
      {claims.map((claim) => (
        <li key={claim.claimId}>
          <Link className="claim-card" to={`/claims/${claim.claimId}`}>
            <span className="claim-card-row">
              <span className="claim-id">#{claim.claimId}</span>
              <StatusBadge status={claim.claimStatus} />
            </span>
            <span className="claim-card-row claim-card-main">
              <span className="truncate">{formatClaimType(claim.claimType)}</span>
              <span className="num-value">{formatCurrency(claim.claimAmount)}</span>
            </span>
            <span className="claim-card-meta">
              Created {formatDay(claim.claimDate)}, {formatTime(claim.claimDate)}{showClaimant ? ` · User ${claim.userId}` : ''}
            </span>
          </Link>
        </li>
      ))}
    </ul>
    </>
  );
}
