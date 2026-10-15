// Lưu trữ phiên đăng nhập phía client.
// Access token sống ngắn, refresh token dùng để xoay vòng (rotation) theo
// thiết kế Identity của backend. Ta lưu ở localStorage để giữ phiên qua reload;
// đây là token người dùng tự giữ, không phải secret hệ thống.

import type { AuthenticationResponse } from './types';

const KEY = 'shop.session.v1';

export interface Session {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
}

type Listener = (s: Session | null) => void;
const listeners = new Set<Listener>();

let current: Session | null = load();

function load(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function persist(s: Session | null) {
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  } catch {
    /* localStorage có thể bị chặn; vẫn giữ state trong bộ nhớ */
  }
}

export function getSession(): Session | null {
  return current;
}

export function setSession(auth: AuthenticationResponse | null) {
  current = auth
    ? {
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
        accessTokenExpiresAt: auth.accessTokenExpiresAt,
        refreshTokenExpiresAt: auth.refreshTokenExpiresAt,
      }
    : null;
  persist(current);
  listeners.forEach((l) => l(current));
}

export function clearSession() {
  setSession(null);
}

export function subscribe(l: Listener): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

/** Access token còn hạn ít nhất `skewMs` nữa không. */
export function isAccessTokenFresh(skewMs = 15_000): boolean {
  if (!current) return false;
  const exp = Date.parse(current.accessTokenExpiresAt);
  return Number.isFinite(exp) && exp - Date.now() > skewMs;
}
