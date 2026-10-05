package com.shop.payment.internal.webhook.stripe;

import com.shop.payment.internal.provider.stripe.StripePaymentProviderProperties;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import com.shop.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/webhooks")
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "stripe")
@Tag(name = "Payment webhook", description = "Nhận sự kiện máy chủ đã ký từ cổng thanh toán")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StripeWebhookController {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    StripeWebhookService webhookService;
    StripePaymentProviderProperties stripeProperties;

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Nhận webhook Stripe đã xác minh chữ ký",
            description =
                    "Endpoint máy-chủ-với-máy-chủ; không dùng JWT. Raw body được xác minh HMAC trước khi parse JSON.")
    @Parameter(
            name = STRIPE_SIGNATURE_HEADER,
            description = "Timestamp và chữ ký v1 do Stripe tạo",
            required = true,
            in = ParameterIn.HEADER,
            schema = @Schema(type = "string"))
    ApiResponse<StripeWebhookReceipt> receive(
            HttpServletRequest request,
            @RequestHeader(name = STRIPE_SIGNATURE_HEADER, required = false) String signatureHeader) {
        byte[] rawPayload = readRawPayload(request);
        return ApiResponse.<StripeWebhookReceipt>builder()
                .result(webhookService.receive(rawPayload, signatureHeader))
                .build();
    }

    private byte[] readRawPayload(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        int maximumPayloadBytes = stripeProperties.getWebhookMaxPayloadBytes();
        if (contentLength > maximumPayloadBytes) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(maximumPayloadBytes + 1);
            if (payload.length == 0 || payload.length > maximumPayloadBytes) {
                throw new AppException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
            }
            return payload;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
    }
}
