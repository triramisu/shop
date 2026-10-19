package com.shop.inventory.internal.mapper;

import com.shop.inventory.internal.dto.response.StockItemPageResponse;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.dto.response.StockMovementPageResponse;
import com.shop.inventory.internal.dto.response.StockMovementResponse;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InventoryMapper {

    @Mapping(target = "available", expression = "java(stockItem.getAvailable())")
    StockItemResponse toStockItemResponse(StockItem stockItem);

    StockMovementResponse toStockMovementResponse(StockMovement movement);

    default StockItemPageResponse toStockItemPageResponse(Page<StockItem> stockItems) {
        return StockItemPageResponse.builder()
                .content(stockItems.getContent().stream()
                        .map(this::toStockItemResponse)
                        .toList())
                .page(stockItems.getNumber())
                .size(stockItems.getSize())
                .totalElements(stockItems.getTotalElements())
                .totalPages(stockItems.getTotalPages())
                .first(stockItems.isFirst())
                .last(stockItems.isLast())
                .build();
    }

    default StockMovementPageResponse toStockMovementPageResponse(Page<StockMovement> movements) {
        return StockMovementPageResponse.builder()
                .content(movements.getContent().stream()
                        .map(this::toStockMovementResponse)
                        .toList())
                .page(movements.getNumber())
                .size(movements.getSize())
                .totalElements(movements.getTotalElements())
                .totalPages(movements.getTotalPages())
                .first(movements.isFirst())
                .last(movements.isLast())
                .build();
    }
}
