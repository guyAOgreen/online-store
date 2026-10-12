package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code 5-order-customer-index} changeset (#9): the foreign key from an order to its customer has an index, and
 * the index is valid, which a {@code CREATE INDEX CONCURRENTLY} that failed part-way would not be.
 */
class OrderCustomerIndexIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ordersAreIndexedByCustomerWithAValidIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                """
                SELECT i.indexname, i.indexdef, x.indisvalid
                FROM pg_indexes i
                         JOIN pg_class c ON c.relname = i.indexname
                         JOIN pg_index x ON x.indexrelid = c.oid
                WHERE i.schemaname = 'public' AND i.tablename = 'order'
                """);

        assertThat(indexes).as("indexes on \"order\": %s", indexes).anySatisfy(index -> {
            assertThat(index.get("indexdef")).asString().endsWith("(customer_id)");
            assertThat(index.get("indisvalid")).isEqualTo(true);
        });
    }
}
