# Data generation

`generateData.js` writes SQL `INSERT` statements for random customers and orders to standard output. It is how the seed data in `src/main/resources/db/changelog/data.sql` was produced. It requires Node.js and npm.

## Installation

```shell
npm install
```

## Generating more data

The seed data is applied by Liquibase changeset `2-data`, and Liquibase records a checksum of every changeset it has run. **Do not regenerate over `data.sql` or edit any other applied changeset:** every database that has already run it, the production one included, would refuse to start with a checksum error, and the only ways out are to accept the changed checksum by hand or to drop the database, neither of which is acceptable for production data.

### First, make the IDs fit the database

The script writes explicit IDs starting from 1 for customers and for orders, and the orders reference customers by literal `customer_id` values from 1 to `N`. Its output as-is therefore collides with the seeded rows. Removing the `id` columns is not enough: the customers would get fresh IDs from the sequence, but the orders would still point at seeded customers 1 to `N` and the new customers would stay unused. Instead, edit the script so every ID starts above the highest existing ID, for example a `FIRST_CUSTOMER = 101` and `FIRST_ORDER = 10001` used by both loops and by the `customerId` reference. Because the IDs are explicit, the sequences must be moved past them after the load; `sequences.sql` (changeset `3-sequences`) shows the statements, which never move a sequence backwards and so are safe to copy.

Two more limits of the script: it does not escape apostrophes, and Faker can produce names such as `O'Conner` that break the SQL, so check the output for `'` inside values or add `.replace(/'/g, "''")` to the two template strings; and it generates no products, because `product` and `order_product` arrived after it was written. Products are created through `POST /products` and attached through `POST /order`; the integration suite's `LargerDataset` shows how to load them in bulk.

### A local database that should hold more rows

Run the generated SQL directly against the database, outside Liquibase:
```shell
node ./generateData.js > more-data.sql
docker exec -i postgres psql -U admin -d store < more-data.sql
```
Nothing in the repository changes, Liquibase is unaware of the rows, and the test suite is unaffected because it migrates its own container. `docker compose down -v` (or removing the `postgres` volume) and starting again is the way back to a clean seed; that is a development convenience, not a procedure for any shared database.

### Data that every environment should hold

Add it as a new changeset, which is the same path every change in this project has taken:

1. Generate into a new file rather than over the seed, with the IDs offset as above:
   ```shell
   node ./generateData.js > ../src/main/resources/db/changelog/more-data.sql
   ```
2. Add `db.changelog-6.yaml` (the next free number) next to the existing changelogs, pointing at the new file in the same `sqlFile` form as `db.changelog-2.yaml`, and include it at the end of `db.changelog-master.yaml`. Append the sequence repair to the same file.
3. Update the tests that pin the seed, in the same change. `MigrationIntegrationTest` and `SchemaUpgradeIntegrationTest` assert the list of applied changeset IDs; `SeedDataIntegrationTest` asserts exactly 100 customers and 10,000 orders, `CustomerSearchIntegrationTest` a 100-customer listing, and several tests take 10,000 as the last seeded order ID. `./gradlew integrationTest` fails until they agree with the new seed, which is the point: committed seed data is part of the contract the suite checks.

Liquibase then applies the new changeset once, on every database, and leaves the applied ones alone.
