package com.shop.payment.internal.provider.stripe;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeCheckoutStateSigner implements StripeCheckoutStateAccess {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_VERSION = "v1";

    private final byte[] secret;
    private final Duration timeToLive;
    private final Clock clock;

    StripeCheckoutStateSigner(String secret, Duration timeToLive, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    String issue(UUID paymentAttemptId) {
        long expiresAt = clock.instant().plus(timeToLive).getEpochSecond();
        String payload = TOKEN_VERSION + "." + paymentAttemptId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    @Override
    public UUID verify(String token) {
        if (token == null || token.length() > 256) {
            throw new IllegalArgumentException("Trạng thái checkout không hợp lệ");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !TOKEN_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Trạng thái checkout không hợp lệ");
        }
        String payload = String.join(".", parts[0], parts[1], parts[2]);
        if (!MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.US_ASCII), parts[3].getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Trạng thái checkout không hợp lệ");
        }
        UUID paymentAttemptId;
        Instant expiresAt;
        try {
            paymentAttemptId = UUID.fromString(parts[1]);
            expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Trạng thái checkout không hợp lệ", exception);
        }
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Trạng thái checkout đã hết hạn");
        }
        return paymentAttemptId;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Thuật toán HMAC-SHA256 không khả dụng", exception);
        }
    }
}
