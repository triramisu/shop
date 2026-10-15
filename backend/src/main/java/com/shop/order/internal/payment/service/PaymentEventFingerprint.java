package com.shop.order.internal.payment.service;

import com.shop.payment.event.PaymentStatusChangedEvent;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class PaymentEventFingerprint {

    String hash(PaymentStatusChangedEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, Integer.toString(event.eventVersion()));
                write(output, event.eventId().toString());
                write(output, event.paymentAttemptId().toString());
                write(output, event.orderId().toString());
                write(output, event.previousStatus().name());
                write(output, event.currentStatus().name());
                write(output, event.amount().toPlainString());
                write(output, event.currency());
                write(output, event.providerCode());
                write(output, event.providerReference());
                write(output, event.failureCode());
                write(output, event.occurredAt().toString());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize payment event fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void write(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
