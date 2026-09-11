package com.trackingaggregator.integration;

import com.trackingaggregator.support.CourierApiTestResource;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the Flyway-managed schema matches what the code assumes at runtime.
 *
 * <p>This is also the suite's canary: if the Testcontainers Postgres, the config
 * overrides from {@link CourierApiTestResource} or the entity/schema validation are
 * wrong, this class fails first and cheapest.
 *
 * <p>Entity-vs-migration drift is additionally caught for free by
 * {@code quarkus.hibernate-orm.schema-management.strategy: validate} in the test config -
 * the whole suite refuses to boot if someone adds a field without a migration.
 */
@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class FlywaySchemaTest {

    @Inject
    DataSource dataSource;

    private List<String> queryStrings(String sql) {
        List<String> values = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
        return values;
    }

    private long queryCount(String sql) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
    }

    @Test
    void bothTablesExist() {
        assertThat(queryStrings("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                """))
                .contains("tracking_query", "tracking_cache");
    }

    @Test
    @DisplayName("the UNIQUE constraint that ON CONFLICT targets exists")
    void uniqueConstraintOnTrackingNumberAndCourierExists() {
        // TrackingCacheService.put issues
        //   INSERT ... ON CONFLICT (tracking_number, courier) DO UPDATE ...
        // Postgres resolves that against a UNIQUE constraint/index on exactly those
        // columns. Drop the constraint and the query fails only at runtime, with
        // "there is no unique or exclusion constraint matching the ON CONFLICT
        // specification" - a production-only failure this catches at build time.
        List<String> columns = queryStrings("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_name = 'tracking_cache'
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY kcu.ordinal_position
                """);

        assertThat(columns).containsExactlyInAnyOrder("tracking_number", "courier");
    }

    @Test
    void lookupIndexExists() {
        assertThat(queryStrings("SELECT indexname FROM pg_indexes WHERE tablename = 'tracking_cache'"))
                .contains("idx_tracking_cache_lookup");
    }

    @Test
    void bothMigrationsAppliedSuccessfully() {
        assertThat(queryCount("SELECT COUNT(*) FROM flyway_schema_history WHERE success = true"))
                .isEqualTo(2L);
    }
}
