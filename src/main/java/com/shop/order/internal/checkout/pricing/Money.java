package com.shop.order.internal.checkout.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("currency is not usable for pricing");
        }
        amount = amount.setScale(fractionDigits, ROUNDING_MODE);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        Currency currency = Currency.getInstance(currencyCode.strip().toUpperCase(Locale.ROOT));
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("money subtraction must not produce a negative amount");
        }
        return new Money(result, currency);
    }

    public Money multiply(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money percentage(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate is required");
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate must be between zero and one");
        }
        return new Money(amount.multiply(rate), currency);
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "money is required");
        if (!currency.equals(other.currency)) {
            throw new CheckoutPricingException(CheckoutPricingException.Reason.MIXED_CURRENCY);
        }
    }
}
