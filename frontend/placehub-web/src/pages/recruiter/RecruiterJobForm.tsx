import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { jobApi } from '../../api/endpoints';
import { parseError, type ParsedError } from '../../api/errors';
import { Field } from '../../components/Field';
import { ErrorState, Spinner } from '../../components/States';
import { emitToast } from '../../components/toastBus';
import type { Branch, EmploymentType, JobRequest } from '../../types';
import { BRANCHES, BRANCH_LABELS, EMPLOYMENT_TYPE_LABELS } from '../../types/enums';
import { fromLocalInput, toLocalInput } from '../../utils/format';

interface FormState {
  title: string;
  description: string;
  location: string;
  employmentType: EmploymentType;
  ctc: string;
  ctcLpa: string;
  minCgpa: string;
  maxBacklogs: string;
  eligibleBranches: Branch[];
  years: string;
  deadline: string;
}

const EMPTY: FormState = {
  title: '', description: '', location: '', employmentType: 'FULL_TIME', ctc: '', ctcLpa: '', minCgpa: '0', maxBacklogs: '0',
  eligibleBranches: [], years: '', deadline: '',
};

function parseYears(raw: string): number[] | null {
  if (!raw.trim()) return [];
  const years = raw.split(',').map((s) => Number(s.trim()));
  return years.every((y) => Number.isInteger(y) && y >= 2020 && y <= 2035) ? [...new Set(years)] : null;
}

function validate(f: FormState): Record<string, string> {
  const e: Record<string, string> = {};
  if (!f.title.trim()) e.title = 'Title is required';
  if (!f.description.trim()) e.description = 'Description is required';
  if (!f.location.trim()) e.location = 'Location is required';
  const cgpa = Number(f.minCgpa);
  if (Number.isNaN(cgpa) || cgpa < 0 || cgpa > 10) e.minCgpa = 'Between 0 and 10';
  if (f.ctcLpa.trim()) {
    const ctc = Number(f.ctcLpa);
    if (Number.isNaN(ctc) || ctc <= 0 || ctc > 9999.99) e.ctcLpa = 'Between 0.01 and 9999.99 LPA';
  } else if (f.employmentType === 'FULL_TIME') {
    e.ctcLpa = 'Required for full-time jobs';
  }
  const backlogs = Number(f.maxBacklogs);
  if (!Number.isInteger(backlogs) || backlogs < 0 || backlogs > 50) e.maxBacklogs = 'Between 0 and 50';
  if (parseYears(f.years) === null) e.years = 'Comma-separated years between 2020 and 2035';
  if (!f.deadline) e.deadline = 'Deadline is required';
  return e;
}

export function RecruiterJobForm() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(EMPTY);
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<ParsedError | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<ParsedError | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    jobApi.get(id)
      .then((j) => setForm({
        title: j.title, description: j.description, location: j.location, employmentType: j.employmentType,
        ctc: j.ctc ?? '', ctcLpa: j.ctcLpa == null ? '' : String(j.ctcLpa), minCgpa: String(j.minCgpa), maxBacklogs: String(j.maxBacklogs),
        eligibleBranches: j.eligibleBranches, years: j.eligibleGraduationYears.join(', '), deadline: toLocalInput(j.deadline),
      }))
      .catch((err) => setLoadError(parseError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const toggleBranch = (b: Branch) =>
    setForm((f) => ({
      ...f,
      eligibleBranches: f.eligibleBranches.includes(b) ? f.eligibleBranches.filter((x) => x !== b) : [...f.eligibleBranches, b],
    }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate(form);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    const body: JobRequest = {
      title: form.title.trim(),
      description: form.description.trim(),
      location: form.location.trim(),
      employmentType: form.employmentType,
      ctc: form.ctc.trim() || undefined,
      ctcLpa: form.ctcLpa.trim() ? Math.round(Number(form.ctcLpa) * 100) / 100 : undefined,
      minCgpa: Math.round(Number(form.minCgpa) * 100) / 100,
      maxBacklogs: Number(form.maxBacklogs),
      eligibleBranches: form.eligibleBranches,
      eligibleGraduationYears: parseYears(form.years) ?? [],
      deadline: fromLocalInput(form.deadline),
    };
    setBusy(true);
    try {
      const saved = editing && id ? await jobApi.update(id, body) : await jobApi.create(body);
      emitToast({ kind: 'success', message: editing ? 'Job updated.' : 'Job saved as draft. Publish it when ready.' });
      navigate(`/recruiter/jobs?status=${saved.status}`);
    } catch (err) {
      const parsed = parseError(err);
      setFormError(parsed);
      setErrors(parsed.fieldErrors);
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <Spinner />;
  if (loadError) return <ErrorState error={loadError} />;

  return (
    <div>
      <p className="small"><Link to="/recruiter/jobs">← Back to my jobs</Link></p>
      <div className="page-header">
        <h1>{editing ? 'Edit job' : 'Post a job'}</h1>
      </div>
      <form className="form card" onSubmit={onSubmit} noValidate>
        {formError && (
          <div className="form-error" role="alert">
            {formError.message}
            {formError.status === 409 && formError.message.includes('company') && (
              <> · <Link to="/recruiter/company">Create company profile</Link></>
            )}
          </div>
        )}
        <Field id="title" label="Title" error={errors.title}>
          {(a) => <input {...a} maxLength={150} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />}
        </Field>
        <Field id="description" label="Description" error={errors.description}>
          {(a) => <textarea {...a} maxLength={5000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />}
        </Field>
        <div className="form-row">
          <Field id="location" label="Location" error={errors.location}>
            {(a) => <input {...a} value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />}
          </Field>
          <Field id="employmentType" label="Employment type">
            {(a) => (
              <select {...a} value={form.employmentType} onChange={(e) => setForm({ ...form, employmentType: e.target.value as EmploymentType })}>
                {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            )}
          </Field>
          <Field id="ctcLpa" label="CTC (LPA)" error={errors.ctcLpa}
            hint={form.employmentType === 'FULL_TIME' ? 'Lakhs per annum; required to publish' : 'Optional for internships'}>
            {(a) => <input {...a} type="number" step="0.01" min={0.01} value={form.ctcLpa} onChange={(e) => setForm({ ...form, ctcLpa: e.target.value })} />}
          </Field>
          <Field id="ctc" label="CTC label (optional)" error={errors.ctc}>
            {(a) => <input {...a} maxLength={100} value={form.ctc} onChange={(e) => setForm({ ...form, ctc: e.target.value })} />}
          </Field>
        </div>
        <div className="form-row">
          <Field id="minCgpa" label="Minimum CGPA" error={errors.minCgpa}>
            {(a) => <input {...a} type="number" step="0.01" min={0} max={10} value={form.minCgpa} onChange={(e) => setForm({ ...form, minCgpa: e.target.value })} />}
          </Field>
          <Field id="maxBacklogs" label="Maximum backlogs" error={errors.maxBacklogs}>
            {(a) => <input {...a} type="number" min={0} max={50} value={form.maxBacklogs} onChange={(e) => setForm({ ...form, maxBacklogs: e.target.value })} />}
          </Field>
          <Field id="deadline" label="Application deadline" error={errors.deadline} hint="Your local time">
            {(a) => <input {...a} type="datetime-local" value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} />}
          </Field>
        </div>
        <fieldset className="field">
          <legend>Eligible branches (none selected = all branches)</legend>
          <div className="checkbox-grid">
            {BRANCHES.map((b) => (
              <label key={b} title={BRANCH_LABELS[b]}>
                <input type="checkbox" checked={form.eligibleBranches.includes(b)} onChange={() => toggleBranch(b)} />
                {b}
              </label>
            ))}
          </div>
        </fieldset>
        <Field id="years" label="Eligible graduation years" error={errors.years} hint="Comma-separated, e.g. 2027, 2028. Leave empty for all years.">
          {(a) => <input {...a} value={form.years} onChange={(e) => setForm({ ...form, years: e.target.value })} />}
        </Field>
        <div>
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Save draft'}</button>
        </div>
      </form>
    </div>
  );
}
