package com.example.store.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * An order placed by a customer, containing products (#7). This is the owning side of {@code order_product}: saving an
 * order writes its associations. Equality is identity-based for the reason given on {@link Product}; with
 * {@code @Data}, hashing an order would initialise its customer proxy and its products.
 */
@Entity
@Getter
@Setter
@Table(name = "\"order\"")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    /**
     * Products are independent of the orders that contain them, so nothing cascades. Ordered by ID so that iteration,
     * and therefore the array #8 serialises, is the same on every call and every instance.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "order_product",
            joinColumns = @JoinColumn(name = "order_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @OrderBy("id")
    private Set<Product> products = new HashSet<>();
}
