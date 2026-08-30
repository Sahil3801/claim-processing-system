import { Link } from 'react-router-dom';
import type { Claim } from '../types';
import { formatCurrency, formatDate } from '../utils';
import { EmptyState } from './Feedback';
import { StatusBadge } from './StatusBadge';

export function ClaimTable({ claims, showClaimant = false }: { claims: Claim[]; showClaimant?: boolean }) {
  if (!claims.length) return <EmptyState title="No claims found" message="There are no claims matching the current filters." />;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Claim</th>{showClaimant && <th>Claimant</th>}<th>Type</th><th>Amount</th><th>Submitted</th><th>Status</th><th /></tr></thead>
        <tbody>
          {claims.map((claim) => (
            <tr key={claim.claimId}>
              <td className="mono">#{claim.claimId}</td>
              {showClaimant && <td>User {claim.userId}</td>}
              <td>{claim.claimType}</td>
              <td>{formatCurrency(claim.claimAmount)}</td>
              <td>{formatDate(claim.claimDate)}</td>
              <td><StatusBadge status={claim.claimStatus} /></td>
              <td><Link className="table-link" to={`/claims/${claim.claimId}`}>View</Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
