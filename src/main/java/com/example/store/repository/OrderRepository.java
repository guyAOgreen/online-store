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
     * out because Spring Data would otherwise read the {@code And} in the method name as a predicate, and it orders by
     * the order's own ID because Hibernate appends the fetched collection's {@code @OrderBy} to the SQL: without a root
     * ordering the list would come back sorted by each order's lowest product ID, legacy orders last.
     */
    @Query("select o from Order o order by o.id")
    @EntityGraph(attributePaths = {"customer", "products"})
    List<Order> findAllWithCustomerAndProducts();

    /**
     * One order with its customer and products in a single statement; see {@link #findAllWithCustomerAndProducts()}.
     */
    @EntityGraph(attributePaths = {"customer", "products"})
    Optional<Order> findWithCustomerAndProductsById(Long id);
}
