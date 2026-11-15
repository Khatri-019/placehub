import { useState, type FormEvent } from 'react';
import { userApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import { useAuth } from '../../auth/useAuth';
import { Pagination } from '../../components/Pagination';
import { AsyncView, EmptyState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { emitToast } from '../../components/toastBus';
import { useAsync } from '../../hooks/useAsync';
import type { AccountStatus, Role, User } from '../../types';
import { formatDate } from '../../utils/format';

export function AdminUsers() {
  const { user: me } = useAuth();
  const [role, setRole] = useState<Role | ''>('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [qDraft, setQDraft] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const users = useAsync(() => userApi.adminList({ role, status, q, page, size: 20 }), [role, status, q, page]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const toggle = async (u: User) => {
    const next = u.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE';
    setBusyId(u.id);
    try {
      await userApi.setStatus(u.id, next);
      emitToast({ kind: 'success', message: `${u.fullName} is now ${next === 'ACTIVE' ? 'active' : 'deactivated'}.` });
      users.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusyId(null);
    }
  };

  const search = (e: FormEvent) => {
    e.preventDefault();
    setQ(qDraft);
    setPage(0);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Users</h1>
          <p className="muted">Deactivation takes effect at the user&apos;s next sign-in (existing tokens expire within 60 minutes).</p>
        </div>
      </div>
      <form className="filters" onSubmit={search} role="search">
        <div className="field">
          <label htmlFor="q">Name or email</label>
          <input id="q" value={qDraft} onChange={(e) => setQDraft(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="role">Role</label>
          <select id="role" value={role} onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0); }}>
            <option value="">All</option>
            <option value="STUDENT">Student</option>
            <option value="RECRUITER">Recruiter</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(e) => { setStatus(e.target.value as AccountStatus | ''); setPage(0); }}>
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="DEACTIVATED">Deactivated</option>
            <option value="PENDING_APPROVAL">Pending approval</option>
          </select>
        </div>
        <button type="submit" className="btn btn-primary">Search</button>
      </form>
      <AsyncView state={users} empty={(p) => (p.content.length === 0 ? <EmptyState title="No users match" /> : null)}>
        {(p) => (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Joined</th><th scope="col">Actions</th></tr>
                </thead>
                <tbody>
                  {p.content.map((u) => (
                    <tr key={u.id}>
                      <td>{u.fullName}</td>
                      <td>{u.email}</td>
                      <td>{u.role}</td>
                      <td><StatusBadge status={u.status} /></td>
                      <td>{formatDate(u.createdAt)}</td>
                      <td>
                        {u.id !== me?.id && (
                          <button type="button" disabled={busyId === u.id} onClick={() => toggle(u)}
                            className={`btn btn-sm ${u.status === 'ACTIVE' ? 'btn-danger' : 'btn-primary'}`}>
                            {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
    </div>
  );
}
