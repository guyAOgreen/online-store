package com.example.store.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A product that orders contain (#7). The relationship is owned by {@link Order#getProducts()}; this side only reads
 * it.
 *
 * <p>Equality is deliberately identity-based rather than Lombok's {@code @Data}. A generated {@code hashCode} would
 * include the lazy {@code orders} collection, so merely adding a product to an order's set (or to any {@code HashSet})
 * would initialise that collection, issuing a query and, mid-load, tripping Hibernate's own consistency assertion.
 * Instances only ever meet inside one persistence context, where identity equality is correct.
 */
@Entity
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    /** Ordered by ID for the same reason as {@link Order#getProducts()}. */
    @ManyToMany(mappedBy = "products", fetch = FetchType.LAZY)
    @OrderBy("id")
    private Set<Order> orders = new HashSet<>();
}
