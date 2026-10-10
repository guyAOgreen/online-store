package com.example.store.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Drives the production Liquibase changelog programmatically so tests can stop at a named changeset, insert
 * representative rows, and then apply the remaining migrations.
 */
public final class LiquibaseRunner implements AutoCloseable {

    /** Same changelog the application runs; the test infrastructure never maintains a copy. */
    static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private final Connection connection;
    private final Liquibase liquibase;

    private LiquibaseRunner(Connection connection, Liquibase liquibase) {
        this.connection = connection;
        this.liquibase = liquibase;
    }

    public static LiquibaseRunner open(String jdbcUrl, String username, String password) {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            return new LiquibaseRunner(
                    connection, new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database));
        } catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException("Could not open Liquibase against " + jdbcUrl, e);
        }
    }

    /** Applies changesets in changelog order up to and including {@code changeSetId}. */
    public void migrateTo(String changeSetId) {
        List<String> all = allChangeSetIds();
        int target = all.indexOf(changeSetId);
        if (target < 0) {
            throw new IllegalArgumentException("No changeset with id '" + changeSetId + "' in " + all);
        }
        int alreadyApplied = all.size() - unrunChangeSetIds().size();
        int toApply = target + 1 - alreadyApplied;
        if (toApply < 0) {
            throw new IllegalStateException(
                    "Database is already past changeset '" + changeSetId + "' (" + alreadyApplied + " applied)");
        }
        try {
            liquibase.update(toApply, new Contexts(), new LabelExpression());
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Migration to '" + changeSetId + "' failed", e);
        }
    }

    public void migrateToLatest() {
        try {
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Migration to latest failed", e);
        }
    }

    public List<String> unrunChangeSetIds() {
        try {
            return ids(liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression()));
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Could not list unrun changesets", e);
        }
    }

    public List<String> allChangeSetIds() {
        try {
            return ids(liquibase.getDatabaseChangeLog().getChangeSets());
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Could not parse changelog " + CHANGELOG, e);
        }
    }

    private static List<String> ids(List<ChangeSet> changeSets) {
        return changeSets.stream().map(ChangeSet::getId).toList();
    }

    @Override
    public void close() {
        try {
            liquibase.close();
            connection.close();
        } catch (LiquibaseException | SQLException e) {
            throw new IllegalStateException("Could not close Liquibase", e);
        }
    }
}
