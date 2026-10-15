import { useAuth } from '@/auth/AuthContext';
import { Spinner } from '@/components/Layout';
import { formatDateTime } from '@/lib/format';

export function ProfilePage() {
  const { user, loadingUser } = useAuth();

  if (loadingUser && !user) return <Spinner label="Đang tải hồ sơ…" />;
  if (!user) return <div className="alert alert-danger">Không tải được hồ sơ.</div>;

  const rows: Array<[string, string]> = [
    ['Tên đăng nhập', user.username],
    ['Email', user.email],
    ['Họ tên', [user.lastName, user.firstName].filter(Boolean).join(' ') || '—'],
    ['Ngày sinh', user.dateOfBirth ?? '—'],
    ['Trạng thái', user.status],
    ['Email xác thực', user.emailVerified ? 'Đã xác thực' : 'Chưa xác thực'],
    ['Vai trò', user.roles?.join(', ') || '—'],
    ['Tạo lúc', formatDateTime(user.createdAt)],
  ];

  return (
    <div style={{ maxWidth: 560, margin: '0 auto' }}>
      <h1>Hồ sơ của tôi</h1>
      <div className="card" style={{ padding: 'var(--space-2) var(--space-5)' }}>
        {rows.map(([k, v], i) => (
          <div
            key={k}
            className="spread"
            style={{ padding: 'var(--space-4) 0', borderTop: i === 0 ? 'none' : '1px solid var(--color-border)' }}
          >
            <span className="muted">{k}</span>
            <span style={{ textAlign: 'right' }}>{v}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
