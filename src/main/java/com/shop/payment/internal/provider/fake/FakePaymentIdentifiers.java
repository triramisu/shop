package com.shop.payment.internal.provider.fake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FakePaymentIdentifiers {

    private FakePaymentIdentifiers() {}

    static String providerReference(String idempotencyKey) {
        return "fake_" + digest(idempotencyKey).substring(0, 32);
    }

    static String providerEventId(String providerReference) {
        return "fake_event_" + digest(providerReference + ":callback").substring(0, 32);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
