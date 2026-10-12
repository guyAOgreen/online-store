package com.example.store.mapper;

import com.example.store.dto.ProductDTO;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO productToProductDTO(Product product);

    List<ProductDTO> productsToProductDTOs(List<Product> products);

    /**
     * The containing orders as ascending IDs. Hibernate already hands the set over in ID order ({@code @OrderBy}), so
     * the sort is linear on that input; it is kept so the response contract holds whatever collection the entity
     * carries rather than depending on how it was loaded.
     */
    default List<Long> ordersToIds(Set<Order> orders) {
        return orders.stream().map(Order::getId).sorted().toList();
    }
}
