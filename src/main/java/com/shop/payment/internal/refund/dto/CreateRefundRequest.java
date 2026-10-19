package com.shop.payment.internal.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreateRefundRequest(
        @Schema(description = "Bỏ/null để hoàn toàn bộ phần chưa được giữ hoặc hoàn", example = "100000")
        @DecimalMin(value = "0", inclusive = false, message = "PAYMENT_REFUND_DATA_INVALID")
        @Digits(integer = 17, fraction = 2, message = "PAYMENT_REFUND_DATA_INVALID")
        BigDecimal amount,

        @NotNull(message = "PAYMENT_REFUND_DATA_INVALID")
        @Pattern(regexp = "[A-Za-z]{3}", message = "PAYMENT_REFUND_DATA_INVALID")
        @Schema(example = "VND")
        String currency) {}
