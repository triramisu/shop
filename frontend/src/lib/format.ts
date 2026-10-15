// Tiện ích định dạng hiển thị.

export function formatMoney(amount: string | number, currency = 'VND'): string {
  const value = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(value)) return String(amount);
  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency,
      maximumFractionDigits: currency === 'VND' ? 0 : 2,
    }).format(value);
  } catch {
    return `${value} ${currency}`;
  }
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('vi-VN');
}

/** Khóa idempotency 8–128 ký tự cho mỗi lần tạo đơn (yêu cầu của checkout API). */
export function newIdempotencyKey(): string {
  const g = globalThis.crypto;
  if (g && 'randomUUID' in g) return `co-${g.randomUUID()}`;
  return `co-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
