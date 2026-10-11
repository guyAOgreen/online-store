package com.example.store.service;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapperImpl;
import com.example.store.repository.CustomerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankQueryListsEveryCustomerWithoutFiltering(String query) {
        Customer only = new Customer();
        only.setId(1L);
        only.setName("First");
        when(customerRepository.findAll()).thenReturn(List.of(only));

        List<CustomerDTO> found = customerService.find(query);

        assertThat(found).extracting(CustomerDTO::getName).containsExactly("First");
        verify(customerRepository, never()).findAll(ArgumentMatchers.<Specification<Customer>>any());
    }

    @Test
    void queryContainingNulIsEmptyWithoutAskingTheDatabase() {
        assertThat(customerService.find("ann\u0000")).isEmpty();
        verifyNoInteractions(customerRepository);
    }

    @Test
    void queryWithWordsFiltersThroughASpecificationAndMapsTheResult() {
        Customer dianne = new Customer();
        dianne.setId(4L);
        dianne.setName("Dianne Lemke");
        when(customerRepository.findAll(ArgumentMatchers.<Specification<Customer>>any()))
                .thenReturn(List.of(dianne));

        List<CustomerDTO> found = customerService.find(" ann ");

        assertThat(found)
                .extracting(CustomerDTO::getId, CustomerDTO::getName)
                .containsExactly(tuple(4L, "Dianne Lemke"));
        assertThat(found.get(0).getOrders()).isEmpty();
        verify(customerRepository, never()).findAll();
    }
}
