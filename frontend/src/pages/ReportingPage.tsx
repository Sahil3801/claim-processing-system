import { useCallback, useEffect, useState } from 'react';
import { getReportingData } from '../api/reports';
import { errorMessage } from '../api/client';
import { claimStatuses } from '../api/claims';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import type { ClaimTypeReport, ClaimsSummary, DailyReport, StatusReport } from '../types';
import { formatCurrency, formatReportDate, isoDate } from '../utils';

interface ReportData { summary: ClaimsSummary; status: StatusReport[]; claimTypes: ClaimTypeReport[]; daily: DailyReport[] }

export function ReportingPage() {
  const now = new Date();
  const monthAgo = new Date(now); monthAgo.setDate(monthAgo.getDate() - 30);
  const [from, setFrom] = useState(isoDate(monthAgo));
  const [to, setTo] = useState(isoDate(now));
  const [data, setData] = useState<ReportData | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    if (from > to) { setError('The start date must be on or before the end date.'); return; }
    setLoading(true); setError('');
    try { setData(await getReportingData(from, to)); }
    catch (requestError) { setError(errorMessage(requestError)); }
    finally { setLoading(false); }
  }, [from, to]);
  useEffect(() => { void load(); }, []); // Initial report only; date changes apply explicitly.

  return (
    <div className="page-stack">
      <PageHeader title="Reporting" description="Live totals across all claims. The date range below applies only to daily activity." />
      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading && !data ? <LoadingState label="Loading report" /> : data && <>
        <section className="metric-strip metric-strip-four" aria-label="All-time summary">
          <div className="metric"><span className="metric-label">Total claims</span><strong className="metric-value">{data.summary.totalClaims}</strong><small className="metric-note">All recorded claims</small></div>
          <div className="metric"><span className="metric-label">Total amount</span><strong className="metric-value">{formatCurrency(data.summary.totalClaimAmount)}</strong><small className="metric-note">Sum of all claim amounts</small></div>
          <div className="metric"><span className="metric-label">Average amount</span><strong className="metric-value">{formatCurrency(data.summary.averageClaimAmount)}</strong><small className="metric-note">Across all claims</small></div>
          <div className="metric metric-emphasis"><span className="metric-label">Pending</span><strong className="metric-value">{data.summary.pending.totalClaims}</strong><small className="metric-note">{formatCurrency(data.summary.pending.totalClaimAmount)} in open claims</small></div>
        </section>
        <div className="report-grid">
          <section className="panel"><div className="panel-header"><div><h2>By status</h2><p>Count and value at each stage.</p></div></div><div className="table-wrap"><table className="data-table"><thead><tr><th scope="col">Status</th><th scope="col" className="num">Claims</th><th scope="col" className="num">Total</th><th scope="col" className="num">Average</th></tr></thead><tbody>{data.status.length ? [...data.status].sort((a, b) => claimStatuses.indexOf(a.claimStatus) - claimStatuses.indexOf(b.claimStatus)).map((row) => <tr key={row.claimStatus}><td><StatusBadge status={row.claimStatus} /></td><td className="num">{row.totalClaims}</td><td className="num">{formatCurrency(row.totalClaimAmount)}</td><td className="num">{formatCurrency(row.averageClaimAmount)}</td></tr>) : <tr><td colSpan={4} className="table-empty">No claims recorded yet.</td></tr>}</tbody></table></div></section>
          <section className="panel"><div className="panel-header"><div><h2>By claim type</h2><p>Count and value for each claim type.</p></div></div><div className="table-wrap"><table className="data-table"><thead><tr><th scope="col">Type</th><th scope="col" className="num">Claims</th><th scope="col" className="num">Total</th><th scope="col" className="num">Average</th></tr></thead><tbody>{data.claimTypes.length ? data.claimTypes.map((row) => <tr key={row.claimType}>{/* Shown exactly as stored: the report groups types case-sensitively, so "AUTO" and "Auto" are separate rows. */}<td className="truncate" title={row.claimType}>{row.claimType}</td><td className="num">{row.totalClaims}</td><td className="num">{formatCurrency(row.totalClaimAmount)}</td><td className="num">{formatCurrency(row.averageClaimAmount)}</td></tr>) : <tr><td colSpan={4} className="table-empty">No claims recorded yet.</td></tr>}</tbody></table></div></section>
        </div>
        <section className="panel">
          <div className="panel-header panel-header-wrap">
            <div><h2>Daily activity</h2><p>Claims created each day from {formatReportDate(from)} to {formatReportDate(to)}.</p></div>
            <form className="date-range" onSubmit={(event) => { event.preventDefault(); void load(); }}>
              <label className="toolbar-field">From<input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></label>
              <label className="toolbar-field">To<input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label>
              <button className="button button-secondary" type="submit" disabled={loading}>{loading ? 'Updating…' : 'Apply'}</button>
            </form>
          </div>
          <div className="table-wrap"><table className="data-table"><thead><tr><th scope="col">Date</th><th scope="col" className="num">Claims</th><th scope="col" className="num">Total amount</th><th scope="col" className="num">Average amount</th></tr></thead><tbody>{data.daily.length ? data.daily.map((row) => <tr key={row.reportDate}><td>{formatReportDate(row.reportDate)}</td><td className="num">{row.totalClaims}</td><td className="num">{formatCurrency(row.totalClaimAmount)}</td><td className="num">{formatCurrency(row.averageClaimAmount)}</td></tr>) : <tr><td colSpan={4} className="table-empty">No claims were created in this date range.</td></tr>}</tbody></table></div>
        </section>
      </>}
    </div>
  );
}
