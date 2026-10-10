package com.example.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCleanerIntegrationTest extends AbstractIntegrationTest {

    private static final long SEEDED_CUSTOMERS = 100;
    private static final long SEEDED_ORDERS = 10_000;

    @Autowired
    private DatabaseCleaner databaseCleaner;

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
    void cleansEveryTableInTheSchema() {
        // Guards against adding a table (an entity or a Liquibase-only join table) without
        // teaching the cleaner about it.
        List<String> schemaTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_name NOT LIKE 'databasechangelog%'",
                String.class);

        assertThat(databaseCleaner.cleanedTables())
                .map(table -> table.replace("\"", ""))
                .containsExactlyInAnyOrderElementsOf(schemaTables);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
