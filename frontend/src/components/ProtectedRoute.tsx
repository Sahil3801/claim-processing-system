import type { PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { UserRole } from '../types';

export function ProtectedRoute({ children, roles }: PropsWithChildren<{ roles?: UserRole[] }>) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) return <Navigate to="/login" state={{ from: location }} replace />;
  if (roles && !roles.includes(session.role)) return <Navigate to="/" replace />;
  return children;
}
