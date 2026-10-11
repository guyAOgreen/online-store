package com.example.store.controller;

import com.example.store.dto.ProductCreateRequest;
import com.example.store.dto.ProductDTO;
import com.example.store.service.ProductService;
import com.example.store.service.ResourceNotFoundException;

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

@WebMvcTest(ProductController.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private ProductDTO product;

    @BeforeEach
    void setUp() {
        product = new ProductDTO();
        product.setId(1L);
        product.setDescription("Lamp");
        product.setOrders(List.of(10L, 20L));
    }

    @Test
    void createReturns201WithTheCreatedProductAndNoOrders() throws Exception {
        ProductDTO created = new ProductDTO();
        created.setId(1L);
        created.setDescription("Lamp");
        created.setOrders(List.of());
        when(productService.create(new ProductCreateRequest("Lamp"))).thenReturn(created);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Lamp\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Lamp"))
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    void getAllReturnsEveryProductWithTheIdsOfItsOrders() throws Exception {
        when(productService.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].description").value("Lamp"))
                .andExpect(jsonPath("$[0].orders[0]").value(10))
                .andExpect(jsonPath("$[0].orders[1]").value(20));
    }

    @Test
    void getAllWithNoProductsIsAnEmptyArray() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getByIdReturns200WithTheProductAndTheIdsOfItsOrders() throws Exception {
        when(productService.findById(1L)).thenReturn(product);

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Lamp"))
                .andExpect(jsonPath("$.orders[0]").value(10))
                .andExpect(jsonPath("$.orders[1]").value(20));
    }

    @Test
    void getByIdForAProductThatDoesNotExistIs404() throws Exception {
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(get("/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product 99 does not exist"))
                .andExpect(jsonPath("$.instance").value("/products/99"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void getByIdWithANonNumericIdIs400NamingThePathVariable() throws Exception {
        mockMvc.perform(get("/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(productService);
    }

    @Test
    void missingDescriptionIs400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(productService);
    }

    @Test
    void whitespaceOnlyDescriptionIs400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(productService);
    }

    @Test
    void descriptionContainingNulIs400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Nul\\u0000Byte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(productService);
    }

    @Test
    void descriptionLongerThan255Is400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(productService);
    }

    @Test
    void suppliedProductIdIs400AsAnUnknownField() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 1, \"description\": \"Lamp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'id'"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
        verifyNoInteractions(productService);
    }

    @Test
    void suppliedOrdersAre400AsAnUnknownField() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Lamp\", \"orders\": [1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'orders'"))
                .andExpect(jsonPath("$.errors[0].field").value("orders"));
        verifyNoInteractions(productService);
    }

    @Test
    void malformedJsonIs400WithoutAFieldError() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.MALFORMED_BODY))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(productService);
    }
}
