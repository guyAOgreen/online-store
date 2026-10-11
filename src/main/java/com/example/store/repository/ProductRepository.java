package com.example.store.repository;

import com.example.store.entity.Product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Every product together with its containing orders in a single statement. Both product responses carry the order
     * IDs, so a lazy {@link Product#getOrders()} would cost one further round trip per batch of products; the entity
     * graph joins them in. The JPQL is spelled out because Spring Data would otherwise read the {@code Or} inside
     * {@code Orders} as a predicate.
     */
    @Query("select p from Product p")
    @EntityGraph(attributePaths = "orders")
    List<Product> findAllWithOrders();

    /** One product with its containing orders in a single statement; see {@link #findAllWithOrders()}. */
    @EntityGraph(attributePaths = "orders")
    Optional<Product> findWithOrdersById(Long id);
}
