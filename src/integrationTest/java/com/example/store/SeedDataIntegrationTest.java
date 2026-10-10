package com.example.store;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.CustomerOrderDTO;
import com.example.store.dto.OrderDTO;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reads the Liquibase-seeded data through the real controllers, repositories, JPA mappings and DTO mappers. A broken
 * relationship mapping or column name fails here.
 */
class SeedDataIntegrationTest extends AbstractIntegrationTest {

    @Test
    void returnsSeededCustomersWithTheirOrders() throws Exception {
        List<CustomerDTO> customers = getList("/customer", new TypeReference<>() {});

        assertThat(customers).hasSize(100);
        CustomerDTO muriel = byId(customers, 1L);
        assertThat(muriel.getName()).isEqualTo("Muriel Donnelly");
        assertThat(muriel.getOrders()).hasSize(96);
        assertThat(muriel.getOrders())
                .extracting(CustomerOrderDTO::getId, CustomerOrderDTO::getDescription)
                .contains(tuple(158L, "Awesome Concrete Shirt"), tuple(317L, "Small Granite Chicken"));
        assertThat(byId(customers, 100L).getName()).isEqualTo("Ed Glover");
        assertThat(byId(customers, 100L).getOrders()).hasSize(104);
    }

    @Test
    void returnsSeededOrdersWithTheirCustomer() throws Exception {
        List<OrderDTO> orders = getList("/order", new TypeReference<>() {});

        assertThat(orders).hasSize(10_000);
        OrderDTO first =
                orders.stream().filter(o -> o.getId() == 1L).findFirst().orElseThrow();
        assertThat(first.getDescription()).isEqualTo("Handcrafted Soft Chair");
        assertThat(first.getCustomer().getId()).isEqualTo(6L);
        assertThat(first.getCustomer().getName()).isEqualTo("Vicki Kutch");
        OrderDTO last =
                orders.stream().filter(o -> o.getId() == 10_000L).findFirst().orElseThrow();
        assertThat(last.getDescription()).isEqualTo("Bespoke Frozen Gloves");
        assertThat(last.getCustomer().getId()).isEqualTo(58L);
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> type) throws Exception {
        String json = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, type);
    }

    private static CustomerDTO byId(List<CustomerDTO> customers, long id) {
        return customers.stream().filter(c -> c.getId() == id).findFirst().orElseThrow();
    }
}
