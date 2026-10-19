package com.shop.payment.internal.provider.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.Locale;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tools.jackson.databind.ObjectMapper;

final class StripeCheckoutHttpGateway implements StripeCheckoutGateway {

    private static final HttpUrl STRIPE_CHECKOUT_SESSIONS_URL =
            HttpUrl.get("https://api.stripe.com/v1/checkout/sessions");
    private static final int MAXIMUM_RESPONSE_CHARACTERS = 65_536;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String apiVersion;
    private final HttpUrl endpoint;

    StripeCheckoutHttpGateway(OkHttpClient httpClient, ObjectMapper objectMapper, String secretKey, String apiVersion) {
        this(httpClient, objectMapper, secretKey, apiVersion, STRIPE_CHECKOUT_SESSIONS_URL);
    }

    StripeCheckoutHttpGateway(
            OkHttpClient httpClient, ObjectMapper objectMapper, String secretKey, String apiVersion, HttpUrl endpoint) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
        this.apiVersion = apiVersion;
        this.endpoint = endpoint;
    }

    @Override
    public StripeCheckoutSession createSession(StripeCheckoutRequest request) {
        FormBody.Builder body = new FormBody.Builder();
        request.formFields().forEach(field -> body.add(field.getKey(), field.getValue()));
        Request httpRequest = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + secretKey)
                .header("Stripe-Version", apiVersion)
                .header("Idempotency-Key", request.idempotencyKey())
                .post(body.build())
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw mapHttpError(response.code());
            }
            return parseResponse(response.body());
        } catch (InterruptedIOException exception) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "STRIPE_TIMEOUT");
        } catch (IOException exception) {
            throw new PaymentProviderException(
                    PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "STRIPE_NETWORK_UNAVAILABLE");
        }
    }

    private StripeCheckoutSession parseResponse(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            throw protocolError("STRIPE_EMPTY_RESPONSE");
        }
        if (responseBody.contentType() == null
                || !"application".equalsIgnoreCase(responseBody.contentType().type())
                || !"json".equalsIgnoreCase(responseBody.contentType().subtype())) {
            throw protocolError("STRIPE_CONTENT_TYPE_INVALID");
        }
        String body;
        try (BufferedReader reader = new BufferedReader(responseBody.charStream())) {
            char[] characters = new char[MAXIMUM_RESPONSE_CHARACTERS + 1];
            int length = 0;
            while (length < characters.length) {
                int count = reader.read(characters, length, characters.length - length);
                if (count < 0) {
                    break;
                }
                length += count;
            }
            body = new String(characters, 0, length);
        }
        if (body.length() > MAXIMUM_RESPONSE_CHARACTERS) {
            throw protocolError("STRIPE_RESPONSE_TOO_LARGE");
        }
        StripeCheckoutSessionPayload payload;
        try {
            payload = objectMapper.readValue(body, StripeCheckoutSessionPayload.class);
        } catch (RuntimeException exception) {
            throw protocolError("STRIPE_RESPONSE_INVALID");
        }
        try {
            return new StripeCheckoutSession(
                    requireText(payload.id(), "STRIPE_SESSION_ID_MISSING"),
                    URI.create(requireText(payload.url(), "STRIPE_CHECKOUT_URL_MISSING")),
                    requireText(payload.status(), "STRIPE_SESSION_STATUS_MISSING")
                            .toLowerCase(Locale.ROOT),
                    requireText(payload.paymentStatus(), "STRIPE_PAYMENT_STATUS_MISSING")
                            .toLowerCase(Locale.ROOT),
                    requireText(payload.clientReferenceId(), "STRIPE_CLIENT_REFERENCE_MISSING"),
                    requireAmount(payload.amountTotal()),
                    requireText(payload.currency(), "STRIPE_CURRENCY_MISSING").toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw protocolError("STRIPE_RESPONSE_INVALID");
        }
    }

    private static long requireAmount(Long amount) {
        if (amount == null || amount < 1) {
            throw new IllegalArgumentException("Số tiền Stripe bị thiếu hoặc không hợp lệ");
        }
        return amount;
    }

    private static String requireText(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw protocolError(errorCode);
        }
        return value;
    }

    private static PaymentProviderException mapHttpError(int statusCode) {
        if (statusCode == 400 || statusCode == 404 || statusCode == 409 || statusCode == 422) {
            return new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "STRIPE_REQUEST_REJECTED");
        }
        if (statusCode == 401 || statusCode == 403) {
            return new PaymentProviderException(
                    PaymentProviderErrorType.AUTHENTICATION_FAILED, "STRIPE_AUTHENTICATION_FAILED");
        }
        if (statusCode == 429) {
            return new PaymentProviderException(PaymentProviderErrorType.RATE_LIMITED, "STRIPE_RATE_LIMITED");
        }
        if (statusCode >= 500) {
            return new PaymentProviderException(
                    PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "STRIPE_SERVER_UNAVAILABLE");
        }
        return protocolError("STRIPE_HTTP_STATUS_UNEXPECTED");
    }

    private static PaymentProviderException protocolError(String errorCode) {
        return new PaymentProviderException(PaymentProviderErrorType.PROTOCOL_ERROR, errorCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StripeCheckoutSessionPayload(
            String id,
            String url,
            String status,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("client_reference_id") String clientReferenceId,
            @JsonProperty("amount_total") Long amountTotal,
            String currency) {}
}
