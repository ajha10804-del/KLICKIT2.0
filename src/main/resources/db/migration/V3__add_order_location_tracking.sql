
ALTER TABLE orders
    ADD COLUMN customer_latitude DOUBLE PRECISION,
    ADD COLUMN customer_longitude DOUBLE PRECISION,
    ADD COLUMN delivery_latitude DOUBLE PRECISION,
    ADD COLUMN delivery_longitude DOUBLE PRECISION,
    ADD COLUMN location_updated_at TIMESTAMP WITH TIME ZONE;
