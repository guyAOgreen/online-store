-- The seed data (2-data) inserts explicit IDs without advancing the BIGSERIAL
-- sequences, so generated IDs collided with seeded rows on a fresh database.
--
-- For each table the sequence is set to the larger of the table's highest ID and
-- the sequence's current value, so a sequence already ahead of the rows is never
-- moved backwards. pg_sequences.last_value is NULL for a sequence that was never
-- used; for that case (and an empty table) the sequence is left so its next
-- value is 1, otherwise its next value is the target plus one.
--
-- Write coordination: this changeset runs in one transaction. The SHARE lock
-- below blocks application inserts (whose nextval runs inside the INSERT) until
-- the repair commits, so a concurrent instance cannot take an ID between the
-- read and the setval. SHARE rather than EXCLUSIVE because an in-flight order
-- insert needs ROW SHARE on customer for its foreign-key check; EXCLUSIVE would
-- deadlock with it. Both tables are locked in one statement, parent first,
-- matching the order a customer created with nested orders takes them, so
-- neither write path can wait on this transaction while it waits on them.
-- A client calling nextval directly or inserting explicit IDs during the
-- migration window is not covered.

LOCK TABLE customer, "order" IN SHARE MODE;

SELECT setval('customer_id_seq', GREATEST(target, 1), target > 0)
FROM (SELECT GREATEST(
                 (SELECT COALESCE(MAX(id), 0) FROM customer),
                 COALESCE((SELECT last_value FROM pg_sequences
                           WHERE schemaname = current_schema() AND sequencename = 'customer_id_seq'), 0)
             ) AS target) AS t;

SELECT setval('order_id_seq', GREATEST(target, 1), target > 0)
FROM (SELECT GREATEST(
                 (SELECT COALESCE(MAX(id), 0) FROM "order"),
                 COALESCE((SELECT last_value FROM pg_sequences
                           WHERE schemaname = current_schema() AND sequencename = 'order_id_seq'), 0)
             ) AS target) AS t;
