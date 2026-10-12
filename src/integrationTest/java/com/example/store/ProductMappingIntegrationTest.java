package com.example.store;

import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JPA side of #7: {@link Order#getProducts()} owns the {@code order_product} rows, {@link Product#getOrders()}
 * reads them back, and neither side is loaded until it is asked for. Each scenario runs inside its own transaction, as
 * a service method would, so the lazy collections are reachable and the statement counts are those of production.
 */
class ProductMappingIntegrationTest extends AbstractIntegrationTest {

    private static final long LAST_SEEDED_ORDER_ID = 10_000;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    /** IDs handed back from a transaction that created an order with products. */
    private record SavedOrder(long orderId, long firstProductId, long secondProductId) {}

    @Test
    void savingANewOrderWritesItsProductAssociations() {
        long productsBefore = count("product");

        SavedOrder saved = transactionTemplate.execute(status -> {
            Product first = productRepository.save(product("First product"));
            Product second = productRepository.save(product("Second product"));
            Order order = new Order();
            order.setDescription("New order with products");
            order.setCustomer(customerRepository.findById(1L).orElseThrow());
            order.getProducts().add(first);
            order.getProducts().add(second);
            long orderId = orderRepository.save(order).getId();
            return new SavedOrder(orderId, first.getId(), second.getId());
        });

        assertThat(saved.orderId()).isGreaterThan(LAST_SEEDED_ORDER_ID);
        assertThat(storedProductIdsOf(saved.orderId()))
                .containsExactly(saved.firstProductId(), saved.secondProductId());
        assertThat(count("product")).as("no cascade re-inserts the products").isEqualTo(productsBefore + 2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT customer_id FROM \"order\" WHERE id = ?", Long.class, saved.orderId()))
                .isEqualTo(1L);
    }

    @Test
    void addingProductsToAManagedOrderWritesItsAssociations() {
        Long productId = transactionTemplate.execute(status -> {
            Product product = productRepository.save(product("Shared product"));
            orderRepository.findById(1L).orElseThrow().getProducts().add(product);
            orderRepository.findById(2L).orElseThrow().getProducts().add(product);
            return product.getId();
        });

        assertThat(productId).isNotNull();
        assertThat(storedOrderIdsOf(productId)).containsExactly(1L, 2L);
    }

    @Test
    void addingTheSameProductTwiceStoresOneAssociation() {
        Long productId = transactionTemplate.execute(status -> {
            Product product = productRepository.save(product("Added twice"));
            Order order = orderRepository.findById(1L).orElseThrow();
            order.getProducts().add(product);
            order.getProducts().add(product);
            return product.getId();
        });

        assertThat(storedOrderIdsOf(productId)).containsExactly(1L);
    }

    @Test
    void readsContainingOrdersFromTheProductSide() {
        long productId = insertProduct("In two orders");
        associate(1, productId);
        associate(2, productId);

        Set<Long> orderIds = transactionTemplate.execute(
                status -> productRepository.findById(productId).orElseThrow().getOrders().stream()
                        .map(Order::getId)
                        .collect(Collectors.toSet()));

        assertThat(orderIds).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void readsProductsFromTheOrderSideInIdOrder() {
        long first = insertProduct("First");
        long second = insertProduct("Second");
        // Inserted newest first, so insertion order and ID order disagree.
        associate(1, second);
        associate(1, first);

        List<Long> productIds =
                transactionTemplate.execute(status -> orderRepository.findById(1L).orElseThrow().getProducts().stream()
                        .map(Product::getId)
                        .toList());

        assertThat(productIds).containsExactly(first, second);
    }

    @Test
    void loadingAnOrderDoesNotLoadItsProducts() {
        long productId = insertProduct("Not loaded");
        associate(1, productId);

        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(1L).orElseThrow();

            assertThat(sqlStatementCount()).isEqualTo(1);
            assertThat(Hibernate.isInitialized(order.getProducts())).isFalse();

            assertThat(order.getProducts()).extracting(Product::getId).containsExactly(productId);
            assertThat(sqlStatementCount())
                    .as("products arrive in one further statement")
                    .isEqualTo(2);
        });
    }

    @Test
    void lookingUpAProductDoesNotLoadItsOrders() {
        long productId = insertProduct("Orders not loaded");
        associate(1, productId);

        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId).orElseThrow();

            assertThat(sqlStatementCount()).isEqualTo(1);
            assertThat(Hibernate.isInitialized(product.getOrders())).isFalse();
        });
    }

    @Test
    void anOrderInAHashSetDoesNotLoadItsRelationships() {
        // Lombok's @Data would hash every field, which on an entity initialises the customer proxy and walks its
        // orders.
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(1L).orElseThrow();

            Set<Order> orders = new HashSet<>(List.of(order));

            assertThat(orders).containsExactly(order);
            assertThat(sqlStatementCount()).isEqualTo(1);
            assertThat(Hibernate.isInitialized(order.getCustomer())).isFalse();
            assertThat(Hibernate.isInitialized(order.getProducts())).isFalse();
        });
    }

    @Test
    void aProductInAHashSetDoesNotLoadItsOrders() {
        long productId = insertProduct("Hashed");
        associate(1, productId);

        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId).orElseThrow();

            Set<Product> products = new HashSet<>(List.of(product));

            assertThat(products).containsExactly(product);
            assertThat(sqlStatementCount()).isEqualTo(1);
            assertThat(Hibernate.isInitialized(product.getOrders())).isFalse();
        });
    }

    private static Product product(String description) {
        Product product = new Product();
        product.setDescription(description);
        return product;
    }

    private long insertProduct(String description) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO product (description) VALUES (?) RETURNING id", Long.class, description);
    }

    private void associate(long orderId, long productId) {
        jdbcTemplate.update("INSERT INTO order_product (order_id, product_id) VALUES (?, ?)", orderId, productId);
    }

    private List<Long> storedOrderIdsOf(long productId) {
        return jdbcTemplate.queryForList(
                "SELECT order_id FROM order_product WHERE product_id = ? ORDER BY order_id", Long.class, productId);
    }

    private List<Long> storedProductIdsOf(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT product_id FROM order_product WHERE order_id = ? ORDER BY product_id", Long.class, orderId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
