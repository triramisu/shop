import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center', maxWidth: 480, margin: '0 auto' }}>
      <div style={{ fontSize: '3rem', color: 'var(--color-brand)' }}>404</div>
      <h1>Không tìm thấy trang</h1>
      <Link to="/" className="btn btn-primary">
        Về cửa hàng
      </Link>
    </div>
  );
}
