import { useEffect, useRef, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Markdown } from '../ui/Markdown';

interface Props {
  open: boolean;
  renderedText: string;
  band: string | null;
  fullName: string;
  busy?: boolean;
  error?: string | null;
  onCancel: () => void;
  onAccept: (signedName: string) => void;
}

/**
 * Placement-policy consent (spec 20.3): the student must scroll the text to the end, tick "I agree" and type their
 * full name exactly as registered. The server re-checks the signature.
 */
export function ConsentModal({ open, renderedText, band, fullName, busy, error, onCancel, onAccept }: Props) {
  const [readToEnd, setReadToEnd] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [name, setName] = useState('');
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    setReadToEnd(false);
    setAgreed(false);
    setName('');
  }, [open, renderedText]);

  // Short texts that need no scrolling count as read.
  useEffect(() => {
    if (!open) return;
    const el = box.current;
    if (el && el.scrollHeight <= el.clientHeight + 4) setReadToEnd(true);
  }, [open, renderedText]);

  const onScroll = () => {
    const el = box.current;
    if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 8) setReadToEnd(true);
  };

  const nameMatches = name.trim().replace(/\s+/g, ' ').toLowerCase() === fullName.trim().replace(/\s+/g, ' ').toLowerCase();
  const canSubmit = readToEnd && agreed && nameMatches && !busy;

  return (
    <Modal open={open} title="Consent required by the placement policy" onClose={onCancel} size="lg"
      description={band ? `This job falls in the policy band ${band}.` : undefined}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
          <button type="button" className="btn btn-primary" disabled={!canSubmit} onClick={() => onAccept(name.trim())}>
            {busy ? 'Submitting…' : 'Agree and apply'}
          </button>
        </>
      }>
      <div ref={box} className="consent-scroll" tabIndex={0} onScroll={onScroll} aria-label="Consent text"
        data-testid="consent-text">
        <Markdown source={renderedText} />
      </div>
      {!readToEnd && <p className="small muted" role="status">Scroll to the end of the text to continue.</p>}
      {error && <div className="form-error" role="alert">{error}</div>}
      <label className="check">
        <input type="checkbox" checked={agreed} disabled={!readToEnd} onChange={(e) => setAgreed(e.target.checked)} />
        I have read and agree to the terms above.
      </label>
      <div className="field">
        <label htmlFor="consent-name">Type your full name to sign</label>
        <input id="consent-name" value={name} disabled={!readToEnd} autoComplete="name"
          aria-invalid={name.length > 0 && !nameMatches} aria-describedby="consent-name-hint"
          onChange={(e) => setName(e.target.value)} />
        <span id="consent-name-hint" className="field-hint">Must match your registered name: {fullName}</span>
      </div>
    </Modal>
  );
}
