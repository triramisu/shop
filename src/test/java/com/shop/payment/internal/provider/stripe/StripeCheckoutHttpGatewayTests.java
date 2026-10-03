package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StripeCheckoutHttpGatewayTests {

    private static final String SECRET_KEY = StripeTestCredentials.apiKey();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsAuthenticatedIdempotentFormAndParsesSanitizedSession() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {
                      "id":"cs_test_session",
                      "url":"https://checkout.stripe.com/c/pay/session",
                      "status":"open",
                      "payment_status":"unpaid",
                      "client_reference_id":"%s",
                      "amount_total":1025,
                      "currency":"usd"
                    }
                    """.formatted(ORDER_ID));
        });

        StripeCheckoutSession session = gateway(Duration.ofSeconds(2)).createSession(request());

        assertThat(session.id()).isEqualTo("cs_test_session");
        assertThat(session.amountTotal()).isEqualTo(1025L);
        assertThat(authorization).hasValue("Bearer " + SECRET_KEY);
        assertThat(idempotencyKey).hasValue("stripe-idempotency-key");
        assertThat(requestBody.get()).contains("mode=payment").contains("client_reference_id=" + ORDER_ID);
    }

    @Test
    void mapsProviderHttpFailuresWithoutLeakingResponseOrSecret() throws Exception {
        startServer(exchange -> respond(exchange, 401, "credential=" + SECRET_KEY));

        assertThatThrownBy(() -> gateway(Duration.ofSeconds(2)).createSession(request()))
                .isInstanceOfSatisfying(PaymentProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.AUTHENTICATION_FAILED);
                    assertThat(exception.getProviderErrorCode()).isEqualTo("STRIPE_AUTHENTICATION_FAILED");
                    assertThat(exception.getMessage())
                            .doesNotContain(SECRET_KEY)
                            .doesNotContain("credential");
                });
    }

    @Test
    void mapsInvalidJsonToProtocolError() throws Exception {
        startServer(exchange -> respond(exchange, 200, "not-json"));

        assertThatThrownBy(() -> gateway(Duration.ofSeconds(2)).createSession(request()))
                .isInstanceOfSatisfying(PaymentProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.PROTOCOL_ERROR);
                    assertThat(exception.getProviderErrorCode()).isEqualTo("STRIPE_RESPONSE_INVALID");
                });
    }

    @Test
    void appliesHardCallTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client intentionally closes the timed-out connection.
            }
        });

        assertThatThrownBy(() -> gateway(Duration.ofMillis(50)).createSession(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.TIMEOUT));
    }

    private static final UUID PAYMENT_ATTEMPT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private StripeCheckoutRequest request() {
        return new StripeCheckoutRequest(
                PAYMENT_ATTEMPT_ID,
                ORDER_ID,
                "stripe-idempotency-key",
                1025L,
                "usd",
                List.of(Map.entry("mode", "payment"), Map.entry("client_reference_id", ORDER_ID.toString())));
    }

    private StripeCheckoutHttpGateway gateway(Duration callTimeout) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(1))
                .callTimeout(callTimeout)
                .retryOnConnectionFailure(false)
                .build();
        return new StripeCheckoutHttpGateway(
                client,
                new ObjectMapper(),
                SECRET_KEY,
                "2026-09-30.endive",
                HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/checkout/sessions"));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/checkout/sessions", exchange -> handler.handle(exchange));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
