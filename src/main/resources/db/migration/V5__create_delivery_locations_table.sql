-- =============================================================================
-- V5 Migration: Create delivery_locations table for live rider tracking
-- =============================================================================

CREATE TABLE delivery_locations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    delivery_partner_id UUID NOT NULL REFERENCES delivery_partners(id),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_delivery_locations_order_id ON delivery_locations(order_id);
CREATE INDEX idx_delivery_locations_partner_id ON delivery_locations(delivery_partner_id);
