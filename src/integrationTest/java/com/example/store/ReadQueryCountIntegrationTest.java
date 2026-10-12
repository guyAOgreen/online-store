package com.example.store;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.CustomerOrderDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.example.store.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The #9 regression tests: on the {@link LargerDataset} every GET prepares a fixed number of SQL statements, the same
 * number as on the seed alone, and each response carries exactly the relationships the database holds, checked parent
 * by parent rather than by totals, so two customers swapping their orders would fail here. The expected counts are the
 * ones the #9 measurements settled on (see docs/design-record.md); they are constants of the fetch strategy, not of the
 * data.
 */
class ReadQueryCountIntegrationTest extends AbstractIntegrationTest {

    private static final long CUSTOMER_LIST_STATEMENTS = 1;
    private static final long CUSTOMER_SEARCH_STATEMENTS = 1;
    private static final long ORDER_LIST_STATEMENTS = 1;
    private static final long ORDER_LOOKUP_STATEMENTS = 1;
    private static final long PRODUCT_LIST_STATEMENTS = 1;
    private static final long PRODUCT_LOOKUP_STATEMENTS = 1;

    private static final long SEEDED_CUSTOMERS = 100;
    private static final long SEEDED_ORDERS = 10_000;

    /** An order that has products on the larger dataset, and one that is legacy (a multiple of seven). */
    private static final long ORDER_WITH_PRODUCTS = 1;

    private static final long LEGACY_ORDER = LargerDataset.LEGACY_ORDER_EVERY;

    /** Requests whose statement count must not depend on how much data there is. */
    private static final List<String> SIZE_INDEPENDENT_REQUESTS =
            List.of("/customer", "/customer?q=ann", "/order", "/order/" + ORDER_WITH_PRODUCTS, "/products");

    @Test
    void customerListingCarriesExactlyEachCustomersOrdersInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> expected = orderIdsByCustomer();
        assertThat(expected.get(LargerDataset.CUSTOMER_WITHOUT_ORDERS_ID))
                .as("precondition: the dataset has a customer with no orders")
                .isEmpty();
        clearStatistics();

        List<CustomerDTO> customers = getList("/customer", new TypeReference<>() {});

        assertThat(sqlStatementCount()).isEqualTo(CUSTOMER_LIST_STATEMENTS);
        assertThat(customers)
                .extracting(CustomerDTO::getId)
                .as("one root per customer, in ID order")
                .hasSize((int) SEEDED_CUSTOMERS + 1)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(nestedIds(customers, CustomerDTO::getId, c -> ids(c.getOrders(), CustomerOrderDTO::getId)))
                .isEqualTo(expected);
    }

    @Test
    void customerSearchCarriesExactlyTheMatchingCustomersAndTheirOrdersInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> ordersByCustomer = orderIdsByCustomer();
        List<Long> matching =
                jdbcTemplate.queryForList("SELECT id FROM customer WHERE name ILIKE '%ann%' ORDER BY id", Long.class);
        assertThat(matching).as("precondition: a selective search").hasSizeBetween(2, 10);
        clearStatistics();

        List<CustomerDTO> found = getList("/customer?q=ann", new TypeReference<>() {});

        assertThat(sqlStatementCount()).isEqualTo(CUSTOMER_SEARCH_STATEMENTS);
        assertThat(found).extracting(CustomerDTO::getId).isEqualTo(matching);
        for (CustomerDTO customer : found) {
            assertThat(ids(customer.getOrders(), CustomerOrderDTO::getId))
                    .as("orders of customer %d", customer.getId())
                    .isEqualTo(ordersByCustomer.get(customer.getId()));
        }
    }

    @Test
    void customerSearchFindsTheCustomerWithoutOrdersWithAnEmptyList() throws Exception {
        LargerDataset.load(jdbcTemplate);
        clearStatistics();

        List<CustomerDTO> found = getList("/customer?q=without", new TypeReference<>() {});

        assertThat(sqlStatementCount()).isEqualTo(CUSTOMER_SEARCH_STATEMENTS);
        assertThat(found).extracting(CustomerDTO::getId).containsExactly(LargerDataset.CUSTOMER_WITHOUT_ORDERS_ID);
        assertThat(found.get(0).getOrders()).isNotNull().isEmpty();
    }

    @Test
    void orderListingCarriesExactlyEachOrdersCustomerAndProductsInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> expectedProducts = productIdsByOrder();
        Map<Long, Long> expectedCustomers = customerIdByOrder();
        assertThat(expectedProducts.get(LEGACY_ORDER))
                .as("precondition: the dataset keeps legacy orders")
                .isEmpty();
        assertThat(expectedProducts.get(ORDER_WITH_PRODUCTS)).hasSize(LargerDataset.PRODUCTS_PER_ORDER);
        clearStatistics();

        List<OrderDTO> orders = getList("/order", new TypeReference<>() {});

        assertThat(sqlStatementCount()).isEqualTo(ORDER_LIST_STATEMENTS);
        assertThat(orders)
                .extracting(OrderDTO::getId)
                .as("one root per order, in ID order")
                .hasSize((int) SEEDED_ORDERS)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(nestedIds(orders, OrderDTO::getId, o -> ids(o.getProducts(), OrderProductDTO::getId)))
                .isEqualTo(expectedProducts);
        assertThat(nestedIds(orders, OrderDTO::getId, o -> o.getCustomer().getId()))
                .isEqualTo(expectedCustomers);
    }

    @Test
    void orderLookupCarriesExactlyTheOrdersProductsInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> expectedProducts = productIdsByOrder();

        for (long id : List.of(ORDER_WITH_PRODUCTS, LEGACY_ORDER)) {
            clearStatistics();

            OrderDTO order = getOne("/order/" + id, OrderDTO.class);

            assertThat(sqlStatementCount()).as("order %d", id).isEqualTo(ORDER_LOOKUP_STATEMENTS);
            assertThat(ids(order.getProducts(), OrderProductDTO::getId))
                    .as("products of order %d", id)
                    .isEqualTo(expectedProducts.get(id));
            assertThat(order.getCustomer().getId())
                    .isEqualTo(customerIdByOrder().get(id));
        }
    }

    @Test
    void productListingCarriesExactlyEachProductsOrdersInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> expected = orderIdsByProduct();
        assertThat(expected.values().stream().filter(List::isEmpty).count())
                .as("precondition: the dataset keeps products that are in no order")
                .isEqualTo(LargerDataset.PRODUCTS_IN_NO_ORDER);
        clearStatistics();

        List<ProductDTO> products = getList("/products", new TypeReference<>() {});

        assertThat(sqlStatementCount()).isEqualTo(PRODUCT_LIST_STATEMENTS);
        assertThat(products)
                .extracting(ProductDTO::getId)
                .as("one root per product, in ID order")
                .hasSize(LargerDataset.PRODUCTS_IN_ORDERS + LargerDataset.PRODUCTS_IN_NO_ORDER)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(nestedIds(products, ProductDTO::getId, ProductDTO::getOrders))
                .isEqualTo(expected);
    }

    @Test
    void productLookupCarriesExactlyTheProductsOrdersInAFixedNumberOfStatements() throws Exception {
        LargerDataset.load(jdbcTemplate);
        Map<Long, List<Long>> expected = orderIdsByProduct();
        long inOrders = jdbcTemplate.queryForObject("SELECT MIN(id) FROM product", Long.class);
        long inNoOrder = jdbcTemplate.queryForObject("SELECT MAX(id) FROM product", Long.class);
        assertThat(expected.get(inOrders)).isNotEmpty();
        assertThat(expected.get(inNoOrder)).isEmpty();

        for (long id : List.of(inOrders, inNoOrder)) {
            clearStatistics();

            ProductDTO product = getOne("/products/" + id, ProductDTO.class);

            assertThat(sqlStatementCount()).as("product %d", id).isEqualTo(PRODUCT_LOOKUP_STATEMENTS);
            assertThat(product.getOrders()).as("orders of product %d", id).isEqualTo(expected.get(id));
        }
    }

    @Test
    void statementCountsAreTheSameOnTheSeedAndOnTheLargerDataset() throws Exception {
        Map<String, Long> onSeed = statementCounts();

        LargerDataset.load(jdbcTemplate);

        assertThat(statementCounts()).isEqualTo(onSeed);
    }

    private Map<String, Long> statementCounts() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String request : SIZE_INDEPENDENT_REQUESTS) {
            clearStatistics();
            mockMvc.perform(MockMvcRequestBuilders.get(request)).andExpect(status().isOk());
            counts.put(request, sqlStatementCount());
        }
        return counts;
    }

    /** Every customer's order IDs ascending, including customers with none. */
    private Map<Long, List<Long>> orderIdsByCustomer() {
        return childrenByParent("SELECT id FROM customer", "SELECT customer_id, id FROM \"order\"");
    }

    /** Every order's product IDs ascending, including orders with none. */
    private Map<Long, List<Long>> productIdsByOrder() {
        return childrenByParent("SELECT id FROM \"order\"", "SELECT order_id, product_id FROM order_product");
    }

    /** Every product's order IDs ascending, including products in no order. */
    private Map<Long, List<Long>> orderIdsByProduct() {
        return childrenByParent("SELECT id FROM product", "SELECT product_id, order_id FROM order_product");
    }

    private Map<Long, Long> customerIdByOrder() {
        Map<Long, Long> customers = new TreeMap<>();
        jdbcTemplate.query("SELECT id, customer_id FROM \"order\"", rs -> {
            customers.put(rs.getLong(1), rs.getLong(2));
        });
        return customers;
    }

    private Map<Long, List<Long>> childrenByParent(String parentIds, String parentChildPairs) {
        Map<Long, List<Long>> children = new TreeMap<>();
        jdbcTemplate.queryForList(parentIds, Long.class).forEach(id -> children.put(id, new ArrayList<>()));
        jdbcTemplate.query(parentChildPairs + " ORDER BY 1, 2", rs -> {
            children.get(rs.getLong(1)).add(rs.getLong(2));
        });
        return children;
    }

    private static <P, N> Map<Long, N> nestedIds(List<P> parents, Function<P, Long> id, Function<P, N> nested) {
        Map<Long, N> result = new TreeMap<>();
        parents.forEach(parent -> result.put(id.apply(parent), nested.apply(parent)));
        return result;
    }

    private static <C> List<Long> ids(List<C> children, Function<C, Long> id) {
        return children.stream().map(id).toList();
    }

    private <T> List<T> getList(String request, TypeReference<List<T>> type) throws Exception {
        return objectMapper.readValue(body(request), type);
    }

    private <T> T getOne(String request, Class<T> type) throws Exception {
        return objectMapper.readValue(body(request), type);
    }

    private String body(String request) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(request))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
