-- =============================================================================
-- V9 Add customer_email to carts for ownership isolation
-- Compatible with PostgreSQL 15+ and Hibernate 6 DDL validation
-- =============================================================================

ALTER TABLE carts ADD COLUMN customer_email VARCHAR(255);

CREATE INDEX idx_carts_customer_email ON carts(customer_email);
