package com.shop.payment.processing;

public interface PaymentInitiationOperations {

    PaymentAttemptSnapshot initiate(PaymentInitiationCommand command);
}
