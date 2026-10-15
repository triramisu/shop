// Các kiểu dữ liệu phản chiếu DTO của backend Shop.
// Nguồn sự thật là các class Java trong src/main/java/com/shop/**.

/** Envelope chuẩn ApiResponse<T> của backend. code=1000 là thành công. */
export interface ApiResponse<T> {
  code: number;
  message?: string;
  result?: T;
}

/** Mã lỗi backend mà frontend cần phân biệt (xem ErrorCode.java). */
export const ErrorCode = {
  SUCCESS: 1000,
  UNAUTHENTICATED: 1006,
  UNAUTHORIZED: 1007,
  INVALID_TOKEN: 1014,
  REFRESH_TOKEN_REUSED: 1015,
  USER_DISABLED: 1017,
  RATE_LIMIT_EXCEEDED: 1024,
  CAPTCHA_REQUIRED: 1025,
  INVALID_CAPTCHA: 1026,
  CAPTCHA_UNAVAILABLE: 1027,
} as const;

// ---------- Identity ----------
export interface AuthenticationResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  authenticated: boolean;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
}

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  dateOfBirth?: string;
  status: string;
  emailVerified: boolean;
  roles: string[];
  createdAt: string;
}

export interface CaptchaResponse {
  captchaId: string;
  imageData: string; // data URI / base64
  expiresAt: string;
}

export interface AuthenticationRequest {
  username: string;
  password: string;
  captchaId?: string;
  captchaAnswer?: string;
}

export interface RegisterUserRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  dateOfBirth?: string;
}

// ---------- Catalog (storefront) ----------
export type ProductSortField = 'CREATED_AT' | 'UPDATED_AT' | 'NAME';
export type CatalogSortDirection = 'ASC' | 'DESC';

export interface StorefrontCategoryResponse {
  id: string;
  name: string;
  slug: string;
}

export interface StorefrontProductVariantResponse {
  id: string;
  sku: string;
  name: string;
  price: string; // BigDecimal serialize thành chuỗi/số; giữ string cho an toàn
  currency: string;
}

export interface StorefrontProductResponse {
  id: string;
  category?: StorefrontCategoryResponse;
  name: string;
  slug: string;
  description?: string;
  variants: StorefrontProductVariantResponse[];
}

export interface StorefrontProductImageResponse {
  id: string;
  url: string;
  contentType: string;
  primary: boolean;
  displayOrder: number;
}

export interface StorefrontProductPageResponse {
  content: StorefrontProductResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface StorefrontProductSearchParams {
  keyword?: string;
  categoryId?: string;
  page?: number;
  size?: number;
  sortBy?: ProductSortField;
  direction?: CatalogSortDirection;
}

// ---------- Cart ----------
export interface CartItemResponse {
  id: string;
  productVariantId: string;
  sku: string;
  quantity: number;
  updatedAt: string;
}

export interface CartResponse {
  id: string;
  items: CartItemResponse[];
  distinctItemCount: number;
  totalQuantity: number;
  version: number;
  updatedAt: string;
}

// ---------- Checkout ----------
export interface CheckoutQuoteLineResponse {
  cartItemId: string;
  productVariantId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: string;
  subtotal: string;
  discount: string;
  tax: string;
  total: string;
}

export interface CheckoutQuoteResponse {
  cartId: string;
  cartVersion: number;
  pricedAt: string;
  currency: string;
  discountRate: string;
  taxRate: string;
  lines: CheckoutQuoteLineResponse[];
  subtotal: string;
  discount: string;
  tax: string;
  grandTotal: string;
}

export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface OrderItemSnapshotResponse {
  id: string;
  productVariantId: string;
  lineNumber: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: string;
  subtotal: string;
  discount: string;
  tax: string;
  total: string;
  currency: string;
}

export interface OrderSnapshotResponse {
  id: string;
  status: OrderStatus;
  version: number;
  createdAt: string;
  currency: string;
  subtotal: string;
  discount: string;
  tax: string;
  grandTotal: string;
  items: OrderItemSnapshotResponse[];
}
