package com.shop.order.internal.mapper;

import com.shop.order.internal.dto.response.CartItemResponse;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.entity.Cart;
import com.shop.order.internal.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CartMapper {

    @Mapping(target = "distinctItemCount", expression = "java(cart.getItems().size())")
    CartResponse toResponse(Cart cart);

    CartItemResponse toItemResponse(CartItem item);
}
