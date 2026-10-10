-- =============================================================================
-- V6 Migration: Add product inventory stock and order_item product_id reference
-- =============================================================================

-- 1. Add non-null integer stock column to products with default of 50
ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 50;

-- 2. Add non-negative stock check constraint
ALTER TABLE products ADD CONSTRAINT chk_product_stock_non_negative CHECK (stock >= 0);

-- 3. Add nullable product_id UUID column to order_items referencing products(id)
-- ON DELETE SET NULL protects historical order_items if a product row were ever deleted
ALTER TABLE order_items ADD COLUMN product_id UUID REFERENCES products(id) ON DELETE SET NULL;

-- 4. Create index for order_items.product_id
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
