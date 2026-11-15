import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { Building2, GraduationCap, Landmark } from 'lucide-react';
import { authApi, type Portal } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { useAuth } from '../../auth/useAuth';
import { AuthLayout } from '../../components/AuthLayout';
import { ROLE_HOME } from '../../types/enums';
import { isPortalKey, validateLogin, type PortalKey } from '../../utils/validation';

const PORTALS: Record<PortalKey, { role: Portal; title: string; heading: string; blurb: string; register?: string;
  demo: string[] }> = {
  student: { role: 'STUDENT', title: 'Student sign in', heading: 'Your placement journey, in one place.',
    blurb: 'See which jobs you qualify for, what the placement policy means for you, and where every application stands.',
    register: '/register/student', demo: ['student@placehub.local', 'student2@placehub.local'] },
  recruiter: { role: 'RECRUITER', title: 'Recruiter sign in', heading: 'Hire from campus with less busywork.',
    blurb: 'Post jobs, let clear criteria auto-shortlist applicants, and review resumes in one queue.',
    register: '/register/recruiter', demo: ['recruiter@placehub.local', 'recruiter2@placehub.local'] },
  admin: { role: 'ADMIN', title: 'Placement office sign in', heading: 'Run placements with an audit trail.',
    blurb: 'Configure the placement policy, record placements and review consents. Accounts are created by the institute.',
    demo: ['admin@placehub.local'] },
};

/** /login: choose a portal. */
export function PortalChooser() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />;
  const suffix = params.toString() ? `?${params.toString()}` : '';
  const items = [
    { key: 'student', label: 'Student', icon: <GraduationCap aria-hidden="true" />, text: 'Browse and apply to jobs' },
    { key: 'recruiter', label: 'Recruiter', icon: <Building2 aria-hidden="true" />, text: 'Manage jobs and applicants' },
    { key: 'admin', label: 'Placement Office', icon: <Landmark aria-hidden="true" />, text: 'Policy, placements and records' },
  ];
  return (
    <AuthLayout heading="Welcome to PlaceHub Lite." blurb="Pick the portal that matches your account.">
      <h1>Sign in</h1>
      {params.get('expired') === '1' && (
        <p className="banner banner-info" role="status">Your session expired. Please sign in again.</p>
      )}
      <ul className="stack-sm" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {items.map((i) => (
          <li key={i.key}>
            <Link to={`/login/${i.key}${suffix}`} className="card card-tight row" style={{ textDecoration: 'none', color: 'inherit' }}>
              <span aria-hidden="true">{i.icon}</span>
              <span className="stack-sm" style={{ gap: 0 }}>
                <span style={{ fontWeight: 600 }}>{i.label}</span>
                <span className="small muted">{i.text}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </AuthLayout>
  );
}

/** /login/:portal. The portal is sent to the API, which rejects other roles (only after a correct password). */
export function Login({ portal: portalProp }: { portal?: PortalKey }) {
  const params = useParams();
  const portalKey: PortalKey = portalProp ?? (isPortalKey(params.portal) ? params.portal : 'student');
  const portal = PORTALS[portalKey];
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [search] = useSearchParams();
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
      const response = await authApi.login(email.trim(), password, portal.role);
      signIn(response);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && !from.startsWith('/login') ? from : ROLE_HOME[response.user.role], { replace: true });
    } catch (err) {
      setFormError(parseError(err).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout heading={portal.heading} blurb={portal.blurb}
      asideFooter={<>Not a {portalKey === 'admin' ? 'placement officer' : portalKey}? <Link to="/login">Choose another portal</Link></>}>
      <h1>{portal.title}</h1>
      {search.get('expired') === '1' && (
        <p className="banner banner-info" role="status">Your session expired. Please sign in again.</p>
      )}
      {search.get('registered') === '1' && (
        <p className="banner banner-success" role="status">Account created. You can sign in now.</p>
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
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      {portal.register ? (
        <p className="small muted" style={{ marginTop: 'var(--space-4)' }}>
          New here? <Link to={portal.register}>Create a {portalKey} account</Link>
        </p>
      ) : (
        <p className="small muted" style={{ marginTop: 'var(--space-4)' }}>
          Placement office accounts are provisioned by the institute.
        </p>
      )}
      {import.meta.env.DEV && (
        <div className="hint">
          <strong>Demo {portalKey} accounts (local only)</strong>, password <code>PlaceHub@123</code>:
          <br />{portal.demo.join(' · ')}
        </div>
      )}
    </AuthLayout>
  );
}
