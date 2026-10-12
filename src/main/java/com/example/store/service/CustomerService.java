package com.example.store.service;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.CustomerSpecifications;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    /** Every read lists customers by ID; see {@link CustomerRepository#findAll(Specification, Sort)} for why. */
    private static final Sort BY_ID = Sort.by("id");

    /** Every customer with their orders, loaded in one statement and mapped inside the transaction (#9). */
    @Transactional(readOnly = true)
    public List<CustomerDTO> findAll() {
        return customerMapper.customersToCustomerDTOs(customerRepository.findAll(CustomerSpecifications.all(), BY_ID));
    }

    /**
     * Customers whose name contains every word of {@code query} (see {@link CustomerSpecifications}). A {@code null} or
     * blank query is not a search and returns the full listing; a query no stored name can contain (one with a NUL) is
     * answered with the empty list without a round trip. The filter runs in the database and the matching customers
     * come back with their orders in the one statement, mapped inside the transaction, as in {@link #findAll()}.
     */
    @Transactional(readOnly = true)
    public List<CustomerDTO> find(String query) {
        List<String> words = CustomerSpecifications.words(query);
        if (words.isEmpty()) {
            return findAll();
        }
        if (!CustomerSpecifications.canMatch(words)) {
            return List.of();
        }
        return customerMapper.customersToCustomerDTOs(
                customerRepository.findAll(CustomerSpecifications.nameContainsEachOf(words), BY_ID));
    }

    /**
     * Creates a customer from the request. The entity is built here so no client-supplied field other than {@code name}
     * can reach {@code save}.
     */
    @Transactional
    public CustomerDTO create(CustomerCreateRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        return customerMapper.customerToCustomerDTO(customerRepository.save(customer));
    }
}
