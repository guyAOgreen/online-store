package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade behaviour of {@code 4-products} (#7) on a database that already holds customers and orders: the new tables
 * arrive empty, every existing order survives with no product associations (the legacy-order policy), and a legacy
 * order can still be given a product afterwards.
 */
class ProductUpgradeIntegrationTest extends AbstractIntegrationTest {

    private static final String BEFORE_PRODUCTS = "3-sequences";

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void leavesExistingOrdersWithoutProducts() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(BEFORE_PRODUCTS);
            db.jdbc().update("INSERT INTO customer (id, name) VALUES (101, 'Zoë O''Brien')");
            db.jdbc()
                    .update(
                            "INSERT INTO \"order\" (id, description, customer_id) VALUES (10001, 'Extra order for Muriel', 1)");
            db.jdbc()
                    .update("INSERT INTO \"order\" (id, description, customer_id) VALUES (10002, 'Zoë''s order', 101)");

            db.liquibase().migrateToLatest();

            assertThat(db.liquibase().unrunChangeSetIds()).isEmpty();
            assertThat(count(db, "customer")).isEqualTo(101);
            assertThat(count(db, "\"order\"")).isEqualTo(10_002);
            assertThat(count(db, "product")).isZero();
            assertThat(count(db, "order_product")).isZero();
            assertThat(db.jdbc()
                            .queryForObject(
                                    "SELECT COUNT(*) FROM \"order\" o"
                                            + " WHERE EXISTS (SELECT 1 FROM order_product op WHERE op.order_id = o.id)",
                                    Long.class))
                    .as("no association is invented for a pre-existing order")
                    .isZero();
        }
    }

    @Test
    void legacyOrdersCanBeGivenProductsAfterTheUpgrade() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateTo(BEFORE_PRODUCTS);
            db.jdbc().update("INSERT INTO \"order\" (id, description, customer_id) VALUES (10001, 'Legacy order', 1)");

            db.liquibase().migrateToLatest();
            long product = db.jdbc()
                    .queryForObject(
                            "INSERT INTO product (description) VALUES ('First product') RETURNING id", Long.class);
            db.jdbc().update("INSERT INTO order_product (order_id, product_id) VALUES (10001, ?)", product);

            assertThat(product)
                    .as("the product sequence starts at one; nothing is seeded")
                    .isEqualTo(1L);
            assertThat(db.jdbc()
                            .queryForObject("SELECT product_id FROM order_product WHERE order_id = 10001", Long.class))
                    .isEqualTo(product);
        }
    }

    @Test
    void freshInstallHasEmptyProductTables() {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres)) {
            db.liquibase().migrateToLatest();

            assertThat(count(db, "product")).isZero();
            assertThat(count(db, "order_product")).isZero();
            assertThat(count(db, "\"order\"")).isEqualTo(10_000);
        }
    }

    private static long count(UpgradeDatabase db, String table) {
        return db.jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
