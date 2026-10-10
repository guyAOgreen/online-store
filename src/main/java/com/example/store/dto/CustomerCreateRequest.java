package com.example.store.dto;

/** Body of {@code POST /customer}. Lists exactly the fields a create accepts; anything else is rejected. */
public record CustomerCreateRequest(String name) {}
