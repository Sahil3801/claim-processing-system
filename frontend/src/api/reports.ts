import { api } from './client';
import type { ClaimsSummary, ClaimTypeReport, DailyReport, StatusReport } from '../types';

export async function getReportingData(from: string, to: string) {
  const [summary, status, claimTypes, daily] = await Promise.all([
    api.get<ClaimsSummary>('/reports/summary'),
    api.get<StatusReport[]>('/reports/status'),
    api.get<ClaimTypeReport[]>('/reports/claim-types'),
    api.get<DailyReport[]>('/reports/daily', { params: { from, to } }),
  ]);
  return {
    summary: summary.data,
    status: status.data,
    claimTypes: claimTypes.data,
    daily: daily.data,
  };
}
