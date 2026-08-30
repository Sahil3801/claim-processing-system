export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return <div className="feedback" role="status"><span className="spinner" />{label}…</div>;
}

export function ErrorAlert({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && <button className="button button-quiet" type="button" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function EmptyState({ title, message }: { title: string; message: string }) {
  return <div className="empty-state"><strong>{title}</strong><p>{message}</p></div>;
}
