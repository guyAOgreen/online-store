package com.example.store.mapper;

import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderDTO orderToOrderDTO(Order order);

    List<OrderDTO> ordersToOrderDTOs(List<Order> orders);

    OrderCustomerDTO orderToOrderCustomerDTO(Customer customer);

    OrderProductDTO productToOrderProductDTO(Product product);

    /**
     * The order's products in ascending ID order. Hibernate already hands the set over sorted ({@code @OrderBy}), so
     * the sort is linear on that input; it is kept so the response contract holds whatever collection the entity
     * carries, as {@link ProductMapper#ordersToIds} does for the other direction.
     */
    default List<OrderProductDTO> productsToOrderProductDTOs(Set<Product> products) {
        return products.stream()
                .sorted(Comparator.comparing(Product::getId))
                .map(this::productToOrderProductDTO)
                .toList();
    }
}
