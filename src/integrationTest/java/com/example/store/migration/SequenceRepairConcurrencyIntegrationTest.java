package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code 3-sequences} repair runs while an older application instance may still be writing (rolling deploy). It
 * must wait for in-flight inserts rather than deadlock with them, on both write paths the application has: an order
 * insert (locks {@code "order"}, then {@code customer} for the foreign-key check) and a customer created with nested
 * orders (locks {@code customer}, then {@code "order"} via the cascade). PostgreSQL aborts a deadlocked statement after
 * {@code deadlock_timeout} (one second), so a regression fails fast on whichever side loses.
 */
class SequenceRepairConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String BEFORE_REPAIR = "2-data";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void waitsForAnInFlightOrderInsertInsteadOfDeadlocking() throws Exception {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres);
                Connection writer = db.jdbc().getDataSource().getConnection()) {
            db.liquibase().migrateTo(BEFORE_REPAIR);
            writer.setAutoCommit(false);
            // Holds ROW EXCLUSIVE on "order" without touching customer yet, as an insert does before its FK check.
            execute(writer, "UPDATE \"order\" SET description = description WHERE id = 1");

            CompletableFuture<Void> repair =
                    CompletableFuture.runAsync(() -> db.liquibase().migrateToLatest());
            awaitBlockedOnLock(db);

            // The FK check on this insert needs ROW SHARE on customer while the repair holds its lock there.
            execute(writer, "INSERT INTO \"order\" (id, description, customer_id) VALUES (10001, 'In flight', 1)");
            writer.commit();

            repair.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertThat(nextOrderId(db)).isEqualTo(10_002L);
        }
    }

    @Test
    void waitsForAnInFlightCustomerWithOrdersInsteadOfDeadlocking() throws Exception {
        try (UpgradeDatabase db = UpgradeDatabase.create(postgres);
                Connection writer = db.jdbc().getDataSource().getConnection()) {
            db.liquibase().migrateTo(BEFORE_REPAIR);
            writer.setAutoCommit(false);
            // POST /customer with nested orders: the customer row first, then its orders through the cascade.
            execute(writer, "INSERT INTO customer (id, name) VALUES (101, 'In flight')");

            CompletableFuture<Void> repair =
                    CompletableFuture.runAsync(() -> db.liquibase().migrateToLatest());
            awaitBlockedOnLock(db);

            execute(writer, "INSERT INTO \"order\" (id, description, customer_id) VALUES (10001, 'In flight', 101)");
            writer.commit();

            repair.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertThat(nextCustomerId(db)).isEqualTo(102L);
            assertThat(nextOrderId(db)).isEqualTo(10_002L);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** Waits until some other session in this database is blocked on a table lock, i.e. the repair is queued. */
    private static void awaitBlockedOnLock(UpgradeDatabase db) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            Integer blocked = db.jdbc()
                    .queryForObject(
                            "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()"
                                    + " AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
                            Integer.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The repair never blocked on the in-flight transaction");
    }

    private static long nextCustomerId(UpgradeDatabase db) {
        return db.jdbc().queryForObject("INSERT INTO customer (name) VALUES ('After') RETURNING id", Long.class);
    }

    private static long nextOrderId(UpgradeDatabase db) {
        return db.jdbc()
                .queryForObject(
                        "INSERT INTO \"order\" (description, customer_id) VALUES ('After', 1) RETURNING id",
                        Long.class);
    }
}
