import { useId, useState, type DragEvent } from 'react';
import styles from './FileDrop.module.css';

interface Props {
  label: string;
  buttonLabel?: string;
  hint?: string;
  accept: string;
  maxBytes: number;
  busy?: boolean;
  onFile: (file: File) => void;
  onReject: (message: string) => void;
}

/**
 * Drag-and-drop zone plus a native file input (visually hidden, still focusable). The visible label acts as the
 * button, so keyboard and screen-reader users get the platform file picker. The server re-validates every file.
 */
export function FileDrop({ label, buttonLabel = 'Choose a PDF', hint, accept, maxBytes, busy, onFile, onReject }: Props) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);

  const take = (file: File | undefined) => {
    if (!file) return;
    if (file.size > maxBytes) {
      onReject(`That file is too large (max ${(maxBytes / (1024 * 1024)).toFixed(0)} MB).`);
      return;
    }
    if (accept === 'application/pdf' && file.type && file.type !== 'application/pdf') {
      onReject('Only PDF files are accepted.');
      return;
    }
    onFile(file);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    if (!busy) take(e.dataTransfer.files?.[0]);
  };

  return (
    <div
      className={`${styles.zone} ${dragging ? styles.dragging : ''} ${busy ? styles.busy : ''}`}
      data-testid="file-drop"
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <p className={styles.text}>{busy ? 'Uploading…' : label}</p>
      {hint && <p id={`${inputId}-hint`} className={styles.hint}>{hint}</p>}
      <input
        id={inputId}
        type="file"
        accept={accept}
        className={styles.input}
        disabled={busy}
        aria-describedby={hint ? `${inputId}-hint` : undefined}
        onChange={(e) => {
          take(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
      <label htmlFor={inputId} className={`btn btn-secondary ${styles.button}`} aria-disabled={busy}>
        {buttonLabel}
      </label>
    </div>
  );
}
