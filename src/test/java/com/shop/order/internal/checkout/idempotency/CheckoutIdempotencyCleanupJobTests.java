package com.shop.order.internal.checkout.idempotency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class CheckoutIdempotencyCleanupJobTests {

    @Test
    void purgesExpiredRecordsAndContainsSchedulerFailures() {
        CheckoutIdempotencyStateService stateService = mock(CheckoutIdempotencyStateService.class);
        CheckoutIdempotencyCleanupJob job = new CheckoutIdempotencyCleanupJob(stateService);
        when(stateService.purgeExpiredTerminal(any())).thenReturn(2);

        job.purgeExpiredRecords();

        verify(stateService).purgeExpiredTerminal(any());

        when(stateService.purgeExpiredTerminal(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(job::purgeExpiredRecords).doesNotThrowAnyException();
    }
}
