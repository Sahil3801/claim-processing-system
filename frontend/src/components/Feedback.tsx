import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';

/** Free hosting can take up to a minute to wake the API; say so instead of spinning silently. */
const SLOW_AFTER_MS = 6000;

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setSlow(true), SLOW_AFTER_MS);
    return () => window.clearTimeout(timer);
  }, []);
  return (
    <div className="feedback" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}…</span>
      {slow && <small className="feedback-hint">This is taking longer than usual. The server may be starting up, which can take up to a minute.</small>}
    </div>
  );
}

export function ErrorAlert({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && <button className="button button-secondary button-small" type="button" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function Notice({ tone, children }: { tone: 'success' | 'warning' | 'info'; children: ReactNode }) {
  return <div className={`alert alert-${tone}`} role="status">{children}</div>;
}

export function EmptyState({ title, message, action }: { title: string; message: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{message}</p>
      {action}
    </div>
  );
}
