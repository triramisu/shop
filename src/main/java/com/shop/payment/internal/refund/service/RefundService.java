package com.shop.payment.internal.refund.service;

import com.shop.payment.internal.refund.configuration.RefundProperties;
import com.shop.payment.internal.refund.constant.RefundConstants;
import com.shop.payment.internal.refund.dto.CreateRefundRequest;
import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.provider.RefundProviderOperations;
import com.shop.payment.provider.RefundProviderResult;
import com.shop.payment.provider.RefundProviderStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + RefundConstants.AUTHORITY + "')")
public class RefundService {
    private final RefundProperties properties;
    private final RefundTransactionService transactions;
    private final List<RefundProviderOperations> providers;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponse create(UUID paymentId, String key, CreateRefundRequest request) {
        if (!properties.isEnabled()) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_UNAVAILABLE);
        }
        RefundCommand command = RefundCommand.of(request.amount(), request.currency(), key);
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            PreparedRefund prepared = transactions.prepare(
                    paymentId,
                    command,
                    actor,
                    providers.stream()
                            .map(RefundProviderOperations::providerCode)
                            .collect(Collectors.toSet()));
            if (prepared.request() == null) {
                return prepared.snapshot();
            }
            RefundProviderResult result;
            try {
                RefundProviderOperations provider = providers.stream()
                        .filter(p -> p.providerCode().equals(prepared.providerCode()))
                        .findFirst()
                        .orElseThrow();
                result = Objects.requireNonNull(provider.refund(prepared.request()));
            } catch (RuntimeException exception) {
                // Không có kết quả chắc chắn: giữ ngân sách để đối soát, không retry mutation.
                log.warn(
                        "Refund outcome is uncertain; refundId={} provider={}",
                        prepared.snapshot().id(),
                        prepared.providerCode());
                result = new RefundProviderResult(RefundProviderStatus.UNKNOWN, null, null);
            }
            return transactions.apply(paymentId, prepared.snapshot().id(), result);
        } catch (DataAccessException exception) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_UNAVAILABLE);
        }
    }

    public RefundResponse get(UUID paymentId, UUID refundId) {
        return transactions.get(paymentId, refundId);
    }
}
