import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { jobApi, type JobBrowseFilters } from '../../api/endpoints';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { Branch, EmploymentType } from '../../types';
import { BRANCHES, BRANCH_LABELS, EMPLOYMENT_TYPE_LABELS } from '../../types/enums';
import { formatDate } from '../../utils/format';

export function StudentJobs() {
  const [draft, setDraft] = useState<JobBrowseFilters>({ q: '', location: '', employmentType: '', branch: '' });
  const [filters, setFilters] = useState<JobBrowseFilters>({ page: 0, size: 10, sort: 'deadline,asc' });
  const jobs = useAsync(() => jobApi.browse(filters), [JSON.stringify(filters)]);

  const apply = (e: FormEvent) => {
    e.preventDefault();
    setFilters((f) => ({ ...f, ...draft, page: 0 }));
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Browse jobs</h1>
          <p className="muted">Only published openings are shown.</p>
        </div>
      </div>
      <form className="filters" onSubmit={apply} role="search">
        <div className="field">
          <label htmlFor="q">Search</label>
          <input id="q" value={draft.q} placeholder="Title, description or company"
            onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="location">Location</label>
          <input id="location" value={draft.location} onChange={(e) => setDraft({ ...draft, location: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="type">Type</label>
          <select id="type" value={draft.employmentType}
            onChange={(e) => setDraft({ ...draft, employmentType: e.target.value as EmploymentType | '' })}>
            <option value="">Any</option>
            {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="branch">Branch</label>
          <select id="branch" value={draft.branch} onChange={(e) => setDraft({ ...draft, branch: e.target.value as Branch | '' })}>
            <option value="">Any</option>
            {BRANCHES.map((b) => <option key={b} value={b}>{BRANCH_LABELS[b]}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="sort">Sort</label>
          <select id="sort" value={filters.sort} onChange={(e) => setFilters({ ...filters, sort: e.target.value, page: 0 })}>
            <option value="deadline,asc">Deadline (soonest)</option>
            <option value="createdAt,desc">Newest</option>
          </select>
        </div>
        <button type="submit" className="btn btn-primary">Apply filters</button>
      </form>
      <AsyncView state={jobs}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="No jobs match your filters" /> : null)}>
        {(page) => (
          <>
            <ul className="job-list" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {page.content.map((job) => (
                <li key={job.id} className="card job-card">
                  <div>
                    <h2><Link to={`/student/jobs/${job.id}`}>{job.title}</Link></h2>
                    <div className="job-meta">
                      <span>{job.companyName}</span>
                      <span>{job.location}</span>
                      <span>{EMPLOYMENT_TYPE_LABELS[job.employmentType]}</span>
                      {job.ctc && <span>{job.ctc}</span>}
                    </div>
                  </div>
                  <div className="small muted">
                    Min CGPA {Number(job.minCgpa).toFixed(2)} · Apply by {formatDate(job.deadline)}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination page={page} onChange={(p) => setFilters({ ...filters, page: p })} />
          </>
        )}
      </AsyncView>
    </div>
  );
}
