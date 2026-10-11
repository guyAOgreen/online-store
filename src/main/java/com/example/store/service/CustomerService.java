package com.example.store.service;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;

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
