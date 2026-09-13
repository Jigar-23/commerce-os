CREATE TABLE IF NOT EXISTS medicine_products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255) NOT NULL,
    dosage_form VARCHAR(64) NOT NULL DEFAULT 'TABLET',
    pack_size VARCHAR(128) NOT NULL,
    rx_requirement VARCHAR(64) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    discounted_price DECIMAL(12,2) NOT NULL,
    discount_percentage INT NOT NULL DEFAULT 20,
    in_stock BOOLEAN NOT NULL DEFAULT TRUE,
    stock_count INT NOT NULL DEFAULT 500,
    cold_chain_required BOOLEAN NOT NULL DEFAULT FALSE,
    therapeuticCategory VARCHAR(128) NOT NULL DEFAULT 'Antibiotic',
    express_delivery_sla_mins INT NOT NULL DEFAULT 10,
    salt_compositions_json TEXT,
    uses_json TEXT,
    side_effects_json TEXT,
    warnings_json TEXT,
    substitutes_count INT NOT NULL DEFAULT 3,
    rating DECIMAL(3,2) NOT NULL DEFAULT 4.8,
    review_count INT NOT NULL DEFAULT 120,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO medicine_products (id, sku, name, brand_name, manufacturer, dosage_form, pack_size, rx_requirement, price, discounted_price, discount_percentage, in_stock, stock_count, cold_chain_required, therapeuticCategory, express_delivery_sla_mins, substitutes_count, rating, review_count)
VALUES
('8f921ab0-0012-4412-9901-112233445501', 'SKU-AUG-625', 'Augmentin 625 Duo Tablet', 'Augmentin', 'GlaxoSmithKline', 'TABLET', 'Strip of 10 Tablets', 'PRESCRIPTION_REQUIRED', 18.50, 14.80, 20, TRUE, 250, FALSE, 'Antibiotics', 10, 5, 4.9, 320),
('8f921ab0-0012-4412-9901-112233445502', 'SKU-CROC-500', 'Crocin Pain Relief 500mg', 'Crocin', 'GSK Pharma', 'TABLET', 'Strip of 15 Tablets', 'OTC', 4.50, 3.60, 20, TRUE, 1000, FALSE, 'Analgesics', 10, 8, 4.8, 850),
('8f921ab0-0012-4412-9901-112233445503', 'SKU-LANT-100', 'Lantus SoloStar 100IU/ml Pen', 'Lantus', 'Sanofi', 'INJECTION', '1 Pen of 3ml (Cold-Chain 2-8°C)', 'PRESCRIPTION_REQUIRED', 32.00, 28.80, 10, TRUE, 120, TRUE, 'Diabetes Care', 15, 2, 4.95, 140),
('8f921ab0-0012-4412-9901-112233445504', 'SKU-DOLO-650', 'Dolo 650mg Paracetamol Tablet', 'Dolo', 'Micro Labs Ltd', 'TABLET', 'Strip of 15 Tablets', 'OTC', 3.80, 3.00, 21, TRUE, 850, FALSE, 'Antipyretics', 10, 6, 4.9, 950)
ON CONFLICT (id) DO NOTHING;
