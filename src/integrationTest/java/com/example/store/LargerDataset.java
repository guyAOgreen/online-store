package com.example.store;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The dataset the #9 read-path measurements and regression tests run on: the Liquibase seed (100 customers, 10,000
 * orders, no products) plus the rows below, loaded in three statements so the setup itself costs no round trips worth
 * speaking of. The shape keeps every empty-relationship case the responses must serialise correctly: a customer with no
 * orders, orders that predate products (every seventh order gets none, the #7 legacy policy) and products that no order
 * contains. Products are addressed by rank rather than by ID because the product sequence is not reset between tests,
 * so the IDs depend on what ran before.
 */
public final class LargerDataset {

    /** Above the seeded range, as the #2 notes prescribe for rows a test adds. */
    public static final long CUSTOMER_WITHOUT_ORDERS_ID = 1_001;

    public static final String CUSTOMER_WITHOUT_ORDERS_NAME = "Customer Without Orders";

    public static final int PRODUCTS_IN_ORDERS = 1_000;

    public static final int PRODUCTS_IN_NO_ORDER = 10;

    public static final int PRODUCTS_PER_ORDER = 3;

    /** Orders whose ID is a multiple of this get no products. */
    public static final int LEGACY_ORDER_EVERY = 7;

    private LargerDataset() {}

    public static void load(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "INSERT INTO customer (id, name) VALUES (?, ?)",
                CUSTOMER_WITHOUT_ORDERS_ID,
                CUSTOMER_WITHOUT_ORDERS_NAME);
        jdbcTemplate.update(
                "INSERT INTO product (description) SELECT 'Product ' || g FROM generate_series(1, ?) g",
                PRODUCTS_IN_ORDERS + PRODUCTS_IN_NO_ORDER);
        // Each non-legacy order gets PRODUCTS_PER_ORDER consecutive products (by rank) starting at a point that moves
        // with the order ID, so every one of the first PRODUCTS_IN_ORDERS products ends up in several orders.
        jdbcTemplate.update(
                """
                WITH ranked AS (SELECT id, row_number() OVER (ORDER BY id) - 1 AS rank FROM product)
                INSERT INTO order_product (order_id, product_id)
                SELECT o.id, ranked.id
                FROM "order" o
                         CROSS JOIN generate_series(0, ?) k
                         JOIN ranked ON ranked.rank = (o.id * 7 + k) % ?
                WHERE o.id % ? <> 0
                """,
                PRODUCTS_PER_ORDER - 1, PRODUCTS_IN_ORDERS, LEGACY_ORDER_EVERY);
    }
}
