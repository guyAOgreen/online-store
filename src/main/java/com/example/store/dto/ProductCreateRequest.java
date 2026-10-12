package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /products}. Lists exactly the field a create accepts; anything else (an {@code id}, a list of
 * {@code orders}) is rejected by Jackson. The constraints mirror {@code product.description VARCHAR(255) NOT NULL} and
 * are the same rules as the customer name and order description (#4): blank is rejected because an undescribed product
 * is not a useful record, and NUL because PostgreSQL cannot store it in text.
 */
public record ProductCreateRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\u0000]*", message = "must not contain NUL characters")
                String description) {}
