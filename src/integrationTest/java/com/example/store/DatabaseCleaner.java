package com.example.store;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes rows that tests created, leaving the Liquibase-seeded data in place. After the migrations run it records the
 * highest ID in each table; cleanup deletes anything above that. Child tables are listed before their parents so
 * foreign keys are satisfied.
 */
@TestComponent
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class DatabaseCleaner {

    /** Delete order matters: children first. Add new tables here when adding entities. */
    private static final List<String> TABLES = List.of("\"order\"", "customer");

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Long> baselineMaxIds = new LinkedHashMap<>();

    @PostConstruct
    void recordBaseline() {
        for (String table : TABLES) {
            Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
            baselineMaxIds.put(table, maxId);
        }
    }

    public void deleteRowsCreatedByTests() {
        baselineMaxIds.forEach((table, maxId) -> jdbcTemplate.update("DELETE FROM " + table + " WHERE id > ?", maxId));
    }

    public List<String> cleanedTables() {
        return TABLES;
    }
}
