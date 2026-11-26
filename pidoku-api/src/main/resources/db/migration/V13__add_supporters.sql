-- Supporters: players who tipped on Ko-fi get a badge next to their name on leaderboards (see
-- "Monetisation and analytics" in docs/ROADMAP.md). Linking a payment to a player needs nothing
-- about the payer: the player asks the app for a single-use code and pastes it into their Ko-fi
-- message, and the webhook (SupporterService) matches the code. Ko-fi's name, email, message and
-- amount are never stored.

-- When the player first supported; null if they haven't. supporter_until is null for a permanent
-- badge (one-off tips); it's there so monthly memberships can expire later without a migration.
ALTER TABLE players
    ADD COLUMN supporter_since TIMESTAMPTZ,
    ADD COLUMN supporter_until TIMESTAMPTZ;

-- The code a player pastes into their Ko-fi message. One live code per player: asking again
-- replaces it, and it's deleted once a payment uses it.
CREATE TABLE supporter_codes (
    player_id  BIGINT      PRIMARY KEY,
    code       VARCHAR(16) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT supporter_codes_player_fkey FOREIGN KEY (player_id) REFERENCES players (id)
);

-- Every Ko-fi payment the webhook accepted, by Ko-fi's transaction id only: so a resent webhook
-- changes nothing, and so a payment without a (valid) code can be linked by hand later.
-- player_id is null until it's linked.
CREATE TABLE kofi_payments (
    transaction_id VARCHAR(64) PRIMARY KEY,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    player_id      BIGINT,

    CONSTRAINT kofi_payments_player_fkey FOREIGN KEY (player_id) REFERENCES players (id)
);

-- Deleting a player's payments.
CREATE INDEX kofi_payments_player_idx ON kofi_payments (player_id);
