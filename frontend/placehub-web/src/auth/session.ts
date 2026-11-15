import type { User } from '../types';

// Token kept in sessionStorage (cleared when the tab closes). Production should prefer HttpOnly secure cookies.
const TOKEN_KEY = 'placehub.token';
const USER_KEY = 'placehub.user';

export interface Session {
  token: string;
  user: User;
}

/** Reads the `exp` claim (seconds) without verifying; the backend is the authority on validity. */
export function tokenExpiry(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp : null;
  } catch {
    return null;
  }
}

export function isExpired(token: string, nowMs = Date.now()): boolean {
  const exp = tokenExpiry(token);
  return exp === null || exp * 1000 <= nowMs;
}

export function loadSession(): Session | null {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const rawUser = sessionStorage.getItem(USER_KEY);
  if (!token || !rawUser) return null;
  if (isExpired(token)) {
    clearSession();
    return null;
  }
  try {
    return { token, user: JSON.parse(rawUser) as User };
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(TOKEN_KEY, session.token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(session.user));
}

export function updateStoredUser(user: User): void {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}
