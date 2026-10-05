package com.shop.payment.internal.webhook.stripe;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeWebhookSignatureVerifier {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int MAXIMUM_SIGNATURE_HEADER_LENGTH = 8192;
    private static final int MAXIMUM_SIGNATURES = 20;

    private final List<byte[]> secrets;
    private final Duration tolerance;
    private final Clock clock;

    StripeWebhookSignatureVerifier(List<String> secrets, Duration tolerance, Clock clock) {
        this.secrets = secrets.stream()
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .toList();
        this.tolerance = tolerance;
        this.clock = clock;
    }

    VerifiedStripeSignature verify(byte[] rawPayload, String signatureHeader) {
        SignatureParts parts = parse(signatureHeader);
        Instant signedAt = toInstant(parts.timestamp());
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw invalidSignature();
        }

        byte[] timestamp = Long.toString(parts.timestamp()).getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (byte[] secret : secrets) {
            byte[] expected = sign(secret, timestamp, rawPayload);
            for (byte[] candidate : parts.signatures()) {
                matched |= MessageDigest.isEqual(expected, candidate);
            }
        }
        if (!matched) {
            throw invalidSignature();
        }
        return new VerifiedStripeSignature(signedAt);
    }

    private static SignatureParts parse(String header) {
        if (header == null || header.isBlank() || header.length() > MAXIMUM_SIGNATURE_HEADER_LENGTH) {
            throw invalidSignature();
        }
        Long timestamp = null;
        List<byte[]> signatures = new ArrayList<>();
        for (String element : header.split(",")) {
            String[] pair = element.strip().split("=", 2);
            if (pair.length != 2) {
                throw invalidSignature();
            }
            if ("t".equals(pair[0])) {
                if (timestamp != null) {
                    throw invalidSignature();
                }
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException exception) {
                    throw invalidSignature();
                }
            } else if ("v1".equals(pair[0])) {
                if (signatures.size() >= MAXIMUM_SIGNATURES) {
                    throw invalidSignature();
                }
                try {
                    byte[] decoded = HexFormat.of().parseHex(pair[1]);
                    if (decoded.length != 32) {
                        throw invalidSignature();
                    }
                    signatures.add(decoded);
                } catch (IllegalArgumentException exception) {
                    throw invalidSignature();
                }
            }
        }
        if (timestamp == null || timestamp < 0 || signatures.isEmpty()) {
            throw invalidSignature();
        }
        return new SignatureParts(timestamp, List.copyOf(signatures));
    }

    private static Instant toInstant(long timestamp) {
        try {
            return Instant.ofEpochSecond(timestamp);
        } catch (DateTimeException exception) {
            throw invalidSignature();
        }
    }

    private static byte[] sign(byte[] secret, byte[] timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
            mac.update(timestamp);
            mac.update((byte) '.');
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize Stripe webhook signature verification", exception);
        }
    }

    private static AppException invalidSignature() {
        return new AppException(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
    }

    private record SignatureParts(long timestamp, List<byte[]> signatures) {}
}
