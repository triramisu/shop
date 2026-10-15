import { api } from '@/lib/apiClient';
import type {
  AuthenticationRequest,
  AuthenticationResponse,
  CaptchaResponse,
  RegisterUserRequest,
  UserResponse,
} from '@/lib/types';

export const authApi = {
  register: (body: RegisterUserRequest) =>
    api.post<UserResponse>('/auth/register', body, { auth: false }),

  login: (body: AuthenticationRequest) =>
    api.post<AuthenticationResponse>('/auth/token', body, { auth: false }),

  // Lấy thử thách CAPTCHA thích ứng (chỉ cần sau khi login thất bại nhiều lần).
  captcha: (username: string) =>
    api.post<CaptchaResponse>('/auth/captcha', { username }, { auth: false }),

  refresh: (token: string) =>
    api.post<AuthenticationResponse>('/auth/refresh', { token }, { auth: false }),

  logout: (token: string) => api.post<void>('/auth/logout', { token }, { auth: false }),

  myInfo: () => api.get<UserResponse>('/auth/my-info'),
};
