import { isAxiosError } from 'axios';
import type { ApiErrorBody } from '../types';

export interface ParsedError {
  status?: number;
  message: string;
  reasons: string[];
  fieldErrors: Record<string, string>;
}

/** Normalises any thrown value into the backend's standard error model. */
export function parseError(err: unknown): ParsedError {
  if (isAxiosError(err)) {
    const body = err.response?.data as Partial<ApiErrorBody> | undefined;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return {
        status: err.response?.status,
        message: body.message,
        reasons: body.details?.reasons ?? [],
        fieldErrors: body.details?.fieldErrors ?? {},
      };
    }
    if (!err.response) {
      return { message: 'Cannot reach the server. Check your connection and try again.', reasons: [], fieldErrors: {} };
    }
    return { status: err.response.status, message: `Request failed (${err.response.status})`, reasons: [], fieldErrors: {} };
  }
  return { message: err instanceof Error ? err.message : 'Something went wrong', reasons: [], fieldErrors: {} };
}
