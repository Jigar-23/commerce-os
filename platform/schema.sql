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
    seller_approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_open BOOLEAN NOT NULL DEFAULT TRUE,
    is_offline BOOLEAN NOT NULL DEFAULT FALSE,
    surge_mode BOOLEAN NOT NULL DEFAULT FALSE,
    opening_time_utc VARCHAR(8) NOT NULL DEFAULT '00:00',
    closing_time_utc VARCHAR(8) NOT NULL DEFAULT '23:59',
    max_active_orders INT NOT NULL DEFAULT 50,
    service_radius_km DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stores_active_coords ON stores(is_active, latitude, longitude);

-- Migration safety: ensure runtime availability of store operational status columns
ALTER TABLE stores ADD COLUMN IF NOT EXISTS is_open BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS is_offline BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS surge_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS opening_time_utc VARCHAR(8) NOT NULL DEFAULT '00:00';
ALTER TABLE stores ADD COLUMN IF NOT EXISTS closing_time_utc VARCHAR(8) NOT NULL DEFAULT '23:59';
ALTER TABLE stores ADD COLUMN IF NOT EXISTS max_active_orders INT NOT NULL DEFAULT 50;

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
    aadhaar_number VARCHAR(32),
    store_id VARCHAR(64),
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
    images JSONB NOT NULL DEFAULT '[]'::jsonb,
    description TEXT,
    expiry_date VARCHAR(64),
    manufacturing_date VARCHAR(64),
    manufacturer VARCHAR(255),
    salt_composition TEXT,
    safety_advice JSONB NOT NULL DEFAULT '[]'::jsonb,
    storage_condition VARCHAR(255),
    therapeutic_class VARCHAR(255),
    hsn_code VARCHAR(16) NOT NULL,
    gst_rate NUMERIC(4, 2) NOT NULL,
    store_id VARCHAR(64) REFERENCES stores(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    service_radius_km DOUBLE PRECISION NOT NULL DEFAULT 10.0,
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
    service_radius_km DOUBLE PRECISION NOT NULL DEFAULT 10.0,
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
    state VARCHAR(100),
    postal_code VARCHAR(32) NOT NULL,
    landmark TEXT,
    contact_name VARCHAR(128),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    contact_phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Migration safety: ensure newly supported address columns exist on existing databases (Finding 5)
ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS state VARCHAR(100);
ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS landmark TEXT;
ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS contact_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_addresses_customer ON customer_addresses(customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_addresses_default ON customer_addresses(customer_id) WHERE is_default = TRUE;

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

CREATE TABLE IF NOT EXISTS cart_idempotency_keys (
    customer_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    response_items JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_cart_idempotency_created ON cart_idempotency_keys(created_at);

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
    idempotency_key VARCHAR(128),
    request_payload_hash VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    pharmacist_id VARCHAR(64),
    license_no VARCHAR(100),
    rejection_reason TEXT,
    authorized_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_customer ON prescriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_status ON prescriptions(status);
CREATE UNIQUE INDEX IF NOT EXISTS unq_prescriptions_customer_idempotency ON prescriptions(customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS pharmacists (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    license_no VARCHAR(100) NOT NULL,
    license_number VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'SUSPENDED', 'REVOKED'
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pharmacists_user ON pharmacists(user_id);
CREATE INDEX IF NOT EXISTS idx_pharmacists_status ON pharmacists(status);

-- -----------------------------------------------------------------------------
-- 7. ORDERS & LINE ITEMS (TRANSACTIONAL SOURCE OF TRUTH)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    short_order_id VARCHAR(16) UNIQUE,
    customer_id VARCHAR(64) REFERENCES customers(id),
    store_id VARCHAR(64) REFERENCES stores(id),
    prescription_id VARCHAR(64) REFERENCES prescriptions(id),
    order_type VARCHAR(32) NOT NULL DEFAULT 'QUICK_COMMERCE_10MIN',
    status VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    seller_approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED', -- 'NOT_REQUIRED', 'PENDING', 'ACCEPTED', 'REJECTED'
    total_amount NUMERIC(10, 2) NOT NULL,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'COD', -- 'COD' (Release standard)
    payment_status VARCHAR(32) NOT NULL DEFAULT 'COD_PENDING', -- 'COD_PENDING', 'COD_COLLECTED', 'CANCELLED'
    is_cod BOOLEAN NOT NULL DEFAULT TRUE,
    cod_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    idempotency_key VARCHAR(128),
    request_hash VARCHAR(64),
    delivery_address JSONB NOT NULL,
    items JSONB NOT NULL,
    bill_summary JSONB,
    delivery_otp_hash VARCHAR(128) NOT NULL,
    otp_expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '45 minutes',
    otp_attempts INT NOT NULL DEFAULT 0,
    cumulative_otp_attempts INT NOT NULL DEFAULT 0,
    otp_resend_count INT NOT NULL DEFAULT 0,
    last_otp_resent_at TIMESTAMPTZ,
    otp_verified_at TIMESTAMPTZ,
    cancellation JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unq_orders_customer_idempotency UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_short_id ON orders(short_order_id);
CREATE SEQUENCE IF NOT EXISTS order_code_seq START WITH 1000000001;
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
    waypoints JSONB NOT NULL DEFAULT '[]'::jsonb,
    route_version INT NOT NULL DEFAULT 1,
    remaining_duration_mins NUMERIC(6, 2) DEFAULT 0.0,
    eta_updated_at TIMESTAMPTZ,
    rider_to_store_mins NUMERIC(6, 2) DEFAULT 0.0,
    store_to_customer_mins NUMERIC(6, 2) DEFAULT 0.0,
    traversed_waypoints JSONB NOT NULL DEFAULT '[]'::jsonb,
    remaining_waypoints JSONB NOT NULL DEFAULT '[]'::jsonb,
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
    rider_id VARCHAR(64),
    delivery_id VARCHAR(64),
    amount_expected NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_upi_utr ON payments(provider_ref) WHERE provider = 'UPI_INTENT' AND provider_ref IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_provider_ref ON payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(128) UNIQUE NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL DEFAULT 'STRIPE',
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED',
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_event_id ON payment_webhook_events(event_id);

CREATE TABLE IF NOT EXISTS payment_refunds (
    id VARCHAR(64) PRIMARY KEY,
    refund_id VARCHAR(64) UNIQUE NOT NULL,
    order_id VARCHAR(64) REFERENCES orders(order_id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    reason TEXT,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_refunds_order ON payment_refunds(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_refunds_idempotency ON payment_refunds(idempotency_key);

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
    waypoints JSONB NOT NULL DEFAULT '[]'::jsonb,
    pricing_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    fcm_delivery_status VARCHAR(32) DEFAULT 'PENDING',
    is_broadcast BOOLEAN NOT NULL DEFAULT FALSE,
    rejection_reason TEXT,
    accepted_at TIMESTAMPTZ,
    declined_at TIMESTAMPTZ,
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

CREATE TABLE IF NOT EXISTS customer_device_tokens (
    customer_id VARCHAR(64) NOT NULL,
    token TEXT NOT NULL,
    platform VARCHAR(32) NOT NULL DEFAULT 'IOS',
    app_version VARCHAR(32),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_id, token)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_device_tokens_token ON customer_device_tokens (token);

CREATE TABLE IF NOT EXISTS rider_telemetry (
    id BIGSERIAL PRIMARY KEY,
    rider_id VARCHAR(64) NOT NULL,
    delivery_id VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL DEFAULT 1,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    heading REAL,
    speed REAL,
    accuracy REAL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_mock_location BOOLEAN NOT NULL DEFAULT FALSE,
    attestation_verified BOOLEAN NOT NULL DEFAULT FALSE,
    attestation_payload JSONB DEFAULT NULL,
    CONSTRAINT uq_telemetry_delivery_seq UNIQUE(delivery_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_rider_time ON rider_telemetry(rider_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_delivery_time ON rider_telemetry(delivery_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_delivery_seq ON rider_telemetry(delivery_id, sequence_number DESC);

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

-- -----------------------------------------------------------------------------
-- 13. DISTRIBUTED REALTIME & REPLAY EVENT BUS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS realtime_events (
    seq BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE NOT NULL,
    channel VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_realtime_events_channel ON realtime_events(channel, seq);
CREATE INDEX IF NOT EXISTS idx_realtime_events_created_at ON realtime_events(created_at);

-- -----------------------------------------------------------------------------
-- 14. PROMOTIONS & MARKETING CAMPAIGNS
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS promotions (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL, -- 'PERCENTAGE', 'FLAT_DISCOUNT'
    discount_text VARCHAR(255),
    description TEXT,
    min_order_value NUMERIC(10, 2) NOT NULL DEFAULT 0,
    discount_percent NUMERIC(5, 2) DEFAULT 0,
    flat_discount NUMERIC(10, 2) DEFAULT 0,
    max_discount NUMERIC(10, 2) DEFAULT 0,
    badge VARCHAR(64),
    usage_count INTEGER DEFAULT 0,
    max_usage INTEGER DEFAULT 5000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'PAUSED', 'EXPIRED'
    valid_until TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '1 year'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_promotions_code ON promotions(code);
CREATE INDEX IF NOT EXISTS idx_promotions_status ON promotions(status);

CREATE TABLE IF NOT EXISTS promotion_redemptions (
    id VARCHAR(64) PRIMARY KEY,
    promotion_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_promotion_order UNIQUE (promotion_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_promo_redemptions_cust ON promotion_redemptions(promotion_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_promo_redemptions_order ON promotion_redemptions(order_id);

-- -----------------------------------------------------------------------------
-- 15. STORE & APP DELIVERY CONFIGURATION
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS delivery_config (
    id VARCHAR(64) PRIMARY KEY,
    free_delivery_threshold NUMERIC(10, 2) NOT NULL DEFAULT 199.00,
    standard_delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 2.00,
    express_delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 2.00,
    cold_chain_packaging_fee NUMERIC(10, 2) NOT NULL DEFAULT 15.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Migrations for Store Operating State & Per-SKU GST Classification
ALTER TABLE stores ADD COLUMN IF NOT EXISTS is_open BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS is_offline BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS surge_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS opening_time_utc VARCHAR(8) NOT NULL DEFAULT '00:00';
ALTER TABLE stores ADD COLUMN IF NOT EXISTS closing_time_utc VARCHAR(8) NOT NULL DEFAULT '23:59';
ALTER TABLE stores ADD COLUMN IF NOT EXISTS max_active_orders INT NOT NULL DEFAULT 50;

-- Categorized per-product tax classification (Finding 8)
ALTER TABLE products ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(16);
ALTER TABLE products ADD COLUMN IF NOT EXISTS gst_rate NUMERIC(4, 2);
UPDATE products SET hsn_code = '3004' WHERE hsn_code IS NULL AND category IN ('Medicines', 'Pharmacy', 'Rx', 'Allopathy', 'Ayurveda');
UPDATE products SET hsn_code = '9018' WHERE hsn_code IS NULL AND category IN ('Medical Devices', 'Diagnostics', 'Equipment');
UPDATE products SET hsn_code = '2106' WHERE hsn_code IS NULL AND category IN ('Supplements', 'Nutrition', 'Wellness');
UPDATE products SET hsn_code = '3004' WHERE hsn_code IS NULL;
UPDATE products SET gst_rate = 5.0 WHERE gst_rate IS NULL AND hsn_code = '3004';
UPDATE products SET gst_rate = 12.0 WHERE gst_rate IS NULL AND hsn_code = '9018';
UPDATE products SET gst_rate = 18.0 WHERE gst_rate IS NULL AND hsn_code = '2106';
UPDATE products SET gst_rate = 5.0 WHERE gst_rate IS NULL;
ALTER TABLE products ALTER COLUMN hsn_code DROP DEFAULT;
ALTER TABLE products ALTER COLUMN gst_rate DROP DEFAULT;

-- Delivery OTP Attempt Rate Limiting & Anti-Flooding
ALTER TABLE orders ADD COLUMN IF NOT EXISTS otp_resend_count INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_otp_resent_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cumulative_otp_attempts INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_provider_ref ON payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;



