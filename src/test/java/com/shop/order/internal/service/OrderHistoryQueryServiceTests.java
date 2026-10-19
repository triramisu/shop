package com.shop.order.internal.service;

import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shop.order.internal.checkout.mapper.OrderSnapshotResponseMapper;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderHistoryQueryServiceTests {

    @Mock
    CustomerOrderRepository repository;

    OrderSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderSnapshotQueryService(repository, new OrderSnapshotResponseMapper());
    }

    @Test
    void normalizesOwnerAndRestoresDatabasePageOrderAfterBatchLoading() {
        var first = pendingOrder("history-a", Instant.parse("2026-10-16T00:00:00Z"));
        var second = pendingOrder("history-a", Instant.parse("2026-10-15T00:00:00Z"));
        var ids = List.of(first.getId(), second.getId());
        when(repository.findOwnedOrderIds("history-a", PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(ids, PageRequest.of(0, 2), 3));
        when(repository.findOwnedOrdersWithItems("history-a", ids)).thenReturn(List.of(second, first));

        var result = service.getOwnedOrders(" HISTORY-A ", 0, 2);

        assertThat(result.content()).extracting(item -> item.id()).containsExactlyElementsOf(ids);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
        assertThat(result.sort()).isEqualTo("createdAt,DESC;id,DESC");
        assertThat(result.content().getFirst().itemCount()).isEqualTo(1);
        assertThat(result.content().getFirst().totalQuantity()).isEqualTo(2);
        assertThat(result.content().getFirst().grandTotal()).isEqualByComparingTo("19.44");
        assertThat(result.content().getFirst().firstProductName()).isEqualTo("Sản phẩm kiểm thử");
    }

    @Test
    void emptyPageKeepsTotalAndDoesNotRunAnEmptyInQuery() {
        var pageable = PageRequest.of(10_000, 50);
        when(repository.findOwnedOrderIds("history-a", pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 3));
        var result = service.getOwnedOrders("history-a", 10_000, 50);
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(10_000);
        assertThat(result.size()).isEqualTo(50);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
        verify(repository, never()).findOwnedOrdersWithItems(anyString(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "-1,10,PAGE_NUMBER_INVALID",
        "10001,10,PAGE_NUMBER_INVALID",
        "0,0,PAGE_SIZE_INVALID",
        "0,51,PAGE_SIZE_INVALID"
    })
    void rejectsInvalidBoundsBeforeQuery(int page, int size, ErrorCode expected) {
        assertThatThrownBy(() -> service.getOwnedOrders("history-a", page, size))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOwnerIsUnauthenticatedForBothQueries(String owner) {
        var id = java.util.UUID.randomUUID();
        assertThatThrownBy(() -> service.getOwnedOrders(owner, 0, 10))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.getOwnedOrder(owner, id)).isInstanceOf(AppException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void detailUsesOwnerPredicateAndUniformNotFound() {
        var order = pendingOrder("history-a", Instant.now());
        when(repository.findDetailedByIdAndOwnerSubject(order.getId(), "history-a"))
                .thenReturn(Optional.of(order));
        assertThat(service.getOwnedOrder(" HISTORY-A ", order.getId()).getId()).isEqualTo(order.getId());
        when(repository.findDetailedByIdAndOwnerSubject(order.getId(), "history-b"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOwnedOrder("history-b", order.getId()))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        verify(repository, never()).findDetailedById(any());
    }
}
