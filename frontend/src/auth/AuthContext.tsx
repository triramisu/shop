import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi } from '@/api/auth';
import {
  clearSession,
  getSession,
  setSession,
  subscribe,
  type Session,
} from '@/lib/session';
import type { AuthenticationRequest, RegisterUserRequest, UserResponse } from '@/lib/types';

interface AuthContextValue {
  session: Session | null;
  user: UserResponse | null;
  loadingUser: boolean;
  isAuthenticated: boolean;
  login: (req: AuthenticationRequest) => Promise<void>;
  register: (req: RegisterUserRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<Session | null>(() => getSession());
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loadingUser, setLoadingUser] = useState<boolean>(!!getSession());

  useEffect(() => subscribe(setSessionState), []);

  const refreshUser = useCallback(async () => {
    if (!getSession()) {
      setUser(null);
      return;
    }
    setLoadingUser(true);
    try {
      setUser(await authApi.myInfo());
    } catch {
      setUser(null);
    } finally {
      setLoadingUser(false);
    }
  }, []);

  // Nạp hồ sơ mỗi khi có/đổi phiên.
  useEffect(() => {
    if (session) void refreshUser();
    else setUser(null);
  }, [session?.accessToken, refreshUser]); // eslint-disable-line react-hooks/exhaustive-deps

  const login = useCallback(
    async (req: AuthenticationRequest) => {
      const auth = await authApi.login(req);
      setSession(auth);
      await refreshUser();
    },
    [refreshUser],
  );

  const register = useCallback((req: RegisterUserRequest) => authApi.register(req), []);

  const logout = useCallback(async () => {
    const s = getSession();
    if (s?.refreshToken) {
      try {
        await authApi.logout(s.refreshToken); // thu hồi cả refresh-token family
      } catch {
        /* vẫn xóa phiên phía client dù logout server lỗi */
      }
    }
    clearSession();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      user,
      loadingUser,
      isAuthenticated: !!session,
      login,
      register,
      logout,
      refreshUser,
    }),
    [session, user, loadingUser, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải nằm trong <AuthProvider>');
  return ctx;
}
