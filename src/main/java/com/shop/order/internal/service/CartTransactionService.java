package com.shop.order.internal.service;

import com.shop.catalog.order.CatalogCartItemReference;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.entity.Cart;
import com.shop.order.internal.entity.CartItem;
import com.shop.order.internal.entity.CartLimitExceededException;
import com.shop.order.internal.mapper.CartMapper;
import com.shop.order.internal.repository.CartRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartTransactionService {

    CartRepository cartRepository;
    CartMapper mapper;

    @Transactional(readOnly = true)
    public CartResponse getCart(String ownerSubject) {
        return mapper.toResponse(getDetailedCart(ownerSubject));
    }

    @Transactional(readOnly = true)
    public String getOwnedItemSku(String ownerSubject, UUID itemId) {
        return requireOwnedItem(getDetailedCart(ownerSubject), itemId).getSku();
    }

    @Transactional
    public CartResponse addItem(String ownerSubject, CatalogCartItemReference reference, int quantity) {
        return mutate(
                ownerSubject, cart -> cart.addOrIncrement(reference.productVariantId(), reference.sku(), quantity));
    }

    @Transactional
    public CartResponse updateItem(String ownerSubject, UUID itemId, int quantity) {
        return mutate(ownerSubject, cart -> {
            requireOwnedItem(cart, itemId);
            cart.updateQuantity(itemId, quantity);
        });
    }

    @Transactional
    public CartResponse removeItem(String ownerSubject, UUID itemId) {
        return mutate(ownerSubject, cart -> {
            requireOwnedItem(cart, itemId);
            cart.removeItem(itemId);
        });
    }

    @Transactional
    public CartResponse clear(String ownerSubject) {
        return mutate(ownerSubject, Cart::clear);
    }

    private CartResponse mutate(String ownerSubject, CartMutation mutation) {
        try {
            Cart cart = getLockedCart(ownerSubject);
            mutation.apply(cart);
            return mapper.toResponse(cartRepository.saveAndFlush(cart));
        } catch (CartLimitExceededException exception) {
            ErrorCode errorCode = exception.getLimitType() == CartLimitExceededException.LimitType.DISTINCT_ITEMS
                    ? ErrorCode.CART_ITEM_LIMIT_EXCEEDED
                    : ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED;
            throw new AppException(errorCode);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CART_CONFLICT);
        } catch (PessimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CART_UNAVAILABLE);
        }
    }

    private Cart getDetailedCart(String ownerSubject) {
        return cartRepository
                .findDetailedByOwnerSubject(ownerSubject)
                .orElseThrow(() -> new AppException(ErrorCode.CART_UNAVAILABLE));
    }

    private Cart getLockedCart(String ownerSubject) {
        Cart cart = cartRepository
                .findByOwnerSubjectForUpdate(ownerSubject)
                .orElseThrow(() -> new AppException(ErrorCode.CART_UNAVAILABLE));
        cart.getItems().size();
        return cart;
    }

    private CartItem requireOwnedItem(Cart cart, UUID itemId) {
        return cart.findItem(itemId).orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    @FunctionalInterface
    private interface CartMutation {
        void apply(Cart cart);
    }
}
