package com.shop.order.internal.checkout.idempotency;

import com.shop.order.internal.checkout.dto.request.CheckoutOrderRequest;
import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutIdempotencyService {

    static final int MIN_KEY_LENGTH = 8;
    static final int MAX_KEY_LENGTH = 128;
    static final int MAX_OWNER_SUBJECT_LENGTH = 100;

    CheckoutIdempotencyStateService stateService;
    CheckoutIdempotencyHasher hasher;
    OrderCreationService orderCreationService;
    OrderSnapshotQueryService orderSnapshotQueryService;

    public OrderSnapshotResponse checkout(String ownerSubject, String idempotencyKey, CheckoutOrderRequest request) {
        String owner = normalizeOwner(ownerSubject);
        String key = validateKey(idempotencyKey);
        CheckoutOrderRequest validatedRequest = validateRequest(request);
        String keyHash = hasher.key(key);
        String requestFingerprint = hasher.request(validatedRequest);
        CheckoutIdempotencyClaim claim = claim(owner, keyHash, requestFingerprint);

        return switch (claim.action()) {
            case REPLAY -> orderSnapshotQueryService.getOwnedOrder(owner, claim.orderId());
            case FAILED -> throw new AppException(claim.failure());
            case IN_PROGRESS -> throw new AppException(ErrorCode.CHECKOUT_ALREADY_PROCESSING);
            case PROCEED -> execute(owner, validatedRequest, claim.executionId());
        };
    }

    private CheckoutIdempotencyClaim claim(String owner, String keyHash, String requestFingerprint) {
        try {
            return stateService.claim(owner, keyHash, requestFingerprint);
        } catch (CheckoutIdempotencyRaceException exception) {
            return stateService.resolveConcurrentClaim(owner, keyHash, requestFingerprint);
        }
    }

    private OrderSnapshotResponse execute(String owner, CheckoutOrderRequest request, UUID executionId) {
        UUID orderId;
        try {
            orderId = orderCreationService.createFromCart(owner, request.getExpectedCartVersion(), executionId);
        } catch (AppException exception) {
            rememberFailure(executionId, exception.getErrorCode(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            rememberFailure(executionId, ErrorCode.UNCATEGORIZED_EXCEPTION, exception);
            throw exception;
        }

        stateService.complete(executionId, orderId);
        return orderSnapshotQueryService.getOwnedOrder(owner, orderId);
    }

    private void rememberFailure(UUID executionId, ErrorCode errorCode, RuntimeException originalFailure) {
        try {
            stateService.fail(executionId, errorCode);
        } catch (RuntimeException persistenceFailure) {
            originalFailure.addSuppressed(persistenceFailure);
            log.error(
                    "Could not persist failed checkout idempotency execution: executionId={}",
                    executionId,
                    persistenceFailure);
        }
    }

    private String normalizeOwner(String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String owner = ownerSubject.strip().toLowerCase(Locale.ROOT);
        if (owner.length() > MAX_OWNER_SUBJECT_LENGTH) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return owner;
    }

    private String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AppException(ErrorCode.CHECKOUT_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (idempotencyKey.length() < MIN_KEY_LENGTH
                || idempotencyKey.length() > MAX_KEY_LENGTH
                || !idempotencyKey.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new AppException(ErrorCode.CHECKOUT_IDEMPOTENCY_KEY_INVALID);
        }
        return idempotencyKey;
    }

    private CheckoutOrderRequest validateRequest(CheckoutOrderRequest request) {
        if (request == null || request.getExpectedCartVersion() == null) {
            throw new AppException(ErrorCode.CHECKOUT_CART_VERSION_REQUIRED);
        }
        if (request.getExpectedCartVersion() < 0) {
            throw new AppException(ErrorCode.CHECKOUT_CART_VERSION_INVALID);
        }
        return request;
    }
}
