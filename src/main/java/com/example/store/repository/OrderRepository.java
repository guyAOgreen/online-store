package com.example.store.repository;

import com.example.store.entity.Order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Loads one order together with its customer in a single statement. The lookup response always carries the
     * customer, so a lazy {@link Order#getCustomer()} would cost a second round trip for every call; the entity graph
     * joins it in.
     */
    @EntityGraph(attributePaths = "customer")
    Optional<Order> findWithCustomerById(Long id);
}
