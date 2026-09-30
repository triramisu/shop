package com.shop.inventory.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.entity.StockReservationStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryRepositoryIntegrationTests {

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsStockMovementAndReservationWithExplicitAuditFields() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "REPOSITORY-SKU-01", "WAREHOUSE_01", 20));
        stockItem.reserve(3);
        stockItemRepository.saveAndFlush(stockItem);
        Instant now = Instant.now();
        StockMovement movement = stockMovementRepository.saveAndFlush(StockMovement.record(
                stockItem, StockMovementType.RESERVATION, 0, 3, "Giữ hàng", "ORDER-REPOSITORY", now));
        UUID reservationId = UUID.randomUUID();
        stockReservationRepository.saveAndFlush(
                StockReservation.issue(reservationId, stockItem, 3, now.plusSeconds(300), now));

        entityManager.clear();

        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        StockReservation reservation =
                stockReservationRepository.findById(reservationId).orElseThrow();
        assertThat(reloaded.getOnHand()).isEqualTo(20);
        assertThat(reloaded.getReserved()).isEqualTo(3);
        assertThat(reloaded.getAvailable()).isEqualTo(17);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(movement.getId()).isNotNull();
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .singleElement()
                .extracting(StockMovement::getReferenceId)
                .isEqualTo("ORDER-REPOSITORY");
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.RESERVED);
    }

    @Test
    void enforcesUniqueSkuAndVariantPerLocationInTheDatabase() {
        UUID variantId = UUID.randomUUID();
        stockItemRepository.saveAndFlush(StockItem.create(variantId, "UNIQUE-SKU-01", "WAREHOUSE_01", 1));

        assertThatThrownBy(() -> stockItemRepository.saveAndFlush(
                        StockItem.create(variantId, "UNIQUE-SKU-01", "warehouse_01", 2)))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }
}
