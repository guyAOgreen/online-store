package com.example.store.service;

import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.ProductReference;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    /** Every order with its customer and products, loaded in one statement and mapped inside the transaction. */
    @Transactional(readOnly = true)
    public List<OrderDTO> findAll() {
        return orderMapper.ordersToOrderDTOs(orderRepository.findAllWithCustomerAndProducts());
    }

    /** Looks up one order with its customer and products; the mapping happens inside the transaction. */
    @Transactional(readOnly = true)
    public OrderDTO findById(Long id) {
        return orderRepository
                .findWithCustomerAndProductsById(id)
                .map(orderMapper::orderToOrderDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    /**
     * Creates an order for an existing customer containing existing products. The customer and then the products are
     * loaded first (one statement each) so a dangling reference becomes a client error before anything is written; the
     * foreign keys remain the final check. The order and its {@code order_product} rows are saved in this one
     * transaction, so nothing partial can remain.
     */
    @Transactional
    public OrderDTO create(OrderCreateRequest request) {
        Long customerId = request.customer().id();
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> new ReferencedEntityNotFoundException("Customer", customerId));
        List<Product> products = resolveProducts(request.products());
        Order order = new Order();
        order.setDescription(request.description());
        order.setCustomer(customer);
        order.getProducts().addAll(products);
        return orderMapper.orderToOrderDTO(orderRepository.save(order));
    }

    /**
     * The referenced products, or {@link ReferencedEntityNotFoundException} naming every ID that has no row. The check
     * is by ID rather than by count, so it does not rely on the request having passed {@code @DistinctProducts}.
     */
    private List<Product> resolveProducts(List<ProductReference> references) {
        List<Long> ids = references.stream().map(ProductReference::id).toList();
        List<Product> products = productRepository.findAllById(ids);
        Set<Long> missing = new TreeSet<>(ids);
        products.forEach(product -> missing.remove(product.getId()));
        if (!missing.isEmpty()) {
            throw new ReferencedEntityNotFoundException("Product", missing);
        }
        return products;
    }
}
