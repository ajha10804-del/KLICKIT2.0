-- =============================================================================
-- V12: Add enabled column to users table for safe account lifecycle and deactivation support
-- Preserves all foreign key relationships, audit records, and user history
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;
