-- Real authentication support.
--
-- Previously every request resolved to a hardcoded "demo-user-1" identity
-- (see CurrentUserArgumentResolver) unless a client supplied its own
-- X-Demo-User-Id header - which the frontend never did, so every visitor
-- shared one account. This migration adds what's needed for real signup/
-- login: a password hash on users, and a simple opaque-token table for
-- sessions. It does not touch existing ownership columns (user_id foreign
-- keys on watchlists, ledger entries, etc.) - those were already correct;
-- only how the acting user's identity is established is changing.

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255);

-- Existing rows (e.g. the original demo-user-1) have no password and are
-- simply no longer reachable via login - they aren't deleted, since the
-- data itself isn't harmful to keep, but nothing can authenticate as them
-- going forward. New accounts are created explicitly via /api/v1/auth/signup.

CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_auth_tokens_user_id ON auth_tokens (user_id);
