package com.example.store;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Puts the database back to the Liquibase-seeded state after each test. Once the migrations have run it copies every
 * table into a {@code test_baseline} schema; restoring truncates the real tables and reloads them from that copy, so
 * rows a test inserted, modified or deleted are all undone. Child tables are listed before their parents so the reload
 * satisfies foreign keys.
 */
@TestComponent
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class DatabaseCleaner {

    /** Children first. Add new tables here when adding entities or join tables. */
    private static final List<String> TABLES = List.of("order_product", "\"order\"", "product", "customer");

    private static final String BASELINE_SCHEMA = "test_baseline";

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    void snapshotBaseline() {
        jdbcTemplate.execute("CREATE SCHEMA " + BASELINE_SCHEMA);
        for (String table : TABLES) {
            jdbcTemplate.execute("CREATE TABLE " + baseline(table) + " AS TABLE " + table);
        }
    }

    public void restoreSeededData() {
        jdbcTemplate.execute("TRUNCATE " + String.join(", ", TABLES));
        // Parents first so foreign keys resolve as rows are reloaded.
        for (int i = TABLES.size() - 1; i >= 0; i--) {
            String table = TABLES.get(i);
            jdbcTemplate.execute("INSERT INTO " + table + " SELECT * FROM " + baseline(table));
        }
    }

    public List<String> cleanedTables() {
        return TABLES;
    }

    private static String baseline(String table) {
        return BASELINE_SCHEMA + "." + table;
    }
}
