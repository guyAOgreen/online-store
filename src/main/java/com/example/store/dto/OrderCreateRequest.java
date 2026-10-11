package com.example.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /order}. Lists exactly the fields a create accepts; anything else is rejected by Jackson. The
 * constraints mirror {@code "order".description VARCHAR(255) NOT NULL} and {@code customer_id BIGINT NOT NULL}; NUL is
 * rejected because PostgreSQL cannot store it in text, and {@code @Valid} cascades into the reference so an empty
 * {@code customer} object fails here rather than in the service.
 */
public record OrderCreateRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\u0000]*", message = "must not contain NUL characters")
                String description,
        @NotNull @Valid CustomerReference customer) {}
