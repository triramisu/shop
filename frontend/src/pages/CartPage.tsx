import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '@/api/cart';
import { ErrorBox, Spinner } from '@/components/Layout';
import type { CartResponse } from '@/lib/types';

export function CartPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();

  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get });

  const onCart = (data: CartResponse) => qc.setQueryData(['cart'], data);

  const update = useMutation({
    mutationFn: ({ id, q }: { id: string; q: number }) => cartApi.updateItem(id, q),
    onSuccess: onCart,
  });
  const remove = useMutation({ mutationFn: (id: string) => cartApi.removeItem(id), onSuccess: onCart });
  const clear = useMutation({ mutationFn: () => cartApi.clear(), onSuccess: onCart });

  if (cart.isLoading) return <Spinner label="Đang tải giỏ hàng…" />;
  if (cart.isError || !cart.data) return <ErrorBox message="Không tải được giỏ hàng." />;

  const c = cart.data;

  if (c.items.length === 0) {
    return (
      <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center' }}>
        <h1>Giỏ hàng trống</h1>
        <p className="muted">Hãy thêm vài sản phẩm nhé.</p>
        <Link to="/" className="btn btn-primary">
          Tới cửa hàng
        </Link>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="spread">
        <h1 style={{ margin: 0 }}>Giỏ hàng</h1>
        <button className="btn btn-ghost btn-sm" onClick={() => clear.mutate()} disabled={clear.isPending}>
          Xóa tất cả
        </button>
      </div>

      <div className="card">
        {c.items.map((item, idx) => (
          <div
            key={item.id}
            className="spread"
            style={{
              padding: 'var(--space-4)',
              borderTop: idx === 0 ? 'none' : '1px solid var(--color-border)',
            }}
          >
            <div>
              <strong>{item.sku}</strong>
              <div className="muted" style={{ fontSize: 'var(--fs-sm)' }}>
                Phiên bản: {item.productVariantId.slice(0, 8)}…
              </div>
            </div>
            <div className="row">
              <input
                type="number"
                min={1}
                className="input"
                style={{ width: 80 }}
                defaultValue={item.quantity}
                onBlur={(e) => {
                  const q = Math.max(1, Number(e.target.value) || 1);
                  if (q !== item.quantity) update.mutate({ id: item.id, q });
                }}
              />
              <button className="btn btn-ghost btn-sm" onClick={() => remove.mutate(item.id)}>
                Xóa
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="spread">
        <span className="muted">
          {c.distinctItemCount} mặt hàng · {c.totalQuantity} sản phẩm
        </span>
        <button className="btn btn-primary" onClick={() => navigate('/checkout')}>
          Thanh toán →
        </button>
      </div>
      <p className="muted" style={{ fontSize: 'var(--fs-sm)' }}>
        Giá và thuế được tính ở bước thanh toán, hoàn toàn từ dữ liệu phía server.
      </p>
    </div>
  );
}
