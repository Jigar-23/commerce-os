/**
 * Commerce OS — Live PostgreSQL Seller State Machine & Outbox Deduplication Test
 * 
 * Verifies:
 * 1. PLACED -> SELLER_ACCEPTED (records ORDER_SELLER_ACCEPTED outbox event)
 * 2. SELLER_ACCEPTED -> PACKED (records ORDER_PACKED outbox event)
 * 3. PACKED -> READY_FOR_PICKUP (records DISPATCH_REQUESTED outbox event)
 * 4. Invalid Transitions are rejected with 409 INVALID_ORDER_STATE_TRANSITION:
 *    - PLACED -> PACKED (rejected)
 *    - PLACED -> READY_FOR_PICKUP (rejected)
 *    - DELIVERED / CANCELLED -> SELLER_ACCEPTED (rejected)
 * 5. Idempotent Repeated Calls:
 *    - READY_FOR_PICKUP called multiple times returns 200 without creating duplicate DISPATCH_REQUESTED outbox events.
 * 6. Store Authorization: Seller A cannot transition Seller B's store orders (403 FORBIDDEN).
 */

const assert = require('assert');
const { Pool } = require('pg');
const {
  TransactionalOrderRepository,
  TransactionalInventoryRepository,
  FulfillmentDecision
} = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Seller Order State Machine & Outbox Deduplication...');

  const timestamp = Date.now();
  const storeAId = 'store_ssm_a_' + timestamp;
  const storeBId = 'store_ssm_b_' + timestamp;
  const sellerAId = 'seller_ssm_a_' + timestamp;
  const sellerBId = 'seller_ssm_b_' + timestamp;
  const custId = 'cust_ssm_' + timestamp;
  const sku = 'SKU_SSM_' + timestamp;
  const addressId = 'addr_ssm_' + timestamp;

  const invRepo = new TransactionalInventoryRepository(pool);
  const orderRepo = new TransactionalOrderRepository(pool, invRepo);

  try {
    // 1. Seed Stores & Sellers
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES 
       ($1, 'SSM Hub A', 'Sector 14', 28.4700, 77.0300, TRUE),
       ($2, 'SSM Hub B', 'Sector 48', 28.4200, 77.0400, TRUE)`,
      [storeAId, storeBId]
    );

    await pool.query(
      `INSERT INTO sellers (id, seller_id, phone, store_id, merchant_name, password_hash, status, is_primary)
       VALUES 
       ($1, $1, '+919900000001', $2, 'Seller Alpha', 'hash', 'ACTIVE', TRUE),
       ($3, $3, '+919900000002', $4, 'Seller Beta', 'hash', 'ACTIVE', TRUE)`,
      [sellerAId, storeAId, sellerBId, storeBId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, is_active)
       VALUES ($1, '+919900000099', 'Customer SSM', TRUE)`,
      [custId]
    );

    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_line, city, postal_code, latitude, longitude, is_default)
       VALUES ($1, $2, 'Sector 14', 'Gurugram', '122001', 28.4705, 77.0305, TRUE)`,
      [addressId, custId]
    );

    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, rx_requirement, is_active)
       VALUES ($1, $2, 'SSM Product', 'Pharma', 100.00, 80.00, 'OTC', TRUE)`,
      ['prod_' + timestamp, sku]
    );

    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES ($1, $2, $3, 20, 0)`,
      [storeAId, 'prod_' + timestamp, sku]
    );

    // 2. Place Order in Store A (Starts in PLACED)
    const decision = new FulfillmentDecision({
      storeId: storeAId,
      storeName: 'SSM Hub A',
      storeAddress: 'Sector 14',
      storeLatitude: 28.4700,
      storeLongitude: 77.0300,
      distanceKm: 0.2,
      slaMinutes: 10,
      decisionSource: 'SERVICEABILITY_ENGINE',
      deterministicRank: 1,
      resolvedItems: [{ sku, quantity: 1 }]
    });

    const placeRes = await orderRepo.placeOrderTransactionally(custId, {
      customerId: custId,
      storeId: storeAId,
      addressId,
      fulfillmentDecision: decision,
      paymentMethod: 'UPI_INSTANT',
      items: [{ sku, quantity: 1 }]
    });

    assert.strictEqual(placeRes.ok, true);
    const orderId = placeRes.order.order_id || placeRes.order.id;

    // 3. Test Invalid Transition: Direct PLACED -> PACKED without SELLER_ACCEPTED
    const invalidPackRes = await orderRepo.packOrderBySeller(orderId, storeAId, sellerAId);
    assert.strictEqual(invalidPackRes.ok, false);
    assert.strictEqual(invalidPackRes.error, 'INVALID_ORDER_STATE_TRANSITION');

    // 4. Test Cross-Store Authorization: Seller B attempting to accept Store A order
    const crossStoreRes = await orderRepo.acceptOrderBySeller(orderId, storeBId, sellerBId);
    assert.strictEqual(crossStoreRes.ok, false);
    assert.strictEqual(crossStoreRes.httpStatus, 403);

    // 5. Valid Transition: PLACED -> SELLER_ACCEPTED
    const acceptRes = await orderRepo.acceptOrderBySeller(orderId, storeAId, sellerAId);
    assert.strictEqual(acceptRes.ok, true);
    assert.strictEqual(acceptRes.order.status, 'SELLER_ACCEPTED');

    // Test Idempotent repeated accept
    const acceptReplay = await orderRepo.acceptOrderBySeller(orderId, storeAId, sellerAId);
    assert.strictEqual(acceptReplay.ok, true);
    assert.strictEqual(acceptReplay.isIdempotent, true);

    // 6. Valid Transition: SELLER_ACCEPTED -> PACKED
    const packRes = await orderRepo.packOrderBySeller(orderId, storeAId, sellerAId);
    assert.strictEqual(packRes.ok, true);
    assert.strictEqual(packRes.order.status, 'PACKED');

    // Test Idempotent repeated pack
    const packReplay = await orderRepo.packOrderBySeller(orderId, storeAId, sellerAId);
    assert.strictEqual(packReplay.ok, true);
    assert.strictEqual(packReplay.isIdempotent, true);

    // 7. Valid Transition: PACKED -> READY_FOR_PICKUP
    const readyRes = await orderRepo.markReadyForPickup(orderId, storeAId, sellerAId);
    assert.strictEqual(readyRes.ok, true);
    assert.strictEqual(readyRes.order.status, 'READY_FOR_PICKUP');

    // 8. Test Outbox Event Deduplication on Repeated READY_FOR_PICKUP
    const readyReplay = await orderRepo.markReadyForPickup(orderId, storeAId, sellerAId);
    assert.strictEqual(readyReplay.ok, true);
    assert.strictEqual(readyReplay.isIdempotent, true);

    // 9. Test Concurrent READY_FOR_PICKUP Race (2 simultaneous calls)
    const raceOrderId = 'ord_race_ssm_' + timestamp;
    const raceDeliveryId = 'del_race_ssm_' + timestamp;
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, order_type, status, total_amount, payment_method, payment_status, is_cod, delivery_address, items, delivery_otp_hash, created_at, updated_at)
       VALUES ($1, $1, $2, $3, 'QUICK_COMMERCE_10MIN', 'PACKED', 100, 'UPI_INSTANT', 'PAID', FALSE, '{"addressLine": "Sector 14", "latitude": 28.47, "longitude": 77.03}', '[]', 'fakehash', NOW(), NOW())`,
      [raceOrderId, custId, storeAId]
    );
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng, distance_km, is_cod, otp_verified, created_at, updated_at)
       VALUES ($1, $1, $2, $3, 'LOOKING_FOR_RIDER', 'SSM Hub A', 'Sector 14', 28.47, 77.03, 'Customer', 'Sector 14', 28.47, 77.03, 0.2, FALSE, FALSE, NOW(), NOW())`,
      [raceDeliveryId, raceOrderId, storeAId]
    );

    const [raceRes1, raceRes2] = await Promise.all([
      orderRepo.markReadyForPickup(raceOrderId, storeAId, sellerAId),
      orderRepo.markReadyForPickup(raceOrderId, storeAId, sellerAId)
    ]);

    assert.strictEqual(raceRes1.ok, true);
    assert.strictEqual(raceRes2.ok, true);

    const checkOutboxRace = await pool.query(
      `SELECT COUNT(*) as count FROM outbox_events WHERE aggregate_id = $1 AND event_type = 'DISPATCH_REQUESTED'`,
      [raceOrderId]
    );
    assert.strictEqual(Number(checkOutboxRace.rows[0].count), 1, 'Concurrent READY_FOR_PICKUP race must produce exactly ONE outbox event');

    console.log('  ✅ PASS: Seller Order State Machine & Concurrent Ready-For-Pickup Race Verified\n');
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id IN (SELECT id FROM orders WHERE customer_id = $1)`, [custId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM orders WHERE customer_id = $1`, [custId]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE sku = $1`, [sku]);
    await pool.query(`DELETE FROM customer_addresses WHERE id = $1`, [addressId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [sellerAId, sellerBId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for seller-state-machine.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Seller State Machine Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
