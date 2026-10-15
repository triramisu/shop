import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { catalogApi } from '@/api/catalog';
import { cartApi } from '@/api/cart';
import { useAuth } from '@/auth/AuthContext';
import { ErrorBox, Spinner } from '@/components/Layout';
import { formatMoney } from '@/lib/format';
import { ApiError } from '@/lib/apiClient';

export function ProductPage() {
  const { productId = '' } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { isAuthenticated } = useAuth();

  const [variantId, setVariantId] = useState<string>('');
  const [qty, setQty] = useState(1);
  const [notice, setNotice] = useState<{ kind: 'success' | 'danger'; text: string } | null>(null);

  const product = useQuery({
    queryKey: ['product', productId],
    queryFn: () => catalogApi.product(productId),
    enabled: !!productId,
  });
  const images = useQuery({
    queryKey: ['product-images', productId],
    queryFn: () => catalogApi.productImages(productId),
    enabled: !!productId,
  });

  const selected = useMemo(() => {
    const vs = product.data?.variants ?? [];
    return vs.find((v) => v.id === variantId) ?? vs[0];
  }, [product.data, variantId]);

  const addToCart = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error('Chưa chọn phiên bản');
      return cartApi.addItem(selected.sku, qty);
    },
    onSuccess: (cart) => {
      qc.setQueryData(['cart'], cart);
      setNotice({ kind: 'success', text: 'Đã thêm vào giỏ hàng.' });
    },
    onError: (e) => {
      const msg = e instanceof ApiError ? e.message : 'Không thêm được vào giỏ.';
      setNotice({ kind: 'danger', text: msg });
    },
  });

  if (product.isLoading) return <Spinner label="Đang tải sản phẩm…" />;
  if (product.isError || !product.data) return <ErrorBox message="Không tìm thấy sản phẩm." />;

  const p = product.data;
  const primaryImg = images.data?.find((i) => i.primary) ?? images.data?.[0];

  return (
    <div style={{ display: 'grid', gap: 'var(--space-6)', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)' }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div
          style={{
            aspectRatio: '1 / 1',
            background: 'linear-gradient(135deg, var(--color-brand-soft), var(--color-surface-2))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {primaryImg ? (
            <img
              src={primaryImg.url}
              alt={p.name}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <span style={{ fontSize: '4rem', color: 'var(--color-brand)' }}>
              {p.name.charAt(0).toUpperCase()}
            </span>
          )}
        </div>
      </div>

      <div className="stack">
        <button className="btn btn-ghost btn-sm" style={{ alignSelf: 'start' }} onClick={() => navigate(-1)}>
          ← Quay lại
        </button>
        {p.category && <span className="badge badge-brand">{p.category.name}</span>}
        <h1 style={{ marginBottom: 0 }}>{p.name}</h1>
        {p.description && <p className="muted">{p.description}</p>}

        {selected && (
          <div className="price" style={{ fontSize: 'var(--fs-2xl)' }}>
            {formatMoney(selected.price, selected.currency)}
          </div>
        )}

        <div className="field">
          <label htmlFor="variant">Phiên bản</label>
          <select
            id="variant"
            className="select"
            value={selected?.id ?? ''}
            onChange={(e) => setVariantId(e.target.value)}
          >
            {p.variants.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name} — {v.sku} — {formatMoney(v.price, v.currency)}
              </option>
            ))}
          </select>
        </div>

        <div className="field" style={{ maxWidth: 140 }}>
          <label htmlFor="qty">Số lượng</label>
          <input
            id="qty"
            type="number"
            min={1}
            className="input"
            value={qty}
            onChange={(e) => setQty(Math.max(1, Number(e.target.value) || 1))}
          />
        </div>

        {notice && <div className={`alert alert-${notice.kind}`}>{notice.text}</div>}

        {isAuthenticated ? (
          <button
            className="btn btn-primary"
            disabled={!selected || addToCart.isPending}
            onClick={() => addToCart.mutate()}
          >
            {addToCart.isPending ? 'Đang thêm…' : 'Thêm vào giỏ'}
          </button>
        ) : (
          <button
            className="btn btn-primary"
            onClick={() => navigate('/login', { state: { from: `/products/${productId}` } })}
          >
            Đăng nhập để mua
          </button>
        )}
      </div>
    </div>
  );
}
