import { useState } from 'react';
import { resumeApi } from '../api/endpoints';
import { parseError } from '../api/errors';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime } from '../utils/format';
import { formatBytes, openPdfInNewTab } from '../utils/openBlob';
import { FileDrop } from './FileDrop';
import { ErrorState, Spinner } from './States';
import { emitToast } from './toastBus';

const MAX_BYTES = 2 * 1024 * 1024;

/** Student resume management: upload/replace (drag-and-drop or picker), view, and the version list. */
export function ResumeCard() {
  const overview = useAsync(() => resumeApi.get(), []);
  const [busy, setBusy] = useState(false);

  const upload = async (file: File) => {
    setBusy(true);
    try {
      const saved = await resumeApi.upload(file);
      emitToast({ kind: 'success', message: `Uploaded ${saved.filename}.` });
      overview.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    } finally {
      setBusy(false);
    }
  };

  const view = (id?: string) =>
    openPdfInNewTab(() => (id ? resumeApi.versionBlob(id) : resumeApi.currentBlob()))
      .catch((err) => emitToast({ kind: 'error', message: parseError(err).message }));

  const remove = async (id: string) => {
    try {
      await resumeApi.remove(id);
      emitToast({ kind: 'success', message: 'Resume version deleted.' });
      overview.reload();
    } catch (err) {
      emitToast({ kind: 'error', message: parseError(err).message });
    }
  };

  return (
    <section className="card stack" aria-labelledby="resume-title">
      <h2 id="resume-title">Resume</h2>
      {overview.loading && overview.data === undefined && <Spinner label="Loading resume…" />}
      {overview.error && <ErrorState error={overview.error} onRetry={overview.reload} />}
      {overview.data === null && <p className="muted">No resume uploaded yet. Most jobs require one before you apply.</p>}
      {overview.data && (
        <div className="stack">
          <dl className="details">
            <dt>Current file</dt><dd>{overview.data.current.filename}</dd>
            <dt>Size</dt><dd className="num">{formatBytes(overview.data.current.sizeBytes)}</dd>
            <dt>Uploaded</dt><dd>{formatDateTime(overview.data.current.uploadedAt)}</dd>
          </dl>
          <div className="button-row">
            <button type="button" className="btn btn-secondary" onClick={() => view()}>View resume</button>
          </div>
        </div>
      )}
      <FileDrop
        label={overview.data ? 'Replace resume: drop a PDF here or choose a file' : 'Drop your PDF resume here or choose a file'}
        hint="PDF only, up to 2 MB. Recruiters keep the version you applied with."
        accept="application/pdf"
        maxBytes={MAX_BYTES}
        busy={busy}
        onFile={upload}
        onReject={(message) => emitToast({ kind: 'error', message })}
      />
      {overview.data && overview.data.versions.length > 1 && (
        <div>
          <h3 className="small muted">Versions</h3>
          <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {overview.data.versions.map((v) => (
              <li key={v.id} className="button-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <span>
                  {v.filename} <span className="muted small">· {formatDateTime(v.uploadedAt)}{v.current ? ' · current' : ''}
                    {v.referenced ? ' · sent with an application' : ''}</span>
                </span>
                <span className="button-row">
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => view(v.id)}
                    aria-label={`View ${v.filename} from ${formatDateTime(v.uploadedAt)}`}>View</button>
                  {!v.referenced && !v.current && (
                    <button type="button" className="btn btn-danger btn-sm" onClick={() => remove(v.id)}
                      aria-label={`Delete ${v.filename} from ${formatDateTime(v.uploadedAt)}`}>Delete</button>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
