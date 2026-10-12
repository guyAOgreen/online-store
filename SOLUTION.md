# Solution summary

The Store application is a Spring Boot 3.4 / Java 17 JSON API over PostgreSQL 16 that keeps customers, their orders and the products those orders contain. This page is the five-minute version of the work: what was built, the decisions an assessor is most likely to question, the performance evidence, what changed for existing clients, and what was deliberately left out. Every decision here is recorded in full, with the alternatives considered and the tests that hold it, in [docs/design-record.md](docs/design-record.md), one section per GitHub issue; the `#n` references below point there. [README.md](README.md) is the operating manual: quick start, worked examples with real responses, and the API table. [OpenAPI.yaml](OpenAPI.yaml) is the contract, and the integration suite checks the running service against it.

## What was delivered

| Assignment task | Delivered | Record |
|---|---|---|
| Fetch an order by ID | `GET /order/{id}` in one SQL statement, 404 and 400 as problem documents | #5 |
| Search customers by a substring of a name word | `GET /customer?q=words`, every word must match, case-insensitive, filtered in the database | #6 |
| GET endpoints over a high-latency link | Every read is one statement; measured with a latency proxy, 13 round trips cut to 2 on the customer listing; an index justified at a million orders; gzip | #9 |
| Products on orders | `product` and `order_product` tables, `POST`/`GET /products`, `GET /products/{id}`, orders created with one or more products | #7, #8 |
| Bonus: CI | GitHub Actions runs format check, unit and integration suites, builds and smoke-tests the image on every pull request | #11 |
| Bonus: Docker image | Multi-stage build from source, JRE on Alpine, non-root, health check, published to GHCR from `master` only | #11 |

Underneath those, three pieces of groundwork were needed first: a PostgreSQL integration suite on Testcontainers with upgrade-path tests (#2), a migration that repairs the ID sequences the seed data had left behind, so that the first `POST` no longer collides with ID 1 (#3), and request validation with a single error contract in place of binding request bodies straight to JPA entities (#4). The documentation and the OpenAPI file were brought into line with the finished API at the end (#10), and an external review's findings were applied (#23).

## Architecture

Controllers validate and delegate; services own the transaction boundary and map entities to response DTOs inside it; repositories are Spring Data interfaces whose read methods carry entity graphs; Liquibase owns the schema and Hibernate only validates it. Request bodies bind to small DTOs that list exactly the accepted fields, with Jackson set to reject unknown properties, duplicate keys and fractional IDs, so a client-supplied `id` or a nested `orders` array is a 400 rather than a silent merge. Every client error is an RFC 9457 problem document with an `errors` array naming the field whenever one can be named: 400 for a request wrong in itself, 404 for a URL naming nothing, 415 for a non-JSON create, 422 for a well-formed body that references a customer or product that does not exist.

Two test suites run under `./gradlew check`: unit tests with mocked collaborators, and an integration suite against a real `postgres:16.2` container that fails rather than skips without Docker. The integration suite restores the seeded database from a snapshot schema after every test rather than wrapping tests in a transaction, so lazy loading and real statement counts stay observable. Every response the suite receives is validated against the OpenAPI document, and one test sends a request for every documented response and checks the two sets match. The CI workflow runs that gate, then builds the image and smoke-tests it against a throwaway database; only a push to `master` or a version tag publishes.

## Decisions most likely to be questioned

- **Legacy orders stay without products (#7).** The 10,000 seeded orders, and any order in a production database, get no invented association and no backfill is planned. The "at least one product" rule applies to orders created through the API and lives in the application, not the schema, because a minimum child-row count is not a plain constraint and every existing order would violate it. Clients must accept an empty `products` array indefinitely.
- **Duplicate products in an order are a 400, not collapsed (#8).** Quantities are outside the model, so `[{"id": 1}, {"id": 1}]` has no meaning the server can store; hiding it would hide a client bug. The join table's pair key and the entity's `Set` would store one row anyway, so the check is defence in depth.
- **404 is for the URL, 422 for the body (#4, #5).** A dangling `customer.id` or product ID in a body is 422; only the resource named in the path is 404. The split is visible without parsing the body, and 404 for a missing referenced customer would be ambiguous once `GET /order/{id}` also returns 404.
- **Client-supplied IDs and unknown fields are rejected (#4).** The old endpoints merged a supplied ID into an existing row, which is how a `POST` could overwrite seeded data. Silently dropping the field was the most compatible option and was rejected because the client would never learn its ID was ignored.
- **The search requires every word and runs in the database (#6).** `ann` matches `Dianne Lemke`; `ed glover` and `glover ed` match only `Ed Glover`. The filter is one `ILIKE` per word with `%`, `_` and `\` escaped, so `q=%` finds the one customer with a percent sign. A NUL in the query returns an empty list without a round trip, because no stored name can contain one. The name column has no index: `ILIKE '%…%'` cannot use a b-tree and the table is one page.
- **One statement per read, through entity graphs, and the wide joins stay (#5, #8, #9).** Each read joins what its response needs in one `SELECT`, which repeats parent columns on every child row. For the order list that is about 15 % more bytes than the body; for the product list it is far more, because the join carries order rows only to keep their IDs. A scalar projection over the same join would be narrower in one statement at the cost of hand-written grouping, and was not taken because the stated problem is latency, not bandwidth. The crossover is recorded with the inputs to revisit it.
- **Reads cost two round trips, not one (#9).** Every read is a read-only transaction whose `COMMIT` is its own round trip. Autocommit reads would halve the floor and were not done because the transaction is the deliberate boundary inside which the DTO is mapped. Under PostgreSQL's default `READ COMMITTED` the transaction does not give a snapshot across statements; the reads are consistent because each is one statement. Open EntityManager in View is off, so a lazy load outside the boundary fails rather than silently issuing a statement.
- **Entities use identity equality (#7, #23).** Lombok's `@Data` on entities hashed lazy collections and initialised them mid-load; it broke five mapping tests before it was removed. Instances only meet inside one persistence context, so identity is the correct semantics and ID-based equality would add code for a case that does not occur.
- **Migrations are written for a rolling deploy (#3, #7, #9).** The sequence repair takes `SHARE` locks on both tables in one statement, parent first, after the first version's `EXCLUSIVE` locks were shown to deadlock with an in-flight order insert; the product tables are additive and invisible to an instance of the previous release; the customer-ID index is built `CONCURRENTLY` and, if interrupted, fails loudly on retry rather than recording an invalid index. Applied changesets are never edited; new data is a new changeset.
- **The OpenAPI file is rewritten against the code, not generated from it (#10).** Generating it with springdoc would document whatever the code happens to do, which is the drift the issue existed to stop. The contract test is what keeps the hand-written document honest.
- **The image builds from source and publishes only from trusted refs (#11).** The builder stage runs the committed Gradle wrapper, so `docker build .` gives the same image on any machine. Pull requests build and smoke-test the image with no credential in scope; `master` publishes `latest` and `sha-<short sha>`.

## Performance evidence

`./gradlew latencyBenchmark` sends every `GET` request against a PostgreSQL container behind a Toxiproxy latency toxic of 25 ms each way, on the seed plus a larger dataset (1,010 products, three on most orders, 25,716 join rows), and records statements prepared, statements and rows as PostgreSQL counted them, query plans, body bytes with and without gzip, and median timings with and without latency. The decisive figure is derived round trips: the latency-induced slowdown divided by the round-trip cost, which counts what crossed the network, transaction chatter included. Timings are never asserted; the statement counts are, by `ReadQueryCountIntegrationTest`, which also compares every response's relationships parent by parent against SQL.

| Request | Before: statements, round trips, median at 25 ms | After: statements, round trips, median at 25 ms | Body, gzipped |
|---|---|---|---|
| `GET /customer` | 12 prepared, 13.1, 705 ms | 1, 2.0, 127 ms | 504 KB, 75 KB |
| `GET /customer?q=ann` | 2, 3.1, 160 ms | 1, 2.1, 109 ms | 15 KB, 3 KB |
| `GET /order` | 1, 1.9, 160 ms | 1, 1.8, 158 ms | 2.08 MB, 240 KB |
| `GET /order/1` | 1, 2.1, 109 ms | 1, 2.1, 107 ms | 208 B, 149 B |
| `GET /products` | 1, 2.0, 145 ms | 1, 1.9, 145 ms | 176 KB, 36 KB |
| `GET /products/{id}` | 1, 2.1, 107 ms | 1, 2.1, 108 ms | 175 B, 133 B |

The customer reads were the work of #9: they batch-loaded orders ten customers at a time, so the listing cost thirteen round trips, and now join them in one statement. The other reads were already at the two-round-trip floor from #5 and #8. The index on `"order".customer_id` made no difference on the seed, where 10,000 orders fit in 85 pages, so it was justified by a scale experiment at a million orders: a search matching ten customers went from 73 ms to 3 ms, and a search returning most of the table, already a 50 MB response, became about 40 % slower. gzip is measured separately and affects the application-to-client leg only.

What the fixed statement count does not fix is the size of the work. The three list endpoints are unpaginated, so the rows fetched, the memory held and the bytes sent grow with the data; a pagination contract is a separate decision and is recorded as open.

## Compatibility changes

All intentional, each with its reason in the record:

- A supplied `id` on any create is a 400 (was a silent merge into the existing row); unknown fields are a 400 (were ignored); nested `orders` on `POST /customer` are a 400 (were created). (#4)
- A blank or missing `name` or `description`, more than 255 characters, or a NUL character is a 400 (was `""` stored, or a 500). A missing or ID-less customer reference is a 400 and a reference to a customer that does not exist is a 422 (both were 500s). (#4)
- `POST /order` without `products` is a 400 (was a 201). Order responses gain `products`. (#8)
- Every list is now in ascending ID order, with nested collections in ID order; before, lists came back in heap order, which for the seed was ID order. (#8, #9)
- `/products` is plural, as the assignment and the original OpenAPI file name it; `/order` and `/customer` keep their paths. (#8)
- The OpenAPI file keeps its `info` block and 3.0 version and now describes every operation, request and response; a `descriptioon` typo in the original is gone. (#4, #10)

## Limitations and open items

- **No pagination.** `GET /order` returns every order; see above.
- **No delete or update endpoints.** The foreign keys are the only guard against a product deleted between a create's existence check and its insert, a window that is theoretical today. If deletes arrive, `ON DELETE CASCADE` on `order_product.order_id` is the decision to revisit.
- **Length is counted in UTF-16 units.** `@Size(max = 255)` rejects a value of more than 127 supplementary-plane characters that the column would hold; the mismatch is only ever over-strict and the OpenAPI document says so.
- **A repeated `q` parameter binds as one comma-joined string** and so matches nothing; documented rather than handled.
- **Search has no trigram index.** `pg_trgm` is the remedy at scale and would be a migration; the customer table is one page.
- **The product-list projection was not implemented;** the inputs to decide are in the benchmark report.
- **The contract check covers the exchanges the suite makes,** not every input the API could receive.
- **The image is amd64 only, unsigned, without an SBOM**, and nothing deploys it; the `sha-` tag is the hand-off point.
- **Generated data goes into a new changeset.** The generator in `utils/` starts IDs at one, does not escape apostrophes and knows nothing about products; [utils/README.md](utils/README.md) says how to use its output without editing an applied changeset.

## Verifying it

```shell
./gradlew clean check bootJar        # format check, both suites against Testcontainers, jar
./gradlew latencyBenchmark           # writes build/reports/latency-benchmark.md; Docker required
docker compose up --build            # the submitted checkout against PostgreSQL on localhost:8080
bash .github/scripts/smoke-test.sh ghcr.io/guyaogreen/online-store:latest
```

The README's quick start and worked examples were run against a fresh `docker compose up --build` and a fresh clone respectively, and the responses shown are pasted from those runs.

## Where the detail is

[docs/design-record.md](docs/design-record.md): #2 test infrastructure and upgrade harness · #3 sequence repair and its locking · #4 validation, the error contract and the service layer · #5 the lookup and the first query-count assertion · #6 search semantics and the `ILIKE` specification · #7 the product schema, mapping and the legacy-order policy · #8 the product endpoints, duplicate rule and fetch strategy · #9 measurement method, both result tables, the index experiment, the round-trip floor and gzip · #11 the Dockerfile, workflow, registry and smoke test · #10 the OpenAPI rewrite, contract tests and README verification · #23 the external review and what it changed.
