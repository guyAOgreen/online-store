package com.example.store.repository;

import com.example.store.entity.Order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Every order together with its customer and products in a single statement. Both are on every order response, so
     * left lazy they would cost one round trip per batch of orders; the entity graph joins them in. The JPQL is spelled
     * out because Spring Data would otherwise read the {@code And} in the method name as a predicate.
     */
    @Query("select o from Order o")
    @EntityGraph(attributePaths = {"customer", "products"})
    List<Order> findAllWithCustomerAndProducts();

    /**
     * One order with its customer and products in a single statement; see {@link #findAllWithCustomerAndProducts()}.
     */
    @EntityGraph(attributePaths = {"customer", "products"})
    Optional<Order> findWithCustomerAndProductsById(Long id);
}
