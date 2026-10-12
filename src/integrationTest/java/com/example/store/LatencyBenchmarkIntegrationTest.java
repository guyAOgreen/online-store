package com.example.store;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The #9 measurement: every GET endpoint against PostgreSQL with network latency between the application and the
 * database, which is the production problem the README describes. The database sits behind a Toxiproxy whose latency
 * toxic adds a fixed delay in each direction, so one round trip costs twice the configured figure; comparing the wall
 * time with and without the toxic therefore gives the number of round trips a request really makes, including the
 * transaction and connection-pool chatter that Hibernate's "statements prepared" count does not see. PostgreSQL's own
 * {@code pg_stat_statements} supplies the statements executed, the rows each returned and the server-side time; the
 * result size is estimated from each plan's row width and is labelled as an estimate, since nothing reports the bytes
 * on the wire between the two. Response sizes are the raw bytes the HTTP client receives, so the gzip figure is the
 * compressed body and not a body the client decompressed.
 *
 * <p>Timings are reported, never asserted: they describe one machine on one day. This class is not part of
 * {@code check}; run it with {@code ./gradlew latencyBenchmark} (optionally {@code -Platency=<ms>}). The report is
 * written to {@code build/reports/latency-benchmark.md} and printed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
        })
class LatencyBenchmarkIntegrationTest {

    private static final int LATENCY_MS = Integer.getInteger("store.benchmark.latency", 25);
    private static final int WARM_UP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final int PROXY_PORT = 8666;
    private static final DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0");
    private static final Path REPORT = Path.of("build", "reports", "latency-benchmark.md");

    private static final long SCALE_FIRST_CUSTOMER_ID = 2_000;
    private static final long SCALE_CUSTOMERS = 10_000;
    private static final long SCALE_ORDERS = 990_000;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+)");

    /** A query, allowing for the SQL comment Hibernate prefixes ({@code use_sql_comments}). */
    private static final Pattern SELECT = Pattern.compile("^(/\\*.*?\\*/\\s*)?select", Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAN_ROOT =
            Pattern.compile("rows=(\\d+) width=(\\d+)\\) \\(actual time=[^ ]+ rows=(\\d+)");

    private static PostgreSQLContainer<?> postgres;
    private static ToxiproxyContainer toxiproxy;
    private static Proxy proxy;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    /** A request to measure; the literals stand in for the bound parameters when its statements are explained. */
    private record Scenario(String name, String path, List<String> parameterLiterals) {}

    private record DbStatement(String sql, long calls, long rows, double execMillis) {}

    private record Measurement(
            Scenario scenario,
            long statementsPrepared,
            List<DbStatement> dbStatements,
            long estimatedResultBytes,
            int responseBytes,
            Integer gzipBytes,
            double medianMillisDirect,
            double medianMillisWithLatency,
            List<String> plans) {

        long dbCalls() {
            return dbStatements.stream().mapToLong(DbStatement::calls).sum();
        }

        long dbRows() {
            return dbStatements.stream().mapToLong(DbStatement::rows).sum();
        }

        double dbExecMillis() {
            return dbStatements.stream().mapToDouble(DbStatement::execMillis).sum();
        }

        double derivedRoundTrips() {
            return (medianMillisWithLatency - medianMillisDirect) / (2.0 * LATENCY_MS);
        }
    }

    @DynamicPropertySource
    static void routeTheDatasourceThroughTheProxy(DynamicPropertyRegistry registry) throws IOException {
        Network network = Network.newNetwork();
        postgres = new PostgreSQLContainer<>(PostgresContainerConfig.POSTGRES_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withCommand("postgres", "-c", "fsync=off", "-c", "shared_preload_libraries=pg_stat_statements");
        toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(network);
        postgres.start();
        toxiproxy.start();
        ToxiproxyClient client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        proxy = client.createProxy("postgres", "0.0.0.0:" + PROXY_PORT, "postgres:5432");
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:postgresql://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(PROXY_PORT) + "/"
                        + postgres.getDatabaseName());
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void measureEveryGetEndpoint() throws Exception {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
        LargerDataset.load(jdbcTemplate);
        jdbcTemplate.execute("ANALYZE");
        long productInOrders = jdbcTemplate.queryForObject("SELECT MIN(id) FROM product", Long.class);
        List<Scenario> scenarios = List.of(
                new Scenario("GET /customer", "/customer", List.of()),
                new Scenario("GET /customer?q=ann (selective)", "/customer?q=ann", List.of("'%ann%'", "'\\'")),
                new Scenario("GET /customer?q=e (broad)", "/customer?q=e", List.of("'%e%'", "'\\'")),
                new Scenario("GET /order", "/order", List.of()),
                new Scenario("GET /order/1", "/order/1", List.of("1")),
                new Scenario("GET /products", "/products", List.of()),
                new Scenario(
                        "GET /products/{id}", "/products/" + productInOrders, List.of(Long.toString(productInOrders))));

        List<Measurement> measurements = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            measurements.add(measure(scenario));
        }
        String searchSql = measurements.get(1).dbStatements().stream()
                .map(DbStatement::sql)
                .filter(sql -> SELECT.matcher(sql).find())
                .findFirst()
                .orElseThrow();

        String report = report(measurements) + indexJustificationAtScale(searchSql);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report);
        assertThat(measurements)
                .allSatisfy(m -> assertThat(m.statementsPrepared()).isPositive());
    }

    private Measurement measure(Scenario scenario) throws Exception {
        removeLatency();
        for (int i = 0; i < WARM_UP_RUNS; i++) {
            send(scenario.path(), false);
        }

        statistics().clear();
        jdbcTemplate.execute("SELECT pg_stat_statements_reset()");
        HttpResponse<byte[]> response = send(scenario.path(), false);
        assertThat(response.statusCode()).as(scenario.name()).isEqualTo(200);
        long statementsPrepared = statistics().getPrepareStatementCount();
        List<DbStatement> dbStatements = dbStatements();

        HttpResponse<byte[]> compressed = send(scenario.path(), true);
        Integer gzipBytes =
                compressed.headers().firstValue("Content-Encoding").orElse("").equals("gzip")
                        ? compressed.body().length
                        : null;

        double direct = median(timeRuns(scenario.path()));
        addLatency(LATENCY_MS);
        double withLatency = median(timeRuns(scenario.path()));
        removeLatency();

        List<String> plans = new ArrayList<>();
        long estimatedResultBytes = 0;
        for (DbStatement statement : dbStatements) {
            if (SELECT.matcher(statement.sql()).find()) {
                List<String> plan = explain(statement.sql(), scenario.parameterLiterals());
                plans.add(String.join("\n", plan));
                estimatedResultBytes += estimatedResultBytes(plan);
            }
        }
        return new Measurement(
                scenario,
                statementsPrepared,
                dbStatements,
                estimatedResultBytes,
                response.body().length,
                gzipBytes,
                direct,
                withLatency,
                plans);
    }

    /**
     * The {@code "order".customer_id} index (#9) at a size where the planner has a choice to make. On the seed every
     * customer's orders are spread over all 85 pages of the table, so a sequential scan beats any index whatever the
     * search; here ten thousand customers hold a million orders, scattered the same way, and the search SQL the
     * application really issues is explained with and without the index, for a search matching ten customers and one
     * matching all of them. The rows are added after the measurements above so they do not affect them.
     */
    private String indexJustificationAtScale(String searchSql) {
        jdbcTemplate.update(
                "INSERT INTO customer (id, name) SELECT g, 'Scale customer #' || g || '#' FROM generate_series(?, ?) g",
                SCALE_FIRST_CUSTOMER_ID,
                SCALE_FIRST_CUSTOMER_ID + SCALE_CUSTOMERS - 1);
        jdbcTemplate.update(
                "INSERT INTO \"order\" (description, customer_id) "
                        + "SELECT 'Scale order ' || g, ? + (g * 7919) % ? FROM generate_series(1, ?) g",
                SCALE_FIRST_CUSTOMER_ID, SCALE_CUSTOMERS, SCALE_ORDERS);
        jdbcTemplate.execute("ANALYZE");
        StringBuilder out = new StringBuilder("\n## Index justification at scale\n\n");
        out.append(String.format(
                Locale.ROOT,
                "Now %d customers and %d orders (%d per scale customer, scattered across the table as the seed's are). "
                        + "The selective search matches ten customers, the broad one every scale customer.%n",
                count("customer"),
                count("\"order\""),
                SCALE_ORDERS / SCALE_CUSTOMERS));
        for (boolean withIndex : List.of(true, false)) {
            if (!withIndex) {
                jdbcTemplate.execute("DROP INDEX idx_order_customer_id");
            }
            for (String pattern : List.of("'%#212%'", "'%scale%'")) {
                out.append("\n```\n-- ")
                        .append(withIndex ? "with" : "without")
                        .append(" idx_order_customer_id, pattern ")
                        .append(pattern)
                        .append('\n')
                        .append(String.join("\n", explain(searchSql, List.of(pattern, "'\\'"))))
                        .append("\n```\n");
            }
        }
        jdbcTemplate.execute("CREATE INDEX idx_order_customer_id ON \"order\" (customer_id)");
        return out.toString();
    }

    private List<Double> timeRuns(String path) throws Exception {
        List<Double> millis = new ArrayList<>();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            send(path, false);
            millis.add((System.nanoTime() - start) / 1_000_000.0);
        }
        return millis;
    }

    private HttpResponse<byte[]> send(String path, boolean acceptGzip) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (acceptGzip) {
            request.header("Accept-Encoding", "gzip");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** What PostgreSQL executed since the last reset, the statistics queries themselves excluded. */
    private List<DbStatement> dbStatements() {
        return jdbcTemplate.query(
                """
                SELECT query, calls, rows, total_exec_time
                FROM pg_stat_statements
                WHERE query NOT ILIKE '%pg_stat_statements%'
                ORDER BY total_exec_time DESC
                """,
                (rs, i) -> new DbStatement(
                        rs.getString("query").replaceAll("\\s+", " ").trim(),
                        rs.getLong("calls"),
                        rs.getLong("rows"),
                        rs.getDouble("total_exec_time")));
    }

    private List<String> explain(String sql, List<String> parameterLiterals) {
        Matcher placeholders = PLACEHOLDER.matcher(sql);
        StringBuilder concrete = new StringBuilder();
        while (placeholders.find()) {
            int index = Integer.parseInt(placeholders.group(1)) - 1;
            if (index >= parameterLiterals.size()) {
                return List.of(
                        "-- " + sql, "-- not explained: the scenario supplies no literal for " + placeholders.group());
            }
            placeholders.appendReplacement(concrete, Matcher.quoteReplacement(parameterLiterals.get(index)));
        }
        placeholders.appendTail(concrete);
        try {
            List<String> plan = new ArrayList<>();
            plan.add("-- " + concrete);
            plan.addAll(jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + concrete, String.class));
            return plan;
        } catch (RuntimeException e) {
            return List.of("-- " + concrete, "-- could not be explained: " + e.getMessage());
        }
    }

    /** Actual rows of the plan root times the planner's estimated row width: an estimate, not bytes on the wire. */
    private static long estimatedResultBytes(List<String> plan) {
        for (String line : plan) {
            Matcher root = PLAN_ROOT.matcher(line);
            if (root.find()) {
                return Long.parseLong(root.group(3)) * Long.parseLong(root.group(2));
            }
        }
        return 0;
    }

    private static void addLatency(int millis) throws IOException {
        proxy.toxics().latency("upstream", ToxicDirection.UPSTREAM, millis);
        proxy.toxics().latency("downstream", ToxicDirection.DOWNSTREAM, millis);
    }

    private static void removeLatency() throws IOException {
        for (Toxic toxic : proxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private String report(List<Measurement> measurements) {
        StringBuilder out = new StringBuilder();
        out.append("# GET endpoint latency benchmark\n\n");
        out.append(String.format(
                Locale.ROOT,
                "Dataset: %d customers, %d orders, %d products, %d order_product rows.%n",
                count("customer"),
                count("\"order\""),
                count("product"),
                count("order_product")));
        out.append(String.format(
                Locale.ROOT,
                "Latency toxic: %d ms upstream and %d ms downstream, so one round trip costs about %d ms. "
                        + "Runs per cell: %d warm-up, %d measured, median reported.%n%n",
                LATENCY_MS,
                LATENCY_MS,
                2 * LATENCY_MS,
                WARM_UP_RUNS,
                MEASURED_RUNS));
        out.append(
                        "| Endpoint | Statements prepared (Hibernate) | DB statements | DB rows returned | DB exec ms "
                                + "| Est. result bytes (rows x width) | HTTP bytes | gzip bytes | Median ms, 0 ms | Median ms, ")
                .append(LATENCY_MS)
                .append(" ms | Derived round trips |\n");
        out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Measurement m : measurements) {
            out.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %d | %.1f | %d | %d | %s | %.1f | %.1f | %.1f |%n",
                    m.scenario().name(),
                    m.statementsPrepared(),
                    m.dbCalls(),
                    m.dbRows(),
                    m.dbExecMillis(),
                    m.estimatedResultBytes(),
                    m.responseBytes(),
                    m.gzipBytes() == null ? "not compressed" : m.gzipBytes().toString(),
                    m.medianMillisDirect(),
                    m.medianMillisWithLatency(),
                    m.derivedRoundTrips()));
        }
        for (Measurement m : measurements) {
            out.append("\n## ").append(m.scenario().name()).append("\n\n");
            out.append("| Calls | Rows | Exec ms | Statement |\n|---:|---:|---:|---|\n");
            for (DbStatement s : m.dbStatements()) {
                out.append(String.format(
                        Locale.ROOT,
                        "| %d | %d | %.2f | `%s` |%n",
                        s.calls(),
                        s.rows(),
                        s.execMillis(),
                        s.sql().replace("|", "\\|")));
            }
            for (String plan : m.plans()) {
                out.append("\n```\n").append(plan).append("\n```\n");
            }
        }
        return out.toString();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
