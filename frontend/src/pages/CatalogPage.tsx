import { useEffect, useRef, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { catalogApi } from '@/api/catalog';
import { ProductCard } from '@/components/ProductCard';
import { ErrorBox, Spinner } from '@/components/Layout';
import type { CatalogSortDirection, ProductSortField } from '@/lib/types';

const PAGE_SIZE = 12;

export function CatalogPage() {
  const [keyword, setKeyword] = useState('');
  const [debounced, setDebounced] = useState('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [sort, setSort] = useState<`${ProductSortField}:${CatalogSortDirection}`>('CREATED_AT:DESC');
  const [page, setPage] = useState(0);

  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogApi.categories });

  const [sortBy, direction] = sort.split(':') as [ProductSortField, CatalogSortDirection];
  const products = useQuery({
    queryKey: ['products', debounced, categoryId, sortBy, direction, page],
    queryFn: () =>
      catalogApi.searchProducts({
        keyword: debounced || undefined,
        categoryId: categoryId || undefined,
        sortBy,
        direction,
        page,
        size: PAGE_SIZE,
      }),
    placeholderData: keepPreviousData,
  });

  // Debounce ô tìm kiếm bằng ref để timer sống qua các lần render.
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  const onKeyword = (v: string) => {
    setKeyword(v);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      setDebounced(v);
      setPage(0);
    }, 350);
  };

  const data = products.data;

  return (
    <div className="stack">
      <div>
        <h1>Cửa hàng</h1>
        <p className="muted">Sản phẩm đã công bố, tải trực tiếp từ API catalog.</p>
      </div>

      <div
        className="card"
        style={{ padding: 'var(--space-4)', display: 'grid', gap: 'var(--space-3)', gridTemplateColumns: '1fr' }}
      >
        <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <input
            className="input"
            style={{ flex: '2 1 240px' }}
            placeholder="Tìm theo tên sản phẩm…"
            value={keyword}
            onChange={(e) => onKeyword(e.target.value)}
          />
          <select
            className="select"
            style={{ flex: '1 1 180px' }}
            value={categoryId}
            onChange={(e) => {
              setCategoryId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả danh mục</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ flex: '1 1 160px' }}
            value={sort}
            onChange={(e) => {
              setSort(e.target.value as typeof sort);
              setPage(0);
            }}
          >
            <option value="CREATED_AT:DESC">Mới nhất</option>
            <option value="CREATED_AT:ASC">Cũ nhất</option>
            <option value="NAME:ASC">Tên A→Z</option>
            <option value="NAME:DESC">Tên Z→A</option>
          </select>
        </div>
      </div>

      {products.isLoading ? (
        <Spinner label="Đang tải sản phẩm…" />
      ) : products.isError ? (
        <ErrorBox message="Không tải được danh sách sản phẩm." />
      ) : !data || data.content.length === 0 ? (
        <div className="card" style={{ padding: 'var(--space-6)', textAlign: 'center' }}>
          <p className="muted">Không có sản phẩm nào khớp bộ lọc.</p>
        </div>
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gap: 'var(--space-4)',
              gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            }}
          >
            {data.content.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>

          <div className="spread">
            <span className="muted">
              Trang {data.page + 1}/{data.totalPages} · {data.totalElements} sản phẩm
            </span>
            <div className="row">
              <button
                className="btn btn-secondary btn-sm"
                disabled={data.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                ← Trước
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Sau →
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
