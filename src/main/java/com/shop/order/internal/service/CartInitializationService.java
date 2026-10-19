package com.shop.order.internal.service;

import com.shop.order.internal.entity.Cart;
import com.shop.order.internal.repository.CartRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartInitializationService {

    CartRepository cartRepository;

    @Transactional
    public void createIfMissing(String ownerSubject) {
        if (!cartRepository.existsByOwnerSubject(ownerSubject)) {
            cartRepository.saveAndFlush(Cart.create(ownerSubject));
        }
    }
}
