package com.example.store.controller;

import com.example.store.dto.CustomerCreateRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    private CustomerDTO customer;

    @BeforeEach
    void setUp() {
        customer = new CustomerDTO();
        customer.setId(1L);
        customer.setName("John Doe");
        customer.setOrders(List.of());
    }

    @Test
    void createReturns201WithTheCreatedCustomer() throws Exception {
        when(customerService.create(new CustomerCreateRequest("John Doe"))).thenReturn(customer);

        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "John Doe"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    void getWithoutAQueryAsksTheServiceForEveryCustomer() throws Exception {
        when(customerService.find(null)).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].orders").isEmpty());
    }

    @Test
    void queryParameterReachesTheServiceUntouched() throws Exception {
        when(customerService.find(" Ann ")).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer").param("q", " Ann "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"));
        verify(customerService).find(" Ann ");
    }

    @Test
    void emptyQueryParameterReachesTheServiceAsAnEmptyString() throws Exception {
        when(customerService.find("")).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer?q=")).andExpect(status().isOk());
        verify(customerService).find("");
    }

    @Test
    void noMatchesIsAnEmptyJsonArray() throws Exception {
        when(customerService.find("zzz")).thenReturn(List.of());

        mockMvc.perform(get("/customer").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void missingNameIs400WithFieldError() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andExpect(jsonPath("$.instance").value("/customer"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        verifyNoInteractions(customerService);
    }

    @Test
    void blankNameIs400() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(customerService);
    }

    @Test
    void whitespaceOnlyNameIs400() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(customerService);
    }

    @Test
    void nameContainingNulIs400() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Nul\\u0000Byte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(customerService);
    }

    @Test
    void nameLongerThan255Is400() throws Exception {
        String tooLong = "x".repeat(256);
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(customerService);
    }

    @Test
    void nameOfExactly255IsAccepted() throws Exception {
        String maxLength = "x".repeat(255);
        when(customerService.create(new CustomerCreateRequest(maxLength))).thenReturn(customer);

        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", maxLength))))
                .andExpect(status().isCreated());
    }

    @Test
    void suppliedIdIs400NamingTheUnknownField() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 1, \"name\": \"John Doe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Unknown field 'id'"))
                .andExpect(jsonPath("$.instance").value("/customer"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(customerService);
    }

    @Test
    void nullIdIsStillAnUnknownField() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": null, \"name\": \"John Doe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(customerService);
    }

    @Test
    void nestedOrdersAre400AsAnUnknownField() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"John Doe\", \"orders\": [{\"description\": \"x\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(customerService);
    }

    @Test
    void malformedJsonIs400WithDetailAndNoErrors() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed or missing request body"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(customerService);
    }

    @Test
    void emptyBodyIs400WithDetailAndNoErrors() throws Exception {
        mockMvc.perform(post("/customer").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed or missing request body"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(customerService);
    }

    @Test
    void jsonArrayInsteadOfObjectIs400() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed or missing request body"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(customerService);
    }

    @Test
    void duplicateJsonKeyIs400NotA500() throws Exception {
        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"a\", \"name\": \"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed or missing request body"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(customerService);
    }

    @Test
    void unsupportedMediaTypeIs415AsProblemJson() throws Exception {
        mockMvc.perform(post("/customer").contentType(MediaType.TEXT_PLAIN).content("name=John"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(customerService);
    }
}
