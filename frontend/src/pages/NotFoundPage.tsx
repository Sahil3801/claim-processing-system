import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return <main className="not-found"><p className="eyebrow">404</p><h1>Page not found</h1><p>The page may have moved or you may not have access.</p><Link className="button button-primary" to="/">Return to dashboard</Link></main>;
}
