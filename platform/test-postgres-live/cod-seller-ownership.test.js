/**
 * Commerce OS — Live PostgreSQL COD Deterministic Seller Ownership Test
 * 
 * Verifies:
 * 1. Store with authoritative primary seller (is_primary = TRUE) records seller_id deterministically in cod_ledger.
 * 2. Store without primary active seller fails transactionally with AMBIGUOUS_STORE_MERCHANT_AUTHORITY (zero arbitrary LIMIT 1).
 */

const assert = require('assert');
const { Pool } = require('pg');
const { TransactionalOrderRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Deterministic COD Seller Ownership & Financial Invariants...');

  const timestamp = Date.now();
  const storeAId = 'store_cod_a_' + timestamp;
  const storeBId = 'store_cod_b_' + timestamp;
  const custId = 'cust_cod_' + timestamp;
  const addrId = 'addr_cod_' + timestamp;
  const sellerA1Id = 'seller_cod_a1_' + timestamp;
  const sellerA2Id = 'seller_cod_a2_' + timestamp;
  const skuA = 'SKU_COD_A_' + timestamp;
  const prodId = 'prod_cod_' + timestamp;

  const orderRepo = new TransactionalOrderRepository(pool);

  try {
    // 1. Seed Stores
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'COD Store A', 'Sec 18 CyberCity', 28.4595, 77.0266, 10, TRUE),
       ($2, 'COD Store B Ambiguous', 'Sec 29 Market', 28.4680, 77.0600, 15, TRUE)`,
      [storeAId, storeBId]
    );

    // 2. Store A has authoritative primary seller A1 and secondary seller A2 (is_primary = FALSE)
    await pool.query(
      `INSERT INTO sellers (id, seller_id, merchant_name, email, password_hash, store_id, is_primary, status)
       VALUES 
       ($1, $1, 'Merchant Primary A1', $4, 'hashA1', $3, TRUE, 'ACTIVE'),
       ($2, $2, 'Merchant Secondary A2', $5, 'hashA2', $3, FALSE, 'ACTIVE')`,
      [sellerA1Id, sellerA2Id, storeAId, `mercha1_${timestamp}@hub.com`, `mercha2_${timestamp}@hub.com`]
    );

    // 3. Seed Customer & Address
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'COD Test Customer', 'STANDARD', TRUE)`,
      [custId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_type, address_line, city, postal_code, latitude, longitude, is_default)
       VALUES ($1, $2, 'HOME', 'Sector 18 CyberCity', 'Gurugram', '122002', 28.4610, 77.0310, TRUE)`,
      [addrId, custId]
    );

    // 4. Seed a single GLOBAL product with unique SKU, plus store-scoped inventory for Store A and Store B
    //    (finalized catalog model: products is global identity; store availability lives in inventory)
    const prodId = 'prod_cod_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, price, mrp, rx_requirement, is_active)
       VALUES ($1, $2, 'COD Product A', 'BrandCOD', 100.00, 120.00, 'OTC', TRUE)`,
      [prodId, skuA]
    );

    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES 
       ($1, $3, $4, 10, 0),
       ($2, $3, $4, 10, 0)`,
      [storeAId, storeBId, prodId, skuA]
    );

    const { FulfillmentDecision } = require('../repositories');

    const decisionA = new FulfillmentDecision({
      storeId: storeAId,
      storeName: 'Store A Hub',
      storeAddress: 'Sector 29, Gurugram',
      storeLatitude: 28.4680,
      storeLongitude: 77.0620,
      distanceKm: 0.5,
      slaMinutes: 10,
      decisionSource: 'SERVICEABILITY_ENGINE',
      deterministicRank: 1,
      resolvedItems: [{ sku: skuA, quantity: 1 }]
    });

    // Case 1: Store A order with primary seller -> cod_ledger.seller_id must be sellerA1Id
    const resA = await orderRepo.placeOrderTransactionally(custId, {
      storeId: storeAId,
      addressId: addrId,
      fulfillmentDecision: decisionA,
      items: [{ sku: skuA, quantity: 1 }],
      paymentMethod: 'COD'
    });

    assert.strictEqual(resA.ok, true, `Store A order must succeed: ${resA.message}`);
    const orderAId = resA.order.id;

    const codCheckA = await pool.query(`SELECT seller_id, amount_expected FROM cod_ledger WHERE order_id = $1`, [orderAId]);
    assert.strictEqual(codCheckA.rows.length, 1);
    assert.strictEqual(codCheckA.rows[0].seller_id, sellerA1Id, 'Must deterministically assign primary seller to COD ledger');

    const decisionB = new FulfillmentDecision({
      storeId: storeBId,
      storeName: 'Store B Hub',
      storeAddress: 'Sector 56, Gurugram',
      storeLatitude: 28.4200,
      storeLongitude: 77.0800,
      distanceKm: 1.0,
      slaMinutes: 10,
      decisionSource: 'SERVICEABILITY_ENGINE',
      deterministicRank: 1,
      resolvedItems: [{ sku: skuA, quantity: 1 }]
    });

    // Case 2: Store B has NO primary seller -> order placement must fail with STORE_MERCHANT_AUTHORITY_MISSING
    const resB = await orderRepo.placeOrderTransactionally(custId, {
      storeId: storeBId,
      addressId: addrId,
      fulfillmentDecision: decisionB,
      items: [{ sku: skuA, quantity: 1 }],
      paymentMethod: 'COD'
    });

    assert.strictEqual(resB.ok, false, 'Must fail when store has no primary active seller');
    assert.strictEqual(resB.error, 'STORE_MERCHANT_AUTHORITY_MISSING');

    // Verify zero orphan records created for Store B
    const checkStoreBOrders = await pool.query(`SELECT COUNT(*) as count FROM orders WHERE store_id = $1`, [storeBId]);
    assert.strictEqual(Number(checkStoreBOrders.rows[0].count), 0, 'No orphan orders created on failure');

    console.log('  ✅ PASS: Deterministic COD Seller Ownership & Financial Invariants\n');
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id IN (SELECT delivery_id FROM delivery_sessions WHERE store_id IN ($1, $2))`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM cod_ledger WHERE seller_id IN ($1, $2)`, [sellerA1Id, sellerA2Id]);
    await pool.query(`DELETE FROM delivery_sessions WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM orders WHERE store_id IN ($1, $2) OR customer_id = $3`, [storeAId, storeBId, custId]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [prodId]);
    await pool.query(`DELETE FROM customer_addresses WHERE customer_id = $1`, [custId]);
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [sellerA1Id, sellerA2Id]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for cod-seller-ownership.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: COD Seller Ownership Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
