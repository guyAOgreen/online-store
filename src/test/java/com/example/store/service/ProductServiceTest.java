package com.example.store.service;

import com.example.store.dto.ProductCreateRequest;
import com.example.store.dto.ProductDTO;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.ProductMapperImpl;
import com.example.store.repository.ProductRepository;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ProductMapperImpl());
    }

    @Test
    void createSavesNewProductWithOnlyTheDescriptionAndReturnsNoOrders() {
        AtomicReference<Long> idAtSave = new AtomicReference<>();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            idAtSave.set(saved.getId());
            saved.setId(5L);
            return saved;
        });

        ProductDTO created = productService.create(new ProductCreateRequest("Lamp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(idAtSave.get()).as("entity handed to save has no client ID").isNull();
        assertThat(captor.getValue().getDescription()).isEqualTo("Lamp");
        assertThat(captor.getValue().getOrders()).isEmpty();

        assertThat(created.getId()).isEqualTo(5L);
        assertThat(created.getDescription()).isEqualTo("Lamp");
        assertThat(created.getOrders()).isEmpty();
    }

    @Test
    void findAllMapsEveryProductWithTheIdsOfItsOrders() {
        Product inTwoOrders = product(1L, "Lamp", 10L, 20L);
        Product unused = product(2L, "Chair");
        when(productRepository.findAllWithOrders()).thenReturn(List.of(inTwoOrders, unused));

        List<ProductDTO> all = productService.findAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getId()).isEqualTo(1L);
        assertThat(all.get(0).getDescription()).isEqualTo("Lamp");
        assertThat(all.get(0).getOrders()).containsExactly(10L, 20L);
        assertThat(all.get(1).getId()).isEqualTo(2L);
        assertThat(all.get(1).getOrders()).isEmpty();
    }

    @Test
    void findByIdMapsTheProductWithTheIdsOfItsOrders() {
        when(productRepository.findWithOrdersById(42L)).thenReturn(Optional.of(product(42L, "Lamp", 7L)));

        ProductDTO found = productService.findById(42L);

        assertThat(found.getId()).isEqualTo(42L);
        assertThat(found.getDescription()).isEqualTo("Lamp");
        assertThat(found.getOrders()).containsExactly(7L);
    }

    @Test
    void findByIdThrowsWhenTheProductDoesNotExist() {
        when(productRepository.findWithOrdersById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 999 does not exist");
    }

    private static Product product(long id, String description, long... orderIds) {
        Product product = new Product();
        product.setId(id);
        product.setDescription(description);
        for (long orderId : orderIds) {
            Order order = new Order();
            order.setId(orderId);
            product.getOrders().add(order);
        }
        return product;
    }
}
