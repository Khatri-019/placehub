export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Client-side checks for the login form; the backend still validates everything. */
export function validateLogin(email: string, password: string): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!email.trim()) errors.email = 'Email is required';
  else if (!EMAIL_PATTERN.test(email.trim())) errors.email = 'Enter a valid email address';
  if (!password) errors.password = 'Password is required';
  return errors;
}

export type PortalKey = 'student' | 'recruiter' | 'admin';

export function isPortalKey(value: string | undefined): value is PortalKey {
  return value === 'student' || value === 'recruiter' || value === 'admin';
}
