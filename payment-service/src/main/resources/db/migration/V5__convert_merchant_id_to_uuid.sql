-- Convert merchant_id column from VARCHAR to UUID (PostgreSQL)
-- Make sure you have a backup before applying in production.

BEGIN;

-- 1) Add a temporary UUID column
ALTER TABLE payments ADD COLUMN merchant_id_tmp UUID;

-- 2) Copy/convert existing string values to UUID; this will fail if values are not valid UUID strings
UPDATE payments SET merchant_id_tmp = merchant_id::uuid;

-- 3) Drop index on old column, drop the old column and rename the new one
DROP INDEX IF EXISTS idx_payments_merchant_id;
ALTER TABLE payments DROP COLUMN merchant_id;
ALTER TABLE payments RENAME COLUMN merchant_id_tmp TO merchant_id;

-- 4) Recreate index
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);

COMMIT;

-- Notes:
-- If some merchant_id values are not valid UUIDs, you must fix or migrate them before running this script.
-- For MySQL or other DBs, adapt the migration accordingly (e.g., use BINARY(16) for UUID storage).

