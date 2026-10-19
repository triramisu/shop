package com.shop.payment.internal.refund.service;

import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.provider.RefundProviderRequest;

record PreparedRefund(RefundResponse snapshot, String providerCode, RefundProviderRequest request) {}
