package com.shop.order.internal.service;

import com.shop.catalog.order.CatalogCartItemLookup;
import com.shop.catalog.order.CatalogCartItemReference;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.request.UpdateCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartService {

    CartInitializationService initializationService;
    CartTransactionService transactionService;
    CatalogCartItemLookup catalogCartItemLookup;

    public CartResponse getCart(String ownerSubject) {
        String owner = normalizeOwner(ownerSubject);
        ensureCartExists(owner);
        return transactionService.getCart(owner);
    }

    public CartResponse addItem(String ownerSubject, AddCartItemRequest request) {
        String owner = normalizeOwner(ownerSubject);
        CatalogCartItemReference reference = catalogCartItemLookup
                .findSellableBySku(request.getSku())
                .orElseThrow(() -> new AppException(ErrorCode.CART_SKU_NOT_FOUND));
        ensureCartExists(owner);
        return transactionService.addItem(owner, reference, request.getQuantity());
    }

    public CartResponse updateItem(String ownerSubject, UUID itemId, UpdateCartItemRequest request) {
        String owner = normalizeOwner(ownerSubject);
        ensureCartExists(owner);
        String sku = transactionService.getOwnedItemSku(owner, itemId);
        catalogCartItemLookup.findSellableBySku(sku).orElseThrow(() -> new AppException(ErrorCode.CART_SKU_NOT_FOUND));
        return transactionService.updateItem(owner, itemId, request.getQuantity());
    }

    public CartResponse removeItem(String ownerSubject, UUID itemId) {
        String owner = normalizeOwner(ownerSubject);
        ensureCartExists(owner);
        return transactionService.removeItem(owner, itemId);
    }

    public CartResponse clear(String ownerSubject) {
        String owner = normalizeOwner(ownerSubject);
        ensureCartExists(owner);
        return transactionService.clear(owner);
    }

    private void ensureCartExists(String ownerSubject) {
        try {
            initializationService.createIfMissing(ownerSubject);
        } catch (DataIntegrityViolationException exception) {
            // Another request committed the same owner cart first; the unique owner constraint is authoritative.
        }
    }

    private String normalizeOwner(String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return ownerSubject.strip().toLowerCase(Locale.ROOT);
    }
}
