# Store Application
The Store application keeps track of customers and orders in a database.

# Assumptions
This README assumes you're using a posix environment. It's possible to run this on Windows as well:
* Instead of `./gradlew` use `gradlew.bat`
* The syntax for creating the Docker container is different. You could also install PostgreSQL on bare metal if you prefer


# Prerequisites
This service assumes the presence of a postgresql 16.2 database server running on localhost:5433 (note the non-standard port)
It assumes a username and password `admin:admin` can be used.
It assumes there's already a database called `store`

You can start the PostgreSQL instance like this:
```shell
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
```

# Running the application
You should be able to run the service using
```shell
./gradlew bootRun
```

The application uses Liquibase to migrate the schema. Some sample data is provided. You can create more data by reading the documentation in utils/README.md

# Docker image
The service is also delivered as a Docker image, built from the `Dockerfile` at the repository root and published to GitHub Container Registry by CI (see below):

| Tag | Meaning |
|-----|---------|
| `ghcr.io/guyaogreen/online-store:latest` | the most recent push to `master` |
| `ghcr.io/guyaogreen/online-store:sha-<short sha>` | the exact commit it was built from |
| `ghcr.io/guyaogreen/online-store:<version>` | a `v<version>` git tag, for example `v1.0.0` |

The repository is private, so pulling needs `docker login ghcr.io` with a GitHub token that has `read:packages`. Building locally needs nothing but Docker: the builder stage compiles the source with the committed Gradle wrapper on a JDK 17 image, and the runtime stage is a JRE with the boot jar's layers extracted, running as a non-root user.
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

The quickest way to run it against PostgreSQL is the compose file, which starts `postgres:16.2` with the credentials above (also published on `localhost:5433` for `bootRun`, so stop the standalone `postgres` container first) and the application on `localhost:8080`:
```shell
docker compose up            # pulls the published image
docker compose up --build    # builds the image from this checkout instead
docker compose down -v       # stops both and drops the database volume
```

To run the image next to the standalone `postgres` container from the prerequisites instead, put both on one Docker network so the application can reach it by name:
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

The same gate can be run locally with:
```shell
./gradlew clean check bootJar
```

Gradle itself needs a JDK to start: install any JDK that Gradle 8.11 can run on (Java 17 to 23 are good choices) before running the wrapper.
The build then declares a Java 17 toolchain. If JDK 17 is not the installed JDK, the Foojay toolchain resolver downloads it automatically for compiling and testing.

There are two test suites:
* `./gradlew test` runs the unit tests in `src/test`. Controller tests mock the services and service tests mock the repositories, so they need no database.
* `./gradlew integrationTest` runs the tests in `src/integrationTest` against a PostgreSQL 16.2 container started by Testcontainers. Docker must be running; the suite fails rather than skips without it. No pre-existing `store` database is needed, and the container is removed when the run finishes.

`./gradlew check` runs both.

# Performance measurement
The GET endpoints are measured, not just tested, because the production database sits behind a high-latency link. `./gradlew latencyBenchmark` (Docker required, not part of `check`) runs every GET request against a PostgreSQL container reached through a Toxiproxy latency toxic, on the seed plus a larger product dataset, and writes `build/reports/latency-benchmark.md`: statements prepared, statements and rows as PostgreSQL counted them, query plans, response bytes with and without gzip, median timings with and without the simulated latency, and the number of round trips derived from the difference. `-Platency=<ms>` sets the one-way latency (default 25). The before-and-after figures and the decisions taken on them are in SOLUTION.md under #9. `ReadQueryCountIntegrationTest`, which is part of `check`, holds the statement count of every GET endpoint constant whatever the dataset size.

# Data model
An order has an ID, a description, and is associated with the customer which made the order.
A customer has an ID, a name, and 0 or more orders.

# API
Two endpoints are provided:
   * /order
   * /customer

Each of them supports a POST and a GET. The data model is circular - a customer owns a number of orders, and that order necessarily refers back to the customer which owns it.
To avoid loops in the serializer, when writing out a Customer or an Order, they're mapped to CustomerDTO and OrderDTO which contain truncated versions of the dependent object - CustomerOrderDTO and OrderCustomerDTO respectively.

The API is documented in the OpenAPI file OpenAPI.yaml. Note that this spec includes part of one of the tasks below (the new /products endpoint)

# Tasks

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

# Bonus points
1. Implement a CI pipeline on the platform of your choice to build the project and deliver it as a Dockerized image

# Notes on the tasks
Assume that the project represents a production application.
Think carefully about the impact on performance when implementing your changes
The specifications of the tasks have been left deliberately vague. You will be required to exercise judgement about what to deliver - in a real world environment, you would clarify these points in refinement, but since this is a project to be completed without interaction, feel free to make assumptions - but be prepared to defend them when asked.
There's no CI pipeline associated with this project, but in reality there would be. Consider the things that you would expect that pipeline to verify before allowing your code to be promoted
Feel free to refactor the codebase if necessary. Bad choices were deliberately made when creating this project.
