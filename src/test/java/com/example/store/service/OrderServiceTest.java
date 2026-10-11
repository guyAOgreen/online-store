package com.example.store.service;

import com.example.store.dto.CustomerReference;
import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.example.store.dto.ProductReference;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.OrderMapperImpl;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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

    @Mock
    private ProductRepository productRepository;

    private OrderService orderService;

    private Customer customer;
    private Product lamp;
    private Product chair;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, customerRepository, productRepository, new OrderMapperImpl());
        customer = new Customer();
        customer.setId(7L);
        customer.setName("Grace Hopper");
        lamp = product(3L, "Lamp");
        chair = product(4L, "Chair");
    }

    @Test
    void createResolvesTheCustomerAndProductsAndSavesTheOrderWithoutAClientId() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(4L, 3L))).thenReturn(List.of(chair, lamp));
        AtomicReference<Long> idAtSave = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            idAtSave.set(saved.getId());
            saved.setId(10_001L);
            return saved;
        });

        OrderDTO created = orderService.create(request("Compiler", 7L, 4L, 3L));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(idAtSave.get()).as("entity handed to save has no client ID").isNull();
        assertThat(captor.getValue().getDescription()).isEqualTo("Compiler");
        assertThat(captor.getValue().getCustomer()).isSameAs(customer);
        assertThat(captor.getValue().getProducts()).containsExactlyInAnyOrder(lamp, chair);

        assertThat(created.getId()).isEqualTo(10_001L);
        assertThat(created.getDescription()).isEqualTo("Compiler");
        assertThat(created.getCustomer().getId()).isEqualTo(7L);
        assertThat(created.getCustomer().getName()).isEqualTo("Grace Hopper");
        assertThat(created.getProducts())
                .as("products are returned in ID order whatever order the request listed them")
                .extracting(OrderProductDTO::getId, OrderProductDTO::getDescription)
                .containsExactly(tuple(3L, "Lamp"), tuple(4L, "Chair"));
    }

    @Test
    void createThrowsAndSavesNothingWhenTheCustomerDoesNotExist() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request("Compiler", 999L, 3L)))
                .isInstanceOf(ReferencedEntityNotFoundException.class)
                .hasMessage("Customer 999 does not exist");

        verifyNoInteractions(orderRepository, productRepository);
    }

    @Test
    void createThrowsAndSavesNothingWhenAProductDoesNotExist() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(3L, 999L))).thenReturn(List.of(lamp));

        assertThatThrownBy(() -> orderService.create(request("Compiler", 7L, 3L, 999L)))
                .isInstanceOf(ReferencedEntityNotFoundException.class)
                .hasMessage("Product 999 does not exist");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void createNamesEveryMissingProductInAscendingOrder() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(999L, 3L, 998L))).thenReturn(List.of(lamp));

        assertThatThrownBy(() -> orderService.create(request("Compiler", 7L, 999L, 3L, 998L)))
                .isInstanceOf(ReferencedEntityNotFoundException.class)
                .hasMessage("Products 998, 999 do not exist");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void findAllLoadsOrdersWithCustomersAndProductsAndMapsThem() {
        Order order = order(1L, "Compiler", chair, lamp);
        when(orderRepository.findAllWithCustomerAndProducts()).thenReturn(List.of(order));

        List<OrderDTO> all = orderService.findAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getDescription()).isEqualTo("Compiler");
        assertThat(all.get(0).getCustomer().getName()).isEqualTo("Grace Hopper");
        assertThat(all.get(0).getProducts())
                .extracting(OrderProductDTO::getId, OrderProductDTO::getDescription)
                .containsExactly(tuple(3L, "Lamp"), tuple(4L, "Chair"));
    }

    @Test
    void findByIdMapsTheOrderWithItsCustomerAndProducts() {
        when(orderRepository.findWithCustomerAndProductsById(42L))
                .thenReturn(Optional.of(order(42L, "Compiler", lamp)));

        OrderDTO found = orderService.findById(42L);

        assertThat(found.getId()).isEqualTo(42L);
        assertThat(found.getDescription()).isEqualTo("Compiler");
        assertThat(found.getCustomer().getId()).isEqualTo(7L);
        assertThat(found.getCustomer().getName()).isEqualTo("Grace Hopper");
        assertThat(found.getProducts())
                .extracting(OrderProductDTO::getId, OrderProductDTO::getDescription)
                .containsExactly(tuple(3L, "Lamp"));
    }

    @Test
    void findByIdMapsALegacyOrderWithoutProductsToAnEmptyList() {
        when(orderRepository.findWithCustomerAndProductsById(42L)).thenReturn(Optional.of(order(42L, "Legacy")));

        assertThat(orderService.findById(42L).getProducts()).isEmpty();
    }

    @Test
    void findByIdThrowsWhenTheOrderDoesNotExist() {
        when(orderRepository.findWithCustomerAndProductsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 999 does not exist");
    }

    private static OrderCreateRequest request(String description, long customerId, long... productIds) {
        List<ProductReference> products =
                Arrays.stream(productIds).mapToObj(ProductReference::new).toList();
        return new OrderCreateRequest(description, new CustomerReference(customerId), products);
    }

    private Order order(long id, String description, Product... products) {
        Order order = new Order();
        order.setId(id);
        order.setDescription(description);
        order.setCustomer(customer);
        order.getProducts().addAll(List.of(products));
        return order;
    }

    private static Product product(long id, String description) {
        Product product = new Product();
        product.setId(id);
        product.setDescription(description);
        return product;
    }
}
