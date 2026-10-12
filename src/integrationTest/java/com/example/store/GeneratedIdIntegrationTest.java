package com.example.store;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.example.store.dto.ProductDTO;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for #3: the seed data inserts explicit IDs without advancing the {@code BIGSERIAL} sequences, so on a
 * fresh database the first POST used to collide with ID 1. IDs are asserted as outside the seeded range rather than
 * exact, because {@link DatabaseCleaner} restores rows but deliberately leaves sequences where they are.
 */
class GeneratedIdIntegrationTest extends AbstractIntegrationTest {

    private static final long LAST_SEEDED_CUSTOMER_ID = 100;
    private static final long LAST_SEEDED_ORDER_ID = 10_000;

    @Test
    void createsCustomerWithGeneratedIdAfterSeedData() throws Exception {
        CustomerDTO created = create("/customer", Map.of("name", "Generated Customer"), CustomerDTO.class);

        assertThat(created.getId()).isGreaterThan(LAST_SEEDED_CUSTOMER_ID);
        assertThat(created.getName()).isEqualTo("Generated Customer");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, created.getId()))
                .isEqualTo("Generated Customer");
        assertThat(count("customer")).isEqualTo(LAST_SEEDED_CUSTOMER_ID + 1);
    }

    @Test
    void createsOrderWithGeneratedIdAfterSeedData() throws Exception {
        ProductDTO product = create("/products", Map.of("description", "Generated product"), ProductDTO.class);

        OrderDTO created = create(
                "/order",
                Map.of(
                        "description",
                        "Generated order",
                        "customer",
                        Map.of("id", 1),
                        "products",
                        List.of(Map.of("id", product.getId()))),
                OrderDTO.class);

        assertThat(created.getId()).isGreaterThan(LAST_SEEDED_ORDER_ID);
        assertThat(created.getCustomer().getId()).isEqualTo(1L);
        assertThat(created.getProducts()).extracting(OrderProductDTO::getId).containsExactly(product.getId());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT customer_id FROM \"order\" WHERE id = ?", Long.class, created.getId()))
                .isEqualTo(1L);
        assertThat(count("\"order\"")).isEqualTo(LAST_SEEDED_ORDER_ID + 1);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM customer WHERE id = 1", String.class))
                .as("seeded parent row is untouched")
                .isEqualTo("Muriel Donnelly");
    }

    @Test
    void consecutiveCreatesGetDistinctIds() throws Exception {
        CustomerDTO first = create("/customer", Map.of("name", "First"), CustomerDTO.class);
        CustomerDTO second = create("/customer", Map.of("name", "Second"), CustomerDTO.class);

        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(count("customer")).isEqualTo(LAST_SEEDED_CUSTOMER_ID + 2);
    }

    private <T> T create(String path, Object body, Class<T> responseType) throws Exception {
        String json = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, responseType);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
