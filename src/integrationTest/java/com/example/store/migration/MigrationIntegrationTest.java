package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the production changelog applies to an empty PostgreSQL and matches the JPA mappings. */
class MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void runsAgainstTheProductionPostgresVersion() {
        String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
        assertThat(version).startsWith("PostgreSQL 16.2");
    }

    @Test
    void appliesEveryChangeSetOnStartup() {
        // Reaching this point means Liquibase ran and Hibernate's ddl-auto=validate passed.
        List<Map<String, Object>> applied =
                jdbcTemplate.queryForList("SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted");

        assertThat(applied).extracting(row -> row.get("id")).containsExactly("1-schema", "2-data");
        assertThat(applied).extracting(row -> row.get("exectype")).containsOnly("EXECUTED");
    }

    @Test
    void leavesNothingToApply() throws Exception {
        try (LiquibaseRunner liquibase =
                LiquibaseRunner.open(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(liquibase.unrunChangeSetIds()).isEmpty();
            assertThat(liquibase.allChangeSetIds()).containsExactly("1-schema", "2-data");
        }
    }
}
