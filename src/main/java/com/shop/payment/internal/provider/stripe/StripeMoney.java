package com.shop.payment.internal.provider.stripe;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import java.math.BigDecimal;
import java.util.Set;

final class StripeMoney {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "VND", "VUV", "XAF", "XOF", "XPF");
    private static final long MAXIMUM_MINOR_AMOUNT = 99_999_999L;

    private StripeMoney() {}

    static long toMinorUnit(BigDecimal amount, String currency) {
        try {
            BigDecimal minorAmount = ZERO_DECIMAL_CURRENCIES.contains(currency)
                    ? amount.setScale(0)
                    : amount.movePointRight(2).setScale(0);
            long value = minorAmount.longValueExact();
            if (value < 1 || value > MAXIMUM_MINOR_AMOUNT) {
                throw invalidAmount();
            }
            return value;
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    private static PaymentProviderException invalidAmount() {
        return new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "STRIPE_AMOUNT_INVALID");
    }
}
