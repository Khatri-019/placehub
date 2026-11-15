import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { Role } from '../types';
import { ROLE_HOME } from '../types/enums';
import { useAuth } from './useAuth';

interface Props {
  roles?: Role[];
  children: ReactNode;
}

/** Requires a session; a user with the wrong role is sent to their own home. Backend still enforces access. */
export function ProtectedRoute({ roles, children }: Props) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={ROLE_HOME[user.role]} replace />;
  }
  return <>{children}</>;
}
