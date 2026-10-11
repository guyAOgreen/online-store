package com.example.store;

import com.example.store.dto.OrderDTO;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /order/{id}} (#5) against the seeded database. The controller tests prove the status codes and bodies with
 * the service mocked; these prove the lookup reads the right row and its customer through the real JPA mapping, answers
 * 404 for an ID no row has, and does it in one SQL statement rather than one for the order and a second for the lazily
 * loaded customer. The list endpoint is covered by {@link SeedDataIntegrationTest}.
 */
class OrderLookupIntegrationTest extends AbstractIntegrationTest {

    private static final long LAST_SEEDED_ORDER_ID = 10_000;

    @Test
    void returnsTheSeededOrderWithItsCustomer() throws Exception {
        String json = mockMvc.perform(get("/order/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        OrderDTO order = objectMapper.readValue(json, OrderDTO.class);

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getDescription()).isEqualTo("Handcrafted Soft Chair");
        assertThat(order.getCustomer().getId()).isEqualTo(6L);
        assertThat(order.getCustomer().getName()).isEqualTo("Vicki Kutch");
    }

    @Test
    void loadsTheOrderAndItsCustomerInOneStatement() throws Exception {
        mockMvc.perform(get("/order/1")).andExpect(status().isOk());

        assertThat(sqlStatementCount()).isEqualTo(1);
    }

    @Test
    void orderThatDoesNotExistIs404() throws Exception {
        long missingId = LAST_SEEDED_ORDER_ID + 1;
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"order\" WHERE id = ?", Long.class, missingId))
                .as("precondition: no row has the looked-up ID")
                .isZero();

        JsonNode problem = getProblem("/order/" + missingId, 404);

        assertThat(problem.get("title").asText()).isEqualTo("Not Found");
        assertThat(problem.get("detail").asText()).isEqualTo("Order " + missingId + " does not exist");
        assertThat(problem.get("instance").asText()).isEqualTo("/order/" + missingId);
        assertThat(problem.has("errors")).isFalse();
    }

    @Test
    void malformedIdIs400NamingThePathVariable() throws Exception {
        JsonNode problem = getProblem("/order/abc", 400);

        assertThat(problem.get("detail").asText()).isEqualTo("Invalid value for 'id'");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");
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
}
