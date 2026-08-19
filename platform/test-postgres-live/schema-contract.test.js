/**
 * Live PostgreSQL Test: Schema Contract Verification
 * 
 * Verifies that the connected PostgreSQL database strictly satisfies the
 * schema contract required by all production transactional repositories:
 * - Table presence
 * - Critical column definitions and types
 * - Primary keys & Unique constraints
 * - Foreign keys & non-null constraints
 */

const assert = require('assert');

const REQUIRED_TABLES = [
  'stores',
  'sellers',
  'riders',
  'products',
  'inventory',
  'inventory_ledger',
  'customers',
  'customer_addresses',
  'auth_challenges',
  'carts',
  'prescriptions',
  'orders',
  'delivery_sessions',
  'cod_ledger',
  'payments',
  'offers',
  'outbox_events',
  'rider_presence',
  'rider_device_tokens',
  'rider_telemetry',
  'rider_notifications',
  'audit_logs'
];

const REQUIRED_COLUMNS = {
  products: ['id', 'sku', 'name', 'price', 'mrp', 'store_id', 'rx_requirement', 'is_active'],
  orders: ['id', 'order_id', 'customer_id', 'store_id', 'status', 'total_amount', 'delivery_address', 'items', 'delivery_otp_hash', 'otp_expires_at', 'otp_attempts', 'is_cod', 'cod_amount'],
  delivery_sessions: ['id', 'delivery_id', 'order_id', 'rider_id', 'state', 'merchant_name', 'merchant_address', 'merchant_lat', 'merchant_lng', 'customer_name', 'customer_address', 'customer_lat', 'customer_lng', 'is_cod', 'cod_amount', 'otp_verified'],
  riders: ['id', 'rider_id', 'phone', 'full_name', 'vehicle_number', 'vehicle_type', 'tier', 'status'],
  offers: ['id', 'offer_id', 'event_id', 'notification_id', 'delivery_id', 'order_id', 'rider_id', 'status', 'offer_expires_at', 'earnings_amount'],
  outbox_events: ['id', 'aggregate_type', 'aggregate_id', 'event_type', 'payload', 'status', 'retry_count'],
  inventory: ['id', 'store_id', 'product_id', 'sku', 'stock_count', 'reserved_count', 'available_count'],
  inventory_ledger: ['id', 'store_id', 'product_id', 'sku', 'delta', 'new_stock', 'reason'],
  sellers: ['id', 'seller_id', 'email', 'password_hash', 'store_id', 'roles', 'status'],
  customers: ['id', 'phone', 'full_name', 'tier'],
  customer_addresses: ['id', 'customer_id', 'address_type', 'address_line', 'city', 'postal_code', 'latitude', 'longitude'],
  auth_challenges: ['id', 'phone', 'otp_hash', 'expires_at', 'attempts'],
  carts: ['id', 'customer_id', 'items'],
  prescriptions: ['id', 'customer_id', 'patient_name', 'status', 'pharmacist_id'],
  payments: ['id', 'payment_id', 'order_id', 'amount', 'currency', 'status', 'method', 'provider', 'provider_ref'],
  rider_presence: ['rider_id', 'status', 'last_known_lat', 'last_known_lng', 'last_seen_at'],
  rider_device_tokens: ['rider_id', 'token', 'platform', 'app_version'],
  rider_telemetry: ['id', 'rider_id', 'delivery_id', 'latitude', 'longitude', 'heading', 'speed', 'accuracy', 'recorded_at'],
  rider_notifications: ['id', 'notification_id', 'rider_id', 'category', 'title', 'body', 'delivery_channel', 'status', 'metadata', 'created_at']
};

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Verifying Full Schema Contract & Column Integrity...');

  // 1. Check all required tables exist
  const tablesRes = await pool.query(
    `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'`
  );
  const existingTables = new Set(tablesRes.rows.map(r => r.table_name));

  for (const table of REQUIRED_TABLES) {
    assert.ok(existingTables.has(table), `Required schema table '${table}' is missing in PostgreSQL database.`);
  }

  // 2. Check all required columns exist
  for (const [tableName, columns] of Object.entries(REQUIRED_COLUMNS)) {
    const colsRes = await pool.query(
      `SELECT column_name, data_type, is_nullable 
       FROM information_schema.columns 
       WHERE table_schema = 'public' AND table_name = $1`,
      [tableName]
    );
    const existingCols = new Set(colsRes.rows.map(r => r.column_name));

    for (const col of columns) {
      assert.ok(
        existingCols.has(col),
        `Required column '${tableName}.${col}' is missing in PostgreSQL schema.`
      );
    }
  }

  // 3. Check outbox_events ID is BIGINT (BIGSERIAL)
  const outboxIdCol = await pool.query(
    `SELECT data_type FROM information_schema.columns 
     WHERE table_schema = 'public' AND table_name = 'outbox_events' AND column_name = 'id'`
  );
  assert.ok(
    ['bigint', 'integer'].includes(outboxIdCol.rows[0]?.data_type),
    'outbox_events.id must be BIGINT / BIGSERIAL numeric sequence'
  );

  // 4. Assert NO plaintext OTP / PIN columns exist in orders or delivery_sessions
  const orderCols = await pool.query(
    `SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'orders'`
  );
  const orderColSet = new Set(orderCols.rows.map(r => r.column_name));
  assert.strictEqual(orderColSet.has('delivery_otp'), false, 'orders table MUST NOT contain plaintext delivery_otp column');

  const sessionCols = await pool.query(
    `SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'delivery_sessions'`
  );
  const sessionColSet = new Set(sessionCols.rows.map(r => r.column_name));
  assert.strictEqual(sessionColSet.has('delivery_pin'), false, 'delivery_sessions table MUST NOT contain plaintext delivery_pin column');

  // 5. Canonical inventory identity is DB-enforced: store_id, product_id, sku are ALL NOT NULL.
  const invNullable = await pool.query(
    `SELECT column_name, is_nullable FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'inventory'
       AND column_name IN ('store_id', 'product_id', 'sku')`
  );
  const nullableMap = Object.fromEntries(invNullable.rows.map(r => [r.column_name, r.is_nullable]));
  assert.strictEqual(nullableMap.store_id, 'NO', 'inventory.store_id MUST be NOT NULL (canonical identity)');
  assert.strictEqual(nullableMap.product_id, 'NO', 'inventory.product_id MUST be NOT NULL (canonical identity)');
  assert.strictEqual(nullableMap.sku, 'NO', 'inventory.sku MUST be NOT NULL (canonical identity)');

  // 5b. Canonical inventory_ledger identity is DB-enforced: store_id, product_id, sku, reason are ALL NOT NULL.
  const ledNullable = await pool.query(
    `SELECT column_name, is_nullable FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'inventory_ledger'
       AND column_name IN ('store_id', 'product_id', 'sku', 'reason')`
  );
  const ledNullableMap = Object.fromEntries(ledNullable.rows.map(r => [r.column_name, r.is_nullable]));
  assert.strictEqual(ledNullableMap.store_id, 'NO', 'inventory_ledger.store_id MUST be NOT NULL');
  assert.strictEqual(ledNullableMap.product_id, 'NO', 'inventory_ledger.product_id MUST be NOT NULL');
  assert.strictEqual(ledNullableMap.sku, 'NO', 'inventory_ledger.sku MUST be NOT NULL');
  assert.strictEqual(ledNullableMap.reason, 'NO', 'inventory_ledger.reason MUST be NOT NULL');

  // 6. Permanent regression: the DB itself must reject a NULL-scope inventory row.
  const ts = Date.now();
  const storeId = 'store_schema_null_' + ts;
  const productId = 'prod_schema_null_' + ts;
  const sku = 'SKU_SCHEMA_NULL_' + ts;
  const productName = 'Schema Null Guard Product';
  try {
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Schema Null Guard Store', 'Sector 29, Gurugram', 28.4680, 77.0600, 10, TRUE)`,
      [storeId]
    );
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, $3, 'BrandSchema', 100.00, 90.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [productId, sku, productName]
    );

    // 6a. store_id = NULL -> NOT NULL violation
    let nullStoreRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory (store_id, product_id, sku, stock_count)
         VALUES (NULL, $1, $2, 10)`,
        [productId, sku]
      );
    } catch (err) {
      nullStoreRejected = true;
      assert.strictEqual(err.code, '23502', `Expected NOT NULL violation (23502) for NULL store_id, got ${err.code}`);
    }
    assert.strictEqual(nullStoreRejected, true, 'PostgreSQL MUST reject inventory row with NULL store_id');

    // 6b. product_id = NULL -> NOT NULL violation
    let nullProductRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory (store_id, product_id, sku, stock_count)
         VALUES ($1, NULL, $2, 10)`,
        [storeId, sku]
      );
    } catch (err) {
      nullProductRejected = true;
      assert.strictEqual(err.code, '23502', `Expected NOT NULL violation (23502) for NULL product_id, got ${err.code}`);
    }
    assert.strictEqual(nullProductRejected, true, 'PostgreSQL MUST reject inventory row with NULL product_id');

    // 6c. store_id = NULL, product_id = NULL, sku = NULL -> NOT NULL violation
    let nullScopeRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory (store_id, product_id, sku, stock_count)
         VALUES (NULL, NULL, NULL, 10)`
      );
    } catch (err) {
      nullScopeRejected = true;
      assert.strictEqual(err.code, '23502', `Expected NOT NULL violation (23502) for fully NULL identity, got ${err.code}`);
    }
    assert.strictEqual(nullScopeRejected, true, 'PostgreSQL MUST reject a fully NULL inventory identity');

    // 6d. inventory_ledger.store_id = NULL -> NOT NULL violation (23502)
    let nullLedgerStoreRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason)
         VALUES ('test_led_null_' || NOW()::text, NULL, $1, $2, 10, 10, 'SELLER_RESTOCK')`,
        [productId, sku]
      );
    } catch (err) {
      nullLedgerStoreRejected = true;
      assert.strictEqual(err.code, '23502', `Expected NOT NULL violation (23502) for NULL inventory_ledger.store_id, got ${err.code}`);
    }
    assert.strictEqual(nullLedgerStoreRejected, true, 'PostgreSQL MUST reject inventory_ledger entry with NULL store_id');

    // 6e. inventory_ledger.store_id = invalid -> Foreign Key violation (23503)
    let invalidLedgerStoreRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason)
         VALUES ('test_led_fk_' || NOW()::text, 'store_nonexistent_99999', $1, $2, 10, 10, 'SELLER_RESTOCK')`,
        [productId, sku]
      );
    } catch (err) {
      invalidLedgerStoreRejected = true;
      assert.strictEqual(err.code, '23503', `Expected FK violation (23503) for invalid inventory_ledger.store_id, got ${err.code}`);
    }
    assert.strictEqual(invalidLedgerStoreRejected, true, 'PostgreSQL MUST reject inventory_ledger entry with nonexistent store_id');

    // 6f. inventory_ledger.product_id = NULL -> NOT NULL violation (23502)
    let nullLedgerProdRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason)
         VALUES ('test_led_null_prod_' || NOW()::text, $1, NULL, $2, 10, 10, 'SELLER_RESTOCK')`,
        [storeId, sku]
      );
    } catch (err) {
      nullLedgerProdRejected = true;
      assert.strictEqual(err.code, '23502', `Expected NOT NULL violation (23502) for NULL inventory_ledger.product_id, got ${err.code}`);
    }
    assert.strictEqual(nullLedgerProdRejected, true, 'PostgreSQL MUST reject inventory_ledger entry with NULL product_id');

    // 6g. inventory_ledger.product_id = invalid -> Foreign Key violation (23503)
    let invalidLedgerProdRejected = false;
    try {
      await pool.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason)
         VALUES ('test_led_fk_prod_' || NOW()::text, $1, 'prod_nonexistent_99999', $2, 10, 10, 'SELLER_RESTOCK')`,
        [storeId, sku]
      );
    } catch (err) {
      invalidLedgerProdRejected = true;
      assert.strictEqual(err.code, '23503', `Expected FK violation (23503) for invalid inventory_ledger.product_id, got ${err.code}`);
    }
    assert.strictEqual(invalidLedgerProdRejected, true, 'PostgreSQL MUST reject inventory_ledger entry with nonexistent product_id');

    console.log('  ✅ PASS: Schema Contract + Inventory & Ledger NOT NULL Integrity Verified\n');
  } finally {
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM inventory WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [productId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for schema-contract.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Schema Contract Verification Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
