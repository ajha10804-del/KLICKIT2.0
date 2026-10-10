-- =============================================================================
-- V3 Migration: Add delivery coordinates, landmark, and meet_at_gate to orders
-- =============================================================================

ALTER TABLE orders ADD COLUMN customer_latitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN customer_longitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN customer_landmark VARCHAR(255);
ALTER TABLE orders ADD COLUMN meet_at_gate BOOLEAN NOT NULL DEFAULT false;
