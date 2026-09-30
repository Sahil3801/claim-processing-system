import type { ClaimStatus } from '../types';

/** Sentence case, matching the rest of the interface: UNDER_REVIEW reads "Under review". */
export function readableStatus(status: ClaimStatus): string {
  const words = status.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}><span className="status-dot" aria-hidden="true" />{readableStatus(status)}</span>;
}
