package com.shop.payment.internal.provider.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnpaySignature {
    private final String secret;
    private final List<String> previousSecrets;

    public VnpaySignature(String secret) {
        this(secret, List.of());
    }

    public VnpaySignature(String secret, List<String> previousSecrets) {
        this.secret = secret;
        this.previousSecrets = List.copyOf(previousSecrets);
    }

    public String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields)
                .entrySet().stream()
                        .filter(e -> !e.getKey().equals("vnp_SecureHash")
                                && !e.getKey().equals("vnp_SecureHashType"))
                        .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> fields) {
        return hmac(canonical(fields));
    }

    public boolean verify(Map<String, String> fields) {
        String data = canonical(fields);
        boolean matches = equalHash(hmac(data), fields.get("vnp_SecureHash"));
        for (String previous : previousSecrets) {
            matches |= equalHash(hmac(previous, data), fields.get("vnp_SecureHash"));
        }
        return matches;
    }

    public String checkoutToken(UUID attemptId) {
        return hmac("shop-vnpay-handoff-v1:" + attemptId);
    }

    public boolean verifyCheckoutToken(UUID attemptId, String token) {
        boolean matches = equalHash(checkoutToken(attemptId), token);
        for (String previous : previousSecrets) {
            matches |= equalHash(hmac(previous, "shop-vnpay-handoff-v1:" + attemptId), token);
        }
        return matches;
    }

    public String fingerprint(Map<String, String> fields) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonical(fields).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmac(String input) {
        return hmac(secret, input);
    }

    private String hmac(String key, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    private boolean equalHash(String expected, String actual) {
        return actual != null
                && actual.matches("[a-fA-F0-9]{128}")
                && MessageDigest.isEqual(
                        HexFormat.of().parseHex(expected), HexFormat.of().parseHex(actual));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
