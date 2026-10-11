package com.example.store;

import com.example.store.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /products} (#8) against PostgreSQL. The controller tests prove the status codes and bodies with the service
 * mocked; these prove the create writes the row, that both GET forms report the containing orders accurately through
 * the real mapping (ascending, no duplicates, empty for an unused product) and that each form does so in one SQL
 * statement. Nothing seeds products, so each test inserts its own above an empty table.
 */
class ProductApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createReturns201StoresTheRowAndReportsNoOrders() throws Exception {
        String json = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Brass lamp"))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        ProductDTO created = objectMapper.readValue(json, ProductDTO.class);

        assertThat(created.getId()).isPositive();
        assertThat(created.getDescription()).isEqualTo("Brass lamp");
        assertThat(created.getOrders()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT description FROM product WHERE id = ?", String.class, created.getId()))
                .isEqualTo("Brass lamp");
        assertThat(count("product")).isEqualTo(1);
    }

    @Test
    void overLengthDescriptionIs400AndInsertsNoRow() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "x".repeat(256)))))
                .andExpect(status().isBadRequest());

        assertThat(count("product")).isZero();
    }

    @Test
    void listIsEmptyBeforeAnyProductIsCreated() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listReportsEachProductsContainingOrdersAscendingWithoutDuplicates() throws Exception {
        long shared = insertProduct("In two orders");
        long unused = insertProduct("In no order");
        // Associated newest order first, so insertion order and ID order disagree.
        associate(2, shared);
        associate(1, shared);

        List<ProductDTO> products = getList("/products");

        assertThat(products).extracting(ProductDTO::getId).containsExactly(shared, unused);
        assertThat(byId(products, shared).getDescription()).isEqualTo("In two orders");
        assertThat(byId(products, shared).getOrders()).containsExactly(1L, 2L);
        assertThat(byId(products, unused).getOrders()).isEmpty();
    }

    @Test
    void listLoadsEveryProductAndItsOrdersInOneStatement() throws Exception {
        for (int i = 0; i < 25; i++) {
            long product = insertProduct("Product " + i);
            associate(i + 1, product);
            associate(i + 2, product);
        }

        List<ProductDTO> products = getList("/products");

        assertThat(products).hasSize(25);
        assertThat(products)
                .allSatisfy(product -> assertThat(product.getOrders()).hasSize(2));
        assertThat(sqlStatementCount()).isEqualTo(1);
    }

    @Test
    void lookupReturnsTheProductWithItsContainingOrdersInOneStatement() throws Exception {
        long product = insertProduct("Looked up");
        associate(3, product);
        associate(1, product);
        associate(2, product);

        String json = mockMvc.perform(get("/products/" + product))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        ProductDTO found = objectMapper.readValue(json, ProductDTO.class);

        assertThat(found.getId()).isEqualTo(product);
        assertThat(found.getDescription()).isEqualTo("Looked up");
        assertThat(found.getOrders()).containsExactly(1L, 2L, 3L);
        assertThat(sqlStatementCount()).isEqualTo(1);
    }

    @Test
    void lookupOfAnUnusedProductReportsAnEmptyOrderList() throws Exception {
        long product = insertProduct("Unused");

        ProductDTO found = objectMapper.readValue(
                mockMvc.perform(get("/products/" + product))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                ProductDTO.class);

        assertThat(found.getOrders()).isNotNull().isEmpty();
    }

    @Test
    void productThatDoesNotExistIs404() throws Exception {
        assertThat(count("product")).as("precondition: no product rows").isZero();

        JsonNode problem = getProblem("/products/1", 404);

        assertThat(problem.get("title").asText()).isEqualTo("Not Found");
        assertThat(problem.get("detail").asText()).isEqualTo("Product 1 does not exist");
        assertThat(problem.get("instance").asText()).isEqualTo("/products/1");
        assertThat(problem.has("errors")).isFalse();
    }

    @Test
    void malformedIdIs400NamingThePathVariable() throws Exception {
        JsonNode problem = getProblem("/products/abc", 400);

        assertThat(problem.get("detail").asText()).isEqualTo("Invalid value for 'id'");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");
    }

    private List<ProductDTO> getList(String path) throws Exception {
        String json = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private JsonNode getProblem(String path, int expectedStatus) throws Exception {
        String json = mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json);
    }

    private static ProductDTO byId(List<ProductDTO> products, long id) {
        return products.stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
    }

    private long insertProduct(String description) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO product (description) VALUES (?) RETURNING id", Long.class, description);
    }

    private void associate(long orderId, long productId) {
        jdbcTemplate.update("INSERT INTO order_product (order_id, product_id) VALUES (?, ?)", orderId, productId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
