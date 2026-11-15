import { useEffect, useState } from 'react';
import { CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { onToast, type ToastMessage } from './toastBus';
import styles from './Toaster.module.css';

interface Item extends ToastMessage {
  id: number;
}

let nextId = 1;

const ICON = {
  success: <CheckCircle2 aria-hidden="true" />,
  error: <XCircle aria-hidden="true" />,
  info: <Info aria-hidden="true" />,
};

/** Toasts in an aria-live region so screen readers announce them. */
export function Toaster() {
  const [items, setItems] = useState<Item[]>([]);

  useEffect(
    () =>
      onToast((toast) => {
        const id = nextId++;
        setItems((current) => [...current.slice(-3), { ...toast, id }]);
        window.setTimeout(() => setItems((current) => current.filter((t) => t.id !== id)), 5000);
      }),
    [],
  );

  return (
    <div className={styles.region} aria-live="polite" aria-atomic="false" role="status">
      {items.map((t) => (
        <div key={t.id} className={`${styles.toast} ${styles[t.kind]}`}>
          <span className={styles.icon}>{ICON[t.kind]}</span>
          <span className={styles.text}>{t.message}</span>
          <button type="button" className={styles.close} aria-label="Dismiss notification"
            onClick={() => setItems((current) => current.filter((x) => x.id !== t.id))}>
            <X aria-hidden="true" />
          </button>
        </div>
      ))}
    </div>
  );
}
