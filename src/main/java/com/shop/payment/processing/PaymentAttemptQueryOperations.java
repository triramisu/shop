package com.shop.payment.processing;

import java.util.Optional;
import java.util.UUID;

/** Internal module contract. The calling Order use case must enforce ownership before querying. */
public interface PaymentAttemptQueryOperations {
    Optional<PaymentAttemptSnapshot> findLatestForOrder(UUID orderId);
}
