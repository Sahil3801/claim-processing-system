import { useCallback, useEffect, useState } from 'react';
import { getReportingData } from '../api/reports';
import { errorMessage } from '../api/client';
import { ErrorAlert, LoadingState } from '../components/Feedback';
import type { ClaimTypeReport, ClaimsSummary, DailyReport, StatusReport } from '../types';
import { formatCurrency, isoDate } from '../utils';

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
      <header className="page-header"><div><p className="eyebrow">Administration</p><h1>Claims reporting</h1><p>Live financial and operational aggregates from the claims ledger.</p></div></header>
      <section className="card report-filter"><label>From<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label><label>To<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label><button className="button button-primary" disabled={loading} onClick={() => void load()}>Apply dates</button></section>
      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading && !data ? <LoadingState label="Building live report" /> : data && <>
        <section className="stat-grid four"><article className="stat-card"><span>Total claims</span><strong>{data.summary.totalClaims}</strong><small>All recorded claims</small></article><article className="stat-card"><span>Total amount</span><strong>{formatCurrency(data.summary.totalClaimAmount)}</strong><small>Gross claim value</small></article><article className="stat-card"><span>Average amount</span><strong>{formatCurrency(data.summary.averageClaimAmount)}</strong><small>Per claim</small></article><article className="stat-card accent"><span>Pending</span><strong>{data.summary.pending.totalClaims}</strong><small>{formatCurrency(data.summary.pending.totalClaimAmount)}</small></article></section>
        <div className="report-grid">
          <section className="card"><div className="section-heading"><div><h2>By status</h2><p>Volume and value by outcome.</p></div></div><div className="table-wrap"><table><thead><tr><th>Status</th><th>Claims</th><th>Total</th><th>Average</th></tr></thead><tbody>{data.status.map((row) => <tr key={row.claimStatus}><td>{row.claimStatus.replace('_', ' ')}</td><td>{row.totalClaims}</td><td>{formatCurrency(row.totalClaimAmount)}</td><td>{formatCurrency(row.averageClaimAmount)}</td></tr>)}</tbody></table></div></section>
          <section className="card"><div className="section-heading"><div><h2>By claim type</h2><p>Portfolio distribution.</p></div></div><div className="table-wrap"><table><thead><tr><th>Type</th><th>Claims</th><th>Total</th><th>Average</th></tr></thead><tbody>{data.claimTypes.map((row) => <tr key={row.claimType}><td>{row.claimType}</td><td>{row.totalClaims}</td><td>{formatCurrency(row.totalClaimAmount)}</td><td>{formatCurrency(row.averageClaimAmount)}</td></tr>)}</tbody></table></div></section>
        </div>
        <section className="card"><div className="section-heading"><div><h2>Daily activity</h2><p>{from} through {to}</p></div></div><div className="table-wrap"><table><thead><tr><th>Date</th><th>Claims</th><th>Total amount</th><th>Average amount</th></tr></thead><tbody>{data.daily.length ? data.daily.map((row) => <tr key={row.reportDate}><td>{row.reportDate}</td><td>{row.totalClaims}</td><td>{formatCurrency(row.totalClaimAmount)}</td><td>{formatCurrency(row.averageClaimAmount)}</td></tr>) : <tr><td colSpan={4} className="table-empty">No claim activity in this date range.</td></tr>}</tbody></table></div></section>
      </>}
    </div>
  );
}
