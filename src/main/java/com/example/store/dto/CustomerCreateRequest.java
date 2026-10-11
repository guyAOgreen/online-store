package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /customer}. Lists exactly the fields a create accepts; anything else is rejected by Jackson. The
 * constraints mirror {@code customer.name VARCHAR(255) NOT NULL}; blank is additionally rejected because an unnamed
 * customer is not a useful record, and NUL because PostgreSQL cannot store it in text (the insert would fail as a 500).
 */
public record CustomerCreateRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\u0000]*", message = "must not contain NUL characters")
                String name) {}
