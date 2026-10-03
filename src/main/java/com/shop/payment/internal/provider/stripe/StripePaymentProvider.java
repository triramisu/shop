package com.shop.payment.internal.provider.stripe;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderOperations;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

final class StripePaymentProvider implements PaymentProviderOperations {

    static final String PROVIDER_CODE = "STRIPE";
    private static final int MAXIMUM_CHECKOUT_URL_LENGTH = 2048;

    private final StripeCheckoutGateway checkoutGateway;
    private final StripeCheckoutRequestFactory requestFactory;
    private final Set<String> allowedCheckoutHosts;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    StripePaymentProvider(
            StripeCheckoutGateway checkoutGateway,
            StripeCheckoutRequestFactory requestFactory,
            Set<String> allowedCheckoutHosts,
            CircuitBreaker circuitBreaker,
            Retry retry) {
        this.checkoutGateway = checkoutGateway;
        this.requestFactory = requestFactory;
        this.allowedCheckoutHosts = Set.copyOf(allowedCheckoutHosts);
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentProviderResult createPayment(PaymentProviderRequest request) {
        StripeCheckoutRequest stripeRequest = requestFactory.create(request);
        Supplier<PaymentProviderResult> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            StripeCheckoutSession session = checkoutGateway.createSession(stripeRequest);
            validateResponse(stripeRequest, session);
            return mapResult(session);
        });
        try {
            return Retry.decorateSupplier(retry, guarded).get();
        } catch (CallNotPermittedException exception) {
            throw new PaymentProviderException(PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "STRIPE_CIRCUIT_OPEN");
        }
    }

    private void validateResponse(StripeCheckoutRequest request, StripeCheckoutSession session) {
        if (session.id() == null
                || session.id().length() > 150
                || !session.id().matches("cs_(test|live)_[A-Za-z0-9_]+")
                || !request.orderId().toString().equals(session.clientReferenceId())
                || request.amountInMinorUnit() != session.amountTotal()
                || !request.currency().equals(session.currency())) {
            throw new PaymentProviderException(PaymentProviderErrorType.PROTOCOL_ERROR, "STRIPE_SESSION_MISMATCH");
        }
        if ("open".equals(session.status())) {
            requireAllowlistedCheckoutUrl(session.checkoutUrl());
        }
    }

    private PaymentProviderResult mapResult(StripeCheckoutSession session) {
        if ("open".equals(session.status())) {
            return new PaymentProviderResult(
                    PaymentProviderStatus.REQUIRES_ACTION, session.id(), session.checkoutUrl(), null);
        }
        if ("complete".equals(session.status()) && "paid".equals(session.paymentStatus())) {
            return new PaymentProviderResult(PaymentProviderStatus.SUCCEEDED, session.id(), null, null);
        }
        if ("expired".equals(session.status())) {
            return new PaymentProviderResult(
                    PaymentProviderStatus.FAILED, session.id(), null, "STRIPE_SESSION_EXPIRED");
        }
        throw new PaymentProviderException(PaymentProviderErrorType.PROTOCOL_ERROR, "STRIPE_STATUS_UNEXPECTED");
    }

    private void requireAllowlistedCheckoutUrl(URI value) {
        String host = value == null || value.getHost() == null
                ? null
                : value.getHost().toLowerCase(Locale.ROOT);
        if (value == null
                || !value.isAbsolute()
                || value.isOpaque()
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getUserInfo() != null
                || (value.getPort() != -1 && value.getPort() != 443)
                || value.toASCIIString().length() > MAXIMUM_CHECKOUT_URL_LENGTH
                || host == null
                || !allowedCheckoutHosts.contains(host)) {
            throw new PaymentProviderException(PaymentProviderErrorType.PROTOCOL_ERROR, "STRIPE_CHECKOUT_URL_REJECTED");
        }
    }
}
