import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Field } from '../../components/Field';
import { emitToast } from '../../components/toastBus';
import type { RecruiterRegistration } from '../../types';

const EMPTY: RecruiterRegistration = { fullName: '', email: '', password: '', companyName: '', designation: '' };

function validate(f: RecruiterRegistration): Record<string, string> {
  const e: Record<string, string> = {};
  if (f.fullName.trim().length < 2) e.fullName = 'Enter your full name';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) e.email = 'Enter a valid work email';
  if (f.password.length < 8 || f.password.length > 72 || !/[A-Za-z]/.test(f.password) || !/\d/.test(f.password)) {
    e.password = '8 to 72 characters with at least one letter and one digit';
  }
  if (!f.companyName.trim()) e.companyName = 'Company name is required';
  if (!f.designation.trim()) e.designation = 'Designation is required';
  return e;
}

export function RegisterRecruiter() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (key: keyof RecruiterRegistration, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate(form);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await authApi.registerRecruiter({ ...form, email: form.email.trim() });
      emitToast({ kind: 'success', message: 'Recruiter account created. Please sign in.' });
      navigate('/login?registered=1');
    } catch (err) {
      const parsed = parseError(err);
      setFormError(parsed.message);
      setErrors(parsed.fieldErrors);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1>Recruiter registration</h1>
        <form className="form" onSubmit={onSubmit} noValidate>
          {formError && <div className="form-error" role="alert">{formError}</div>}
          <Field id="fullName" label="Full name" error={errors.fullName}>
            {(a) => <input {...a} autoComplete="name" value={form.fullName} onChange={(e) => set('fullName', e.target.value)} />}
          </Field>
          <Field id="email" label="Work email" error={errors.email}>
            {(a) => <input {...a} type="email" autoComplete="email" value={form.email} onChange={(e) => set('email', e.target.value)} />}
          </Field>
          <Field id="password" label="Password" error={errors.password} hint="At least 8 characters, with a letter and a digit">
            {(a) => <input {...a} type="password" autoComplete="new-password" value={form.password} onChange={(e) => set('password', e.target.value)} />}
          </Field>
          <Field id="companyName" label="Company name" error={errors.companyName} hint="Used to prefill your company profile">
            {(a) => <input {...a} autoComplete="organization" value={form.companyName} onChange={(e) => set('companyName', e.target.value)} />}
          </Field>
          <Field id="designation" label="Designation" error={errors.designation}>
            {(a) => <input {...a} autoComplete="organization-title" value={form.designation} onChange={(e) => set('designation', e.target.value)} />}
          </Field>
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
        </form>
        <p className="small muted">Already registered? <Link to="/login">Sign in</Link></p>
      </div>
    </div>
  );
}
