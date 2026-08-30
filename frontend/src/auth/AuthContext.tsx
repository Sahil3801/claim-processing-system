import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { PropsWithChildren } from 'react';
import { login as loginRequest } from '../api/auth';
import { AUTH_EXPIRED_EVENT, AUTH_STORAGE_KEY } from '../api/client';
import type { AuthSession, UserRole } from '../types';

interface JwtPayload {
  role?: UserRole;
  exp?: number;
}

interface AuthContextValue {
  session: AuthSession | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<AuthSession>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const validRoles: UserRole[] = ['CLAIMANT', 'CLAIMS_OFFICER', 'ADMIN'];

function decodePayload(token: string): JwtPayload {
  const payload = token.split('.')[1];
  if (!payload) throw new Error('The authentication token is invalid.');
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const decoded = decodeURIComponent(
    atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))
      .split('')
      .map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`)
      .join(''),
  );
  return JSON.parse(decoded) as JwtPayload;
}

function toSession(username: string, token: string): AuthSession {
  const payload = decodePayload(token);
  if (!payload.role || !validRoles.includes(payload.role) || !payload.exp) {
    throw new Error('The authentication token is missing required access information.');
  }
  return { username, token, role: payload.role, expiresAt: payload.exp * 1000 };
}

function restoreSession(): AuthSession | null {
  const stored = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) return null;
  try {
    const session = JSON.parse(stored) as AuthSession;
    if (!validRoles.includes(session.role) || session.expiresAt <= Date.now()) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthSession | null>(restoreSession);

  const logout = useCallback(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setSession(null);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginRequest(username, password);
    const nextSession = toSession(response.username, response.token);
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    return nextSession;
  }, []);

  useEffect(() => {
    window.addEventListener(AUTH_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, logout);
  }, [logout]);

  useEffect(() => {
    if (!session) return;
    const remaining = session.expiresAt - Date.now();
    if (remaining <= 0) {
      logout();
      return;
    }
    const timer = window.setTimeout(logout, Math.min(remaining, 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const value = useMemo(() => ({ session, isAuthenticated: Boolean(session), login, logout }), [session, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
