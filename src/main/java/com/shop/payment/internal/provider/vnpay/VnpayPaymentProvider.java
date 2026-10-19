package com.shop.payment.internal.provider.vnpay;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderOperations;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class VnpayPaymentProvider implements PaymentProviderOperations {
    public static final String CODE = "VNPAY";
    public static final String CHECKOUT_PATH = "/api/payments/checkout/vnpay";
    public static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final VnpayProperties properties;
    private final VnpaySignature signature;

    public VnpayPaymentProvider(VnpayProperties properties, VnpaySignature signature) {
        this.properties = properties;
        this.signature = signature;
    }

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public PaymentProviderResult createPayment(PaymentProviderRequest request) {
        amount(request.amount(), request.currency());
        URI action = URI.create(properties.getPublicBaseUrl() + CHECKOUT_PATH + "/start?attempt="
                + request.paymentAttemptId() + "&token=" + signature.checkoutToken(request.paymentAttemptId()));
        return new PaymentProviderResult(
                PaymentProviderStatus.REQUIRES_ACTION, reference(request.paymentAttemptId()), action, null);
    }

    public URI gatewayUrl(
            UUID attemptId, UUID orderId, BigDecimal amount, String currency, Instant createdAt, String clientIp) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_Amount", amount(amount, currency));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", reference(attemptId));
        fields.put("vnp_OrderInfo", orderInfo(orderId));
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getPublicBaseUrl() + CHECKOUT_PATH + "/return");
        fields.put("vnp_IpAddr", clientIp);
        fields.put("vnp_CreateDate", DATE.format(createdAt));
        fields.put("vnp_ExpireDate", DATE.format(createdAt.plus(properties.getCheckoutTtl())));
        if (!properties.getBankCode().isEmpty()) {
            fields.put("vnp_BankCode", properties.getBankCode());
        }
        return URI.create(
                SANDBOX_URL + "?" + signature.canonical(fields) + "&vnp_SecureHash=" + signature.sign(fields));
    }

    public static String reference(UUID id) {
        return id.toString().replace("-", "");
    }

    public static String orderInfo(UUID orderId) {
        return "Thanh toan don hang " + orderId;
    }

    public static String amount(BigDecimal value, String currency) {
        if (!"VND".equals(currency)) {
            throw new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "VNPAY_CURRENCY_UNSUPPORTED");
        }
        try {
            String encoded = value.setScale(0, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .toPlainString();
            if (value.signum() <= 0 || !encoded.matches("[0-9]{1,12}")) {
                throw new ArithmeticException();
            }
            return encoded;
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "VNPAY_AMOUNT_INVALID");
        }
    }
}
