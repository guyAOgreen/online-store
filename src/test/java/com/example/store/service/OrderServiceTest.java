package com.example.store.service;

import com.example.store.dto.CustomerReference;
import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.mapper.OrderMapperImpl;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    private OrderService orderService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, customerRepository, new OrderMapperImpl());
        customer = new Customer();
        customer.setId(7L);
        customer.setName("Grace Hopper");
    }

    @Test
    void createResolvesTheCustomerAndSavesTheOrderWithoutAClientId() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        AtomicReference<Long> idAtSave = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            idAtSave.set(saved.getId());
            saved.setId(10_001L);
            return saved;
        });

        OrderDTO created = orderService.create(new OrderCreateRequest("Compiler", new CustomerReference(7L)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(idAtSave.get()).as("entity handed to save has no client ID").isNull();
        assertThat(captor.getValue().getDescription()).isEqualTo("Compiler");
        assertThat(captor.getValue().getCustomer()).isSameAs(customer);

        assertThat(created.getId()).isEqualTo(10_001L);
        assertThat(created.getDescription()).isEqualTo("Compiler");
        assertThat(created.getCustomer().getId()).isEqualTo(7L);
        assertThat(created.getCustomer().getName()).isEqualTo("Grace Hopper");
    }

    @Test
    void createThrowsAndSavesNothingWhenTheCustomerDoesNotExist() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(new OrderCreateRequest("Compiler", new CustomerReference(999L))))
                .isInstanceOf(ReferencedEntityNotFoundException.class)
                .hasMessage("Customer 999 does not exist");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void findAllMapsEveryOrderWithItsCustomer() {
        Order order = new Order();
        order.setId(1L);
        order.setDescription("Compiler");
        order.setCustomer(customer);
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDTO> all = orderService.findAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getDescription()).isEqualTo("Compiler");
        assertThat(all.get(0).getCustomer().getName()).isEqualTo("Grace Hopper");
    }

    @Test
    void findByIdMapsTheOrderWithItsCustomer() {
        Order order = new Order();
        order.setId(42L);
        order.setDescription("Compiler");
        order.setCustomer(customer);
        when(orderRepository.findWithCustomerById(42L)).thenReturn(Optional.of(order));

        OrderDTO found = orderService.findById(42L);

        assertThat(found.getId()).isEqualTo(42L);
        assertThat(found.getDescription()).isEqualTo("Compiler");
        assertThat(found.getCustomer().getId()).isEqualTo(7L);
        assertThat(found.getCustomer().getName()).isEqualTo("Grace Hopper");
    }

    @Test
    void findByIdThrowsWhenTheOrderDoesNotExist() {
        when(orderRepository.findWithCustomerById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 999 does not exist");
    }
}
