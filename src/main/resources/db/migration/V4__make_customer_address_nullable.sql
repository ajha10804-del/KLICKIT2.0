-- =============================================================================
-- V4 Migration: Allow nullable customer_address for map-first checkouts
-- =============================================================================

ALTER TABLE orders ALTER COLUMN customer_address DROP NOT NULL;
