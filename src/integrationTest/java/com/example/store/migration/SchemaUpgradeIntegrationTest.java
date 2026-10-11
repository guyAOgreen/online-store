package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrade path: stop at an earlier changeset, add representative rows as a production database would hold, then apply
 * the remaining migrations and check the rows survived. The generic check here is that rows and the foreign key are
 * preserved; feature-specific upgrade behaviour (such as the #3 sequence repair in
 * {@link SequenceRepairUpgradeIntegrationTest}) gets its own test on this harness.
 */
class SchemaUpgradeIntegrationTest extends AbstractIntegrationTest {

    private static final String LAST_RELEASED_CHANGESET = "2-data";

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void preservesExistingRowsAcrossLaterMigrations() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(LAST_RELEASED_CHANGESET);
            db.jdbc().update("INSERT INTO customer (id, name) VALUES (101, 'Zoë O''Brien')");
            db.jdbc().update("INSERT INTO customer (id, name) VALUES (102, 'Never Ordered')");
            db.jdbc()
                    .update(
                            "INSERT INTO \"order\" (id, description, customer_id) VALUES (10001, 'Extra order for Muriel', 1)");
            db.jdbc()
                    .update("INSERT INTO \"order\" (id, description, customer_id) VALUES (10002, 'Zoë''s order', 101)");

            db.liquibase().migrateToLatest();

            assertThat(db.liquibase().unrunChangeSetIds()).isEmpty();
            assertThat(count(db, "customer")).isEqualTo(102);
            assertThat(count(db, "\"order\"")).isEqualTo(10_002);
            assertThat(db.jdbc().queryForObject("SELECT name FROM customer WHERE id = 101", String.class))
                    .isEqualTo("Zoë O'Brien");
            assertThat(db.jdbc().queryForObject("SELECT customer_id FROM \"order\" WHERE id = 10002", Long.class))
                    .isEqualTo(101L);
            assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM \"order\" WHERE customer_id = 102", Long.class))
                    .isZero();
            assertThatThrownBy(
                            () -> db.jdbc()
                                    .update(
                                            "INSERT INTO \"order\" (id, description, customer_id) VALUES (10003, 'Orphan', 999999)"))
                    .as("foreign key survives the upgrade")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void migratesFromEmptyToLatest() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateToLatest();

            assertThat(db.liquibase().unrunChangeSetIds()).isEmpty();
            assertThat(count(db, "customer")).isEqualTo(100);
            assertThat(count(db, "\"order\"")).isEqualTo(10_000);
        }
    }

    @Test
    void migrateToStopsAtTheNamedChangeSet() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo("1-schema");

            assertThat(count(db, "customer")).isZero();
            assertThat(db.liquibase().unrunChangeSetIds()).containsExactly("2-data", "3-sequences", "4-products");
        }
    }

    @Test
    void migrateToRejectsUnknownChangeSet() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            assertThatThrownBy(() -> db.liquibase().migrateTo("does-not-exist"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does-not-exist");
        }
    }

    @Test
    void migrateToRejectsGoingBackwards() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateToLatest();

            assertThatThrownBy(() -> db.liquibase().migrateTo("1-schema"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already past");
        }
    }

    @Test
    void dropsItsDatabaseOnClose() {
        String name;
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            name = db.name();
            assertThat(databaseExists(name)).isTrue();
        }
        assertThat(databaseExists(name)).isFalse();
    }

    private static long count(UpgradeDatabase db, String table) {
        return db.jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private boolean databaseExists(String name) {
        Integer found =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, name);
        return found == 1;
    }
}
