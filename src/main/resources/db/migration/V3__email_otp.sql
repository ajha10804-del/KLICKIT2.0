-- =============================================================================
-- V3: Email OTP login
--   * otp_codes stores a keyed hash of each one-time code (never the code itself)
--   * users.phone becomes optional so a customer can be created from email alone
--     (UNIQUE still applies to non-null values)
-- =============================================================================

CREATE TABLE otp_codes (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_otp_codes_email_created ON otp_codes(email, created_at DESC);
CREATE INDEX idx_otp_codes_expires_at ON otp_codes(expires_at);

ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
