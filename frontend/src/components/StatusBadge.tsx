import type { ClaimStatus } from '../types';

export function readableStatus(status: ClaimStatus): string {
  return status.toLowerCase().split('_').map((word) => word[0].toUpperCase() + word.slice(1)).join(' ');
}

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}>{readableStatus(status)}</span>;
}
