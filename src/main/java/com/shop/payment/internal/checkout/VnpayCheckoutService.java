package com.shop.payment.internal.checkout;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.internal.provider.vnpay.VnpayProperties;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
class VnpayCheckoutService {
    private final PaymentAttemptRepository repository;
    private final VnpayPaymentProvider provider;
    private final VnpaySignature signature;
    private final VnpayProperties properties;

    VnpayBrowserReturnReceipt verifyReturn(Map<String, String> fields) {
        if (!signature.verify(fields) || !properties.getTmnCode().equals(fields.get("vnp_TmnCode"))) {
            throw invalid();
        }
        return new VnpayBrowserReturnReceipt(true, false);
    }

    @Transactional(readOnly = true)
    URI start(UUID attemptId, String token, String remoteAddress) {
        if (!signature.verifyCheckoutToken(attemptId, token)) {
            throw invalid();
        }
        var attempt = repository.findById(attemptId).orElseThrow(this::invalid);
        Instant now = Instant.now();
        if (!VnpayPaymentProvider.CODE.equals(attempt.getProviderCode())
                || !VnpayPaymentProvider.reference(attemptId).equals(attempt.getProviderReference())
                || attempt.getStatus() != PaymentStatus.REQUIRES_ACTION
                || now.isBefore(attempt.getCreatedAt())
                || !now.isBefore(attempt.getCreatedAt().plus(properties.getCheckoutTtl()))) {
            throw invalid();
        }
        return provider.gatewayUrl(
                attemptId,
                attempt.getOrderId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getCreatedAt(),
                literalAddress(remoteAddress));
    }

    private String literalAddress(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) {
            throw invalid();
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            throw invalid();
        }
    }

    private AppException invalid() {
        return new AppException(ErrorCode.PAYMENT_CHECKOUT_RETURN_INVALID);
    }
}
