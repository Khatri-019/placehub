import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { authApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { useAuth } from '../../auth/useAuth';
import { ROLE_HOME } from '../../types/enums';
import { validateLogin } from '../../utils/validation';

export function Login() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validateLogin(email, password);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    try {
      const response = await authApi.login(email.trim(), password);
      signIn(response);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : ROLE_HOME[response.user.role], { replace: true });
    } catch (err) {
      setFormError(parseError(err).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1>Sign in to PlaceHub</h1>
        {params.get('expired') === '1' && (
          <p className="banner banner-info" role="status">Your session expired. Please sign in again.</p>
        )}
        {params.get('registered') === '1' && (
          <p className="banner banner-info" role="status">Account created. You can sign in now.</p>
        )}
        <form className="form" onSubmit={onSubmit} noValidate>
          {formError && <div className="form-error" role="alert">{formError}</div>}
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="email" value={email}
              onChange={(e) => setEmail(e.target.value)} aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined} />
            {errors.email && <span id="email-error" className="field-error">{errors.email}</span>}
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" value={password}
              onChange={(e) => setPassword(e.target.value)} aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined} />
            {errors.password && <span id="password-error" className="field-error">{errors.password}</span>}
          </div>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="small muted">
          New here? <Link to="/register/student">Register as a student</Link> or{' '}
          <Link to="/register/recruiter">as a recruiter</Link>.
        </p>
        {import.meta.env.DEV && (
          <div className="hint">
            <strong>Demo accounts (local only)</strong>, password <code>PlaceHub@123</code>:
            <br />admin@placehub.local · recruiter@placehub.local · student@placehub.local · student2@placehub.local
          </div>
        )}
      </div>
    </div>
  );
}
