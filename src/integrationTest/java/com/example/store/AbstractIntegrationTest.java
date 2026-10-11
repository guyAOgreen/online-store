package com.example.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for tests that need the real application against PostgreSQL. Tests are deliberately not transactional so they
 * observe the same transaction boundaries, lazy loading and query counts as production. Hibernate statistics are
 * switched on so a test can assert how many SQL statements a request issued; the per-session metrics log that comes
 * with them is silenced because it would print after every request.
 */
@SpringBootTest(
        properties = {
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
        })
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, DatabaseCleaner.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetStatistics() {
        hibernateStatistics().clear();
    }

    @AfterEach
    void restoreSeededData() {
        databaseCleaner.restoreSeededData();
    }

    /** Number of SQL statements Hibernate has prepared since the start of the test. */
    protected long sqlStatementCount() {
        return hibernateStatistics().getPrepareStatementCount();
    }

    private Statistics hibernateStatistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
