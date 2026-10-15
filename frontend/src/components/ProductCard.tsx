import { Link } from 'react-router-dom';
import type { StorefrontProductResponse } from '@/lib/types';
import { formatMoney } from '@/lib/format';

function lowestPrice(p: StorefrontProductResponse): { price: string; currency: string } | null {
  if (!p.variants?.length) return null;
  return p.variants.reduce((min, v) =>
    Number(v.price) < Number(min.price) ? v : min,
  );
}

export function ProductCard({ product }: { product: StorefrontProductResponse }) {
  const low = lowestPrice(product);
  return (
    <Link
      to={`/products/${product.id}`}
      className="card"
      style={{
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        color: 'var(--color-text)',
        transition: 'box-shadow .15s ease, transform .15s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = 'var(--shadow-md)';
        e.currentTarget.style.transform = 'translateY(-2px)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = 'var(--shadow-sm)';
        e.currentTarget.style.transform = 'none';
      }}
    >
      <div
        aria-hidden
        style={{
          aspectRatio: '4 / 3',
          background:
            'linear-gradient(135deg, var(--color-brand-soft), var(--color-surface-2))',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '2rem',
          color: 'var(--color-brand)',
        }}
      >
        {product.name.charAt(0).toUpperCase()}
      </div>
      <div style={{ padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        {product.category && <span className="badge">{product.category.name}</span>}
        <strong style={{ fontSize: 'var(--fs-md)' }}>{product.name}</strong>
        {low ? (
          <span className="price">{formatMoney(low.price, low.currency)}</span>
        ) : (
          <span className="muted">Chưa có phiên bản bán</span>
        )}
      </div>
    </Link>
  );
}
