package com.example.store.service;

import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public List<OrderDTO> findAll() {
        return orderMapper.ordersToOrderDTOs(orderRepository.findAll());
    }

    /** Looks up one order with its customer; the mapping happens inside the transaction. */
    @Transactional(readOnly = true)
    public OrderDTO findById(Long id) {
        return orderRepository
                .findWithCustomerById(id)
                .map(orderMapper::orderToOrderDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    /**
     * Creates an order for an existing customer. The customer is loaded first so a dangling reference becomes a client
     * error before anything is written; the foreign key remains the final check. This is the transaction later product
     * associations join, so an invalid product list rolls the order back with it.
     */
    @Transactional
    public OrderDTO create(OrderCreateRequest request) {
        Long customerId = request.customer().id();
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> new ReferencedEntityNotFoundException("Customer", customerId));
        Order order = new Order();
        order.setDescription(request.description());
        order.setCustomer(customer);
        return orderMapper.orderToOrderDTO(orderRepository.save(order));
    }
}
