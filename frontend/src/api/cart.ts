import { api } from '@/lib/apiClient';
import type { CartResponse } from '@/lib/types';

export const cartApi = {
  get: () => api.get<CartResponse>('/cart'),

  addItem: (sku: string, quantity: number) =>
    api.post<CartResponse>('/cart/items', { sku, quantity }),

  updateItem: (itemId: string, quantity: number) =>
    api.put<CartResponse>(`/cart/items/${itemId}`, { quantity }),

  removeItem: (itemId: string) => api.del<CartResponse>(`/cart/items/${itemId}`),

  clear: () => api.del<CartResponse>('/cart/items'),
};
