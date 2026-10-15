import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { cartApi } from '@/api/cart';
import { checkoutApi } from '@/api/checkout';
import { ErrorBox, Spinner } from '@/components/Layout';
import { formatMoney, newIdempotencyKey } from '@/lib/format';
import { ApiError } from '@/lib/apiClient';

export function CheckoutPage() {
  const navigate = useNavigate();
  const [error, setError] = useState('');

  // Giữ nguyên một idempotency key cho cả lần thử lại của cùng đơn hàng.
  const idemKey = useRef(newIdempotencyKey());

  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get });
  const cartVersion = cart.data?.version;

  const quote = useQuery({
    queryKey: ['quote', cartVersion],
    queryFn: () => checkoutApi.quote(cartVersion as number),
    enabled: cartVersion !== undefined,
  });

  const placeOrder = useMutation({
    mutationFn: () => checkoutApi.createOrder(quote.data!.cartVersion, idemKey.current),
    onSuccess: (order) => {
      navigate('/order-confirmation', { replace: true, state: { order } });
    },
    onError: (e) => {
      setError(
        e instanceof ApiError
          ? `${e.message} (mã ${e.code})`
          : 'Đặt hàng thất bại, vui lòng thử lại.',
      );
    },
  });

  if (cart.isLoading || quote.isLoading) return <Spinner label="Đang tính báo giá…" />;
  if (cart.isError) return <ErrorBox message="Không tải được giỏ hàng." />;
  if (!cart.data || cart.data.items.length === 0) {
    return (
      <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center' }}>
        <p className="muted">Giỏ hàng trống.</p>
        <Link to="/" className="btn btn-primary">
          Tới cửa hàng
        </Link>
      </div>
    );
  }
  if (quote.isError || !quote.data) return <ErrorBox message="Không tạo được báo giá." />;

  const q = quote.data;

  return (
    <div style={{ display: 'grid', gap: 'var(--space-6)', gridTemplateColumns: 'minmax(0,2fr) minmax(0,1fr)' }}>
      <div className="stack">
        <h1>Thanh toán</h1>
        <div className="card">
          {q.lines.map((l, i) => (
            <div
              key={l.cartItemId}
              className="spread"
              style={{
                padding: 'var(--space-4)',
                borderTop: i === 0 ? 'none' : '1px solid var(--color-border)',
              }}
            >
              <div>
                <strong>{l.name}</strong>
                <div className="muted" style={{ fontSize: 'var(--fs-sm)' }}>
                  {l.sku} · SL {l.quantity} × {formatMoney(l.unitPrice, q.currency)}
                </div>
              </div>
              <span className="price">{formatMoney(l.total, q.currency)}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="stack">
        <div className="card" style={{ padding: 'var(--space-5)' }}>
          <h2 style={{ fontSize: 'var(--fs-lg)' }}>Tổng kết</h2>
          <Row label="Tạm tính" value={formatMoney(q.subtotal, q.currency)} />
          <Row label={`Giảm giá (${pct(q.discountRate)})`} value={`- ${formatMoney(q.discount, q.currency)}`} />
          <Row label={`Thuế (${pct(q.taxRate)})`} value={formatMoney(q.tax, q.currency)} />
          <div
            className="spread"
            style={{ borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-3)', marginTop: 'var(--space-3)' }}
          >
            <strong>Tổng cộng</strong>
            <strong className="price" style={{ fontSize: 'var(--fs-xl)', color: 'var(--color-brand)' }}>
              {formatMoney(q.grandTotal, q.currency)}
            </strong>
          </div>

          {error && (
            <div className="alert alert-danger" style={{ margin: 'var(--space-4) 0' }}>
              {error}
            </div>
          )}

          <button
            className="btn btn-primary btn-block"
            style={{ marginTop: 'var(--space-4)' }}
            disabled={placeOrder.isPending}
            onClick={() => {
              setError('');
              placeOrder.mutate();
            }}
          >
            {placeOrder.isPending ? 'Đang đặt hàng…' : 'Đặt hàng'}
          </button>
          <p className="muted" style={{ fontSize: 'var(--fs-xs)', marginTop: 'var(--space-3)' }}>
            Idempotency-Key bảo đảm bấm lại không tạo trùng đơn.
          </p>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="spread" style={{ padding: 'var(--space-1) 0' }}>
      <span className="muted">{label}</span>
      <span>{value}</span>
    </div>
  );
}

function pct(rate: string): string {
  const n = Number(rate);
  return Number.isFinite(n) ? `${(n * 100).toFixed(0)}%` : rate;
}
