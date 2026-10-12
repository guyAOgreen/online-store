package com.example.store;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The health endpoint the container image (#11) is probed on. Against the real database it reports UP, which is what
 * the Dockerfile's HEALTHCHECK and the CI smoke check wait for; its component details and every other actuator endpoint
 * stay off the wire, because the image is meant to expose the API and a liveness signal, nothing about its internals.
 */
class HealthEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthReportsUpAgainstTheDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthHidesItsComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
    }
}
