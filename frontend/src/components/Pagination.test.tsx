import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('shows the visible range and moves between pages', async () => {
    const onChange = vi.fn();
    render(<Pagination page={1} totalPages={3} pageSize={10} totalElements={23} onChange={onChange} />);
    expect(screen.getByText('11–20 of 23')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('caps the range on the last page and disables Next', () => {
    render(<Pagination page={2} totalPages={3} pageSize={10} totalElements={23} onChange={() => {}} />);
    expect(screen.getByText('21–23 of 23')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('renders nothing for a single page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
