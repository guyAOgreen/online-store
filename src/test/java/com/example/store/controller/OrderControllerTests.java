package com.example.store.controller;

import com.example.store.dto.CustomerReference;
import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.service.OrderService;
import com.example.store.service.ReferencedEntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTests {

    private static final String VALID_BODY = "{\"description\": \"Test Order\", \"customer\": {\"id\": 1}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private OrderDTO order;

    @BeforeEach
    void setUp() {
        OrderCustomerDTO customer = new OrderCustomerDTO();
        customer.setId(1L);
        customer.setName("John Doe");
        order = new OrderDTO();
        order.setId(1L);
        order.setDescription("Test Order");
        order.setCustomer(customer);
    }

    @Test
    void createReturns201WithTheCreatedOrderAndItsCustomer() throws Exception {
        when(orderService.create(new OrderCreateRequest("Test Order", new CustomerReference(1L))))
                .thenReturn(order);

        mockMvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.id").value(1))
                .andExpect(jsonPath("$.customer.name").value("John Doe"));
    }

    @Test
    void getAllReturnsEveryOrder() throws Exception {
        when(orderService.findAll()).thenReturn(List.of(order));

        mockMvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Test Order"))
                .andExpect(jsonPath("$[0].customer.name").value("John Doe"));
    }

    @Test
    void missingCustomerThatDoesNotExistIs422() throws Exception {
        when(orderService.create(new OrderCreateRequest("Test Order", new CustomerReference(1L))))
                .thenThrow(new ReferencedEntityNotFoundException("Customer", 1L));

        mockMvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Customer 1 does not exist"))
                .andExpect(jsonPath("$.instance").value("/order"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void missingDescriptionIs400() throws Exception {
        mockMvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).content("{\"customer\": {\"id\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void whitespaceOnlyDescriptionIs400() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"  \", \"customer\": {\"id\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void descriptionContainingNulIs400() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Nul\\u0000Byte\", \"customer\": {\"id\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void descriptionLongerThan255Is400() throws Exception {
        String tooLong = "x".repeat(256);
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"" + tooLong + "\", \"customer\": {\"id\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void absentCustomerIs400() throws Exception {
        mockMvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("customer"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullCustomerIs400() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer"));
        verifyNoInteractions(orderService);
    }

    @Test
    void emptyCustomerObjectIs400() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullCustomerIdIs400() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": {\"id\": null}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nonNumericCustomerIdIs400NamingTheField() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": {\"id\": \"abc\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'customer.id'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
        verifyNoInteractions(orderService);
    }

    @Test
    void fractionalCustomerIdIs400RatherThanTruncatedToAnotherCustomer() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": {\"id\": 7.9}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'customer.id'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
        verifyNoInteractions(orderService);
    }

    @Test
    void suppliedOrderIdIs400AsAnUnknownField() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 1, \"description\": \"x\", \"customer\": {\"id\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'id'"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void unknownFieldInsideCustomerIs400WithTheNestedPath() throws Exception {
        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"customer\": {\"id\": 1, \"name\": \"John\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'customer.name'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.name"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(orderService);
    }

    @Test
    void severalViolationsAreAllReportedSortedByField() throws Exception {
        mockMvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).content("{\"customer\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[1].field").value("description"));
        verifyNoInteractions(orderService);
    }
}
