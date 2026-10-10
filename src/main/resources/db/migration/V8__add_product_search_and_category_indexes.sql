-- =============================================================================
-- V8 Migration: Add indexes for product search, category filtering, and active status
-- =============================================================================

-- 1. Index on active status for fast catalog filtering
CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);

-- 2. Index on category for category browsing and filtering
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);

-- 3. Functional indexes for case-insensitive product name and category searches
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_products_category_lower ON products(LOWER(category));
