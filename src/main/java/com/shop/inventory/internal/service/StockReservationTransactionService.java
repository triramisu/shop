package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationTransactionService {

    static final String RESERVATION_REASON = "Giữ tồn kho";

    StockItemRepository stockItemRepository;
    StockReservationRepository stockReservationRepository;
    StockMovementRecorder movementRecorder;
    PlatformTransactionManager transactionManager;
    InventoryReservationProperties properties;

    public StockReservationResult reserve(ReserveStockCommand command, Instant issuedAt) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(
                Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
        return Objects.requireNonNull(
                transaction.execute(status -> reserveInTransaction(command, issuedAt)),
                "reservation transaction returned no result");
    }

    private StockReservationResult reserveInTransaction(ReserveStockCommand command, Instant issuedAt) {
        if (stockReservationRepository.existsById(command.reservationId())) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_ALREADY_EXISTS);
        }

        int updatedRows = stockItemRepository.reserveIfAvailable(command.stockItemId(), command.quantity(), issuedAt);
        if (updatedRows == 0) {
            if (!stockItemRepository.existsById(command.stockItemId())) {
                throw new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND);
            }
            throw new AppException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK);
        }

        StockItem stockItem = stockItemRepository
                .findById(command.stockItemId())
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        StockReservation reservation;
        try {
            reservation = stockReservationRepository.saveAndFlush(StockReservation.issue(
                    command.reservationId(), stockItem, command.quantity(), command.expiresAt(), issuedAt));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_ALREADY_EXISTS);
        }
        movementRecorder.record(
                stockItem,
                StockMovementType.RESERVATION,
                0,
                command.quantity(),
                RESERVATION_REASON,
                command.reservationId().toString(),
                issuedAt);
        return new StockReservationResult(
                reservation.getId(),
                stockItem.getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                stockItem.getOnHand(),
                stockItem.getReserved(),
                stockItem.getAvailable());
    }
}
