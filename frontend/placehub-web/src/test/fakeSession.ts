import { saveSession } from '../auth/session';
import type { Role, User } from '../types';

function base64Url(value: object): string {
  return btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
}

/** Builds an unsigned JWT-shaped token (the frontend only reads `exp`). */
export function fakeToken(expSecondsFromNow: number): string {
  const exp = Math.floor(Date.now() / 1000) + expSecondsFromNow;
  return `${base64Url({ alg: 'HS256' })}.${base64Url({ sub: 'u1', exp })}.signature`;
}

export function signInAs(role: Role, expSecondsFromNow = 3600): User {
  const user: User = {
    id: '00000000-0000-0000-0000-00000000000a',
    fullName: `Test ${role.toLowerCase()}`,
    email: `${role.toLowerCase()}@test.local`,
    role,
    status: 'ACTIVE',
    createdAt: new Date().toISOString(),
  };
  saveSession({ token: fakeToken(expSecondsFromNow), user });
  return user;
}
