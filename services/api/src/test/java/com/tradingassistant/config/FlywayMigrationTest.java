package com.tradingassistant.config;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void appliesMultiMarketFoundationAndBackfillsLegacyAShares() throws Exception {
        String url = "jdbc:h2:mem:flyway-v6;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("5").load();
        flyway.migrate();

        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.prepareStatement("""
                    insert into users(id,email,display_name,password_hash,role,status,created_at)
                    values (?,?,?,?,?,?,?)
                    """)) {
                statement.setObject(1, userId);
                statement.setString(2, "migration@example.com");
                statement.setString(3, "迁移用户");
                statement.setString(4, "hash");
                statement.setString(5, "USER");
                statement.setString(6, "ACTIVE");
                statement.setObject(7, Instant.parse("2026-01-01T16:30:00Z"));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    insert into securities(instrument_id,exchange,code,name,asset_type,status,source,
                        source_updated_at,last_seen_at,created_at,updated_at)
                    values ('SSE:600000','SSE','600000','浦发银行','STOCK','ACTIVE','AKSHARE',?,?,?,?)
                    """)) {
                for (int index = 1; index <= 4; index++) statement.setObject(index, Instant.parse("2026-01-02T00:00:00Z"));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    insert into portfolio_items(id,user_id,exchange,symbol,asset_type,display_name,quantity,
                        cost_price,sort_order,created_at,updated_at)
                    values (?,?,'SSE','600000','STOCK','浦发银行',100,10,0,?,?)
                    """)) {
                statement.setObject(1, positionId);
                statement.setObject(2, userId);
                statement.setObject(3, Instant.parse("2026-01-01T16:30:00Z"));
                statement.setObject(4, Instant.parse("2026-01-01T16:30:00Z"));
                statement.executeUpdate();
            }
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        select market,currency,opened_on from portfolio_items where id=?
                        """)) {
            statement.setObject(1, positionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("market")).isEqualTo("A_SHARE");
                assertThat(result.getString("currency")).isEqualTo("CNY");
                assertThat(result.getDate("opened_on").toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 2));
            }
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        select count(*) from information_schema.tables where table_name in
                        ('MARKET_SESSIONS','MARKET_SYNC_RUNS','POSITION_DAILY_BASELINES','FUND_NAV_QUOTES')
                        """)) {
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }

    @Test
    void refusesToGuessUnknownLegacyExchange() throws Exception {
        String url = "jdbc:h2:mem:flyway-invalid-exchange;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        insert into securities(instrument_id,exchange,code,name,asset_type,status,source,
                            source_updated_at,last_seen_at,created_at,updated_at)
                        values ('UNKNOWN:ABC','UNKNOWN','ABC','未知证券','STOCK','ACTIVE','TEST',?,?,?,?)
                        """)) {
            for (int index = 1; index <= 4; index++) statement.setObject(index, Instant.now());
            statement.executeUpdate();
        }

        assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
                .isInstanceOf(FlywayException.class);
    }
}
