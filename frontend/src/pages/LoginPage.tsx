import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthContext';
import { authApi } from '@/api/auth';
import { ApiError } from '@/lib/apiClient';
import { ErrorCode, type CaptchaResponse } from '@/lib/types';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const from = location.state?.from ?? '/';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [captcha, setCaptcha] = useState<CaptchaResponse | null>(null);
  const [captchaAnswer, setCaptchaAnswer] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function loadCaptcha() {
    try {
      setCaptcha(await authApi.captcha(username));
      setCaptchaAnswer('');
    } catch {
      setError('Không tải được CAPTCHA, vui lòng thử lại.');
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login({
        username,
        password,
        captchaId: captcha?.captchaId,
        captchaAnswer: captcha ? captchaAnswer : undefined,
      });
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === ErrorCode.CAPTCHA_REQUIRED) {
          setError('Cần xác thực CAPTCHA để tiếp tục.');
          await loadCaptcha();
        } else if (err.code === ErrorCode.INVALID_CAPTCHA) {
          setError('CAPTCHA không đúng hoặc đã hết hạn, thử lại.');
          await loadCaptcha();
        } else if (err.code === ErrorCode.RATE_LIMIT_EXCEEDED) {
          setError('Bạn thao tác quá nhanh, vui lòng chờ rồi thử lại.');
        } else {
          setError(err.message || 'Đăng nhập thất bại.');
        }
      } else {
        setError('Đăng nhập thất bại.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 420, margin: '0 auto' }}>
      <div className="card" style={{ padding: 'var(--space-6)' }}>
        <h1>Đăng nhập</h1>
        <p className="muted" style={{ marginTop: 0 }}>
          Dùng tài khoản Shop của bạn.
        </p>

        <form onSubmit={submit}>
          {error && (
            <div className="alert alert-danger" style={{ marginBottom: 'var(--space-4)' }}>
              {error}
            </div>
          )}

          <div className="field">
            <label htmlFor="username">Tên đăng nhập</label>
            <input
              id="username"
              className="input"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Mật khẩu</label>
            <input
              id="password"
              type="password"
              className="input"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          {captcha && (
            <div className="field">
              <label htmlFor="captcha">Nhập mã trong ảnh</label>
              <div className="row" style={{ marginBottom: 'var(--space-2)' }}>
                <img
                  src={captcha.imageData}
                  alt="CAPTCHA"
                  style={{ borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)' }}
                />
                <button type="button" className="btn btn-ghost btn-sm" onClick={loadCaptcha}>
                  ↻ Mã khác
                </button>
              </div>
              <input
                id="captcha"
                className="input"
                value={captchaAnswer}
                onChange={(e) => setCaptchaAnswer(e.target.value)}
                autoComplete="off"
                required
              />
            </div>
          )}

          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Đang xử lý…' : 'Đăng nhập'}
          </button>
        </form>

        <p className="muted" style={{ marginTop: 'var(--space-4)', textAlign: 'center' }}>
          Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
        </p>
      </div>
    </div>
  );
}
