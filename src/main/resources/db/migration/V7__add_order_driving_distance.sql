-- =============================================================================
-- V7 Migration: Add order driving distance, status, and destination coordinates
-- =============================================================================

-- 1. Add integer metre column for verified driving distance (nullable for uncalculated/legacy orders)
ALTER TABLE orders ADD COLUMN driving_distance_meters INTEGER;

-- 2. Add calculation status column with default 'UNAVAILABLE' so existing orders fail closed
ALTER TABLE orders ADD COLUMN driving_distance_status VARCHAR(32) NOT NULL DEFAULT 'UNAVAILABLE';

-- 3. Add columns to record the destination coordinates evaluated for the route
ALTER TABLE orders ADD COLUMN driving_distance_destination_lat DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN driving_distance_destination_lng DOUBLE PRECISION;

-- 4. Create index for driving_distance_status
CREATE INDEX idx_orders_driving_distance_status ON orders(driving_distance_status);
