package com.example.store;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one PostgreSQL container, matching the production version in the README, and points the Spring datasource at
 * it. Every test class sharing this configuration shares one cached context and therefore one container. If Docker is
 * unavailable the suite fails rather than silently skipping.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16.2");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }
}
