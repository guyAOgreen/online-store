package com.example.store.dto;

import lombok.Data;

/**
 * A product as it appears inside an order response: ID and description only. The product's own containing orders are
 * left out so the graph stays finite, as {@link OrderCustomerDTO} does for the customer.
 */
@Data
public class OrderProductDTO {
    private Long id;
    private String description;
}
