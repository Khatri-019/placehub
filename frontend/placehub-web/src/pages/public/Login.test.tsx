import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { validateLogin } from '../../utils/validation';
import { Login } from './Login';

const loginMock = vi.fn();
vi.mock('../../api/endpoints', () => ({
  authApi: { login: (...args: unknown[]) => loginMock(...args) },
}));

function renderLogin(path = '/login') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('Login form validation', () => {
  beforeEach(() => loginMock.mockReset());

  it('requires email and password before calling the API', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('rejects a malformed email', async () => {
    renderLogin();
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('submits valid credentials and shows backend errors', async () => {
    loginMock.mockRejectedValueOnce(new Error('Invalid email or password'));
    renderLogin();
    await userEvent.type(screen.getByLabelText('Email'), 'student@placehub.local');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(loginMock).toHaveBeenCalledWith('student@placehub.local', 'wrong-pass1');
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });

  it('shows the session-expired notice', () => {
    renderLogin('/login?expired=1');
    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
  });

  it('validateLogin is pure', () => {
    expect(validateLogin(' a@b.co ', 'x')).toEqual({});
    expect(validateLogin('', '')).toEqual({ email: 'Email is required', password: 'Password is required' });
  });
});
