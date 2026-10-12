package com.example.store.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A customer and the orders placed for them. Equality is identity-based for the reason given on {@link Product}: with
 * {@code @Data}, hashing or printing a customer would initialise its lazy orders collection.
 */
@Entity
@Getter
@Setter
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * Ordered by ID so the nested array of a customer response is the same on every call (#9), as on the other side.
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<Order> orders = new ArrayList<>();
}
