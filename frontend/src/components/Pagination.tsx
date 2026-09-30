export function Pagination({ page, totalPages, onChange, pageSize, totalElements }: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  pageSize?: number;
  totalElements?: number;
}) {
  if (totalPages <= 1) return null;
  const range = pageSize && totalElements !== undefined
    ? `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, totalElements)} of ${totalElements}`
    : `Page ${page + 1} of ${totalPages}`;
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="pagination-range">{range}</span>
      <div className="pagination-buttons">
        <button className="button button-secondary button-small" type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
        <button className="button button-secondary button-small" type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
      </div>
    </nav>
  );
}
