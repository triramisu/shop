import { Link, useLocation } from 'react-router-dom';
import type { OrderSnapshotResponse } from '@/lib/types';
import { formatMoney, formatDateTime } from '@/lib/format';

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đã gửi',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};

export function OrderConfirmationPage() {
  const location = useLocation() as { state?: { order?: OrderSnapshotResponse } };
  const order = location.state?.order;

  if (!order) {
    return (
      <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center' }}>
        <h1>Không có đơn hàng để hiển thị</h1>
        <Link to="/" className="btn btn-primary">
          Về cửa hàng
        </Link>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }} className="stack">
      <div className="card" style={{ padding: 'var(--space-6)' }}>
        <div style={{ fontSize: '2.5rem' }}>✅</div>
        <h1>Đặt hàng thành công</h1>
        <p className="muted" style={{ marginTop: 0 }}>
          Mã đơn <strong>{order.id}</strong> · {formatDateTime(order.createdAt)}
        </p>
        <span className="badge badge-brand">{STATUS_LABEL[order.status] ?? order.status}</span>

        <div style={{ marginTop: 'var(--space-5)' }}>
          {order.items.map((it) => (
            <div key={it.id} className="spread" style={{ padding: 'var(--space-2) 0' }}>
              <span>
                {it.productName} <span className="muted">× {it.quantity}</span>
              </span>
              <span>{formatMoney(it.total, order.currency)}</span>
            </div>
          ))}
        </div>

        <div
          className="spread"
          style={{ borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-3)', marginTop: 'var(--space-3)' }}
        >
          <strong>Tổng cộng</strong>
          <strong className="price">{formatMoney(order.grandTotal, order.currency)}</strong>
        </div>
      </div>
      <Link to="/" className="btn btn-secondary">
        Tiếp tục mua sắm
      </Link>
    </div>
  );
}
