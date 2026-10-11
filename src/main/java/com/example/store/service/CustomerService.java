package com.example.store.service;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.CustomerSpecifications;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public List<CustomerDTO> findAll() {
        return customerMapper.customersToCustomerDTOs(customerRepository.findAll());
    }

    /**
     * Customers whose name contains every word of {@code query} (see {@link CustomerSpecifications}). A {@code null} or
     * blank query is not a search and returns the full listing. The filter runs in the database; the DTOs are mapped
     * inside the transaction so the nested orders load in the same session, as in {@link #findAll()}.
     */
    @Transactional(readOnly = true)
    public List<CustomerDTO> find(String query) {
        List<String> words = CustomerSpecifications.words(query);
        if (words.isEmpty()) {
            return findAll();
        }
        return customerMapper.customersToCustomerDTOs(
                customerRepository.findAll(CustomerSpecifications.nameContainsEachOf(words)));
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
