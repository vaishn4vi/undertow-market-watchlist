-- UNDERTOW schema V1
-- Covers the full ERD from docs/architecture.md up front so later phases
-- (market, signals, attention, reconciliation, backtest) only ever append
-- to this file in new migrations rather than restructuring it.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id       VARCHAR(100) NOT NULL UNIQUE,   -- the demo-user header value
    display_name      VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_checkin_at   TIMESTAMPTZ
);

CREATE TABLE user_preferences (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    persistence_threshold           INT NOT NULL DEFAULT 70,
    decoupling_threshold_delta      INT NOT NULL DEFAULT 0,
    silence_threshold_delta         INT NOT NULL DEFAULT 0,
    abnormality_threshold_delta     INT NOT NULL DEFAULT 0,
    notification_pref               VARCHAR(30) NOT NULL DEFAULT 'IN_APP',
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- WATCHLISTS
-- ============================================================

CREATE TABLE watchlists (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    position     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_watchlists_user_id ON watchlists(user_id);

CREATE TABLE watchlist_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watchlist_id   UUID NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    symbol         VARCHAR(20) NOT NULL,
    sector         VARCHAR(60) NOT NULL,
    position       INT NOT NULL DEFAULT 0,
    added_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist_symbol UNIQUE (watchlist_id, symbol)
);

CREATE INDEX idx_watchlist_items_watchlist_id ON watchlist_items(watchlist_id);
CREATE INDEX idx_watchlist_items_symbol ON watchlist_items(symbol);

-- ============================================================
-- MARKET DATA (shared, symbol-scoped — Phase 3)
-- ============================================================

CREATE TABLE market_snapshots (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol                    VARCHAR(20) NOT NULL,
    as_of                     TIMESTAMPTZ NOT NULL,
    ingested_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    price                     NUMERIC(14,4) NOT NULL,
    return_pct                NUMERIC(8,4) NOT NULL,
    sector                    VARCHAR(60) NOT NULL,
    sector_return_pct         NUMERIC(8,4) NOT NULL,
    peer_basket_return_pct    NUMERIC(8,4) NOT NULL,
    market_status             VARCHAR(20) NOT NULL,
    provider                  VARCHAR(20) NOT NULL
);

CREATE INDEX idx_market_snapshots_symbol ON market_snapshots(symbol);
CREATE INDEX idx_market_snapshots_as_of ON market_snapshots(as_of);

CREATE TABLE market_observations (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol             VARCHAR(20) NOT NULL,
    source_timestamp   TIMESTAMPTZ NOT NULL,
    received_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    trust_status       VARCHAR(20) NOT NULL,
    raw_payload        JSONB NOT NULL,
    snapshot_id        UUID REFERENCES market_snapshots(id) ON DELETE SET NULL
);

CREATE INDEX idx_market_observations_symbol ON market_observations(symbol);

-- ============================================================
-- SIGNALS (shared, symbol-scoped — Phase 4)
-- ============================================================

CREATE TABLE signal_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol           VARCHAR(20) NOT NULL,
    type             VARCHAR(30) NOT NULL,
    severity         INT NOT NULL,
    confidence       NUMERIC(4,3) NOT NULL,
    detected_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    snapshot_id      UUID NOT NULL REFERENCES market_snapshots(id),
    superseded_by    UUID REFERENCES signal_events(id)
);

CREATE INDEX idx_signal_events_symbol ON signal_events(symbol);
CREATE INDEX idx_signal_events_detected_at ON signal_events(detected_at);

CREATE TABLE signal_evidence (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_event_id          UUID NOT NULL REFERENCES signal_events(id) ON DELETE CASCADE,
    stock_return             NUMERIC(8,4) NOT NULL,
    sector_return            NUMERIC(8,4) NOT NULL,
    expected_return          NUMERIC(8,4) NOT NULL,
    deviation                NUMERIC(8,4) NOT NULL,
    historical_percentile    NUMERIC(5,2) NOT NULL,
    evidence_extra           JSONB
);

-- ============================================================
-- ATTENTION LEDGER (per-user — Phase 6)
-- ============================================================

CREATE TABLE signal_ledger_entries (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol                    VARCHAR(20) NOT NULL,
    signal_type               VARCHAR(30) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    first_detected_at         TIMESTAMPTZ NOT NULL,
    last_detected_at          TIMESTAMPTZ NOT NULL,
    previous_severity         INT,
    current_severity          INT NOT NULL,
    max_severity              INT NOT NULL,
    resolved_at               TIMESTAMPTZ,
    verification_status       VARCHAR(20) NOT NULL,
    persistence_count         INT NOT NULL DEFAULT 0,
    worsened_flag             BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged              BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at           TIMESTAMPTZ,
    latest_signal_event_id    UUID REFERENCES signal_events(id),
    dismissed                 BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_ledger_user_id ON signal_ledger_entries(user_id);
CREATE INDEX idx_ledger_status ON signal_ledger_entries(status);
CREATE INDEX idx_ledger_symbol ON signal_ledger_entries(symbol);

-- ============================================================
-- RECONCILIATION (per-user — Phase 8)
-- ============================================================

CREATE TABLE checkins (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id            VARCHAR(100) NOT NULL UNIQUE,   -- idempotency key
    checkin_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_checkin_at   TIMESTAMPTZ,
    days_away             NUMERIC(6,2)
);

CREATE INDEX idx_checkins_user_id ON checkins(user_id);

CREATE TABLE signal_reconciliations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkin_id                  UUID NOT NULL REFERENCES checkins(id) ON DELETE CASCADE,
    signal_ledger_entry_id      UUID NOT NULL REFERENCES signal_ledger_entries(id) ON DELETE CASCADE,
    outcome                     VARCHAR(20) NOT NULL,
    severity_before             INT,
    severity_after              INT,
    narrative_text              TEXT
);

CREATE INDEX idx_reconciliations_checkin_id ON signal_reconciliations(checkin_id);

-- ============================================================
-- ATTENTION DEBT (per-user — Phase 7)
-- ============================================================

CREATE TABLE attention_debt_snapshots (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_debt              NUMERIC(10,4) NOT NULL,
    normalized_debt       NUMERIC(5,2) NOT NULL,
    band                  VARCHAR(20) NOT NULL,
    trajectory            VARCHAR(20) NOT NULL,
    new_signal_component       NUMERIC(10,4) NOT NULL DEFAULT 0,
    worsened_component         NUMERIC(10,4) NOT NULL DEFAULT 0,
    resolved_component         NUMERIC(10,4) NOT NULL DEFAULT 0
);

CREATE INDEX idx_debt_snapshots_user_id ON attention_debt_snapshots(user_id);
CREATE INDEX idx_debt_snapshots_computed_at ON attention_debt_snapshots(computed_at);
