-- =============================================================================
-- COMMERCE OS — ENTERPRISE POSTGRESQL RELATIONAL SCHEMA & MIGRATIONS
-- Blinkit-Grade Quick-Commerce Platform Authoritative Schema (PostgreSQL 16+)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- -----------------------------------------------------------------------------
-- 1. STORES & SELLERS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stores (
    id VARCHAR(64) PRIMARY KEY,
    store_name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    sla_minutes INT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stores_active_coords ON stores(is_active, latitude, longitude);

CREATE TABLE IF NOT EXISTS sellers (
    id VARCHAR(64) PRIMARY KEY,
    seller_id VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(32),
    password_hash VARCHAR(255) NOT NULL,
    store_id VARCHAR(64) REFERENCES stores(id),
    store_name VARCHAR(255),
    merchant_name VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    roles JSONB NOT NULL DEFAULT '["ROLE_SELLER"]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sellers_store ON sellers(store_id);
CREATE INDEX IF NOT EXISTS idx_sellers_email ON sellers(email);
CREATE UNIQUE INDEX IF NOT EXISTS unq_store_primary_active_seller ON sellers(store_id) WHERE status = 'ACTIVE' AND is_primary = TRUE;

CREATE TABLE IF NOT EXISTS admins (
    id VARCHAR(64) PRIMARY KEY,
    admin_id VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 1b. CATALOG OPERATORS (DB-BACKED GLOBAL CATALOG AUTHORITY — SELLER WRITE ISOLATION)
--     JWT.sub -> catalog_admins membership + GLOBAL_CATALOG_WRITE permission.
--     A normal seller NEVER holds this permission; sellers are STORE_INVENTORY_WRITE
--     only and must never mutate the global products table.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS catalog_admins (
    id VARCHAR(64) PRIMARY KEY,
    operator_id VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    full_name VARCHAR(255),
    permissions JSONB NOT NULL DEFAULT '["GLOBAL_CATALOG_WRITE"]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_catalog_admins_status ON catalog_admins(status);

-- -----------------------------------------------------------------------------
-- 2. RIDERS (AUTHORITATIVE FLEET IDENTITY)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS riders (
    id VARCHAR(64) PRIMARY KEY,
    rider_id VARCHAR(64) UNIQUE NOT NULL,
    phone VARCHAR(32) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    vehicle_number VARCHAR(64) NOT NULL,
    vehicle_type VARCHAR(32) NOT NULL DEFAULT 'TWO_WHEELER',
    tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD', -- 'STANDARD', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'SUSPENDED', 'ONBOARDING'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_riders_phone ON riders(phone);
CREATE INDEX IF NOT EXISTS idx_riders_status ON riders(status);

-- -----------------------------------------------------------------------------
-- 3. PRODUCTS (CATALOG DOMAIN ONLY — ZERO AUTHORITATIVE STOCK)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(64) PRIMARY KEY,
    sku VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(255),
    pack_size VARCHAR(100),
    mrp NUMERIC(10, 2) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    discounted_price NUMERIC(10, 2),
    rx_requirement VARCHAR(32) NOT NULL DEFAULT 'OTC', -- 'OTC', 'RX_REQUIRED', 'SCHEDULE_H'
    cold_chain_required BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(100),
    image_url TEXT,
    store_id VARCHAR(64) REFERENCES stores(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_products_id_sku UNIQUE (id, sku)
);

CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_store ON products(store_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(is_active);

-- -----------------------------------------------------------------------------
-- 4. INVENTORY & LEDGER (3-STATE INVENTORY DOMAIN)
-- -----------------------------------------------------------------------------

-- The canonical inventory identity is (store_id, product_id, sku). NONE of these may be NULL:
-- an inventory row without a store would break store isolation, and one without a global
-- product would break the global-catalog -> store-scoped-inventory model. The composite FK
-- (product_id + sku) keeps SKU/product identity consistent at the DB level.
CREATE TABLE IF NOT EXISTS inventory (
    id VARCHAR(64) PRIMARY KEY DEFAULT 'inv_' || uuid_generate_v4()::text,
    store_id VARCHAR(64) NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255),
    stock_count INT NOT NULL DEFAULT 0,
    reserved_count INT NOT NULL DEFAULT 0,
    available_count INT GENERATED ALWAYS AS (stock_count - reserved_count) STORED,
    min_threshold INT NOT NULL DEFAULT 5,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_stock_non_negative CHECK (stock_count >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved_count >= 0),
    CONSTRAINT chk_reserved_lte_stock CHECK (reserved_count <= stock_count),
    CONSTRAINT uq_store_sku UNIQUE(store_id, sku),
    CONSTRAINT fk_inventory_product_sku FOREIGN KEY (product_id, sku) REFERENCES products(id, sku) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inventory_sku_stock ON inventory(sku, stock_count);
CREATE INDEX IF NOT EXISTS idx_inventory_store_sku ON inventory(store_id, sku);
CREATE INDEX IF NOT EXISTS idx_inventory_store_prod_sku ON inventory(store_id, product_id, sku);

CREATE TABLE IF NOT EXISTS inventory_ledger (
    id VARCHAR(64) PRIMARY KEY DEFAULT 'led_' || uuid_generate_v4()::text,
    store_id VARCHAR(64) NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL,
    delta INT NOT NULL,
    new_stock INT NOT NULL,
    reason TEXT NOT NULL,
    actor_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_ledger_product_sku FOREIGN KEY (product_id, sku) REFERENCES products(id, sku) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inventory_ledger_sku ON inventory_ledger(store_id, sku, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_store_prod_sku ON inventory_ledger(store_id, product_id, sku, created_at DESC);

-- -----------------------------------------------------------------------------
-- 5. CUSTOMER IDENTITY & ADDRESSES & AUTH CHALLENGES & CARTS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(64) PRIMARY KEY,
    phone VARCHAR(32) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255),
    tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);

CREATE TABLE IF NOT EXISTS customer_addresses (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) REFERENCES customers(id) ON DELETE CASCADE,
    address_type VARCHAR(32) NOT NULL DEFAULT 'HOME', -- 'HOME', 'WORK', 'OTHER'
    address_line TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(32) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_addresses_customer ON customer_addresses(customer_id);

CREATE TABLE IF NOT EXISTS auth_challenges (
    id VARCHAR(64) PRIMARY KEY,
    phone VARCHAR(32) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_challenges_phone ON auth_challenges(phone);

CREATE TABLE IF NOT EXISTS carts (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    items JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_carts_customer ON carts(customer_id);

-- -----------------------------------------------------------------------------
-- 6. PRESCRIPTIONS & PHARMACIST VERIFICATION
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS prescriptions (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) REFERENCES customers(id) ON DELETE CASCADE,
    patient_name VARCHAR(255) NOT NULL,
    age INT,
    gender VARCHAR(16),
    doctor_name VARCHAR(255),
    doctor_registration_no VARCHAR(100),
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    pharmacist_id VARCHAR(64),
    license_no VARCHAR(100),
    rejection_reason TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_customer ON prescriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_status ON prescriptions(status);

-- -----------------------------------------------------------------------------
-- 7. ORDERS & LINE ITEMS (TRANSACTIONAL SOURCE OF TRUTH)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    customer_id VARCHAR(64) REFERENCES customers(id),
    store_id VARCHAR(64) REFERENCES stores(id),
    prescription_id VARCHAR(64) REFERENCES prescriptions(id),
    order_type VARCHAR(32) NOT NULL DEFAULT 'QUICK_COMMERCE_10MIN',
    status VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    total_amount NUMERIC(10, 2) NOT NULL,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'UPI_INSTANT', -- 'UPI_INSTANT', 'COD', 'CARD', 'WALLET'
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PAYMENT_PENDING', -- 'PAYMENT_PENDING', 'PAID', 'COD_PENDING', 'COD_COLLECTED', 'CANCELLED'
    is_cod BOOLEAN NOT NULL DEFAULT FALSE,
    cod_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    idempotency_key VARCHAR(128),
    request_hash VARCHAR(64),
    delivery_address JSONB NOT NULL,
    items JSONB NOT NULL,
    delivery_otp_hash VARCHAR(128) NOT NULL,
    otp_expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '45 minutes',
    otp_attempts INT NOT NULL DEFAULT 0,
    otp_verified_at TIMESTAMPTZ,
    cancellation JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unq_orders_customer_idempotency UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_store ON orders(store_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency ON orders(customer_id, idempotency_key);

-- -----------------------------------------------------------------------------
-- 8. DELIVERY SESSIONS & COD LEDGER & PAYMENTS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS delivery_sessions (
    id VARCHAR(64) PRIMARY KEY,
    delivery_id VARCHAR(64) UNIQUE NOT NULL,
    order_id VARCHAR(64) REFERENCES orders(order_id) ON DELETE CASCADE,
    store_id VARCHAR(64) REFERENCES stores(id),
    rider_id VARCHAR(64) REFERENCES riders(rider_id),
    rider_name VARCHAR(255),
    rider_phone VARCHAR(32),
    rider_vehicle VARCHAR(64),
    state VARCHAR(32) NOT NULL DEFAULT 'LOOKING_FOR_RIDER',
    merchant_name VARCHAR(255) NOT NULL,
    merchant_address TEXT NOT NULL,
    merchant_lat DOUBLE PRECISION NOT NULL,
    merchant_lng DOUBLE PRECISION NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(32),
    customer_address TEXT NOT NULL,
    customer_lat DOUBLE PRECISION NOT NULL,
    customer_lng DOUBLE PRECISION NOT NULL,
    distance_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_cod BOOLEAN NOT NULL DEFAULT FALSE,
    cod_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    cod_collected_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    cod_reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    otp_verified BOOLEAN NOT NULL DEFAULT FALSE,
    pricing_snapshot JSONB,
    history JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_delivery_order ON delivery_sessions(order_id);
CREATE INDEX IF NOT EXISTS idx_delivery_rider ON delivery_sessions(rider_id);
CREATE INDEX IF NOT EXISTS idx_delivery_state ON delivery_sessions(state);
CREATE UNIQUE INDEX IF NOT EXISTS unq_rider_active_delivery_session 
ON delivery_sessions(rider_id) 
WHERE rider_id IS NOT NULL AND state IN ('ACCEPTED', 'ARRIVED_MERCHANT', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED');

CREATE TABLE IF NOT EXISTS cod_ledger (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) REFERENCES orders(order_id) ON DELETE CASCADE,
    seller_id VARCHAR(64),
    amount_expected NUMERIC(10, 2) NOT NULL,
    amount_collected NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    shortage_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_COLLECTION',
    collector_id VARCHAR(64),
    notes TEXT,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cod_ledger_order ON cod_ledger(order_id);
CREATE INDEX IF NOT EXISTS idx_cod_ledger_collector ON cod_ledger(collector_id);

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(64) PRIMARY KEY,
    payment_id VARCHAR(64) UNIQUE NOT NULL,
    order_id VARCHAR(64) REFERENCES orders(order_id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    method VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_ref VARCHAR(255),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);

-- -----------------------------------------------------------------------------
-- 9. OFFERS & DISPATCH (SERIALIZABLE ACCEPTANCE BOUNDARY)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS offers (
    id VARCHAR(64) PRIMARY KEY DEFAULT 'off_' || uuid_generate_v4()::text,
    offer_id VARCHAR(64) UNIQUE NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    notification_id VARCHAR(64) NOT NULL,
    delivery_id VARCHAR(64) REFERENCES delivery_sessions(delivery_id) ON DELETE CASCADE,
    order_id VARCHAR(64) REFERENCES orders(order_id) ON DELETE CASCADE,
    rider_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    offer_created_at BIGINT NOT NULL,
    offer_expires_at BIGINT NOT NULL,
    earnings_amount NUMERIC(10, 2) NOT NULL,
    delivery_distance_km DOUBLE PRECISION NOT NULL,
    total_distance_km DOUBLE PRECISION NOT NULL,
    estimated_duration_mins INT NOT NULL,
    pricing_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    fcm_delivery_status VARCHAR(32) DEFAULT 'PENDING',
    rejection_reason TEXT,
    accepted_at TIMESTAMPTZ,
    history JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_offers_rider_status ON offers(rider_id, status);
CREATE INDEX IF NOT EXISTS idx_offers_delivery ON offers(delivery_id);

-- -----------------------------------------------------------------------------
-- 10. TRANSACTIONAL OUTBOX PATTERN (DURABLE EVENT CHAIN)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_claim ON outbox_events(status, next_attempt_at, retry_count, created_at)
WHERE status IN ('PENDING', 'PROCESSING');

-- -----------------------------------------------------------------------------
-- 11. RIDER TELEMETRY, PRESENCE, TOKENS & NOTIFICATIONS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rider_presence (
    rider_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE', -- 'ONLINE', 'OFFLINE', 'ON_DELIVERY', 'BREAK'
    last_known_lat DOUBLE PRECISION,
    last_known_lng DOUBLE PRECISION,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    shift_started_at TIMESTAMPTZ,
    shift_ended_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS rider_device_tokens (
    rider_id VARCHAR(64) PRIMARY KEY,
    token TEXT NOT NULL,
    platform VARCHAR(32) NOT NULL DEFAULT 'ANDROID',
    app_version VARCHAR(32),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rider_telemetry (
    id BIGSERIAL PRIMARY KEY,
    rider_id VARCHAR(64) NOT NULL,
    delivery_id VARCHAR(64),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    heading REAL,
    speed REAL,
    accuracy REAL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_telemetry_rider_time ON rider_telemetry(rider_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS rider_notifications (
    id VARCHAR(64) PRIMARY KEY,
    notification_id VARCHAR(64) UNIQUE NOT NULL,
    rider_id VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL DEFAULT 'DISPATCH_OFFER',
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    delivery_channel VARCHAR(32) NOT NULL DEFAULT 'FCM_PRIMARY',
    status VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_rider_notif_rider_unread ON rider_notifications(rider_id, status, created_at DESC);

-- -----------------------------------------------------------------------------
-- 12. AUDIT LOGS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(64) PRIMARY KEY DEFAULT 'aud_' || uuid_generate_v4()::text,
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    store_id VARCHAR(64),
    ip_address VARCHAR(64),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_store ON audit_logs(store_id, created_at DESC);
