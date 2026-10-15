import { api } from '@/lib/apiClient';
import type {
  StorefrontCategoryResponse,
  StorefrontProductImageResponse,
  StorefrontProductPageResponse,
  StorefrontProductResponse,
  StorefrontProductSearchParams,
} from '@/lib/types';

// Toàn bộ endpoint này là công khai (GET /api/catalog/**) nên auth:false.
export const catalogApi = {
  categories: () =>
    api.get<StorefrontCategoryResponse[]>('/catalog/categories', { auth: false }),

  searchProducts: (params: StorefrontProductSearchParams) =>
    api.get<StorefrontProductPageResponse>('/catalog/products', {
      auth: false,
      query: {
        keyword: params.keyword,
        categoryId: params.categoryId,
        page: params.page,
        size: params.size,
        sortBy: params.sortBy,
        direction: params.direction,
      },
    }),

  product: (productId: string) =>
    api.get<StorefrontProductResponse>(`/catalog/products/${productId}`, { auth: false }),

  productImages: (productId: string) =>
    api.get<StorefrontProductImageResponse[]>(`/catalog/products/${productId}/images`, {
      auth: false,
    }),
};
