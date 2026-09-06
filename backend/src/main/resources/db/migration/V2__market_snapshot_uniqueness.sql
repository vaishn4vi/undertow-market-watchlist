-- Adds the constraint that makes market snapshot ingestion idempotent:
-- one snapshot per (symbol, as_of). Kept as its own migration rather than
-- editing V1, since V1 may already be applied against a running database.

ALTER TABLE market_snapshots
    ADD CONSTRAINT uq_market_snapshot_symbol_as_of UNIQUE (symbol, as_of);
