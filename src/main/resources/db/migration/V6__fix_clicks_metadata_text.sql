-- Fix: R2DBC maps Java String to VARCHAR, not JSONB.
-- TEXT can still store JSON and be queried with ::jsonb cast when needed.

ALTER TABLE clicks ALTER COLUMN metadata TYPE TEXT;
