package com.shop.payment.internal.checkout;

import com.shop.payment.event.PaymentStatus;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HostedCheckoutReturnResponse {
    UUID paymentAttemptId;
    PaymentStatus status;
    CheckoutRedirectOutcome outcome;
}
