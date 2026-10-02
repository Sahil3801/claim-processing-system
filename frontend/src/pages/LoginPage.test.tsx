import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { login } from '../api/auth';
import { AuthProvider } from '../auth/AuthContext';
import { SLOW_AFTER_MS } from '../components/Feedback';
import { LoginPage } from './LoginPage';

vi.mock('../api/auth', () => ({ login: vi.fn(), register: vi.fn() }));

describe('LoginPage on a sleeping server', () => {
  afterEach(() => { vi.useRealTimers(); });

  it('explains the free-tier wake-up while sign-in is still waiting', async () => {
    vi.useFakeTimers();
    vi.mocked(login).mockReturnValue(new Promise(() => {}));
    render(<MemoryRouter><AuthProvider><LoginPage /></AuthProvider></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'demo-admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();
    expect(screen.queryByText(/runs on free hosting/)).not.toBeInTheDocument();

    await act(async () => { vi.advanceTimersByTime(SLOW_AFTER_MS + 1); });

    expect(screen.getByText(/runs on free hosting/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Waking up the server…' })).toBeDisabled();
  });
});
