package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /customer}. Lists exactly the fields a create accepts; anything else is rejected by Jackson. The
 * constraints mirror {@code customer.name VARCHAR(255) NOT NULL}; blank is additionally rejected because an unnamed
 * customer is not a useful record.
 */
public record CustomerCreateRequest(@NotBlank @Size(max = 255) String name) {}
