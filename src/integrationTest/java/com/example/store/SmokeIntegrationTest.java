package com.example.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeIntegrationTest {
    @Test
    void suiteRuns() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
