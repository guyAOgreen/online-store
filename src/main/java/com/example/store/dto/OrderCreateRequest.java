package com.example.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /order}. Lists exactly the fields a create accepts; anything else is rejected by Jackson. The
 * constraints mirror {@code "order".description VARCHAR(255) NOT NULL} and {@code customer_id BIGINT NOT NULL};
 * {@code @Valid} cascades into the reference so an empty {@code customer} object fails here rather than in the service.
 */
public record OrderCreateRequest(
        @NotBlank @Size(max = 255) String description, @NotNull @Valid CustomerReference customer) {}
