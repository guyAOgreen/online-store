package com.example.store.repository;

import com.example.store.entity.Customer;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /**
     * The customers matching {@code spec} together with their orders in a single statement (#9). Both customer
     * responses carry every order of every customer, so left lazy the orders cost one round trip per batch of ten
     * customers; the entity graph joins them in, and Spring Data applies it to the specification query as it does to a
     * derived one. Callers pass a root sort because Hibernate appends the collection's {@code @OrderBy} to the SQL:
     * without one the customers would come back in the order of their lowest order ID.
     */
    @Override
    @EntityGraph(attributePaths = "orders")
    List<Customer> findAll(Specification<Customer> spec, Sort sort);
}
