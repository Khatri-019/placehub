import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { Field } from '../../components/Field';
import { emitToast } from '../../components/toastBus';
import type { Branch, StudentRegistration } from '../../types';
import { BRANCHES, BRANCH_LABELS } from '../../types/enums';

const EMPTY: StudentRegistration = {
  fullName: '', email: '', password: '', collegeName: '', branch: '', graduationYear: '', cgpa: '', backlogCount: '',
};

function validate(f: StudentRegistration): Record<string, string> {
  const e: Record<string, string> = {};
  if (f.fullName.trim().length < 2) e.fullName = 'Enter your full name (2 to 100 characters)';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) e.email = 'Enter a valid email address';
  if (f.password.length < 8 || f.password.length > 72 || !/[A-Za-z]/.test(f.password) || !/\d/.test(f.password)) {
    e.password = '8 to 72 characters with at least one letter and one digit';
  }
  if (!f.collegeName.trim()) e.collegeName = 'College name is required';
  if (!f.branch) e.branch = 'Select your branch';
  const year = Number(f.graduationYear);
  if (f.graduationYear === '' || !Number.isInteger(year) || year < 2020 || year > 2035) {
    e.graduationYear = 'Graduation year must be between 2020 and 2035';
  }
  const cgpa = Number(f.cgpa);
  if (f.cgpa === '' || Number.isNaN(cgpa) || cgpa < 0 || cgpa > 10) e.cgpa = 'CGPA must be between 0.00 and 10.00';
  const backlogs = Number(f.backlogCount);
  if (f.backlogCount === '' || !Number.isInteger(backlogs) || backlogs < 0 || backlogs > 50) {
    e.backlogCount = 'Backlogs must be between 0 and 50';
  }
  return e;
}

export function RegisterStudent() {
  const navigate = useNavigate();
  const [form, setForm] = useState<StudentRegistration>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = <K extends keyof StudentRegistration>(key: K, value: StudentRegistration[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate(form);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await authApi.registerStudent({
        ...form,
        email: form.email.trim(),
        graduationYear: Number(form.graduationYear),
        cgpa: Math.round(Number(form.cgpa) * 100) / 100,
        backlogCount: Number(form.backlogCount),
      });
      emitToast({ kind: 'success', message: 'Account created. Please sign in.' });
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
        <h1>Student registration</h1>
        <p className="muted small">Academic details are read-only after registration in v1 (placement office verification is planned).</p>
        <form className="form" onSubmit={onSubmit} noValidate>
          {formError && <div className="form-error" role="alert">{formError}</div>}
          <Field id="fullName" label="Full name" error={errors.fullName}>
            {(a) => <input {...a} autoComplete="name" value={form.fullName} onChange={(e) => set('fullName', e.target.value)} />}
          </Field>
          <Field id="email" label="Email" error={errors.email}>
            {(a) => <input {...a} type="email" autoComplete="email" value={form.email} onChange={(e) => set('email', e.target.value)} />}
          </Field>
          <Field id="password" label="Password" error={errors.password} hint="At least 8 characters, with a letter and a digit">
            {(a) => <input {...a} type="password" autoComplete="new-password" value={form.password} onChange={(e) => set('password', e.target.value)} />}
          </Field>
          <Field id="collegeName" label="College" error={errors.collegeName}>
            {(a) => <input {...a} value={form.collegeName} onChange={(e) => set('collegeName', e.target.value)} />}
          </Field>
          <Field id="branch" label="Branch" error={errors.branch}>
            {(a) => (
              <select {...a} value={form.branch} onChange={(e) => set('branch', e.target.value as Branch)}>
                <option value="">Select a branch</option>
                {BRANCHES.map((b) => <option key={b} value={b}>{BRANCH_LABELS[b]} ({b})</option>)}
              </select>
            )}
          </Field>
          <div className="form-row">
            <Field id="graduationYear" label="Graduation year" error={errors.graduationYear}>
              {(a) => <input {...a} type="number" min={2020} max={2035} value={form.graduationYear}
                onChange={(e) => set('graduationYear', e.target.value === '' ? '' : Number(e.target.value))} />}
            </Field>
            <Field id="cgpa" label="CGPA" error={errors.cgpa}>
              {(a) => <input {...a} type="number" step="0.01" min={0} max={10} value={form.cgpa}
                onChange={(e) => set('cgpa', e.target.value === '' ? '' : Number(e.target.value))} />}
            </Field>
            <Field id="backlogCount" label="Active backlogs" error={errors.backlogCount}>
              {(a) => <input {...a} type="number" min={0} max={50} value={form.backlogCount}
                onChange={(e) => set('backlogCount', e.target.value === '' ? '' : Number(e.target.value))} />}
            </Field>
          </div>
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
        </form>
        <p className="small muted">Already registered? <Link to="/login">Sign in</Link></p>
      </div>
    </div>
  );
}
