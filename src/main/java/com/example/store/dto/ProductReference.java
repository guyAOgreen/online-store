package com.example.store.dto;

import jakarta.validation.constraints.NotNull;

/** Reference to an existing product by ID, one element of the {@code products} array of {@code POST /order}. */
public record ProductReference(@NotNull Long id) {}
