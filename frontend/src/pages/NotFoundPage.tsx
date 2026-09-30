import { Link } from 'react-router-dom';
import { usePageTitle } from '../usePageTitle';

export function NotFoundPage() {
  usePageTitle('Page not found');
  return (
    <main className="not-found">
      <p className="not-found-code">404</p>
      <h1>Page not found</h1>
      <p>Check the address for typos. If you followed a link to a claim, it may not exist or you may not have access to it.</p>
      <Link className="button button-primary" to="/">Go to your dashboard</Link>
    </main>
  );
}
