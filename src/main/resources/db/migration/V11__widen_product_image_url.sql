-- V11: Widen products.image_url from VARCHAR(255) to TEXT to support CDN URLs and data URIs
ALTER TABLE products ALTER COLUMN image_url TYPE TEXT;
