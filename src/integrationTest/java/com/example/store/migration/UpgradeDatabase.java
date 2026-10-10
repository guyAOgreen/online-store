package com.example.store.migration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * A throwaway PostgreSQL database on the shared test container, created empty so a test can drive Liquibase through an
 * upgrade scenario in isolation. Closing it drops the database.
 */
public final class UpgradeDatabase implements AutoCloseable {

    private final JdbcTemplate admin;
    private final String name;
    private final LiquibaseRunner liquibase;
    private final JdbcTemplate jdbc;

    private UpgradeDatabase(JdbcTemplate admin, String name, LiquibaseRunner liquibase, JdbcTemplate jdbc) {
        this.admin = admin;
        this.name = name;
        this.liquibase = liquibase;
        this.jdbc = jdbc;
    }

    public static UpgradeDatabase create(PostgreSQLContainer<?> container) {
        String name = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(
                new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        admin.execute("CREATE DATABASE " + name);

        String url = "jdbc:postgresql://" + container.getHost() + ":"
                + container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + name;
        LiquibaseRunner liquibase = LiquibaseRunner.open(url, container.getUsername(), container.getPassword());
        JdbcTemplate jdbc =
                new JdbcTemplate(new DriverManagerDataSource(url, container.getUsername(), container.getPassword()));
        return new UpgradeDatabase(admin, name, liquibase, jdbc);
    }

    public LiquibaseRunner liquibase() {
        return liquibase;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public String name() {
        return name;
    }

    @Override
    public void close() {
        liquibase.close();
        // FORCE disconnects any session still attached (PostgreSQL 13+).
        admin.execute("DROP DATABASE " + name + " WITH (FORCE)");
    }
}
