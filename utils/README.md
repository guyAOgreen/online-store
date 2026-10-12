# Data generation

`generateData.js` writes SQL `INSERT` statements for random customers and orders to standard output. It is how the seed data in `src/main/resources/db/changelog/data.sql` was produced. It requires Node.js and npm.

## Installation

```shell
npm install
```

## Generating more data

The seed data is applied by Liquibase changeset `2-data`, and Liquibase records a checksum of every changeset it has run. **Do not regenerate over `data.sql` or edit any other applied changeset:** every database that has already run it, the production one included, would refuse to start with a checksum error, and the only ways out are to accept the changed checksum by hand or to drop the database, neither of which is acceptable for production data.

Generated data is added as a new changeset instead, which is the same path every schema change in this project takes:

1. Generate into a new file rather than over the seed:
   ```shell
   node ./generateData.js > ../src/main/resources/db/changelog/more-data.sql
   ```
2. Add `db.changelog-6.yaml` (the next free number) next to the existing changelogs, pointing at the new file in the same `sqlFile` form as `db.changelog-2.yaml`, and include it at the end of `db.changelog-master.yaml`.
3. Start the application, or run `./gradlew integrationTest`: the migration tests assert the list of applied changesets, so add the new ID where `3-sequences`, `4-products` and `5-order-customer-index` are listed.

Liquibase then applies the new changeset once, on every database, and leaves the applied ones alone.

## What the script does not do

- **It writes explicit IDs starting from 1**, so its output as-is collides with the seeded rows on any database that already holds them. Either edit the `N`, `M` and loop starts so the IDs begin above the highest existing ID, or remove the `id` columns from the generated `INSERT`s and let the sequences assign them. If explicit IDs are kept, the sequences must be moved past them afterwards; `sequences.sql` (changeset `3-sequences`) shows how, and the same statements can be copied into the new changeset because they never move a sequence backwards.
- **It does not escape apostrophes.** Faker can produce names such as `O'Conner`, which break the SQL. Check the output for `'` inside values, or add `.replace(/'/g, "''")` to the two template strings before using it.
- **It generates no products.** `product` and `order_product` arrived after the script was written. Products are created through `POST /products` and attached through `POST /order`; the integration suite's `LargerDataset` shows how to load them in bulk for testing.

For a local database that holds nothing worth keeping, `docker compose down -v` (or removing the `postgres` volume) and starting again is still the quickest way back to a clean seed. That is a development convenience, not a procedure for any shared database.
