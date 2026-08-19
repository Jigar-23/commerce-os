/**
 * Commerce OS — Live PostgreSQL Server-Authoritative Store Selection & Preference Policy Test
 * 
 * Verifies:
 * 1. Client Preference is NEVER Final Authority: `client preference != server authority`.
 * 2. Scenario 1: Client requests Store A (preferred), but Store A is out of stock -> Server selects Store B.
 * 3. Scenario 2: Client requests Store Far (>20km away) -> Server rejects Store Far and selects nearest serviceable Store B.
 * 4. Scenario 3: All network fulfillment nodes are out of stock -> Server returns OUT_OF_STOCK_ACROSS_NETWORK.
 */

const assert = require('assert');
const { Pool } = require('pg');
const { ServiceabilityService } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Server-Authoritative Fulfillment Store Selection vs Client Preference...');

  const timestamp = Date.now();
  const storeAId = 'store_pref_a_' + timestamp;
  const storeBId = 'store_pref_b_' + timestamp;
  const storeFarId = 'store_pref_far_' + timestamp;
  const prodId = 'prod_pref_' + timestamp;
  const skuX = 'SKU_PREF_X_' + timestamp;

  try {
    // 1. Seed Stores
    // Store A: Near customer (2.5 km away), but will have 0 inventory
    // Store B: 5.0 km away, has 10 inventory
    // Store Far: 35.0 km away (out of service zone), has 10 inventory
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Hub Near A', 'Sector 18 CyberCity', 28.4595, 77.0266, 10, TRUE),
       ($2, 'Hub Near B', 'Sector 29 Market', 28.4680, 77.0600, 15, TRUE),
       ($3, 'Hub Far Away', 'Far Outpost', 28.9000, 77.9000, 45, TRUE)`,
      [storeAId, storeBId, storeFarId]
    );

    // 2. Seed Single Master Product in Global Catalog
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, price, mrp, rx_requirement, is_active)
       VALUES ($1, $2, 'Preference Test Product', 'BrandX', 150.00, 180.00, 'OTC', TRUE)`,
      [prodId, skuX]
    );

    // 3. Seed Inventory: Store A has 0 stock, Store B has 10 stock, Store Far has 10 stock
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES 
       ($1, $4, $5, 0, 0),
       ($2, $4, $5, 10, 0),
       ($3, $4, $5, 10, 0)`,
      [storeAId, storeBId, storeFarId, prodId, skuX]
    );

    const custAddr = { latitude: 28.4610, longitude: 77.0310 };
    const orderItems = [{ sku: skuX, quantity: 1 }];

    // Scenario 1: Customer requests Store A (which is OOS) -> Server selects Store B
    const decision1 = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: custAddr,
      items: orderItems,
      preferredStoreId: storeAId, // Client preferred Store A
      pool
    });
    assert.strictEqual(decision1.ok, true);
    assert.strictEqual(decision1.storeId, storeBId, 'Server authority must select Store B, overriding client preference for OOS Store A');

    // Scenario 2: Customer requests Store Far (which is >20km away) -> Server selects Store B
    const decision2 = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: custAddr,
      items: orderItems,
      preferredStoreId: storeFarId, // Client preferred Store Far
      pool
    });
    assert.strictEqual(decision2.ok, true);
    assert.strictEqual(decision2.storeId, storeBId, 'Server authority must select Store B, overriding client preference for unserviceable Store Far');

    // Scenario 3: Store B stock depleted to 0 -> Network-wide out of stock
    await pool.query(`UPDATE inventory SET stock_count = 0 WHERE store_id = $1 AND sku = $2`, [storeBId, skuX]);
    const decision3 = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: custAddr,
      items: orderItems,
      preferredStoreId: storeBId,
      pool
    });
    assert.strictEqual(decision3.ok, false);
    assert.strictEqual(decision3.error, 'OUT_OF_STOCK_ACROSS_NETWORK', 'Must return OUT_OF_STOCK_ACROSS_NETWORK when no serviceable store has stock');

    console.log('  ✅ PASS: Server-Authoritative Fulfillment Store Selection vs Client Preference\n');
  } finally {
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2, $3)`, [storeAId, storeBId, storeFarId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2, $3)`, [storeAId, storeBId, storeFarId]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [prodId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2, $3)`, [storeAId, storeBId, storeFarId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for server-fulfillment-selection.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Server Fulfillment Selection Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
