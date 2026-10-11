package com.example.store.dto;

import jakarta.validation.constraints.NotNull;

/** Reference to an existing customer by ID, the nested {@code customer} object of {@code POST /order}. */
public record CustomerReference(@NotNull Long id) {}
