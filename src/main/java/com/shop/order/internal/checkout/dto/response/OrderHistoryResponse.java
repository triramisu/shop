package com.shop.order.internal.checkout.dto.response;

import java.util.List;

public record OrderHistoryResponse(
        List<OrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {}
