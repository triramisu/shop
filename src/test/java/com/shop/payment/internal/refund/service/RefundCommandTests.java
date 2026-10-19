package com.shop.payment.internal.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.shared.error.AppException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RefundCommandTests {
    @Test
    void canonicalizesMoneyAndCurrencyButNotFullIntentOrKeyCase() {
        var first = RefundCommand.of(new BigDecimal("10.0"), "usd", "Key-A");
        var same = RefundCommand.of(new BigDecimal("10"), "USD", "Key-A");
        assertThat(first).isEqualTo(same);
        assertThat(first.keyHash())
                .hasSize(64)
                .isNotEqualTo(
                        RefundCommand.of(new BigDecimal("10"), "USD", "key-a").keyHash());
        assertThat(first.fingerprint())
                .isNotEqualTo(RefundCommand.of(null, "USD", "Key-A").fingerprint());
        assertThat(first.fingerprint())
                .isNotEqualTo(
                        RefundCommand.of(new BigDecimal("11"), "USD", "Key-A").fingerprint());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad key", "đơn", "!abc"})
    void rejectsInvalidKeys(String key) {
        assertThatThrownBy(() -> RefundCommand.of(null, "USD", key)).isInstanceOf(AppException.class);
    }

    @Test
    void validatesLimitsAndCurrency() {
        assertThat(RefundCommand.of(null, "USD", "a".repeat(100)).amount()).isNull();
        assertThatThrownBy(() -> RefundCommand.of(null, "USD", "a".repeat(101))).isInstanceOf(AppException.class);
        for (var value : new String[] {"0", "-1", "0.001", "100000000000000000"}) {
            assertThatThrownBy(() -> RefundCommand.of(new BigDecimal(value), "USD", "key"))
                    .isInstanceOf(AppException.class);
        }
        for (var currency : new String[] {null, "", "US", "123", " USD"}) {
            assertThatThrownBy(() -> RefundCommand.of(null, currency, "key")).isInstanceOf(AppException.class);
        }
    }
}
