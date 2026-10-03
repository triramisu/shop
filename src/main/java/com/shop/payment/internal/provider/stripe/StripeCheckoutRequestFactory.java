package com.shop.payment.internal.provider.stripe;

import com.shop.payment.provider.PaymentProviderRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StripeCheckoutRequestFactory {

    private static final String SESSION_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";

    private final URI successUrl;
    private final URI cancelUrl;
    private final StripeCheckoutStateSigner stateSigner;

    StripeCheckoutRequestFactory(URI successUrl, URI cancelUrl, StripeCheckoutStateSigner stateSigner) {
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.stateSigner = stateSigner;
    }

    StripeCheckoutRequest create(PaymentProviderRequest request) {
        long amount = StripeMoney.toMinorUnit(request.amount(), request.currency());
        String currency = request.currency().toLowerCase(Locale.ROOT);
        String state = stateSigner.issue(request.paymentAttemptId());
        List<Map.Entry<String, String>> fields = new ArrayList<>();
        add(fields, "mode", "payment");
        add(fields, "ui_mode", "hosted_page");
        add(fields, "success_url", successUrl + "?state=" + state + "&session_id=" + SESSION_PLACEHOLDER);
        add(fields, "cancel_url", cancelUrl + "?state=" + state);
        add(fields, "client_reference_id", request.orderId().toString());
        add(fields, "metadata[payment_attempt_id]", request.paymentAttemptId().toString());
        add(fields, "metadata[order_id]", request.orderId().toString());
        add(
                fields,
                "payment_intent_data[metadata][payment_attempt_id]",
                request.paymentAttemptId().toString());
        add(fields, "line_items[0][quantity]", "1");
        add(fields, "line_items[0][price_data][currency]", currency);
        add(fields, "line_items[0][price_data][unit_amount]", Long.toString(amount));
        add(fields, "line_items[0][price_data][product_data][name]", "Order " + request.orderId());
        return new StripeCheckoutRequest(
                request.paymentAttemptId(),
                request.orderId(),
                request.idempotencyKey(),
                amount,
                currency,
                List.copyOf(fields));
    }

    private static void add(List<Map.Entry<String, String>> fields, String name, String value) {
        fields.add(Map.entry(name, value));
    }
}
