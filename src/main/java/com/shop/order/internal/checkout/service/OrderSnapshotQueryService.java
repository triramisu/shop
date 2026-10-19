package com.shop.order.internal.checkout.service;

import static com.shop.order.internal.constant.OrderQueryConstants.HISTORY_SORT;
import static com.shop.order.internal.constant.OrderQueryConstants.MAX_PAGE;
import static com.shop.order.internal.constant.OrderQueryConstants.MAX_PAGE_SIZE;

import com.shop.order.internal.checkout.dto.response.OrderHistoryResponse;
import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.checkout.mapper.OrderSnapshotResponseMapper;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderSnapshotQueryService {

    CustomerOrderRepository orderRepository;
    OrderSnapshotResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public OrderHistoryResponse getOwnedOrders(String ownerSubject, int page, int size) {
        String owner = normalizeOwner(ownerSubject);
        if (page < 0 || page > MAX_PAGE) {
            throw new AppException(ErrorCode.PAGE_NUMBER_INVALID);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.PAGE_SIZE_INVALID);
        }
        var ids = orderRepository.findOwnedOrderIds(owner, PageRequest.of(page, size));
        if (ids.isEmpty()) {
            return new OrderHistoryResponse(
                    List.of(),
                    page,
                    size,
                    ids.getTotalElements(),
                    ids.getTotalPages(),
                    ids.isFirst(),
                    ids.isLast(),
                    HISTORY_SORT);
        }
        var summaries = orderRepository.findOwnedOrdersWithItems(owner, ids.getContent()).stream()
                .map(responseMapper::toSummary)
                .collect(Collectors.toMap(summary -> summary.id(), Function.identity()));
        return new OrderHistoryResponse(
                ids.getContent().stream().map(summaries::get).toList(),
                page,
                size,
                ids.getTotalElements(),
                ids.getTotalPages(),
                ids.isFirst(),
                ids.isLast(),
                HISTORY_SORT);
    }

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
