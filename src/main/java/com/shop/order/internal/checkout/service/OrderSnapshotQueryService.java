package com.shop.order.internal.checkout.service;

import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.checkout.mapper.OrderSnapshotResponseMapper;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderSnapshotQueryService {

    CustomerOrderRepository orderRepository;
    OrderSnapshotResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public OrderSnapshotResponse getOwnedOrder(String ownerSubject, UUID orderId) {
        String owner = normalizeOwner(ownerSubject);
        return orderRepository
                .findDetailedByIdAndOwnerSubject(orderId, owner)
                .map(responseMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private String normalizeOwner(String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return ownerSubject.strip().toLowerCase(Locale.ROOT);
    }
}
