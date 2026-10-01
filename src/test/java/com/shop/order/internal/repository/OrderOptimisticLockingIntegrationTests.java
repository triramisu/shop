package com.shop.order.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.entity.CustomerOrder;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OrderOptimisticLockingIntegrationTests {

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanCommittedOrderData() {
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang");
    }

    @Test
    void rejectsAStaleDetachedAggregateInsteadOfOverwritingTheCommittedState() {
        CustomerOrder created = orderRepository.saveAndFlush(
                CustomerOrder.createPending("concurrent-owner", Instant.now().minusSeconds(10)));
        CustomerOrder firstWriter = loadInIndependentTransaction(created.getId());
        CustomerOrder staleWriter = loadInIndependentTransaction(created.getId());
        Instant now = Instant.now();
        firstWriter.transition(OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM, now);
        staleWriter.transition(OrderTransitionEvent.PAYMENT_EXPIRED, OrderTransitionActor.SYSTEM, now);

        saveInIndependentTransaction(firstWriter);

        assertThatThrownBy(() -> saveInIndependentTransaction(staleWriter))
                .isInstanceOf(OptimisticLockingFailureException.class);
        CustomerOrder reloaded = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    private CustomerOrder loadInIndependentTransaction(UUID orderId) {
        return transactionTemplate().execute(status -> entityManager.find(CustomerOrder.class, orderId));
    }

    private void saveInIndependentTransaction(CustomerOrder order) {
        transactionTemplate().executeWithoutResult(status -> orderRepository.saveAndFlush(order));
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
