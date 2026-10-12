package com.example.store;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for #4 on the real database: a rejected create must leave the tables exactly as they were. The unit tests
 * prove the status codes and bodies; these prove that nothing was written and that a client-supplied ID can no longer
 * overwrite a seeded row (which {@code repository.save} used to do by merging). The happy path with a stored
 * {@code customer_id} is already covered by {@link GeneratedIdIntegrationTest}.
 */
class CreateValidationIntegrationTest extends AbstractIntegrationTest {

    private static final long SEEDED_CUSTOMERS = 100;
    private static final long SEEDED_ORDERS = 10_000;

    @Test
    void suppliedCustomerIdIsRejectedAndTheSeededRowIsUnchanged() throws Exception {
        JsonNode problem = postExpecting("/customer", Map.of("id", 1, "name", "Overwritten"), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM customer WHERE id = 1", String.class))
                .isEqualTo("Muriel Donnelly");
        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
    }

    @Test
    void suppliedOrderIdIsRejectedAndTheSeededRowIsUnchanged() throws Exception {
        Map<String, Object> before =
                jdbcTemplate.queryForMap("SELECT description, customer_id FROM \"order\" WHERE id = 1");

        JsonNode problem = postExpecting(
                "/order",
                Map.of(
                        "id",
                        1,
                        "description",
                        "Overwritten",
                        "customer",
                        Map.of("id", 2),
                        "products",
                        List.of(Map.of("id", insertProduct()))),
                400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");
        assertThat(jdbcTemplate.queryForMap("SELECT description, customer_id FROM \"order\" WHERE id = 1"))
                .isEqualTo(before);
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void overLengthNameInsertsNoRow() throws Exception {
        JsonNode problem = postExpecting("/customer", Map.of("name", "x".repeat(256)), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("name");
        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
    }

    @Test
    void nameContainingNulIs400AndInsertsNoRow() throws Exception {
        JsonNode problem = postExpecting("/customer", Map.of("name", "Nul\u0000Byte"), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("name");
        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
    }

    @Test
    void nameOfExactly255IsStored() throws Exception {
        String maxLength = "y".repeat(255);

        JsonNode created = postExpecting("/customer", Map.of("name", maxLength), 201);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM customer WHERE id = ?",
                        String.class,
                        created.get("id").asLong()))
                .isEqualTo(maxLength);
    }

    @Test
    void orderForACustomerThatDoesNotExistIs422AndInsertsNoRow() throws Exception {
        JsonNode problem = postExpecting(
                "/order",
                Map.of(
                        "description",
                        "Dangling",
                        "customer",
                        Map.of("id", 999_999),
                        "products",
                        List.of(Map.of("id", insertProduct()))),
                422);

        assertThat(problem.get("detail").asText()).isEqualTo("Customer 999999 does not exist");
        assertThat(problem.has("errors")).isFalse();
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
    }

    @Test
    void nestedOrdersOnCustomerCreateAreRejectedAndNothingIsInserted() throws Exception {
        JsonNode problem = postExpecting(
                "/customer", Map.of("name", "With Orders", "orders", List.of(Map.of("description", "Nested"))), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("orders");
        assertThat(count("customer")).isEqualTo(SEEDED_CUSTOMERS);
        assertThat(count("\"order\"")).isEqualTo(SEEDED_ORDERS);
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

    /** Since #8 an order needs a product; the scenarios here are about the order itself, so any product will do. */
    private long insertProduct() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO product (description) VALUES ('Any product') RETURNING id", Long.class);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
