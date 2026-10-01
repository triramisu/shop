package com.shop.order.internal.checkout.service;

import com.shop.catalog.order.CatalogCheckoutItemPrice;
import com.shop.catalog.order.CatalogCheckoutPricingLookup;
import com.shop.order.internal.checkout.dto.request.CheckoutQuoteRequest;
import com.shop.order.internal.checkout.dto.response.CheckoutQuoteResponse;
import com.shop.order.internal.checkout.mapper.CheckoutQuoteMapper;
import com.shop.order.internal.checkout.pricing.CheckoutLineInput;
import com.shop.order.internal.checkout.pricing.CheckoutPricingBreakdown;
import com.shop.order.internal.checkout.pricing.CheckoutPricingCalculator;
import com.shop.order.internal.checkout.pricing.CheckoutPricingException;
import com.shop.order.internal.entity.Cart;
import com.shop.order.internal.repository.CartRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutPricingService {

    CartRepository cartRepository;
    CatalogCheckoutPricingLookup catalogPricingLookup;
    CheckoutPricingCalculator pricingCalculator;
    CheckoutQuoteMapper quoteMapper;

    @Transactional(readOnly = true)
    public CheckoutQuoteResponse quote(String ownerSubject, CheckoutQuoteRequest request) {
        String owner = normalizeOwner(ownerSubject);
        requireValidRequest(request);
        Cart cart = cartRepository
                .findDetailedByOwnerSubject(owner)
                .filter(current -> !current.getItems().isEmpty())
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_CART_EMPTY));
        if (cart.getVersion() != request.getExpectedCartVersion()) {
            throw new AppException(ErrorCode.CHECKOUT_CART_CHANGED);
        }

        Set<UUID> variantIds = cart.getItems().stream()
                .map(item -> item.getProductVariantId())
                .collect(Collectors.toUnmodifiableSet());
        Map<UUID, CatalogCheckoutItemPrice> currentPrices =
                catalogPricingLookup.findSellableByVariantIds(variantIds).stream()
                        .collect(Collectors.toUnmodifiableMap(
                                CatalogCheckoutItemPrice::productVariantId, Function.identity()));
        if (currentPrices.size() != variantIds.size()) {
            throw new AppException(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE);
        }

        var lineInputs = cart.getItems().stream()
                .map(item -> {
                    CatalogCheckoutItemPrice price = currentPrices.get(item.getProductVariantId());
                    if (price == null) {
                        throw new AppException(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE);
                    }
                    return new CheckoutLineInput(
                            item.getId(),
                            price.productVariantId(),
                            price.sku(),
                            price.name(),
                            item.getQuantity(),
                            price.unitPrice(),
                            price.currency());
                })
                .toList();

        try {
            CheckoutPricingBreakdown pricing = pricingCalculator.calculate(lineInputs);
            return quoteMapper.toResponse(cart, pricing, Instant.now());
        } catch (CheckoutPricingException exception) {
            throw new AppException(ErrorCode.CHECKOUT_CURRENCY_MISMATCH);
        }
    }

    private String normalizeOwner(String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return ownerSubject.strip().toLowerCase(Locale.ROOT);
    }

    private void requireValidRequest(CheckoutQuoteRequest request) {
        if (request == null || request.getExpectedCartVersion() == null) {
            throw new AppException(ErrorCode.CHECKOUT_CART_VERSION_REQUIRED);
        }
        if (request.getExpectedCartVersion() < 0) {
            throw new AppException(ErrorCode.CHECKOUT_CART_VERSION_INVALID);
        }
    }
}
