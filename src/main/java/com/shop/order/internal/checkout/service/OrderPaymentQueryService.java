package com.shop.order.internal.checkout.service;

import com.shop.order.event.OrderStatus;
import com.shop.order.internal.checkout.dto.response.OrderPaymentResponse;
import com.shop.payment.processing.PaymentAttemptQueryOperations;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaymentQueryService {
    private final OrderSnapshotQueryService orders;
    private final PaymentAttemptQueryOperations payments;

    public OrderPaymentResponse getOwnedPayment(String subject, UUID orderId) {
        var order = orders.getOwnedOrder(subject, orderId);
        var attempt = payments.findLatestForOrder(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
        return new OrderPaymentResponse(
                attempt.id(),
                attempt.providerCode(),
                attempt.status(),
                order.getStatus() == OrderStatus.PENDING ? attempt.actionUrl() : null);
    }
}
