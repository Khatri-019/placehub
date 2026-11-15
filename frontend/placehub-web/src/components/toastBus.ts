export type ToastKind = 'success' | 'error' | 'info';

export interface ToastMessage {
  kind: ToastKind;
  message: string;
}

const EVENT = 'placehub:toast';

/** Lets non-React code (the Axios interceptor) raise toasts. */
export function emitToast(toast: ToastMessage): void {
  window.dispatchEvent(new CustomEvent<ToastMessage>(EVENT, { detail: toast }));
}

export function onToast(listener: (toast: ToastMessage) => void): () => void {
  const handler = (e: Event) => listener((e as CustomEvent<ToastMessage>).detail);
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}
