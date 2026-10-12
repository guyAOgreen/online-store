package com.example.store.controller;

import com.example.store.dto.CustomerReference;
import com.example.store.dto.OrderCreateRequest;
import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.dto.OrderProductDTO;
import com.example.store.dto.ProductReference;
import com.example.store.service.OrderService;
import com.example.store.service.ReferencedEntityNotFoundException;
import com.example.store.service.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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

    private static final String PRODUCTS = "\"products\": [{\"id\": 1}, {\"id\": 2}]";
    private static final String VALID_BODY =
            "{\"description\": \"Test Order\", \"customer\": {\"id\": 1}, " + PRODUCTS + "}";
    private static final OrderCreateRequest VALID_REQUEST = new OrderCreateRequest(
            "Test Order", new CustomerReference(1L), List.of(new ProductReference(1L), new ProductReference(2L)));

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
        order.setProducts(List.of(product(1L, "Lamp"), product(2L, "Chair")));
    }

    @Test
    void createReturns201WithTheCreatedOrderItsCustomerAndItsProducts() throws Exception {
        when(orderService.create(VALID_REQUEST)).thenReturn(order);

        postOrder(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.id").value(1))
                .andExpect(jsonPath("$.customer.name").value("John Doe"))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value(1))
                .andExpect(jsonPath("$.products[0].description").value("Lamp"))
                .andExpect(jsonPath("$.products[1].id").value(2))
                .andExpect(jsonPath("$.products[1].description").value("Chair"));
    }

    @Test
    void getAllReturnsEveryOrderWithItsProducts() throws Exception {
        when(orderService.findAll()).thenReturn(List.of(order));

        mockMvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Test Order"))
                .andExpect(jsonPath("$[0].customer.name").value("John Doe"))
                .andExpect(jsonPath("$[0].products[0].id").value(1))
                .andExpect(jsonPath("$[0].products[1].description").value("Chair"));
    }

    @Test
    void getByIdReturns200WithTheOrderItsCustomerAndItsProducts() throws Exception {
        when(orderService.findById(1L)).thenReturn(order);

        mockMvc.perform(get("/order/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.id").value(1))
                .andExpect(jsonPath("$.customer.name").value("John Doe"))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value(1))
                .andExpect(jsonPath("$.products[0].description").value("Lamp"));
    }

    @Test
    void legacyOrderWithoutProductsSerialisesAnEmptyArray() throws Exception {
        order.setProducts(List.of());
        when(orderService.findById(1L)).thenReturn(order);

        mockMvc.perform(get("/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products").isEmpty());
    }

    @Test
    void getByIdForAnOrderThatDoesNotExistIs404() throws Exception {
        when(orderService.findById(12_345L)).thenThrow(new ResourceNotFoundException("Order", 12_345L));

        mockMvc.perform(get("/order/12345"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order 12345 does not exist"))
                .andExpect(jsonPath("$.instance").value("/order/12345"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void getByIdWithANonNumericIdIs400NamingThePathVariable() throws Exception {
        mockMvc.perform(get("/order/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"))
                .andExpect(jsonPath("$.instance").value("/order/abc"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
        verifyNoInteractions(orderService);
    }

    @Test
    void getByIdWithAnIdTooLargeForALongIs400() throws Exception {
        mockMvc.perform(get("/order/99999999999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void customerThatDoesNotExistIs422() throws Exception {
        when(orderService.create(VALID_REQUEST)).thenThrow(new ReferencedEntityNotFoundException("Customer", 1L));

        postOrder(VALID_BODY)
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
    void productsThatDoNotExistAre422NamingEveryMissingId() throws Exception {
        when(orderService.create(VALID_REQUEST))
                .thenThrow(new ReferencedEntityNotFoundException("Product", List.of(1L, 2L)));

        postOrder(VALID_BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Products 1, 2 do not exist"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void missingDescriptionIs400() throws Exception {
        postOrder("{\"customer\": {\"id\": 1}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void whitespaceOnlyDescriptionIs400() throws Exception {
        postOrder("{\"description\": \"  \", \"customer\": {\"id\": 1}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void descriptionContainingNulIs400() throws Exception {
        postOrder("{\"description\": \"Nul\\u0000Byte\", \"customer\": {\"id\": 1}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void descriptionLongerThan255Is400() throws Exception {
        postOrder("{\"description\": \"" + "x".repeat(256) + "\", \"customer\": {\"id\": 1}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(orderService);
    }

    @Test
    void absentCustomerIs400() throws Exception {
        postOrder("{\"description\": \"x\", " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("customer"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullCustomerIs400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": null, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer"));
        verifyNoInteractions(orderService);
    }

    @Test
    void emptyCustomerObjectIs400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullCustomerIdIs400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": null}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nonNumericCustomerIdIs400NamingTheField() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": \"abc\"}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'customer.id'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
        verifyNoInteractions(orderService);
    }

    @Test
    void fractionalCustomerIdIs400RatherThanTruncatedToAnotherCustomer() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 7.9}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'customer.id'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
        verifyNoInteractions(orderService);
    }

    @Test
    void absentProductsIs400() throws Exception {
        // The pre-#8 request shape: an order with no products is no longer accepted.
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("products"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullProductsIs400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("products"));
        verifyNoInteractions(orderService);
    }

    @Test
    void emptyProductsIs400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("products"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be empty"));
        verifyNoInteractions(orderService);
    }

    @Test
    void duplicateProductIdsAre400NamingTheList() throws Exception {
        postOrder(
                        "{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": [{\"id\": 1}, {\"id\": 2}, {\"id\": 1}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("products"))
                .andExpect(jsonPath("$.errors[0].message").value("must not contain the same product more than once"));
        verifyNoInteractions(orderService);
    }

    @Test
    void emptyProductObjectIs400NamingTheElement() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": [{\"id\": 1}, {}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("products[1].id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nullProductElementIs400NamingTheElement() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": [null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("products[0]"));
        verifyNoInteractions(orderService);
    }

    @Test
    void nonNumericProductIdIs400NamingTheElement() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": [{\"id\": \"abc\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'products[0].id'"))
                .andExpect(jsonPath("$.errors[0].field").value("products[0].id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void productsThatAreNotAnArrayAre400() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": {\"id\": 1}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for field 'products'"))
                .andExpect(jsonPath("$.errors[0].field").value("products"));
        verifyNoInteractions(orderService);
    }

    @Test
    void unknownFieldInsideAProductIs400WithTheNestedPath() throws Exception {
        postOrder(
                        "{\"description\": \"x\", \"customer\": {\"id\": 1}, \"products\": [{\"id\": 1, \"description\": \"Lamp\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'products[0].description'"))
                .andExpect(jsonPath("$.errors[0].field").value("products[0].description"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(orderService);
    }

    @Test
    void suppliedOrderIdIs400AsAnUnknownField() throws Exception {
        postOrder("{\"id\": 1, \"description\": \"x\", \"customer\": {\"id\": 1}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'id'"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(orderService);
    }

    @Test
    void unknownFieldInsideCustomerIs400WithTheNestedPath() throws Exception {
        postOrder("{\"description\": \"x\", \"customer\": {\"id\": 1, \"name\": \"John\"}, " + PRODUCTS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'customer.name'"))
                .andExpect(jsonPath("$.errors[0].field").value("customer.name"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(orderService);
    }

    @Test
    void severalViolationsAreAllReportedSortedByField() throws Exception {
        postOrder("{\"customer\": {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("customer.id"))
                .andExpect(jsonPath("$.errors[1].field").value("description"))
                .andExpect(jsonPath("$.errors[2].field").value("products"));
        verifyNoInteractions(orderService);
    }

    private ResultActions postOrder(String body) throws Exception {
        return mockMvc.perform(
                post("/order").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static OrderProductDTO product(long id, String description) {
        OrderProductDTO product = new OrderProductDTO();
        product.setId(id);
        product.setDescription(description);
        return product;
    }
}
