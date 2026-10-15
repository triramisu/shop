import { Outlet } from 'react-router-dom';
import { Navbar } from './Navbar';

export function Layout() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
      <Navbar />
      <main className="container" style={{ flex: 1, padding: 'var(--space-6) var(--space-4)' }}>
        <Outlet />
      </main>
      <footer
        style={{
          borderTop: '1px solid var(--color-border)',
          background: 'var(--color-surface)',
          color: 'var(--color-text-muted)',
          fontSize: 'var(--fs-sm)',
        }}
      >
        <div className="container" style={{ padding: 'var(--space-5) var(--space-4)' }}>
          Shop — demo storefront. Giá, tồn kho và trạng thái đơn do server quyết định.
        </div>
      </footer>
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="center-pad" style={{ gap: 'var(--space-3)', flexDirection: 'column' }}>
      <div className="spinner" />
      {label && <span className="muted">{label}</span>}
    </div>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return <div className="alert alert-danger">{message}</div>;
}
