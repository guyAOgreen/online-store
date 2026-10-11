-- Products and the order/product relationship (#7). Additive only: nothing
-- here reads, rewrites or backfills customer or order rows, so an application
-- instance still running the previous release keeps working while this is
-- applied. The foreign key to "order" takes a SHARE ROW EXCLUSIVE lock on that
-- table for the changeset's transaction: an order insert already in flight
-- finishes first, and inserts arriving afterwards wait the few milliseconds
-- the two CREATE TABLEs take. The customer table is not locked, so the #3
-- deadlock shape (an insert holding "order" and needing customer) cannot arise.
--
-- No products are seeded and no associations are invented for existing
-- orders: an order created before this changeset simply has no rows in
-- order_product. The rule that a new order carries at least one product is
-- an application rule (#8) and is not expressed in the schema, because a
-- row-count minimum cannot be a plain constraint and every existing order
-- would violate it.
--
-- order_product's primary key is the (order_id, product_id) pair, which both
-- forbids storing the same association twice and indexes the order -> products
-- direction. The second index serves product -> orders. Both foreign keys keep
-- the default NO ACTION behaviour, which refuses the delete as RESTRICT would:
-- a product still in an order cannot be deleted out from under it, and an
-- order's associations go when the application deletes the order (nothing
-- does today).

CREATE TABLE product (
                         id BIGSERIAL PRIMARY KEY,
                         description VARCHAR(255) NOT NULL
);

CREATE TABLE order_product (
                               order_id BIGINT NOT NULL,
                               product_id BIGINT NOT NULL,
                               CONSTRAINT pk_order_product PRIMARY KEY (order_id, product_id),
                               CONSTRAINT fk_order_product_order FOREIGN KEY (order_id) REFERENCES "order" (id),
                               CONSTRAINT fk_order_product_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_order_product_product_id ON order_product (product_id);
