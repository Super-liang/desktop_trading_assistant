package com.tradingassistant.config;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {
    @Test
    void appliesUserPerformanceAndAuditMigrationFromEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:flyway-migrations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        select count(*) from information_schema.tables
                        where table_name in ('USER_OPERATION_AUDITS', 'USER_PERFORMANCE_DAILY')
                        """)) {
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }
}
