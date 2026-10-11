package com.example.store.controller;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.service.CustomerService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Lists customers. With {@code q}, only those whose name contains every word of it; see
     * {@link CustomerService#find(String)}. An absent or blank {@code q} lists every customer.
     */
    @GetMapping
    public List<CustomerDTO> getCustomers(@RequestParam(name = "q", required = false) String query) {
        return customerService.find(query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        return customerService.create(request);
    }
}
