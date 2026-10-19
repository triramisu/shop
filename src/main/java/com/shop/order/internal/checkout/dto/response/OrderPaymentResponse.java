package com.shop.order.internal.checkout.dto.response;

import com.shop.payment.event.PaymentStatus;
import java.net.URI;
import java.util.UUID;

public record OrderPaymentResponse(UUID paymentAttemptId, String providerCode, PaymentStatus status, URI actionUrl) {}
