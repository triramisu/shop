package com.shop.order.internal.checkout.mapper;

import com.shop.order.internal.checkout.dto.response.OrderItemSnapshotResponse;
import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.entity.OrderItemSnapshot;
import org.springframework.stereotype.Component;

@Component
public class OrderSnapshotResponseMapper {

    public OrderSnapshotResponse toResponse(CustomerOrder order) {
        return OrderSnapshotResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .version(order.getVersion())
                .createdAt(order.getCreatedAt())
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount())
                .tax(order.getTax())
                .grandTotal(order.getGrandTotal())
                .items(order.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    private OrderItemSnapshotResponse toItemResponse(OrderItemSnapshot item) {
        return OrderItemSnapshotResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariantId())
                .lineNumber(item.getLineNumber())
                .sku(item.getSku())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .discount(item.getDiscount())
                .tax(item.getTax())
                .total(item.getTotal())
                .currency(item.getCurrency())
                .build();
    }
}
