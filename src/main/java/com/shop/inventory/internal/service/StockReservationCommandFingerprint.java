package com.shop.inventory.internal.service;

import com.shop.inventory.internal.entity.StockReservationOperation;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.ReserveStockCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class StockReservationCommandFingerprint {

    String reserve(ReserveStockCommand command) {
        return hash(String.join(
                "|",
                StockReservationOperation.RESERVE.name(),
                command.reservationId().toString(),
                command.stockItemId().toString(),
                Long.toString(command.quantity()),
                Long.toString(command.expiresAt().getEpochSecond()),
                Integer.toString(command.expiresAt().getNano())));
    }

    String confirm(ConfirmStockReservationCommand command) {
        return hash(StockReservationOperation.CONFIRM.name() + "|" + command.reservationId());
    }

    String release(ReleaseStockReservationCommand command) {
        return hash(StockReservationOperation.RELEASE.name() + "|" + command.reservationId());
    }

    private String hash(String canonicalPayload) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
