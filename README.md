# Store Application
The Store application keeps track of customers, their orders, and the products those orders contain, in a PostgreSQL database behind a small JSON API.

* [OpenAPI.yaml](OpenAPI.yaml) is the API contract. The integration suite validates every request it sends that the server accepts, and every response it receives, against it, so it describes what the running service does for the exchanges the suite exercises.
* [SOLUTION.md](SOLUTION.md) is the five-minute summary: what was delivered, the decisions most likely to be questioned, the performance evidence, the compatibility changes and the limitations.
* [docs/design-record.md](docs/design-record.md) is the full design record behind it: one section per GitHub issue with what was chosen, what was considered and why, and the measurements behind the performance work.
* The original assignment text is kept at the end of this file under [The assignment](#the-assignment).

# Quick start from a clean checkout
You need Git and Docker (Docker Desktop on Windows and macOS); nothing else. The compose file builds the image from the checkout, which compiles nothing on your machine and needs no login for the published image, which is a private package (it does pull the public `postgres` and Temurin images and the build's Gradle dependencies), then starts PostgreSQL 16.2 and the application:

```shell
git clone https://github.com/guyAOgreen/online-store.git
cd online-store
docker compose up --build
```

The application is ready when its log shows `Started StoreApplication`; Liquibase has created the schema and loaded the seed data (100 customers, 10,000 orders, no products) by then. From another terminal, create a product, put it in an order for a seeded customer, then read the order, the product and the customer back:
```shell
curl -s -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"description": "Lamp"}'
curl -s -X POST localhost:8080/order -H 'Content-Type: application/json' \
  -d '{"description": "Reading lamp", "customer": {"id": 1}, "products": [{"id": 1}]}'
curl -s localhost:8080/order/10001
curl -s localhost:8080/products/1
curl -s 'localhost:8080/customer?q=muriel'
```
```json
{"id":1,"description":"Lamp","orders":[]}
{"id":10001,"description":"Reading lamp","customer":{"id":1,"name":"Muriel Donnelly"},"products":[{"id":1,"description":"Lamp"}]}
{"id":10001,"description":"Reading lamp","customer":{"id":1,"name":"Muriel Donnelly"},"products":[{"id":1,"description":"Lamp"}]}
{"id":1,"description":"Lamp","orders":[10001]}
[{"id":1,"name":"Muriel Donnelly","orders":[{"id":158,"description":"Awesome Concrete Shirt"},…,{"id":10001,"description":"Reading lamp"}]}]
```
`docker compose down -v` stops both containers and drops the database. The [API](#api) table lists every endpoint and the [worked examples](#worked-examples) walk through each assignment task with the real responses, including the error cases. On Windows write `curl.exe` and read JSON bodies from a file; see [Windows and other differences](#windows-and-other-differences).

# Development setup
To build, test or run the application outside Docker you also need a JDK. The build compiles and tests on **Java 17**, which the Gradle toolchain downloads automatically if it is not installed; the only requirement on the JDK you have is that Gradle 8.11 can start on it, which means **Java 17 to 23**. On a machine whose default JDK is newer, point `JAVA_HOME` at a 17–23 JDK for the wrapper (see [Windows and other differences](#windows-and-other-differences)). The compose file's database is published on `localhost:5433` with the credentials below, so it can serve `bootRun` instead of step 1; but the compose application holds port 8080, so stop it first (`docker compose stop app`), or start the database alone with `docker compose up db`.

```shell
# 1. PostgreSQL 16.2 on localhost:5433 (note the non-standard port), user and password admin, database store
docker run -d \
  --name postgres \
  --restart always \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin \
  -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data \
  -p 5433:5432 \
  postgres:16.2 \
  postgres -c wal_level=logical

# 2. The same gate CI runs: format check, unit tests, integration tests (Testcontainers, so Docker must be running), jar
./gradlew clean check bootJar

# 3. Run it; Liquibase creates the schema and loads the seed data on first start
./gradlew bootRun

# 4. From another terminal
curl localhost:8080/order/1
```

The seed data is 100 customers and 10,000 orders and no products. The database is migrated by Liquibase from `src/main/resources/db/changelog`; five changesets run on an empty database (schema, seed data, sequence repair, product tables, an index), and a database that already holds some of them receives only the rest. To generate more data see [utils/README.md](utils/README.md), which explains why it must go into a new changeset rather than into an applied one.

The application reads its database connection from Spring Boot's standard properties, so `bootRun` can be pointed elsewhere with environment variables, exactly as the Docker image is:

| Variable | Default in `application.yaml` |
|----------|-------------------------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/store` |
| `SPRING_DATASOURCE_USERNAME` | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` |

# Windows and other differences
The commands in this file are written for a POSIX shell. Everything works on Windows with these substitutions, which were checked on Windows 11 with Docker Desktop:

* **Gradle wrapper.** Use `gradlew.bat` instead of `./gradlew` in PowerShell or `cmd`; `./gradlew` also works from Git Bash. The Unix wrapper is committed with its executable bit set (git mode `100755`) and `.gitattributes` forces LF line endings on it, so a checkout made on Windows and pushed back still runs on the Linux CI runner and inside the Docker build. If a copy ever loses the bit (for example after being zipped), `chmod +x gradlew` restores it; `git update-index --chmod=+x gradlew` records it.
* **JDK for the wrapper.** Gradle 8.11 starts on Java 17 to 23. If `java -version` reports something newer, set `JAVA_HOME` for the command: `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'; .\gradlew.bat check` in PowerShell, or `JAVA_HOME=/path/to/jdk-21 ./gradlew check` in bash. The Java 17 toolchain used for compiling and testing is unaffected.
* **Line continuation.** Bash continues a line with `\`; PowerShell uses a backtick. The PostgreSQL container in PowerShell:
  ```powershell
  docker run -d `
    --name postgres `
    --restart always `
    -e POSTGRES_USER=admin `
    -e POSTGRES_PASSWORD=admin `
    -e POSTGRES_DB=store `
    -v postgres:/var/lib/postgresql/data `
    -p 5433:5432 `
    postgres:16.2 `
    postgres -c wal_level=logical
  ```
* **curl.** In PowerShell `curl` is an alias for `Invoke-WebRequest`; write `curl.exe`, which Windows 10 and 11 ship. PowerShell 5.1 also strips the double quotes from a JSON body passed as an argument, so a request body is best read from a file: `curl.exe -H "Content-Type: application/json" -d "@body.json" localhost:8080/customer`. `Invoke-RestMethod -Method Post -ContentType application/json -Body '{"name":"Ada Lovelace"}' localhost:8080/customer` is the native equivalent.
* **Path length.** Cloning into a deeply nested directory can fail with `Filename too long`, because Git for Windows observes the 260-character path limit by default. Clone somewhere shorter, or run `git config --global core.longpaths true` first.
* **Shell scripts.** `.github/scripts/smoke-test.sh` is a bash script; run it from Git Bash (`bash .github/scripts/smoke-test.sh <image>`), which Git for Windows installs. `docker compose` and `docker build` are the same on every platform.
* **Alternatively**, install PostgreSQL 16 directly instead of in Docker; the application only needs a `store` database reachable on `localhost:5433` with the credentials above. Docker is still required for the integration tests.

# Docker image
The service is also delivered as a Docker image, built from the `Dockerfile` at the repository root and published to GitHub Container Registry by CI (see below):

| Tag | Meaning |
|-----|---------|
| `ghcr.io/guyaogreen/online-store:latest` | the most recent push to `master` |
| `ghcr.io/guyaogreen/online-store:sha-<short sha>` | the exact commit it was built from |
| `ghcr.io/guyaogreen/online-store:<version>` | a `v<version>` git tag, for example `v1.0.0` |

The repository is public, but the container package has its own visibility and is private, so pulling needs `docker login ghcr.io` with a GitHub token that has `read:packages`. Building locally needs nothing but Docker: the builder stage compiles the source with the committed Gradle wrapper on a JDK 17 image, and the runtime stage is a JRE with the boot jar's layers extracted, running as a non-root user.
```shell
docker pull ghcr.io/guyaogreen/online-store:latest   # or:
docker build -t ghcr.io/guyaogreen/online-store:latest .
```

The image listens on port 8080 and reads its database connection from Spring Boot's standard environment variables; the defaults baked into `application.yaml` point at `localhost:5433`, which inside a container is the container itself, so always set at least the URL:

| Variable | Example |
|----------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/store` |
| `SPRING_DATASOURCE_USERNAME` | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` |
| `JDK_JAVA_OPTIONS` | optional JVM flags, for example `-XX:MaxRAMPercentage=75` |

Liquibase runs on startup, exactly as under `bootRun`, so an empty `store` database is enough. The image declares a `HEALTHCHECK` on `GET /actuator/health`, which reports `UP` only once the migrations have run, the web server is accepting requests and the database answers a ping; that is the only actuator endpoint exposed.

The compose file from the quick start starts `postgres:16.2` with the credentials above (also published on `localhost:5433` for `bootRun`, so the standalone `postgres` container from the development setup and the compose database cannot run at the same time) and the application on `localhost:8080`:
```shell
docker compose up            # pulls the published image
docker compose up --build    # builds the image from this checkout instead
docker compose down -v       # stops both and drops the database volume
```

To run the image next to the standalone `postgres` container from the quick start instead, put both on one Docker network so the application can reach it by name:
```shell
docker network create store
docker network connect store postgres
docker run -d --name store --network store -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/store \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=admin \
  ghcr.io/guyaogreen/online-store:latest
curl localhost:8080/actuator/health
curl localhost:8080/order/1
```

`.github/scripts/smoke-test.sh <image>` is the check CI runs before publishing and works locally too: it starts a throwaway PostgreSQL and the image on a private network, waits for the image's own health check, fetches `/order/1` and verifies the seed data came back, then removes both containers.

# Continuous integration
Every pull request targeting `master`, and every push to `master`, runs the GitHub Actions workflow in `.github/workflows/ci.yml`.
It uses Java 17 and the committed Gradle wrapper to compile the code, check formatting with Spotless, run the unit and integration tests, generate the JaCoCo coverage report and package the executable jar.
Test results for both suites, the coverage report and the jar are uploaded as workflow artifacts.

A second job then builds the Docker image and runs the smoke test above against it. On a pull request that is where it stops: no registry login happens, so no credentials are needed and nothing is published. On a push to `master`, or of a `v*` tag, the same build is pushed to `ghcr.io/guyaogreen/online-store` with the tags listed under *Docker image*, using the workflow's own `GITHUB_TOKEN`. Publication is reached only after both the test suites and the smoke test have passed.

The same gate, exactly as the workflow invokes it, runs locally with:
```shell
./gradlew clean check bootJar
```

There are two test suites:
* `./gradlew test` runs the unit tests in `src/test`. Controller tests mock the services and service tests mock the repositories, so they need no database.
* `./gradlew integrationTest` runs the tests in `src/integrationTest` against a PostgreSQL 16.2 container started by Testcontainers. Docker must be running; the suite fails rather than skips without it. No pre-existing `store` database is needed, and the container is removed when the run finishes. This suite also holds the API to `OpenAPI.yaml`: every response the suite receives on a documented path is validated against the document, every request the server accepts must match its documented schema (the deliberately malformed requests are judged by their 4xx response alone), and `OpenApiContractIntegrationTest` sends one request for every documented response and checks the set reached equals the set documented. The actuator paths are outside the document and the check. That covers the exchanges the suite exercises, not every input the API could receive: a response shape or status code that only an untested input produces is outside what the check can see.

`./gradlew check` runs both. Spotless formatting violations fail `check` too; `./gradlew spotlessApply` fixes them.

# Performance measurement
The GET endpoints are measured, not just tested, because the production database sits behind a high-latency link. `./gradlew latencyBenchmark` (Docker required, not part of `check`) runs every GET request against a PostgreSQL container reached through a Toxiproxy latency toxic, on the seed plus a larger product dataset, and writes `build/reports/latency-benchmark.md`: statements prepared, statements and rows as PostgreSQL counted them, query plans, response bytes with and without gzip, median timings with and without the simulated latency, and the number of round trips derived from the difference. `-Platency=<ms>` sets the one-way latency (default 25). The before-and-after figures and the decisions taken on them are in docs/design-record.md under #9. `ReadQueryCountIntegrationTest`, which is part of `check`, holds the statement count of every GET endpoint constant whatever the dataset size.

# Data model
* A **customer** has an ID, a name, and zero or more orders.
* An **order** has an ID, a description, belongs to exactly one customer, and contains products. An order created through this API contains one or more; an order that predates products (every seeded order) contains none and is reported with an empty list. No association was invented for those orders and none will be, so clients must accept an empty `products` array indefinitely.
* A **product** has an ID and a description and appears in zero or more orders. The same product appears in an order at most once; there are no quantities.

IDs are `BIGSERIAL` columns assigned by PostgreSQL; the API never accepts one on a create. The relationship between orders and products is the `order_product` join table, keyed on the pair, with a second index on `product_id` for the product-to-orders direction, and `"order".customer_id` is indexed for the customer search.

# API
| Method and path | Purpose | Success | Client errors |
|-----------------|---------|---------|---------------|
| `GET /customer` | every customer with the IDs and descriptions of their orders | 200 | |
| `GET /customer?q=words` | customers whose name contains every word of `q` | 200 | |
| `POST /customer` | create a customer from `{"name"}` | 201 | 400, 415 |
| `GET /order` | every order with its customer and products | 200 | |
| `GET /order/{id}` | one order | 200 | 400, 404 |
| `POST /order` | create an order from `{"description", "customer": {"id"}, "products": [{"id"}, …]}` | 201 | 400, 415, 422 |
| `GET /products` | every product with the IDs of the orders containing it | 200 | |
| `GET /products/{id}` | one product | 200 | 400, 404 |
| `POST /products` | create a product from `{"description"}` | 201 | 400, 415 |

`/products` is plural because the assignment names it so; `/order` and `/customer` keep their original paths.

**Responses.** The data model is circular, so each representation truncates the other side: a customer's orders carry only ID and description, an order's customer only ID and name, an order's products only ID and description, and a product's orders are IDs alone. Every list, and every nested list, is in ascending ID order. Lists are not paginated; `GET /order` returns all 10,000 seeded orders (about 1.1 MB on the seed alone, or 113 KB with `Accept-Encoding: gzip`, which the server honours for JSON).

**Errors.** Every client error is an RFC 9457 problem document, `application/problem+json`, with `type`, `title`, `status`, `detail` and `instance`, plus an `errors` array of `{field, message}` whenever a field can be named. One rule decides the status:

* **400** when the request itself is wrong: malformed JSON, a duplicated key, an unknown field (which is how a client-supplied `id` is rejected), a missing or blank string, more than 255 characters or a NUL character, a fractional ID, a missing or empty `products` array, the same product listed twice, or a path or query value that cannot be converted.
* **404** when the resource named in the URL does not exist.
* **415** when a create is sent with a `Content-Type` that is not JSON (`application/json`, or any `application/*+json`).
* **422** when the body is well formed but references a customer or product that does not exist. Every missing product ID is named, so one correction fixes the request.

**Search semantics.** `q` is split on whitespace; a customer matches when every word is a case-insensitive substring of their name, in any order, so `ann` matches `Dianne Lemke` and `ed glover` matches only `Ed Glover`. A query that is empty or only whitespace lists everyone. `%`, `_` and `\` match themselves, and a character no name can contain (a NUL) matches nothing, without a client error. Matching runs in the database in one statement.

**Writes.** Creating an order checks the customer and all products exist before anything is written and saves the order and its associations in one transaction, so a rejected request writes nothing. A repeated product is rejected rather than collapsed because quantities are not in the model and a duplicate is most likely a client bug.

The full detail of each decision, and the alternatives, is in docs/design-record.md: validation and the error contract under #4, the lookup under #5, search under #6, the product model and the legacy-order policy under #7, the product endpoints and duplicate handling under #8, the fetch strategy and measurements under #9, and this documentation under #10.

# Worked examples
Each assignment task below is demonstrated against a fresh database, in this order, so the IDs match; after the quick start's requests, `docker compose down -v` and `docker compose up` give a fresh one. Responses are shown as the server returns them, pretty-printed; the long order lists in the search results are elided. `OpenApiContractIntegrationTest` runs the same sequence on every build.

## Task 4 first: products, because orders now need them
Create two products:
```shell
curl -s -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"description": "Lamp"}'
curl -s -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"description": "Chair"}'
```
```json
{
  "id": 1,
  "description": "Lamp",
  "orders": []
}
{
  "id": 2,
  "description": "Chair",
  "orders": []
}
```
Create an order containing both for customer 1. The response carries the products in ID order whatever order the body listed them:
```shell
curl -s -X POST localhost:8080/order -H 'Content-Type: application/json' \
  -d '{"description": "Reading lamp and chair", "customer": {"id": 1}, "products": [{"id": 2}, {"id": 1}]}'
```
```json
{
  "id": 10001,
  "description": "Reading lamp and chair",
  "customer": {
    "id": 1,
    "name": "Muriel Donnelly"
  },
  "products": [
    {
      "id": 1,
      "description": "Lamp"
    },
    {
      "id": 2,
      "description": "Chair"
    }
  ]
}
```
Products report the orders that contain them, as IDs:
```shell
curl -s localhost:8080/products
curl -s localhost:8080/products/1
```
```json
[
  {
    "id": 1,
    "description": "Lamp",
    "orders": [
      10001
    ]
  },
  {
    "id": 2,
    "description": "Chair",
    "orders": [
      10001
    ]
  }
]
{
  "id": 1,
  "description": "Lamp",
  "orders": [
    10001
  ]
}
```
An order that predates products has an empty list; it is still a valid order:
```shell
curl -s localhost:8080/order/1
```
```json
{
  "id": 1,
  "description": "Handcrafted Soft Chair",
  "customer": {
    "id": 6,
    "name": "Vicki Kutch"
  },
  "products": []
}
```
The error cases: a missing `products` array (the pre-product request shape), the same product twice, and a product that does not exist. The first two are 400 because the request is wrong in itself; the third is 422 because the request is well formed but the reference dangles. None of them writes anything:
```shell
curl -s -X POST localhost:8080/order -H 'Content-Type: application/json' \
  -d '{"description": "No products", "customer": {"id": 1}}'
curl -s -X POST localhost:8080/order -H 'Content-Type: application/json' \
  -d '{"description": "Twice", "customer": {"id": 1}, "products": [{"id": 1}, {"id": 1}]}'
curl -s -X POST localhost:8080/order -H 'Content-Type: application/json' \
  -d '{"description": "Unknown", "customer": {"id": 1}, "products": [{"id": 1}, {"id": 7001}, {"id": 7002}]}'
```
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/order",
  "errors": [
    {
      "field": "products",
      "message": "must not be empty"
    }
  ]
}
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/order",
  "errors": [
    {
      "field": "products",
      "message": "must not contain the same product more than once"
    }
  ]
}
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Products 7001, 7002 do not exist",
  "instance": "/order"
}
```
A client-supplied ID is an unknown field, on every create:
```shell
curl -s -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"id": 1, "description": "Lamp"}'
```
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Unknown field 'id'",
  "instance": "/products",
  "errors": [
    {
      "field": "id",
      "message": "unknown field"
    }
  ]
}
```

## Task 1: an order by ID
```shell
curl -s -i localhost:8080/order/10001
```
```
HTTP/1.1 200 
vary: accept-encoding
Content-Type: application/json
Transfer-Encoding: chunked
Date: Mon, 12 Oct 2026 08:55:10 GMT

{"id":10001,"description":"Reading lamp and chair","customer":{"id":1,"name":"Muriel Donnelly"},"products":[{"id":1,"description":"Lamp"},{"id":2,"description":"Chair"}]}
```
A missing order is 404 and a malformed ID is 400, both as problem documents:
```shell
curl -s localhost:8080/order/999999
curl -s localhost:8080/order/abc
```
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Order 999999 does not exist",
  "instance": "/order/999999"
}
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid value for 'id'",
  "instance": "/order/abc",
  "errors": [
    {
      "field": "id",
      "message": "invalid value"
    }
  ]
}
```

## Task 2: customers by a substring of a word in their name
```shell
curl -s 'localhost:8080/customer?q=ann'
```
```json
[
  {
    "id": 2,
    "name": "Lance Stiedemann Sr.",
    "orders": [
      {
        "id": 17,
        "description": "Rustic Bronze Salad"
      },
      {
        "id": 113,
        "description": "Practical Soft Salad"
      }
      ... 97 more orders ...
    ]
  },
  {
    "id": 4,
    "name": "Dianne Lemke",
    "orders": [
      {
        "id": 5,
        "description": "Recycled Wooden Shoes"
      },
      {
        "id": 123,
        "description": "Incredible Concrete Salad"
      }
      ... 86 more orders ...
    ]
  },
  {
    "id": 18,
    "name": "Angelo Pfannerstill",
    "orders": [
      {
        "id": 19,
        "description": "Sleek Cotton Towels"
      },
      {
        "id": 30,
        "description": "Refined Soft Computer"
      }
      ... 104 more orders ...
    ]
  }
]
```
Several words must all match, in any order; an unmatched query is an empty list rather than an error:
```shell
curl -s 'localhost:8080/customer?q=glover%20ed'
curl -s 'localhost:8080/customer?q=zzzz'
```
```json
[
  {
    "id": 100,
    "name": "Ed Glover",
    "orders": [
      {
        "id": 169,
        "description": "Oriental Rubber Hat"
      },
      {
        "id": 183,
        "description": "Bespoke Cotton Shoes"
      }
      ... 102 more orders ...
    ]
  }
]
[]
```

## Task 3: performance of the GET endpoints
Every GET endpoint issues exactly one SQL statement whatever the dataset holds, which the suite asserts. That fixes the number of round trips, which is the cost a high-latency link multiplies; it does not fix the size of the work, because the three lists are unpaginated and their rows, memory and response bytes grow with the data. The evidence that round trips are what matters over such a link is produced by the benchmark:
```shell
./gradlew latencyBenchmark                # 25 ms each way; -Platency=50 for more
```
It writes `build/reports/latency-benchmark.md`. The figures from the run that decided the design are in docs/design-record.md under #9; in short, the customer listing went from 13 round trips (705 ms at 25 ms latency) to 2 (127 ms), every other read was already at the floor the read-only transaction sets, an index on `"order".customer_id` was added for the search once its effect was shown at a million orders, and gzip cuts the benchmark dataset's 2 MB order listing to 240 KB on the wire. From the running application the effect is visible as response time alone; the statement counts and plans need the benchmark's instrumentation.

## Bonus: CI and the Docker image
Every pull request runs `./gradlew clean check bootJar`, builds the image and smoke-tests it; a push to `master` publishes it. See [Continuous integration](#continuous-integration) and [Docker image](#docker-image); `docker compose up --build` runs this checkout's image against PostgreSQL in one command.

# Assumptions and production notes
The decisions most likely to be questioned, each recorded in full in docs/design-record.md:

* **Legacy orders stay without products (#7).** No association is invented for existing orders and no backfill is planned; the "at least one product" rule applies to orders created through the API and lives in the application, not the schema.
* **Duplicate products in an order are a 400 (#8),** not collapsed, because quantities are not in the model.
* **404 is for the URL, 422 for the body (#4).** A dangling `customer.id` or product ID is 422; only the resource in the path is 404.
* **Client-supplied IDs and unknown fields are 400 (#4).** The old endpoints merged a supplied ID into an existing row; that is now impossible.
* **Search requires every word (#6)** and runs in the database; the name column has no index because `ILIKE '%…%'` cannot use a b-tree and the table is one page.
* **Reads cost two round trips, not one (#9).** Each read is a read-only transaction whose `COMMIT` is its own round trip; autocommit reads would halve the floor and are recorded as the option not taken.
* **Lists are not paginated.** A pagination contract is a separate decision; `GET /order` returns everything, so the statement count is constant but the rows fetched, the memory held and the bytes sent grow with the table.
* **Migrations and rolling deploys.** The sequence repair (#3) locks `customer` and `"order"` in `SHARE` mode for one short transaction; the product tables (#7) are additive but adding the foreign key briefly blocks order inserts; the index (#9) is built `CONCURRENTLY` and, if interrupted, must be dropped by hand before the next start succeeds. An instance of the previous release running during a deploy keeps working against the migrated schema; orders it creates fall under the legacy rule.
* **Seed and generated data are changesets.** Applied changesets are never edited; new data is a new changeset ([utils/README.md](utils/README.md)).

# The assignment
The text below is the original task description, kept for reference.

## Tasks
1. Extend the order endpoint to find a specific order, by ID
2. Extend the customer endpoint to find customers based on a query string to match a substring of one of the words in their name
3. Users have complained that in production the GET endpoints can get very slow. The database is unfortunately not co-located with the application server, and there's high latency between the two. Identify if there are any optimisations that can improve performance
4. Add a new endpoint /products to model products which appear in an order:
      * A single order contains 1 or more products.
      * A product has an ID and a description.
      * Add a POST endpoint to create a product
      * Add a GET endpoint to return all products, and a specific product by ID
        * In both cases, also return a list of the order IDs which contain those products
      * Change the orders endpoint to return a list of products contained in the order

## Bonus points
1. Implement a CI pipeline on the platform of your choice to build the project and deliver it as a Dockerized image

## Notes on the tasks
Assume that the project represents a production application.
Think carefully about the impact on performance when implementing your changes
The specifications of the tasks have been left deliberately vague. You will be required to exercise judgement about what to deliver - in a real world environment, you would clarify these points in refinement, but since this is a project to be completed without interaction, feel free to make assumptions - but be prepared to defend them when asked.
There's no CI pipeline associated with this project, but in reality there would be. Consider the things that you would expect that pipeline to verify before allowing your code to be promoted
Feel free to refactor the codebase if necessary. Bad choices were deliberately made when creating this project.
