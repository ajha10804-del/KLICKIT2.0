-- =============================================================================
-- V2 Migration: Add delivery_fee to orders table
-- =============================================================================

ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0;
