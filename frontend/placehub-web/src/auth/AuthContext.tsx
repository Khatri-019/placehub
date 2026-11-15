import { useCallback, useMemo, useState, type ReactNode } from 'react';
import type { LoginResponse, User } from '../types';
import { clearSession, loadSession, saveSession, updateStoredUser } from './session';
import { AuthContext, type AuthState } from './useAuth';

export function AuthProvider({ children }: { children: ReactNode }) {
  // loadSession() drops expired tokens on app load (spec 7.3).
  const [session, setSession] = useState(() => loadSession());

  const signIn = useCallback((response: LoginResponse) => {
    const next = { token: response.accessToken, user: response.user };
    saveSession(next);
    setSession(next);
  }, []);

  const signOut = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const setUser = useCallback((user: User) => {
    updateStoredUser(user);
    setSession((s) => (s ? { ...s, user } : s));
  }, []);

  const value = useMemo<AuthState>(
    () => ({ user: session?.user ?? null, token: session?.token ?? null, signIn, signOut, setUser }),
    [session, signIn, signOut, setUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
