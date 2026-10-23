package com.shop.payment.internal.reconciliation;

import org.springframework.test.context.TestPropertySource;

// H2 maps Instant through TIMESTAMP WITH TIME ZONE; normalize its session before
// converting to the common TIMESTAMP(6) schema, matching Hibernate/JDBC UTC on MySQL.
@TestPropertySource(properties = "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'")
class ReconciliationIntegrationTests extends ReconciliationIntegrationSupport {}
