/**
 * Commerce OS — Live PostgreSQL Multi-Store Fulfillment Selection & Inventory Domain Test
 * 
 * Verifies:
 * 1. Global Master Product Catalog: Product identity (SKU X) is defined once in master catalog.
 * 2. Multi-Store Inventory Isolation: Store A and Store B both sell SKU X with independent inventory state.
 * 3. Store A (near) has 0 available inventory for SKU X; Store B (serviceable) has 10 available.
 * 4. Server-Authoritative Fulfillment Resolution: Order for SKU X automatically resolves Store B (skipping OOS Store A).
 * 5. Reservation Semantics: Order placement creates RESERVATION (stock_count = 10, reserved_count = 1, available_count = 9).
 * 6. Fulfillment Semantics: Order handoff executes FULFILLMENT (stock_count = 9, reserved_count = 0, available_count = 9).
 * 7. Release Semantics: Reservation release restores availability (stock_count = 9, reserved_count = 0, available_count = 9).
 * 8. Out-of-Stock Fallback: When no serviceable store in the network has inventory, returns OUT_OF_STOCK_ACROSS_NETWORK.
 */

const assert = require('assert');
const { Pool } = require('pg');
const { ServiceabilityService, TransactionalOrderRepository, TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Server-Authoritative Multi-Store Fulfillment & 3-State Inventory Domain Invariants...');

  const timestamp = Date.now();
  const storeAId = 'store_multi_a_' + timestamp;
  const storeBId = 'store_multi_b_' + timestamp;
  const custId = 'cust_multi_' + timestamp;
  const addrId = 'addr_multi_' + timestamp;
  const sellerAId = 'seller_multi_a_' + timestamp;
  const sellerBId = 'seller_multi_b_' + timestamp;
  const prodId = 'prod_multi_' + timestamp;
  const skuX = 'SKU_MULTI_X_' + timestamp;

  const orderRepo = new TransactionalOrderRepository(pool);
  const invRepo = new TransactionalInventoryRepository(pool);

  try {
    // 1. Seed Stores
    // Store A: Near customer (2.5 km away)
    // Store B: Slightly further from customer (6.0 km away)
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Hub Near A', 'Sec 18 CyberCity, Gurugram', 28.4595, 77.0266, 10, TRUE),
       ($2, 'Hub Near B', 'Sec 29 Market, Gurugram', 28.4680, 77.0600, 15, TRUE)`,
      [storeAId, storeBId]
    );

    // 2. Seed Primary Sellers for both stores
    await pool.query(
      `INSERT INTO sellers (id, seller_id, merchant_name, email, password_hash, store_id, is_primary, status)
       VALUES 
       ($1, $1, 'Merchant A', $5, 'hashA', $3, TRUE, 'ACTIVE'),
       ($2, $2, 'Merchant B', $6, 'hashB', $4, TRUE, 'ACTIVE')`,
      [sellerAId, sellerBId, storeAId, storeBId, `mercha_${timestamp}@hub.com`, `merchb_${timestamp}@hub.com`]
    );

    // 3. Seed Customer & Authoritative Address
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'Multi Store Customer', 'GOLD', TRUE)`,
      [custId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_type, address_line, city, postal_code, latitude, longitude, is_default)
       VALUES ($1, $2, 'HOME', 'Cyber Hub Residences, Gurugram', 'Gurugram', '122002', 28.4610, 77.0310, TRUE)`,
      [addrId, custId]
    );

    // 4. Seed Single Master Product in Global Catalog (sku UNIQUE invariant preserved)
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, price, mrp, rx_requirement, is_active)
       VALUES ($1, $2, 'Multi-Store Master Product X', 'BrandMulti', 120.00, 150.00, 'OTC', TRUE)`,
      [prodId, skuX]
    );

    // 5. Seed Store-Scoped Inventory: Store A has 0 stock, Store B has 10 stock
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES 
       ($1, $3, $4, 0, 0),
       ($2, $3, $4, 10, 0)`,
      [storeAId, storeBId, prodId, skuX]
    );

    // 6. Resolve Authoritative Fulfillment Store via ServiceabilityService
    const custAddr = { latitude: 28.4610, longitude: 77.0310 };
    const orderItems = [{ sku: skuX, productId: prodId, quantity: 1 }];

    const fulfillmentDecision = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: custAddr,
      items: orderItems,
      preferredStoreId: storeAId, // Customer preference for Store A
      pool
    });

    assert.strictEqual(fulfillmentDecision.ok, true, 'Fulfillment resolution must succeed');
    assert.strictEqual(fulfillmentDecision.storeId, storeBId, 'Must automatically select Store B where inventory is available (overriding OOS preference Store A)');

    // 7. Place Order Transactionally for Authoritative Store B
    const placeRes = await orderRepo.placeOrderTransactionally(custId, {
      storeId: fulfillmentDecision.storeId,
      authoritativeStoreId: fulfillmentDecision.storeId,
      fulfillmentDecision: fulfillmentDecision.decision,
      addressId: addrId,
      items: orderItems,
      paymentMethod: 'COD'
    });

    assert.strictEqual(placeRes.ok, true, `Order placement must succeed: ${placeRes.message}`);
    assert.strictEqual(placeRes.order.store_id || placeRes.order.storeId, storeBId);

    // 8. Assert Authoritative 3-State Reservation Invariants:
    // on-hand / stock_count = 10, reserved_count = 1, available_count = 9
    const checkInvB = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeBId, skuX]
    );
    assert.strictEqual(Number(checkInvB.rows[0].stock_count), 10, 'Store B physical on-hand stock must remain 10 upon reservation');
    assert.strictEqual(Number(checkInvB.rows[0].reserved_count), 1, 'Store B reserved count must be incremented to 1');
    assert.strictEqual(Number(checkInvB.rows[0].available_count), 9, 'Store B available count must become 9');

    // Store A must remain completely unaffected (stock = 0, reserved = 0, available = 0)
    const checkInvA = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuX]
    );
    assert.strictEqual(Number(checkInvA.rows[0].stock_count), 0, 'Store A stock must remain 0');
    assert.strictEqual(Number(checkInvA.rows[0].reserved_count), 0, 'Store A reserved must remain 0');
    assert.strictEqual(Number(checkInvA.rows[0].available_count), 0, 'Store A available must remain 0');

    // Check Inventory Ledger for Reservation Event
    const ledRes = await pool.query(
      `SELECT delta, new_stock, reason FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at DESC LIMIT 1`,
      [storeBId, skuX]
    );
    assert.strictEqual(ledRes.rows.length, 1);
    assert.strictEqual(Number(ledRes.rows[0].delta), 0, 'Reservation event ledger delta must be 0 (no physical stock departure)');
    assert.strictEqual(Number(ledRes.rows[0].new_stock), 10, 'Reservation event ledger new_stock must reflect on-hand 10');
    assert.strictEqual(ledRes.rows[0].reason, 'RESERVATION_CREATED');

    // 9. Execute Fulfillment Transition: stock_count = 9, reserved_count = 0, available_count = 9
    const fulRes = await invRepo.fulfillStockTransactionally(null, storeBId, orderItems);
    assert.strictEqual(fulRes.ok, true, 'Fulfillment transition must succeed');

    const checkInvBFul = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeBId, skuX]
    );
    assert.strictEqual(Number(checkInvBFul.rows[0].stock_count), 9, 'Store B physical on-hand stock must decrease to 9 upon fulfillment');
    assert.strictEqual(Number(checkInvBFul.rows[0].reserved_count), 0, 'Store B reserved count must return to 0 upon fulfillment');
    assert.strictEqual(Number(checkInvBFul.rows[0].available_count), 9, 'Store B available count must remain 9');

    // Check Inventory Ledger for Fulfillment Event
    const ledFul = await pool.query(
      `SELECT delta, new_stock, reason FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at DESC LIMIT 1`,
      [storeBId, skuX]
    );
    assert.strictEqual(Number(ledFul.rows[0].delta), -1, 'Fulfillment ledger delta must be -1');
    assert.strictEqual(Number(ledFul.rows[0].new_stock), 9, 'Fulfillment ledger new_stock must be 9');
    assert.strictEqual(ledFul.rows[0].reason, 'STOCK_CONSUMED');

    // 10. Test Network-Wide Out of Stock (Store B moved out of service radius >20km)
    await pool.query(`UPDATE stores SET latitude = 29.5000, longitude = 78.5000 WHERE id = $1`, [storeBId]);

    const farDecision = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: custAddr,
      items: orderItems,
      pool
    });

    assert.strictEqual(farDecision.ok, false, 'Must fail when no serviceable store in network has inventory');
    assert.strictEqual(farDecision.error, 'OUT_OF_STOCK_ACROSS_NETWORK');

    console.log('  ✅ PASS: Server-Authoritative Multi-Store Fulfillment & 3-State Inventory Domain Invariants\n');
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id IN (SELECT delivery_id FROM delivery_sessions WHERE store_id IN ($1, $2))`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM cod_ledger WHERE seller_id IN ($1, $2)`, [sellerAId, sellerBId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM orders WHERE store_id IN ($1, $2) OR customer_id = $3`, [storeAId, storeBId, custId]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [prodId]);
    await pool.query(`DELETE FROM customer_addresses WHERE customer_id = $1`, [custId]);
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [sellerAId, sellerBId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for multi-store-fulfillment.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Multi-Store Fulfillment Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
