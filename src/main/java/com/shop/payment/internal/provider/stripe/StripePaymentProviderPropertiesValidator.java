package com.shop.payment.internal.provider.stripe;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.InitializingBean;

final class StripePaymentProviderPropertiesValidator implements InitializingBean {

    private static final int MINIMUM_SECRET_LENGTH = 32;
    private static final String HOST_PATTERN =
            "(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*" + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";

    private final StripePaymentProviderProperties properties;

    StripePaymentProviderPropertiesValidator(StripePaymentProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        requireStripeSecret(properties.getSecretKey());
        requireSecret("Khóa ký trạng thái trả về của Stripe", properties.getReturnStateSecret());
        requireApiVersion(properties.getApiVersion());
        properties.setAllowedReturnHosts(
                normalizeHosts("Danh sách host trả về của Stripe", properties.getAllowedReturnHosts()));
        properties.setAllowedCheckoutHosts(
                normalizeHosts("Danh sách host checkout của Stripe", properties.getAllowedCheckoutHosts()));
        requireReturnUrl(
                "URL Stripe thành công",
                properties.getSuccessUrl(),
                properties.getAllowedReturnHosts(),
                properties.getSecretKey(),
                "/api/payments/checkout/return");
        requireReturnUrl(
                "URL Stripe hủy",
                properties.getCancelUrl(),
                properties.getAllowedReturnHosts(),
                properties.getSecretKey(),
                "/api/payments/checkout/cancel");
        requireDuration(
                "Thời gian sống trạng thái trả về của Stripe",
                properties.getReturnStateTtl(),
                Duration.ofMinutes(1),
                Duration.ofDays(7));
        requireDuration(
                "Thời gian chờ kết nối Stripe",
                properties.getConnectTimeout(),
                Duration.ofMillis(100),
                Duration.ofSeconds(30));
        requireDuration(
                "Thời gian chờ đọc Stripe",
                properties.getReadTimeout(),
                Duration.ofMillis(100),
                Duration.ofSeconds(60));
        requireDuration(
                "Thời gian chờ toàn bộ lời gọi Stripe",
                properties.getCallTimeout(),
                Duration.ofMillis(100),
                Duration.ofSeconds(90));
        if (properties.getCallTimeout().compareTo(properties.getConnectTimeout()) < 0
                || properties.getCallTimeout().compareTo(properties.getReadTimeout()) < 0) {
            throw new IllegalStateException(
                    "Thời gian chờ toàn bộ lời gọi Stripe không được ngắn hơn thời gian kết nối hoặc đọc");
        }
        validateRetry(properties.getRetry());
        validateCircuitBreaker(properties.getCircuitBreaker());
    }

    private static void requireStripeSecret(String value) {
        requireSecret("Khóa bí mật Stripe", value);
        if (!value.startsWith("sk_test_") && !value.startsWith("sk_live_")) {
            throw new IllegalStateException("Khóa bí mật Stripe phải là khóa test hoặc live dành cho máy chủ");
        }
    }

    private static void requireSecret(String name, String value) {
        if (value == null
                || value.length() < MINIMUM_SECRET_LENGTH
                || value.length() > 512
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalStateException(name + " phải có từ 32 đến 512 ký tự hiển thị và không chứa khoảng trắng");
        }
    }

    private static void requireApiVersion(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}(\\.[a-z]+)?")) {
            throw new IllegalStateException("Phiên bản API Stripe không hợp lệ");
        }
    }

    private static Set<String> normalizeHosts(String name, Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(name + " không được để trống");
        }
        Set<String> normalized = values.stream()
                .map(value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.stream().anyMatch(host -> !host.matches(HOST_PATTERN))) {
            throw new IllegalStateException(name + " chứa host không hợp lệ");
        }
        return normalized;
    }

    private static void requireReturnUrl(
            String name, URI value, Set<String> allowedHosts, String stripeSecretKey, String expectedPath) {
        if (value == null
                || !value.isAbsolute()
                || value.isOpaque()
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null
                || !expectedPath.equals(value.getPath())
                || !hasAllowedTransport(value, stripeSecretKey)
                || !allowedHosts.contains(value.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    name
                            + " phải là URL HTTPS thuộc danh sách cho phép; chỉ khóa test được dùng HTTP trên localhost hoặc 127.0.0.1 và URL không được chứa query hoặc fragment");
        }
    }

    private static boolean hasAllowedTransport(URI value, String stripeSecretKey) {
        if ("https".equalsIgnoreCase(value.getScheme())) {
            return value.getPort() == -1 || value.getPort() == 443;
        }
        if (!"http".equalsIgnoreCase(value.getScheme())
                || stripeSecretKey == null
                || !stripeSecretKey.startsWith("sk_test_")) {
            return false;
        }
        String host = value.getHost().toLowerCase(Locale.ROOT);
        return ("localhost".equals(host) || "127.0.0.1".equals(host))
                && (value.getPort() == -1 || (value.getPort() > 0 && value.getPort() <= 65_535));
    }

    private static void requireDuration(String name, Duration value, Duration minimum, Duration maximum) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(name + " nằm ngoài khoảng được hỗ trợ");
        }
    }

    private static void validateRetry(StripePaymentProviderProperties.Retry retry) {
        if (retry == null || retry.getMaxAttempts() < 1 || retry.getMaxAttempts() > 3) {
            throw new IllegalStateException("Số lần thử lại Stripe phải từ 1 đến 3");
        }
        requireDuration(
                "Thời gian chờ thử lại Stripe", retry.getWaitDuration(), Duration.ofMillis(10), Duration.ofSeconds(5));
    }

    private static void validateCircuitBreaker(StripePaymentProviderProperties.CircuitBreaker circuitBreaker) {
        if (circuitBreaker == null
                || circuitBreaker.getSlidingWindowSize() < 2
                || circuitBreaker.getSlidingWindowSize() > 100
                || circuitBreaker.getMinimumNumberOfCalls() < 1
                || circuitBreaker.getMinimumNumberOfCalls() > circuitBreaker.getSlidingWindowSize()
                || circuitBreaker.getFailureRateThreshold() < 1.0F
                || circuitBreaker.getFailureRateThreshold() > 100.0F
                || circuitBreaker.getPermittedCallsInHalfOpenState() < 1
                || circuitBreaker.getPermittedCallsInHalfOpenState() > circuitBreaker.getSlidingWindowSize()) {
            throw new IllegalStateException("Cấu hình circuit breaker của Stripe không hợp lệ");
        }
        requireDuration(
                "Thời gian mở circuit breaker của Stripe",
                circuitBreaker.getOpenStateDuration(),
                Duration.ofSeconds(1),
                Duration.ofMinutes(10));
    }
}
