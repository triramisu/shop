package com.shop.order.internal.checkout.idempotency;

import com.shop.order.internal.checkout.dto.request.CheckoutOrderRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class CheckoutIdempotencyHasher {

    String key(String idempotencyKey) {
        return hash(idempotencyKey);
    }

    String request(CheckoutOrderRequest request) {
        return hash(CheckoutIdempotencyOperation.CREATE_ORDER.name() + "|" + request.getExpectedCartVersion());
    }

    private String hash(String canonicalValue) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonicalValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
