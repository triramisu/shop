package com.shop.payment.internal.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shop.payment.internal.refund.configuration.RefundProperties;
import com.shop.payment.internal.refund.dto.CreateRefundRequest;
import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.internal.refund.entity.RefundStatus;
import com.shop.payment.provider.RefundProviderOperations;
import com.shop.payment.provider.RefundProviderRequest;
import com.shop.payment.provider.RefundProviderStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RefundServiceTests {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledByDefaultAndDoesNotReserveOrCallProvider() {
        var transactions = mock(RefundTransactionService.class);
        var provider = mock(RefundProviderOperations.class);
        var service = new RefundService(new RefundProperties(), transactions, List.of(provider));
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "key", new CreateRefundRequest(null, "USD")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_REFUND_UNAVAILABLE));
        verifyNoInteractions(transactions, provider);
    }

    @Test
    void unexpectedProviderExceptionKeepsUnknownAndNeverExposesRawMessage() {
        var transactions = mock(RefundTransactionService.class);
        var provider = mock(RefundProviderOperations.class);
        var service = service(transactions, provider);
        var prepared = prepared();
        when(transactions.prepare(eq(prepared.snapshot().paymentAttemptId()), any(), eq("unit-admin"), any()))
                .thenReturn(prepared);
        when(provider.refund(any())).thenThrow(new IllegalStateException("raw-secret-must-not-be-exposed"));
        when(transactions.apply(any(), any(), any())).thenAnswer(call -> {
            com.shop.payment.provider.RefundProviderResult result = call.getArgument(2);
            assertThat(result.status()).isEqualTo(RefundProviderStatus.UNKNOWN);
            assertThat(result.failureCode()).isNull();
            return prepared.snapshot();
        });
        assertThat(service.create(prepared.snapshot().paymentAttemptId(), "key", new CreateRefundRequest(null, "USD")))
                .isEqualTo(prepared.snapshot());
        verify(provider).refund(any());
    }

    @Test
    void databaseFailureIsSanitizedAndDoesNotCallProvider() {
        var transactions = mock(RefundTransactionService.class);
        var provider = mock(RefundProviderOperations.class);
        var service = service(transactions, provider);
        when(transactions.prepare(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("internal-db-detail"));
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "key", new CreateRefundRequest(null, "USD")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_REFUND_UNAVAILABLE));
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.never()).refund(any());
    }

    private RefundService service(RefundTransactionService transactions, RefundProviderOperations provider) {
        var properties = new RefundProperties();
        properties.setEnabled(true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "unit-admin", "unused", List.of(new SimpleGrantedAuthority("PAYMENT_REFUND"))));
        when(provider.providerCode()).thenReturn("FAKE");
        return new RefundService(properties, transactions, List.of(provider));
    }

    private PreparedRefund prepared() {
        var snapshot = new RefundResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "USD",
                RefundStatus.CREATED,
                null,
                Instant.now(),
                Instant.now());
        return new PreparedRefund(
                snapshot,
                "FAKE",
                new RefundProviderRequest(
                        snapshot.id(),
                        snapshot.paymentAttemptId(),
                        snapshot.orderId(),
                        "fake_payment",
                        snapshot.amount(),
                        "USD",
                        "refund-" + snapshot.id()));
    }
}
