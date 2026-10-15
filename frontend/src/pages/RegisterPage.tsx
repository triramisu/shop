import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthContext';
import { ApiError } from '@/lib/apiClient';

export function RegisterPage() {
  const { register, login } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    dateOfBirth: '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await register({
        username: form.username,
        email: form.email,
        password: form.password,
        firstName: form.firstName || undefined,
        lastName: form.lastName || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
      });
      // Đăng ký xong thử đăng nhập luôn cho tiện; nếu cần CAPTCHA sẽ chuyển sang trang login.
      try {
        await login({ username: form.username, password: form.password });
        navigate('/', { replace: true });
      } catch {
        navigate('/login', { replace: true });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Đăng ký thất bại.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 480, margin: '0 auto' }}>
      <div className="card" style={{ padding: 'var(--space-6)' }}>
        <h1>Tạo tài khoản</h1>
        <form onSubmit={submit}>
          {error && (
            <div className="alert alert-danger" style={{ marginBottom: 'var(--space-4)' }}>
              {error}
            </div>
          )}

          <div className="field">
            <label htmlFor="username">Tên đăng nhập</label>
            <input id="username" className="input" value={form.username} onChange={set('username')} required />
          </div>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" className="input" value={form.email} onChange={set('email')} required />
          </div>
          <div className="field">
            <label htmlFor="password">Mật khẩu</label>
            <input
              id="password"
              type="password"
              className="input"
              value={form.password}
              onChange={set('password')}
              autoComplete="new-password"
              required
            />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <div className="field">
              <label htmlFor="firstName">Tên</label>
              <input id="firstName" className="input" value={form.firstName} onChange={set('firstName')} />
            </div>
            <div className="field">
              <label htmlFor="lastName">Họ</label>
              <input id="lastName" className="input" value={form.lastName} onChange={set('lastName')} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="dob">Ngày sinh</label>
            <input id="dob" type="date" className="input" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
          </div>

          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Đang tạo…' : 'Đăng ký'}
          </button>
        </form>
        <p className="muted" style={{ marginTop: 'var(--space-4)', textAlign: 'center' }}>
          Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
        </p>
      </div>
    </div>
  );
}
