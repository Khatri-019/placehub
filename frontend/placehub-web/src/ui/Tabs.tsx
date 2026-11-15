import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import styles from './Tabs.module.css';

export interface TabItem {
  id: string;
  label: ReactNode;
}

/** WAI-ARIA tabs with roving tabindex and arrow/Home/End keys. The caller renders the active panel. */
export function Tabs({ tabs, active, onChange, label, children }: {
  tabs: TabItem[];
  active: string;
  onChange: (id: string) => void;
  label: string;
  children: ReactNode;
}) {
  const base = useId();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const index = Math.max(0, tabs.findIndex((t) => t.id === active));

  const onKey = (e: KeyboardEvent) => {
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next >= 0) {
      e.preventDefault();
      onChange(tabs[next].id);
      refs.current[next]?.focus();
    }
  };

  return (
    <div>
      <div role="tablist" aria-label={label} className={styles.list} onKeyDown={onKey}>
        {tabs.map((t, i) => (
          <button key={t.id} ref={(el) => { refs.current[i] = el; }} type="button" role="tab"
            id={`${base}-tab-${t.id}`} aria-selected={t.id === active} aria-controls={`${base}-panel`}
            tabIndex={t.id === active ? 0 : -1} className={`${styles.tab} ${t.id === active ? styles.active : ''}`}
            onClick={() => onChange(t.id)}>
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-tab-${active}`} className={styles.panel}>
        {children}
      </div>
    </div>
  );
}
