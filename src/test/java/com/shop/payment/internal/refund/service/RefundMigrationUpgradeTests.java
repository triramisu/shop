package com.shop.payment.internal.refund.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RefundMigrationUpgradeTests {
    @Test
    void upgradesV24OnH2WithoutChangingCapturedPaymentsOrRevokingUnaffectedUsers() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:refund_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target("24")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        RefundMigrationFixtures.seedV24(jdbc);
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration", "classpath:db/h2")
                .load()
                .migrate();
        RefundMigrationFixtures.assertUpgraded(jdbc);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thanh_toan_ngan_sach_hoan", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thanh_toan_lan_hoan", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='25' AND success=TRUE",
                        Integer.class))
                .isEqualTo(1);
    }
}
