package com.example.store.dto;

import lombok.Data;

import java.util.List;

/**
 * A product as returned by {@code /products}. The containing orders appear as their IDs only, in ascending order: the
 * README asks for the order IDs, and an order's own representation (with its customer and products) would make the
 * graph circular.
 */
@Data
public class ProductDTO {
    private Long id;
    private String description;
    private List<Long> orders;
}
