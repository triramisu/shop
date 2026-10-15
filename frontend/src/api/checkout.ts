import { api } from '@/lib/apiClient';
import type { CheckoutQuoteResponse, OrderSnapshotResponse } from '@/lib/types';

export const checkoutApi = {
  // Báo giá do server tính hoàn toàn; client chỉ gửi version giỏ hàng nó thấy.
  quote: (expectedCartVersion: number) =>
    api.post<CheckoutQuoteResponse>('/checkout/quote', { expectedCartVersion }),

  // Tạo đơn với Idempotency-Key để gửi lại an toàn.
  createOrder: (expectedCartVersion: number, idempotencyKey: string) =>
    api.post<OrderSnapshotResponse>(
      '/checkout/orders',
      { expectedCartVersion },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
};
