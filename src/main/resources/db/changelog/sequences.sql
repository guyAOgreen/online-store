-- The seed data (2-data) inserts explicit IDs without advancing the BIGSERIAL
-- sequences, so generated IDs collided with seeded rows on a fresh database.
--
-- For each table the sequence is set to the larger of the table's highest ID and
-- the sequence's current value, so a sequence already ahead of the rows is never
-- moved backwards. pg_sequences.last_value is NULL for a sequence that was never
-- used; for that case (and an empty table) the sequence is left so its next
-- value is 1, otherwise its next value is the target plus one.
--
-- Write coordination: this changeset runs in one transaction and EXCLUSIVE locks
-- block application inserts (whose nextval runs inside the INSERT) until the
-- repair commits, so a concurrent instance cannot take an ID between the read
-- and the setval. A client calling nextval directly or inserting explicit IDs
-- during the migration window is not covered.

LOCK TABLE customer IN EXCLUSIVE MODE;
SELECT setval('customer_id_seq', GREATEST(target, 1), target > 0)
FROM (SELECT GREATEST(
                 (SELECT COALESCE(MAX(id), 0) FROM customer),
                 COALESCE((SELECT last_value FROM pg_sequences
                           WHERE schemaname = current_schema() AND sequencename = 'customer_id_seq'), 0)
             ) AS target) AS t;

LOCK TABLE "order" IN EXCLUSIVE MODE;
SELECT setval('order_id_seq', GREATEST(target, 1), target > 0)
FROM (SELECT GREATEST(
                 (SELECT COALESCE(MAX(id), 0) FROM "order"),
                 COALESCE((SELECT last_value FROM pg_sequences
                           WHERE schemaname = current_schema() AND sequencename = 'order_id_seq'), 0)
             ) AS target) AS t;
