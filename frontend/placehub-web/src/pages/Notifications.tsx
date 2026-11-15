import { useState } from 'react';
import { notificationApi } from '../api/endpoints';
import { parseError } from '../api/errors';
import { Pagination } from '../components/Pagination';
import { AsyncView, EmptyState } from '../components/States';
import { emitToast } from '../components/toastBus';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime } from '../utils/format';

function notifyBadge() {
  window.dispatchEvent(new Event('placehub:notifications-changed'));
}

export function Notifications() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const list = useAsync(() => notificationApi.list({ unreadOnly, page, size: 20 }), [unreadOnly, page]);

  const markRead = async (id: string) => {
    try {
      await notificationApi.markRead(id);
      list.reload();
      notifyBadge();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    }
  };

  const markAll = async () => {
    try {
      const updated = await notificationApi.markAllRead();
      emitToast({ kind: 'success', message: `${updated} notification(s) marked as read.` });
      list.reload();
      notifyBadge();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Notifications</h1>
          <p className="muted">Updates about your applications and jobs.</p>
        </div>
        <div className="button-row">
          <label className="small" style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
            <input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
            Unread only
          </label>
          <button type="button" className="btn btn-secondary" onClick={markAll}>Mark all as read</button>
        </div>
      </div>
      <AsyncView state={list} empty={(p) => (p.content.length === 0 ? <EmptyState title="You're all caught up" /> : null)}>
        {(p) => (
          <>
            <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {p.content.map((n) => (
                <li key={n.id} className={`card notification-item ${n.read ? '' : 'unread'}`}>
                  <div>
                    <strong>{n.title}</strong>
                    <p style={{ margin: 'var(--space-1) 0' }}>{n.message}</p>
                    <span className="small muted">{formatDateTime(n.createdAt)}</span>
                  </div>
                  {!n.read && (
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => markRead(n.id)}>
                      Mark read
                    </button>
                  )}
                </li>
              ))}
            </ul>
            <Pagination page={p} onChange={setPage} />
          </>
        )}
      </AsyncView>
    </div>
  );
}
