package com.example.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextStartsAgainstRealPostgres() {
        String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
        assertThat(version).startsWith("PostgreSQL 16.2");
    }
}
