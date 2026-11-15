import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CalendarClock, MapPin, SlidersHorizontal, Wallet } from 'lucide-react';
import { applicationApi, jobApi, type JobBrowseFilters } from '../../api/endpoints';
import { useAuth } from '../../auth/useAuth';
import { Pagination } from '../../components/Pagination';
import { AsyncView } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import type { Branch, EmploymentType, Job, StudentDetails } from '../../types';
import { BRANCHES, BRANCH_LABELS, EMPLOYMENT_TYPE_LABELS } from '../../types/enums';
import { Badge, PolicyBadge } from '../../ui/Badge';
import { EmptyState } from '../../ui/EmptyState';
import { Drawer } from '../../ui/Modal';
import { Skeleton } from '../../ui/Skeleton';
import { policyBadgeFor } from '../../utils/policy';
import { deadlineLabel } from './jobFormat';
import styles from './StudentJobs.module.css';

/** Quick client-side eligibility hint; the job page shows the authoritative server evaluation. */
function eligibleHint(job: Job, s?: StudentDetails): boolean | null {
  if (!s) return null;
  if (Number(s.cgpa) < Number(job.minCgpa)) return false;
  if (s.backlogCount > job.maxBacklogs) return false;
  if (job.eligibleBranches.length && !job.eligibleBranches.includes(s.branch)) return false;
  if (job.eligibleGraduationYears.length && !job.eligibleGraduationYears.includes(s.graduationYear)) return false;
  return true;
}

function Filters({ draft, setDraft, onApply, sort, onSort }: {
  draft: JobBrowseFilters;
  setDraft: (f: JobBrowseFilters) => void;
  onApply: (e: FormEvent) => void;
  sort: string | undefined;
  onSort: (s: string) => void;
}) {
  // The rail and the mobile sheet both render this form, so ids must be unique per instance.
  const uid = useId();
  const id = (name: string) => `${uid}-${name}`;
  return (
    <form className="stack" onSubmit={onApply} role="search" aria-label="Filter jobs">
      <div className="field">
        <label htmlFor={id('q')}>Search</label>
        <input id={id('q')} value={draft.q} placeholder="Title, description or company" onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
      </div>
      <div className="field">
        <label htmlFor={id('location')}>Location</label>
        <input id={id('location')} value={draft.location} onChange={(e) => setDraft({ ...draft, location: e.target.value })} />
      </div>
      <div className="field">
        <label htmlFor={id('type')}>Type</label>
        <select id={id('type')} value={draft.employmentType} onChange={(e) => setDraft({ ...draft, employmentType: e.target.value as EmploymentType | '' })}>
          <option value="">Any</option>
          {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor={id('branch')}>Branch</label>
        <select id={id('branch')} value={draft.branch} onChange={(e) => setDraft({ ...draft, branch: e.target.value as Branch | '' })}>
          <option value="">Any</option>
          {BRANCHES.map((b) => <option key={b} value={b}>{BRANCH_LABELS[b]}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor={id('sort')}>Sort</label>
        <select id={id('sort')} value={sort} onChange={(e) => onSort(e.target.value)}>
          <option value="deadline,asc">Deadline (soonest)</option>
          <option value="createdAt,desc">Newest</option>
        </select>
      </div>
      <button type="submit" className="btn btn-primary">Apply filters</button>
    </form>
  );
}

export function StudentJobs() {
  const { user } = useAuth();
  const [draft, setDraft] = useState<JobBrowseFilters>({ q: '', location: '', employmentType: '', branch: '' });
  const [filters, setFilters] = useState<JobBrowseFilters>({ page: 0, size: 10, sort: 'deadline,asc' });
  const [sheet, setSheet] = useState(false);
  const jobs = useAsync(() => jobApi.browse(filters), [JSON.stringify(filters)]);
  const placement = useAsync(() => applicationApi.placementState(), []);

  const apply = (e: FormEvent) => {
    e.preventDefault();
    setFilters((f) => ({ ...f, ...draft, page: 0 }));
    setSheet(false);
  };
  const onSort = (sort: string) => setFilters((f) => ({ ...f, sort, page: 0 }));

  return (
    <div className={styles.layout}>
      <aside className={`card ${styles.rail}`} aria-label="Filters">
        <h2 className="label">Filters</h2>
        <Filters draft={draft} setDraft={setDraft} onApply={apply} sort={filters.sort} onSort={onSort} />
      </aside>
      <div className="stack">
        <div className="row-between">
          <p className="muted" style={{ margin: 0 }}>Only published openings are shown.</p>
          <button type="button" className={`btn btn-ghost ${styles.sheetButton}`} onClick={() => setSheet(true)}>
            <SlidersHorizontal aria-hidden="true" /> Filters
          </button>
        </div>
        <Drawer open={sheet} title="Filter jobs" side="bottom" onClose={() => setSheet(false)}>
          <Filters draft={draft} setDraft={setDraft} onApply={apply} sort={filters.sort} onSort={onSort} />
        </Drawer>
        <AsyncView state={jobs} skeleton={<div className="stack">{[0, 1, 2].map((i) => <Skeleton key={i} lines={3} card />)}</div>}
          empty={(p) => (p.content.length === 0 ? <EmptyState title="No jobs match your filters" illustration="search" /> : null)}>
          {(page) => (
            <>
              <ul className={styles.list}>
                {page.content.map((job) => {
                  const due = deadlineLabel(job.deadline);
                  const eligible = eligibleHint(job, user?.studentDetails);
                  const policy = policyBadgeFor(job, placement.data);
                  return (
                    <li key={job.id} className={`card ${styles.job}`}>
                      <span className={styles.avatar} aria-hidden="true">{job.companyName.slice(0, 1).toUpperCase()}</span>
                      <div className={styles.body}>
                        <h2 className={styles.title}><Link to={`/student/jobs/${job.id}`}>{job.title}</Link></h2>
                        <div className={styles.company}>{job.companyName} · {EMPLOYMENT_TYPE_LABELS[job.employmentType]}</div>
                        <div className={styles.meta}>
                          <span><MapPin aria-hidden="true" />{job.location}</span>
                          <span className="num"><Wallet aria-hidden="true" />
                            {job.ctcLpa != null ? `${Number(job.ctcLpa).toFixed(2)} LPA` : job.ctc ?? 'Not specified'}</span>
                          <span className={due.urgent ? styles.urgent : undefined}><CalendarClock aria-hidden="true" />{due.text}</span>
                        </div>
                      </div>
                      <div className={styles.badges}>
                        {eligible !== null && (eligible
                          ? <Badge tone="success">Eligible</Badge> : <Badge tone="neutral">Criteria not met</Badge>)}
                        {policy && <PolicyBadge kind={policy} />}
                      </div>
                    </li>
                  );
                })}
              </ul>
              <Pagination page={page} onChange={(p) => setFilters({ ...filters, page: p })} />
            </>
          )}
        </AsyncView>
      </div>
    </div>
  );
}
