package com.shop.payment.internal.provider.vnpay.query;

import static com.shop.payment.internal.provider.vnpay.query.VnpayQueryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VnpayQueryHttpGatewayTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOnlySignedJsonAndAcceptsAuthenticatedResponse() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        start(exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, "application/json; charset=utf-8", signed(responseFields(), SECRET), false);
        });
        var result = gateway(Duration.ofSeconds(2)).query(request());
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(method).hasValue("POST");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(payload.get())
                .contains("\"vnp_Command\":\"querydr\"", "\"vnp_SecureHash\":")
                .doesNotContain(SECRET);
        assertThat(VnpayQueryHttpGateway.SANDBOX_ENDPOINT)
                .isEqualTo("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
    }

    @ParameterizedTest
    @CsvSource({
        "400,INVALID_REQUEST",
        "404,INVALID_REQUEST",
        "409,INVALID_REQUEST",
        "422,INVALID_REQUEST",
        "401,AUTHENTICATION_FAILED",
        "403,AUTHENTICATION_FAILED",
        "429,RATE_LIMITED",
        "500,TEMPORARY_UNAVAILABLE",
        "503,TEMPORARY_UNAVAILABLE",
        "418,PROTOCOL_ERROR"
    })
    void mapsHttpFailuresWithoutBodyLeakOrAutomaticRetry(int status, PaymentProviderErrorType type) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, status, "text/html", SECRET.getBytes(StandardCharsets.UTF_8), false);
        });
        fails(type, () -> gateway(Duration.ofSeconds(2)).query(request()));
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 503})
    void doesNotResendSignedRequestEvenWhenServerAsksForImmediateRetry(int status) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "0");
            respond(exchange, status, "application/json", new byte[0], false);
        });
        assertThatThrownBy(() -> gateway(Duration.ofSeconds(2)).query(request()))
                .isInstanceOf(PaymentProviderException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void refusesRedirectWithoutSendingSignedRequestToAnotherPath() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        start(exchange -> {
            exchange.getResponseHeaders().set("Location", "/leak");
            respond(exchange, 307, "application/json", new byte[0], false);
        });
        server.createContext("/leak", exchange -> {
            redirected.incrementAndGet();
            respond(exchange, 200, "application/json", signed(responseFields(), SECRET), false);
        });
        fails(PaymentProviderErrorType.PROTOCOL_ERROR, () -> gateway(Duration.ofSeconds(2))
                .query(request()));
        assertThat(redirected).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "application/problem+json", ""})
    void refusesNonJsonOrMissingContentType(String type) throws Exception {
        start(exchange -> respond(exchange, 200, type, signed(responseFields(), SECRET), false));
        fails(PaymentProviderErrorType.PROTOCOL_ERROR, () -> gateway(Duration.ofSeconds(2))
                .query(request()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void boundsBothChunkedAndDeclaredResponseSize(boolean chunked) throws Exception {
        start(exchange -> respond(
                exchange, 200, "application/json", "x".repeat(16_385).getBytes(StandardCharsets.UTF_8), chunked));
        assertThatThrownBy(() -> gateway(Duration.ofSeconds(2)).query(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, error -> assertThat(error.getProviderErrorCode())
                                .isEqualTo(chunked ? "VNPAY_QUERY_BODY_TOO_LARGE" : "VNPAY_QUERY_BODY_INVALID"));
    }

    @Test
    void boundsDecompressedBodyInsteadOfTrustingSmallGzipContentLength() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write("x".repeat(16_385).getBytes(StandardCharsets.UTF_8));
        }
        assertThat(bytes.size()).isLessThan(1024);
        start(exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            respond(exchange, 200, "application/json", bytes.toByteArray(), false);
        });
        assertThatThrownBy(() -> gateway(Duration.ofSeconds(2)).query(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class,
                        error -> assertThat(error.getProviderErrorCode()).isEqualTo("VNPAY_QUERY_BODY_TOO_LARGE"));
    }

    @Test
    void enforcesTotalCallTimeout() throws Exception {
        start(exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "application/json", signed(responseFields(), SECRET), false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Timed-out client closes this connection.
            }
        });
        fails(PaymentProviderErrorType.TIMEOUT, () -> gateway(Duration.ofMillis(50))
                .query(request()));
    }

    @Test
    void reportsUnreachableDependencyAsUnavailableWithoutRawCause() throws Exception {
        start(exchange -> {});
        var gateway = gateway(Duration.ofSeconds(2));
        server.stop(0);
        server = null;
        fails(PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, () -> gateway.query(request()));
    }

    private void start(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", handler);
        server.start();
    }

    private VnpayQueryHttpGateway gateway(Duration timeout) {
        VnpayQueryProperties properties = new VnpayQueryProperties();
        properties.setServerIp("203.0.113.10");
        OkHttpClient client = new VnpayQueryConfiguration()
                .vnpayQueryHttpClient(properties)
                .newBuilder()
                .callTimeout(timeout)
                .build();
        return new VnpayQueryHttpGateway(
                client,
                codec(),
                HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort() + "/query"));
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] body, boolean chunked)
            throws IOException {
        if (!type.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", type);
        }
        exchange.sendResponseHeaders(status, chunked ? 0 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void fails(PaymentProviderErrorType type, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PaymentProviderException.class, error -> {
            assertThat(error.getErrorType()).isEqualTo(type);
            assertThat(error.getMessage()).doesNotContain(SECRET, "127.0.0.1", "vnp_");
            assertThat(error.getCause()).isNull();
        });
    }
}
