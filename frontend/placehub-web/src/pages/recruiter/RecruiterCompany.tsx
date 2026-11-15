import { useEffect, useState, type FormEvent } from 'react';
import { companyApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { useAuth } from '../../auth/useAuth';
import { Field } from '../../components/Field';
import { Spinner, ErrorState } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import type { Company, CompanyRequest } from '../../types';
import type { ParsedError } from '../../api/errors';

export function RecruiterCompany() {
  const { user } = useAuth();
  const [existing, setExisting] = useState<Company | null>(null);
  const [form, setForm] = useState<CompanyRequest>({ name: '', website: '', industry: '', description: '' });
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ParsedError | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError(null);
    companyApi.mine()
      .then((c) => {
        if (!active) return;
        setExisting(c);
        setForm({ name: c.name, website: c.website ?? '', industry: c.industry ?? '', description: c.description ?? '' });
      })
      .catch((err) => {
        if (!active) return;
        const parsed = parseError(err);
        if (parsed.status === 404) {
          // Prefill from the company name captured at registration.
          setForm((f) => ({ ...f, name: user?.recruiterDetails?.companyName ?? '' }));
        } else {
          setLoadError(parsed);
        }
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [user, attempt]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.name.trim()) found.name = 'Company name is required';
    if (form.website && !/^https?:\/\/.+/.test(form.website)) found.website = 'Must start with http:// or https://';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      const saved = existing ? await companyApi.updateMine(form) : await companyApi.create(form);
      setExisting(saved);
      emitToast({ kind: 'success', message: existing ? 'Company profile updated.' : 'Company profile created.' });
    } catch (err) {
      const parsed = parseError(err);
      setFormError(parsed.message);
      setErrors(parsed.fieldErrors);
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <Spinner />;
  if (loadError) return <ErrorState error={loadError} onRetry={() => setAttempt((a) => a + 1)} />;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{existing ? 'Company profile' : 'Create your company profile'}</h1>
          <p className="muted">One company per recruiter. Students see this on every job you post.</p>
        </div>
      </div>
      <form className="form card" onSubmit={onSubmit} noValidate>
        {formError && <div className="form-error" role="alert">{formError}</div>}
        <Field id="name" label="Company name" error={errors.name}>
          {(a) => <input {...a} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />}
        </Field>
        <Field id="website" label="Website" error={errors.website}>
          {(a) => <input {...a} type="url" placeholder="https://" value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} />}
        </Field>
        <Field id="industry" label="Industry" error={errors.industry}>
          {(a) => <input {...a} value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} />}
        </Field>
        <Field id="description" label="Description" error={errors.description}>
          {(a) => <textarea {...a} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />}
        </Field>
        <div>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : existing ? 'Save changes' : 'Create company'}
          </button>
        </div>
      </form>
    </div>
  );
}
