package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {
    private Long id;
    private String description;
    private OrderCustomerDTO customer;

    /** In ascending ID order; empty for an order created before products existed (#7's legacy policy). */
    private List<OrderProductDTO> products;
}
