import { createContext, useContext } from 'react';
import type { LoginResponse, User } from '../types';

export interface AuthState {
  user: User | null;
  token: string | null;
  signIn: (response: LoginResponse) => void;
  signOut: () => void;
  setUser: (user: User) => void;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
