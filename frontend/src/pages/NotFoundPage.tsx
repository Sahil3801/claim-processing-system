import { Link } from 'react-router-dom';
import { usePageTitle } from '../usePageTitle';

export function NotFoundPage() {
  usePageTitle('Page not found');
  return (
    <main className="not-found">
      <p className="not-found-code">404</p>
      <h1>Page not found</h1>
      <p>The address may be mistyped, or the page may have moved. If you followed a link to a claim, you may not have access to it.</p>
      <Link className="button button-primary" to="/">Go to your dashboard</Link>
    </main>
  );
}
