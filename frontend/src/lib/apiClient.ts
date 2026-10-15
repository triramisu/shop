// Client HTTP mỏng cho API Shop.
// Trách nhiệm:
//  - Ghép base URL, gắn Authorization: Bearer <accessToken>.
//  - Bóc envelope ApiResponse<T>, ném ApiError mang theo code + status.
//  - Tự refresh access token (single-flight) khi hết hạn hoặc gặp 401, rồi
//    phát lại request đúng một lần.
// Không chứa logic nghiệp vụ; các module trong src/api/ mới mô tả endpoint.

import {
  clearSession,
  getSession,
  isAccessTokenFresh,
  setSession,
} from './session';
import type { ApiResponse, AuthenticationResponse } from './types';

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  /** body đã là FormData (upload ảnh) thì không set Content-Type JSON. */
  formData?: FormData;
  headers?: Record<string, string>;
  /** Endpoint công khai: không gắn token, không thử refresh. */
  auth?: boolean;
  /** Query string. */
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${BASE_URL}${path}`;
  if (!query) return url;
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v));
  }
  const s = qs.toString();
  return s ? `${url}?${s}` : url;
}

// --- Single-flight refresh: nhiều request 401 cùng lúc chỉ refresh một lần ---
let refreshing: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  if (refreshing) return refreshing;
  const session = getSession();
  if (!session?.refreshToken) return false;

  refreshing = (async () => {
    try {
      const res = await fetch(buildUrl('/auth/refresh'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: session.refreshToken }),
      });
      const json = (await res.json()) as ApiResponse<AuthenticationResponse>;
      if (res.ok && json.result?.authenticated) {
        setSession(json.result);
        return true;
      }
      clearSession();
      return false;
    } catch {
      clearSession();
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

async function parse<T>(res: Response): Promise<T> {
  let json: ApiResponse<T> | undefined;
  try {
    json = (await res.json()) as ApiResponse<T>;
  } catch {
    json = undefined;
  }
  if (res.ok && json && (json.code === undefined || json.code === 1000)) {
    return json.result as T;
  }
  throw new ApiError(
    json?.code ?? res.status,
    json?.message ?? `Lỗi máy chủ (${res.status})`,
    res.status,
  );
}

async function send<T>(path: string, opts: RequestOptions, retry: boolean): Promise<T> {
  const useAuth = opts.auth !== false;

  // Chủ động refresh trước khi gọi nếu access token đã/sắp hết hạn.
  if (useAuth && getSession() && !isAccessTokenFresh()) {
    await refreshAccessToken();
  }

  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
  if (!opts.formData && opts.body !== undefined) headers['Content-Type'] = 'application/json';
  const session = getSession();
  if (useAuth && session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;

  let res: Response;
  try {
    res = await fetch(buildUrl(path, opts.query), {
      method: opts.method ?? 'GET',
      headers,
      body: opts.formData ?? (opts.body !== undefined ? JSON.stringify(opts.body) : undefined),
      signal: opts.signal,
    });
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, 'Không kết nối được tới máy chủ', 0);
  }

  // 401 khi đang dùng auth: thử refresh và phát lại đúng một lần.
  if (res.status === 401 && useAuth && retry && getSession()) {
    const ok = await refreshAccessToken();
    if (ok) return send<T>(path, opts, false);
  }

  return parse<T>(res);
}

export function apiRequest<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  return send<T>(path, opts, true);
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) => apiRequest<T>(path, { ...opts, method: 'GET' }),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: 'PUT', body }),
  del: <T>(path: string, opts?: RequestOptions) => apiRequest<T>(path, { ...opts, method: 'DELETE' }),
};
