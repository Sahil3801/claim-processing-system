import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { usePageTitle } from '../usePageTitle';

export interface Crumb { label: string; to?: string }

export function PageHeader({ title, description, actions, crumbs, meta }: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  crumbs?: Crumb[];
  meta?: ReactNode;
}) {
  usePageTitle(title);
  return (
    <header className="page-header">
      {crumbs && crumbs.length > 0 && (
        <nav className="breadcrumbs" aria-label="Breadcrumb">
          <ol>
            {crumbs.map((crumb) => (
              <li key={crumb.label}>{crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span aria-current="page">{crumb.label}</span>}</li>
            ))}
          </ol>
        </nav>
      )}
      <div className="page-header-row">
        <div className="page-header-text">
          <div className="page-title-line"><h1>{title}</h1>{meta}</div>
          {description && <p className="page-description">{description}</p>}
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </div>
    </header>
  );
}
