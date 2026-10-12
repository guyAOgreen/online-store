package com.example.store.mapper;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.CustomerOrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The nested orders of a customer response are in ascending ID order whatever order the entity's list holds them in
 * (#9), as {@link OrderMapper} and {@link ProductMapper} guarantee for their collections since #8.
 */
class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapperImpl();

    @Test
    void ordersAreMappedInAscendingIdOrderWhateverOrderTheListHolds() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Ada");
        customer.getOrders().add(order(30L, "Third"));
        customer.getOrders().add(order(10L, "First"));
        customer.getOrders().add(order(20L, "Second"));

        CustomerDTO dto = mapper.customerToCustomerDTO(customer);

        assertThat(dto.getOrders())
                .extracting(CustomerOrderDTO::getId, CustomerOrderDTO::getDescription)
                .containsExactly(tuple(10L, "First"), tuple(20L, "Second"), tuple(30L, "Third"));
    }

    @Test
    void aCustomerWithoutOrdersMapsToAnEmptyList() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Ada");

        List<CustomerDTO> dtos = mapper.customersToCustomerDTOs(List.of(customer));

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getOrders()).isNotNull().isEmpty();
    }

    private static Order order(long id, String description) {
        Order order = new Order();
        order.setId(id);
        order.setDescription(description);
        return order;
    }
}
