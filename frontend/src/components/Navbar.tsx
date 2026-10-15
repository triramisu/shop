import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/auth/AuthContext';
import { cartApi } from '@/api/cart';
import { useTheme } from '@/lib/useTheme';

export function Navbar() {
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const { theme, cycle } = useTheme();

  const cart = useQuery({
    queryKey: ['cart'],
    queryFn: cartApi.get,
    enabled: isAuthenticated,
  });

  const count = cart.data?.totalQuantity ?? 0;
  const themeIcon = theme === 'dark' ? '🌙' : theme === 'light' ? '☀️' : '🖥️';

  return (
    <header
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        background: 'var(--color-surface)',
        borderBottom: '1px solid var(--color-border)',
      }}
    >
      <div
        className="container"
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-5)', height: 64 }}
      >
        <Link to="/" style={{ fontWeight: 800, fontSize: 'var(--fs-lg)', color: 'var(--color-text)' }}>
          <span style={{ color: 'var(--color-brand)' }}>◆</span> Shop
        </Link>

        <nav style={{ display: 'flex', gap: 'var(--space-4)', marginRight: 'auto' }}>
          <NavLink to="/" className="nav-link" end>
            Cửa hàng
          </NavLink>
        </nav>

        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={cycle}
          title={`Giao diện: ${theme}`}
          aria-label="Đổi giao diện sáng/tối"
        >
          {themeIcon}
        </button>

        <Link to="/cart" className="btn btn-ghost btn-sm" style={{ position: 'relative' }}>
          🛒 Giỏ
          {count > 0 && (
            <span
              className="badge badge-brand"
              style={{ position: 'absolute', top: -6, right: -8, minWidth: 18, justifyContent: 'center' }}
            >
              {count}
            </span>
          )}
        </Link>

        {isAuthenticated ? (
          <div className="row">
            <Link to="/profile" className="btn btn-ghost btn-sm">
              {user?.username ?? 'Tài khoản'}
            </Link>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={async () => {
                await logout();
                navigate('/');
              }}
            >
              Đăng xuất
            </button>
          </div>
        ) : (
          <div className="row">
            <Link to="/login" className="btn btn-ghost btn-sm">
              Đăng nhập
            </Link>
            <Link to="/register" className="btn btn-primary btn-sm">
              Đăng ký
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}
