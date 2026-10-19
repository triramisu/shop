package com.shop.payment.internal.checkout;

public record VnpayBrowserReturnReceipt(boolean signatureVerified, boolean paymentConfirmedByBrowser) {}
