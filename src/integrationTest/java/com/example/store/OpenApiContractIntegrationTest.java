package com.example.store;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OpenAPI document is complete as well as correct (#10). {@link OpenApiDocumentTest} checks it parses and
 * {@link OpenApiContractConfig} checks every exchange in the suite against it; this test adds the other direction by
 * sending one request for every response the document promises and asserting the set of responses reached is exactly
 * the documented set. A status code documented for an operation that no request can produce, or one the implementation
 * produces that is not documented, both fail here. The requests double as the worked examples in the README.
 */
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    private static final long ABSENT_ID = 999_999;

    private final Set<String> reached = new LinkedHashSet<>();

    @Test
    void everyDocumentedResponseIsReachedByARequest() throws Exception {
        long product = exchange("POST", "/products", json(post("/products"), Map.of("description", "Lamp")), 201)
                .get("id")
                .asLong();
        exchange("POST", "/products", json(post("/products"), Map.of("description", " ")), 400);
        exchange("GET", "/products", get("/products"), 200);
        exchange("GET", "/products/{id}", get("/products/{id}", product), 200);
        exchange("GET", "/products/{id}", get("/products/{id}", ABSENT_ID), 404);
        exchange("GET", "/products/{id}", get("/products/abc"), 400);

        exchange("POST", "/customer", json(post("/customer"), Map.of("name", "Ada Lovelace")), 201);
        exchange("POST", "/customer", json(post("/customer"), Map.of("name", "Ada", "id", 1)), 400);
        exchange("GET", "/customer", get("/customer"), 200);
        exchange("GET", "/customer", get("/customer").param("q", "ann"), 200);

        exchange("POST", "/order", json(post("/order"), orderBody(1, product)), 201);
        exchange("POST", "/order", json(post("/order"), orderBody(1, product, product)), 400);
        exchange("POST", "/order", json(post("/order"), orderBody(1, ABSENT_ID)), 422);
        exchange("GET", "/order", get("/order"), 200);
        exchange("GET", "/order/{id}", get("/order/{id}", 1), 200);
        exchange("GET", "/order/{id}", get("/order/{id}", ABSENT_ID), 404);
        exchange("GET", "/order/{id}", get("/order/abc"), 400);

        assertThat(reached).containsExactlyInAnyOrderElementsOf(documentedResponses());
    }

    /** Sends the request, which {@link OpenApiContractConfig} validates, and records the documented response it hit. */
    private JsonNode exchange(String method, String template, MockHttpServletRequestBuilder request, int status)
            throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
        reached.add(method + " " + template + " " + status);
        return objectMapper.readTree(body);
    }

    /** Every {@code METHOD /path/template status} the document lists, from the parsed document itself. */
    private static Set<String> documentedResponses() {
        OpenAPI api = new OpenAPIV3Parser()
                .read(OpenApiContractConfig.OPENAPI_DOCUMENT.toUri().toString());
        Set<String> responses = new TreeSet<>();
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> operation
                .getResponses()
                .keySet()
                .forEach(status -> responses.add(method.name() + " " + path + " " + status))));
        return responses;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> orderBody(long customerId, long... productIds) {
        List<Map<String, Object>> products = Arrays.stream(productIds)
                .mapToObj(id -> Map.<String, Object>of("id", id))
                .toList();
        return Map.of("description", "Reading lamp", "customer", Map.of("id", customerId), "products", products);
    }
}
