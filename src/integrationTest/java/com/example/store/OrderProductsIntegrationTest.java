package com.example.store;

import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Products on the order endpoints (#8) against PostgreSQL: a create stores exactly the requested associations in one
 * transaction, a rejected create stores neither the order nor any association, and both order reads carry the products
 * while still issuing one SQL statement. The unit tests cover the status codes and bodies with the service mocked.
 */
class OrderProductsIntegrationTest extends AbstractIntegrationTest {

    private static final long SEEDED_ORDERS = 10_000;

    @Test
    void createStoresTheAssociationsAndReturnsTheProductsInIdOrder() throws Exception {
        long lamp = insertProduct("Lamp");
        long chair = insertProduct("Chair");

        OrderDTO created = objectMapper.readValue(
                postExpecting("/order", orderBody(1, chair, lamp), 201).toString(), OrderDTO.class);

        assertThat(created.getId()).isGreaterThan(SEEDED_ORDERS);
        assertThat(created.getCustomer().getId()).isEqualTo(1L);
        assertThat(created.getProducts())
                .extracting(OrderProductDTO::getId, OrderProductDTO::getDescription)
                .containsExactly(tuple(lamp, "Lamp"), tuple(chair, "Chair"));
        assertThat(storedProductIdsOf(created.getId())).containsExactly(lamp, chair);
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS + 1);
        assertThat(count("product")).as("no cascade re-inserts the products").isEqualTo(2);
    }

    @Test
    void createWithAMissingProductIs422AndWritesNeitherOrderNorAssociation() throws Exception {
        long lamp = insertProduct("Lamp");
        long missing = lamp + 1_000;

        JsonNode problem = postExpecting("/order", orderBody(1, lamp, missing), 422);

        assertThat(problem.get("detail").asText()).isEqualTo("Product " + missing + " does not exist");
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
        assertThat(count("order_product")).isZero();
    }

    @Test
    void createNamesEveryMissingProduct() throws Exception {
        JsonNode problem = postExpecting("/order", orderBody(1, 7_002, 7_001), 422);

        assertThat(problem.get("detail").asText()).isEqualTo("Products 7001, 7002 do not exist");
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void createWithADuplicateProductIs400AndWritesNothing() throws Exception {
        long lamp = insertProduct("Lamp");

        JsonNode problem = postExpecting("/order", orderBody(1, lamp, lamp), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("products");
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
        assertThat(count("order_product")).isZero();
    }

    @Test
    void createWithoutProductsIs400AndWritesNothing() throws Exception {
        // The pre-#8 request shape.
        JsonNode problem = postExpecting("/order", Map.of("description", "Legacy", "customer", Map.of("id", 1)), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("products");
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void createForACustomerThatDoesNotExistWritesNoAssociationEither() throws Exception {
        long lamp = insertProduct("Lamp");

        postExpecting("/order", orderBody(999_999, lamp), 422);

        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
        assertThat(count("order_product")).isZero();
    }

    @Test
    void lookupReturnsTheProductsInIdOrderInOneStatement() throws Exception {
        long lamp = insertProduct("Lamp");
        long chair = insertProduct("Chair");
        associate(1, chair);
        associate(1, lamp);

        OrderDTO order = getOrder(1);

        assertThat(order.getCustomer().getName()).isEqualTo("Vicki Kutch");
        assertThat(order.getProducts())
                .extracting(OrderProductDTO::getId, OrderProductDTO::getDescription)
                .containsExactly(tuple(lamp, "Lamp"), tuple(chair, "Chair"));
        assertThat(sqlStatementCount()).isEqualTo(1);
    }

    @Test
    void lookupOfALegacyOrderReportsAnEmptyProductList() throws Exception {
        assertThat(count("order_product")).as("precondition: no associations").isZero();

        OrderDTO order = getOrder(1);

        assertThat(order.getProducts()).isNotNull().isEmpty();
    }

    @Test
    void listCarriesEveryOrdersProductsAndLoadsThemInOneStatement() throws Exception {
        long lamp = insertProduct("Lamp");
        long chair = insertProduct("Chair");
        associate(1, lamp);
        associate(1, chair);
        associate(2, lamp);

        String json = mockMvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<OrderDTO> orders = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(orders).as("no duplicated root records from the join").hasSize((int) SEEDED_ORDERS);
        assertThat(orders).extracting(OrderDTO::getId).doesNotHaveDuplicates();
        assertThat(byId(orders, 1).getProducts())
                .extracting(OrderProductDTO::getId)
                .containsExactly(lamp, chair);
        assertThat(byId(orders, 2).getProducts())
                .extracting(OrderProductDTO::getId)
                .containsExactly(lamp);
        assertThat(byId(orders, 3).getProducts()).isEmpty();
        assertThat(byId(orders, 1).getCustomer().getName()).isEqualTo("Vicki Kutch");
        assertThat(sqlStatementCount()).isEqualTo(1);
    }

    private static Map<String, Object> orderBody(long customerId, long... productIds) {
        List<Map<String, Object>> products = Arrays.stream(productIds)
                .mapToObj(id -> Map.<String, Object>of("id", id))
                .toList();
        return Map.of("description", "With products", "customer", Map.of("id", customerId), "products", products);
    }

    private JsonNode postExpecting(String path, Object body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                expectedStatus >= 400
                                        ? MediaType.APPLICATION_PROBLEM_JSON
                                        : MediaType.APPLICATION_JSON))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private OrderDTO getOrder(long id) throws Exception {
        String json = mockMvc.perform(get("/order/" + id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, OrderDTO.class);
    }

    private static OrderDTO byId(List<OrderDTO> orders, long id) {
        return orders.stream().filter(o -> o.getId() == id).findFirst().orElseThrow();
    }

    private long insertProduct(String description) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO product (description) VALUES (?) RETURNING id", Long.class, description);
    }

    private void associate(long orderId, long productId) {
        jdbcTemplate.update("INSERT INTO order_product (order_id, product_id) VALUES (?, ?)", orderId, productId);
    }

    private List<Long> storedProductIdsOf(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT product_id FROM order_product WHERE order_id = ? ORDER BY product_id", Long.class, orderId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
