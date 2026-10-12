package com.example.store.mapper;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.CustomerOrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;

import org.mapstruct.Mapper;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerDTO customerToCustomerDTO(Customer customer);

    List<CustomerDTO> customersToCustomerDTOs(List<Customer> customer);

    CustomerOrderDTO orderToCustomerOrderDTO(Order order);

    /**
     * The customer's orders in ascending ID order. Hibernate already hands the list over sorted ({@code @OrderBy}), so
     * the sort is linear on that input; it is kept so the response contract holds whatever list the entity carries, as
     * {@link OrderMapper#productsToOrderProductDTOs} does since #8.
     */
    default List<CustomerOrderDTO> ordersToCustomerOrderDTOs(List<Order> orders) {
        return orders.stream()
                .sorted(Comparator.comparing(Order::getId))
                .map(this::orderToCustomerOrderDTO)
                .toList();
    }
}
