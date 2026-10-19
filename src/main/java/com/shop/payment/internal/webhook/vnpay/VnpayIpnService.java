package com.shop.payment.internal.webhook.vnpay;

import com.shop.payment.internal.provider.vnpay.VnpayProperties;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
class VnpayIpnService {
    private final VnpaySignature signature;
    private final VnpayProperties properties;
    private final VnpayIpnTransactionService transactions;
    private final MeterRegistry meters;

    VnpayIpnReceipt receive(Map<String, String> fields) {
        VnpayIpnReceipt receipt;
        try {
            if (!signature.verify(fields) || !properties.getTmnCode().equals(fields.get("vnp_TmnCode"))) {
                receipt = VnpayIpnReceipt.of("97");
            } else {
                Instant now = Instant.now();
                receipt = transactions.process(VnpayIpnEvent.parse(fields, now), signature.fingerprint(fields), now);
            }
        } catch (IllegalArgumentException exception) {
            receipt = VnpayIpnReceipt.of("99");
        } catch (RuntimeException exception) {
            // Never log exception messages/raw query: notification contains sensitive payment metadata.
            log.warn("VNPay IPN processing unavailable; provider may retry");
            receipt = VnpayIpnReceipt.of("99");
        }
        meters.counter("shop.payment.vnpay.ipn", "code", receipt.code()).increment();
        return receipt;
    }
}
