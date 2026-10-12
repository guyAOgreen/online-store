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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Sort BY_ID = Sort.by("id");

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
        when(customerRepository.findAll(ArgumentMatchers.<Specification<Customer>>any(), eq(BY_ID)))
                .thenReturn(List.of(first, second));

        List<CustomerDTO> all = customerService.findAll();

        assertThat(all)
                .extracting(CustomerDTO::getId, CustomerDTO::getName)
                .containsExactly(tuple(1L, "First"), tuple(2L, "Second"));
        assertThat(captureSpecification().toPredicate(null, null, null))
                .as("the full listing has no predicate")
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankQueryListsEveryCustomerWithoutFiltering(String query) {
        Customer only = new Customer();
        only.setId(1L);
        only.setName("First");
        when(customerRepository.findAll(ArgumentMatchers.<Specification<Customer>>any(), eq(BY_ID)))
                .thenReturn(List.of(only));

        List<CustomerDTO> found = customerService.find(query);

        assertThat(found).extracting(CustomerDTO::getName).containsExactly("First");
        assertThat(captureSpecification().toPredicate(null, null, null))
                .as("a blank query is the unfiltered listing")
                .isNull();
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
        when(customerRepository.findAll(ArgumentMatchers.<Specification<Customer>>any(), eq(BY_ID)))
                .thenReturn(List.of(dianne));

        List<CustomerDTO> found = customerService.find(" ann ");

        assertThat(found)
                .extracting(CustomerDTO::getId, CustomerDTO::getName)
                .containsExactly(tuple(4L, "Dianne Lemke"));
        assertThat(found.get(0).getOrders()).isEmpty();
        assertThat(captureSpecification()).as("the words become a predicate").isNotNull();
        verify(customerRepository, never()).findAll();
    }

    /** The specification handed to the one repository call every read makes; every read sorts by customer ID. */
    private Specification<Customer> captureSpecification() {
        ArgumentCaptor<Specification<Customer>> captor = ArgumentCaptor.captor();
        verify(customerRepository).findAll(captor.capture(), eq(BY_ID));
        return captor.getValue();
    }
}
