import { useState } from 'react';
import { Bell, BriefcaseBusiness, CheckCheck, Lock, Star } from 'lucide-react';
import { notificationApi } from '../api/endpoints';
import { parseError } from '../api/errors';
import { Pagination } from '../components/Pagination';
import { AsyncView } from '../components/States';
import { emitToast } from '../components/toastBus';
import { useAsync } from '../hooks/useAsync';
import { EmptyState } from '../ui/EmptyState';
import { Skeleton } from '../ui/Skeleton';
import { formatDateTime } from '../utils/format';
import styles from './Notifications.module.css';

function notifyBadge() {
  window.dispatchEvent(new Event('placehub:notifications-changed'));
}

function iconFor(type: string) {
  if (type.includes('PLACEMENT') || type.includes('LOCK')) return <Lock aria-hidden="true" />;
  if (type === 'APPLICATION_STATUS_CHANGED') return <Star aria-hidden="true" />;
  if (type === 'NEW_APPLICANT' || type === 'APPLICATION_CREATED') return <BriefcaseBusiness aria-hidden="true" />;
  return <Bell aria-hidden="true" />;
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
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Notifications</h1>
          <p className="muted" style={{ margin: 0 }}>Updates about your applications and jobs.</p>
        </div>
        <div className="row">
          <label className="check">
            <input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
            Unread only
          </label>
          <button type="button" className="btn btn-secondary" onClick={markAll}><CheckCheck size={16} aria-hidden="true" />Mark all as read</button>
        </div>
      </div>
      <AsyncView state={list} skeleton={<Skeleton lines={5} card />}
        empty={(p) => (p.content.length === 0 ? <EmptyState title="You're all caught up" illustration="bell" /> : null)}>
        {(p) => (
          <>
            <ul className={styles.list}>
              {p.content.map((n) => (
                <li key={n.id} className={`${styles.item} ${n.read ? '' : styles.unread}`}>
                  <span className={styles.icon}>{iconFor(n.type)}</span>
                  <div className={styles.body}>
                    <p className={styles.title}>{n.title}{!n.read && <span className="visually-hidden"> (unread)</span>}</p>
                    <p className={styles.message}>{n.message}</p>
                    <span className="small muted">{formatDateTime(n.createdAt)}</span>
                  </div>
                  {!n.read && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => markRead(n.id)}>Mark read</button>
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
