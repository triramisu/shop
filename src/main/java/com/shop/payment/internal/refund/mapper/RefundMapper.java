package com.shop.payment.internal.refund.mapper;

import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.internal.refund.entity.RefundAttempt;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefundMapper {
    RefundResponse toResponse(RefundAttempt refund);
}
