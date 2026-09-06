-- Attention ledger hysteresis support columns.
--
-- resolve_streak mirrors persistence_count (already in V1) but for the
-- opposite direction: consecutive verified readings at/below the resolve
-- threshold. Both together implement the two-consecutive-observation
-- hysteresis that prevents a signal from flapping between states.
--
-- last_verified_as_of records which market snapshot date this entry was
-- last updated against, making check-ins idempotent: re-running a sync for
-- the same underlying day's data must never double-count toward either
-- streak, which requires knowing whether "today" has actually advanced
-- since the last update.

ALTER TABLE signal_ledger_entries ADD COLUMN resolve_streak INT NOT NULL DEFAULT 0;
ALTER TABLE signal_ledger_entries ADD COLUMN last_verified_as_of TIMESTAMPTZ;
