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
          <Route path="/login/admin" element={<p>Admin login page</p>} />
          <Route path="/login/recruiter" element={<p>Recruiter login page</p>} />
          <Route path="/notifications" element={<ProtectedRoute><p>Notifications</p></ProtectedRoute>} />
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
  it('redirects anonymous users to the login portal of the area they opened', () => {
    renderAt('/admin');
    expect(screen.getByText('Admin login page')).toBeInTheDocument();
  });

  it('redirects anonymous users on shared pages to the portal chooser', () => {
    renderAt('/notifications');
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
    expect(screen.getByText('Recruiter login page')).toBeInTheDocument();
    expect(sessionStorage.getItem('placehub.token')).toBeNull();
  });

  it('decodes token expiry', () => {
    expect(isExpired(fakeToken(120))).toBe(false);
    expect(isExpired(fakeToken(-1))).toBe(true);
    expect(isExpired('garbage')).toBe(true);
  });
});
