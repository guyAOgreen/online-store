package com.example.store;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCleanerIntegrationTest extends AbstractIntegrationTest {

    private static final long SEEDED_CUSTOMERS = 100;
    private static final long SEEDED_ORDERS = 10_000;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void deletesRowsInsertedAboveTheSeededBaseline() {
        jdbcTemplate.update("INSERT INTO customer (id, name) VALUES (9001, 'Temporary Customer')");
        jdbcTemplate.update(
                "INSERT INTO \"order\" (id, description, customer_id) VALUES (90001, 'Temporary order', 9001)");

        databaseCleaner.deleteRowsCreatedByTests();

        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void leavesSeededRowsAlone() {
        databaseCleaner.deleteRowsCreatedByTests();

        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void cleansEveryEntityTable() {
        // Guards against adding an entity (e.g. products) without teaching the cleaner about it.
        List<String> entityTables = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .map(String::toLowerCase)
                .toList();

        assertThat(databaseCleaner.cleanedTables())
                .map(table -> table.replace("\"", ""))
                .containsExactlyInAnyOrderElementsOf(entityTables);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
