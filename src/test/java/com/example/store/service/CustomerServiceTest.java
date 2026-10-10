package com.example.store.service;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapperImpl;
import com.example.store.repository.CustomerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, new CustomerMapperImpl());
    }

    @Test
    void createSavesNewCustomerWithOnlyTheNameAndReturnsGeneratedId() {
        AtomicReference<Long> idAtSave = new AtomicReference<>();
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            idAtSave.set(saved.getId());
            saved.setId(101L);
            return saved;
        });

        CustomerDTO created = customerService.create(new CustomerCreateRequest("Ada Lovelace"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(idAtSave.get()).as("entity handed to save has no client ID").isNull();
        assertThat(captor.getValue().getName()).isEqualTo("Ada Lovelace");
        assertThat(captor.getValue().getOrders()).isEmpty();

        assertThat(created.getId()).isEqualTo(101L);
        assertThat(created.getName()).isEqualTo("Ada Lovelace");
        assertThat(created.getOrders()).isEmpty();
    }

    @Test
    void findAllMapsEveryCustomer() {
        Customer first = new Customer();
        first.setId(1L);
        first.setName("First");
        Customer second = new Customer();
        second.setId(2L);
        second.setName("Second");
        when(customerRepository.findAll()).thenReturn(List.of(first, second));

        List<CustomerDTO> all = customerService.findAll();

        assertThat(all)
                .extracting(CustomerDTO::getId, CustomerDTO::getName)
                .containsExactly(tuple(1L, "First"), tuple(2L, "Second"));
    }
}
