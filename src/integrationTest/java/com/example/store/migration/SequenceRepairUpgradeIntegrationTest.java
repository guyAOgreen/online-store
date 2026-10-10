package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrade behaviour of the {@code 3-sequences} repair (#3) on databases that already hold the original seed data: it
 * must move each sequence past the stored IDs, never backwards, and leave existing rows alone.
 */
class SequenceRepairUpgradeIntegrationTest extends AbstractIntegrationTest {

    private static final String BEFORE_REPAIR = "2-data";

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void seedDataAloneLeavesSequencesBehindTheRows() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(BEFORE_REPAIR);

            assertThatThrownBy(() -> insertCustomer(db, "Collides with ID 1"))
                    .as("documents the defect the repair fixes")
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Test
    void advancesSequencesPastRowsInsertedWithExplicitIds() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(BEFORE_REPAIR);
            db.jdbc().update("INSERT INTO customer (id, name) VALUES (150, 'Explicit ID')");
            db.jdbc()
                    .update(
                            "INSERT INTO \"order\" (id, description, customer_id) VALUES (10050, 'Explicit order', 150)");

            db.liquibase().migrateToLatest();

            assertThat(insertCustomer(db, "Generated")).isEqualTo(151L);
            assertThat(insertOrder(db, "Generated order", 150)).isEqualTo(10_051L);
            assertThat(db.jdbc().queryForObject("SELECT name FROM customer WHERE id = 150", String.class))
                    .isEqualTo("Explicit ID");
            assertThat(db.jdbc().queryForObject("SELECT customer_id FROM \"order\" WHERE id = 10050", Long.class))
                    .isEqualTo(150L);
            assertThat(count(db, "customer")).isEqualTo(102);
            assertThat(count(db, "\"order\"")).isEqualTo(10_002);
        }
    }

    @Test
    void doesNotMoveAnAlreadyAdvancedSequenceBackwards() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(BEFORE_REPAIR);
            db.jdbc().execute("SELECT setval('customer_id_seq', 500)");
            db.jdbc().execute("SELECT setval('order_id_seq', 20000)");

            db.liquibase().migrateToLatest();

            assertThat(insertCustomer(db, "After advanced sequence")).isEqualTo(501L);
            assertThat(insertOrder(db, "After advanced sequence", 1)).isEqualTo(20_001L);
        }
    }

    private static long insertCustomer(UpgradeDatabase db, String name) {
        return db.jdbc().queryForObject("INSERT INTO customer (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private static long insertOrder(UpgradeDatabase db, String description, long customerId) {
        return db.jdbc()
                .queryForObject(
                        "INSERT INTO \"order\" (description, customer_id) VALUES (?, ?) RETURNING id",
                        Long.class,
                        description,
                        customerId);
    }

    private static long count(UpgradeDatabase db, String table) {
        return db.jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
