import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { signInAs } from '../test/fakeSession';
import { AuthProvider } from './AuthContext';
import { ProtectedRoute } from './ProtectedRoute';
import { isExpired } from './session';
import { fakeToken } from '../test/fakeSession';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Login page</p>} />
          <Route path="/student" element={<p>Student home</p>} />
          <Route path="/recruiter" element={<p>Recruiter home</p>} />
          <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><p>Admin area</p></ProtectedRoute>} />
          <Route path="/recruiter/jobs" element={<ProtectedRoute roles={['RECRUITER']}><p>Recruiter jobs</p></ProtectedRoute>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('redirects anonymous users to login', () => {
    renderAt('/admin');
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('redirects a student away from admin pages to the student home', () => {
    signInAs('STUDENT');
    renderAt('/admin');
    expect(screen.getByText('Student home')).toBeInTheDocument();
    expect(screen.queryByText('Admin area')).not.toBeInTheDocument();
  });

  it('redirects a recruiter away from admin pages to the recruiter home', () => {
    signInAs('RECRUITER');
    renderAt('/admin');
    expect(screen.getByText('Recruiter home')).toBeInTheDocument();
  });

  it('renders the page for the right role', () => {
    signInAs('ADMIN');
    renderAt('/admin');
    expect(screen.getByText('Admin area')).toBeInTheDocument();
  });

  it('treats an expired token as signed out', () => {
    signInAs('RECRUITER', -60);
    renderAt('/recruiter/jobs');
    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(sessionStorage.getItem('placehub.token')).toBeNull();
  });

  it('decodes token expiry', () => {
    expect(isExpired(fakeToken(120))).toBe(false);
    expect(isExpired(fakeToken(-1))).toBe(true);
    expect(isExpired('garbage')).toBe(true);
  });
});
