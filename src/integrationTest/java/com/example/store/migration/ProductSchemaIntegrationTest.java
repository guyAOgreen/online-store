package com.example.store.migration;

import com.example.store.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code 4-products} schema (#7) as the database enforces it, independently of the JPA mapping: a product can sit
 * in many orders and an order can hold many products, the same pair cannot be stored twice, a row cannot reference an
 * order or product that does not exist, and both directions of the relationship have an index to walk.
 */
class ProductSchemaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void oneProductCanBelongToManyOrders() {
        long product = insertProduct("Shared product");

        associate(1, product);
        associate(2, product);

        assertThat(orderIdsOf(product)).containsExactly(1L, 2L);
    }

    @Test
    void oneOrderCanContainManyProducts() {
        long first = insertProduct("First product");
        long second = insertProduct("Second product");

        associate(1, first);
        associate(1, second);

        assertThat(productIdsOf(1)).containsExactly(first, second);
    }

    @Test
    void rejectsTheSameAssociationTwice() {
        long product = insertProduct("Once only");
        associate(1, product);

        assertThatThrownBy(() -> associate(1, product)).isInstanceOf(DuplicateKeyException.class);
        assertThat(orderIdsOf(product)).containsExactly(1L);
    }

    @Test
    void rejectsAssociationWithAnOrderThatDoesNotExist() {
        long product = insertProduct("Orphan order");
        long missingOrder = 10_001;
        assertThat(count("\"order\" WHERE id = " + missingOrder)).isZero();

        assertThatThrownBy(() -> associate(missingOrder, product))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_order_product_order");
    }

    @Test
    void rejectsAssociationWithAProductThatDoesNotExist() {
        long missingProduct = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM product", Long.class);

        assertThatThrownBy(() -> associate(1, missingProduct))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_order_product_product");
    }

    @Test
    void rejectsDeletingAProductThatAnOrderContains() {
        long product = insertProduct("Still in an order");
        associate(1, product);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM product WHERE id = ?", product))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("product WHERE id = " + product)).isEqualTo(1);
    }

    @Test
    void rejectsAProductWithoutADescription() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO product (description) VALUES (NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indexesBothDirectionsOfTheRelationship() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'order_product'",
                String.class);

        assertThat(indexes)
                .as("order -> products is served by the primary key, product -> orders by its own index")
                .anySatisfy(def -> assertThat(def).contains("(order_id, product_id)"))
                .anySatisfy(def -> assertThat(def).endsWith("(product_id)"));
    }

    private long insertProduct(String description) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO product (description) VALUES (?) RETURNING id", Long.class, description);
    }

    private void associate(long orderId, long productId) {
        jdbcTemplate.update("INSERT INTO order_product (order_id, product_id) VALUES (?, ?)", orderId, productId);
    }

    private List<Long> orderIdsOf(long productId) {
        return jdbcTemplate.queryForList(
                "SELECT order_id FROM order_product WHERE product_id = ? ORDER BY order_id", Long.class, productId);
    }

    private List<Long> productIdsOf(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT product_id FROM order_product WHERE order_id = ? ORDER BY product_id", Long.class, orderId);
    }

    private long count(String tableAndFilter) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndFilter, Long.class);
    }
}
