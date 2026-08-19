/**
 * Live PostgreSQL Test: Complete Order Placement Transaction
 * 
 * Verifies that TransactionalOrderRepository.placeOrderTransactionally():
 * 1. Atomically debits stock in inventory table.
 * 2. Inserts orders row with delivery_otp_hash (NO plaintext OTP).
 * 3. Inserts delivery_sessions row with matching IDs and valid coordinates (NO plaintext PIN).
 * 4. Inserts outbox_events row with DISPATCH_REQUESTED.
 * 5. Runs cleanly without schema/column mismatch against schema.sql.
 */

const assert = require('assert');
const {
  TransactionalOrderRepository,
  TransactionalInventoryRepository,
  TransactionalCatalogRepository,
  DeliveryOtpService
} = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Atomic Order -> Inventory -> Delivery -> Outbox Transaction...');

  const timestamp = Date.now();
  const storeId = 'STORE_TX_TEST_' + timestamp;
  const custId = 'cust_tx_' + timestamp;
  const sku = 'SKU_TX_' + timestamp;
  const orderId = 'ord_tx_live_' + timestamp;
  const deliveryId = 'del_tx_live_' + timestamp;
  const addressId = 'addr_tx_' + timestamp;
  let outboxId = null;

  const invRepo = new TransactionalInventoryRepository(pool);
  const orderRepo = new TransactionalOrderRepository(pool, invRepo);

  try {
    // 1. Seed Store
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Test Store Hub', 'Cyber City, Gurugram', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    // 2. Seed Customer & Authoritative Address
    const customerPhone = '+9199' + String(timestamp).slice(-8);
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'Live Test Customer', 'PRO', TRUE)`,
      [custId, customerPhone]
    );

    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_type, address_line, city, postal_code, latitude, longitude, is_default)
       VALUES ($1, $2, 'HOME', 'Flat 402, Test Tower, Gurugram', 'Gurugram', '122002', 28.4610, 77.0310, TRUE)`,
      [addressId, custId]
    );

    // 3. Seed Global Product (using rx_requirement column) & Store Inventory (stock = 5) with canonical triple
    const prodIdTx = 'prod_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, price, mrp, store_id, rx_requirement, is_active)
       VALUES ($1, $2, 'Order Tx Test Product', 'BrandX', 120.00, 150.00, $3, 'OTC', TRUE)`,
      [prodIdTx, sku, storeId]
    );

    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES ($1, $2, $3, 5, 0)`,
      [storeId, prodIdTx, sku]
    );

    const { FulfillmentDecision } = require('../repositories');

    const fulfillmentDecision = new FulfillmentDecision({
      storeId,
      storeName: 'Test Store Hub',
      storeAddress: 'Cyber City, Gurugram',
      storeLatitude: 28.4595,
      storeLongitude: 77.0266,
      distanceKm: 0.5,
      slaMinutes: 10,
      decisionSource: 'SERVICEABILITY_ENGINE',
      deterministicRank: 1,
      resolvedItems: [{ sku, quantity: 1 }]
    });

    // 4. Test Missing addressId Rejection
    const missingAddrRes = await orderRepo.placeOrderTransactionally(custId, {
      customerId: custId,
      storeId: storeId,
      fulfillmentDecision,
      items: [{ sku, quantity: 1 }]
    });
    assert.strictEqual(missingAddrRes.ok, false, 'Missing addressId must fail');
    assert.strictEqual(missingAddrRes.error, 'ADDRESS_ID_REQUIRED');

    // 4b. Test Missing FulfillmentDecision Rejection
    const missingDecisionRes = await orderRepo.placeOrderTransactionally(custId, {
      customerId: custId,
      storeId: storeId,
      addressId: addressId,
      items: [{ sku, quantity: 1 }]
    });
    assert.strictEqual(missingDecisionRes.ok, false, 'Missing FulfillmentDecision must fail');
    assert.strictEqual(missingDecisionRes.error, 'FULFILLMENT_DECISION_REQUIRED');

    // 5. Place Order Transactionally with Authoritative addressId and prepaid method
    const orderData = {
      customerId: custId,
      storeId: storeId,
      addressId: addressId,
      fulfillmentDecision,
      idempotencyKey: 'idem_tx_' + timestamp,
      paymentMethod: 'UPI_INSTANT',
      orderType: 'QUICK_COMMERCE_10MIN',
      deliveryOtpHash: 'fake_client_hash_that_must_be_ignored',
      items: [
        { sku: sku, quantity: 1, price: 120.00, name: 'Order Tx Test Product' }
      ]
    };

    const sessionData = {
      riderId: 'client_rider_that_must_be_ignored',
      state: 'DELIVERED' // Client attempt to set state must be ignored
    };

    // 5a. Test Invalid Order Type Rejection
    const invalidTypeRes = await orderRepo.placeOrderTransactionally(custId, { ...orderData, orderType: 'UNKNOWN_FLYING_DRONE' }, sessionData);
    assert.strictEqual(invalidTypeRes.ok, false);
    assert.strictEqual(invalidTypeRes.error, 'INVALID_ORDER_TYPE');

    // 5b. Valid Order Placement
    const result = await orderRepo.placeOrderTransactionally(custId, orderData, sessionData);
    assert.strictEqual(result.ok, true, `Order placement failed: ${result.error || result.message}`);
    
    const actualOrderId = result.order.id;
    const actualDeliveryId = result.session.id;
    assert.ok(actualOrderId.startsWith('ord_'), 'Order ID must be server-generated with ord_ prefix');
    assert.ok(actualDeliveryId.startsWith('del_'), 'Delivery ID must be server-generated with del_ prefix');
    assert.strictEqual(result.order.payment_status, 'PAYMENT_PENDING', 'Prepaid order must be PAYMENT_PENDING upon creation');

    // 5c. Idempotency Key Reuse Mismatch Test (Same Key + Different Items -> 409)
    const mismatchIdemRes = await orderRepo.placeOrderTransactionally(custId, {
      ...orderData,
      items: [{ sku: sku, quantity: 2 }] // Different quantity
    });
    assert.strictEqual(mismatchIdemRes.ok, false, 'Reusing idempotency key with different payload must fail');
    assert.strictEqual(mismatchIdemRes.httpStatus, 409, 'Must return 409 status');
    assert.strictEqual(mismatchIdemRes.error, 'IDEMPOTENCY_KEY_REUSE_MISMATCH');

    // 5c2. Idempotency Key Reuse Mismatch Test (Same Key + Different Order Type -> 409)
    const mismatchOrderTypeRes = await orderRepo.placeOrderTransactionally(custId, {
      ...orderData,
      orderType: 'SCHEDULED_DELIVERY'
    });
    assert.strictEqual(mismatchOrderTypeRes.ok, false);
    assert.strictEqual(mismatchOrderTypeRes.httpStatus, 409);
    assert.strictEqual(mismatchOrderTypeRes.error, 'IDEMPOTENCY_KEY_REUSE_MISMATCH');

    // 5c3. Idempotency Key Reuse Mismatch Test (Same Key + Different Payment Method -> 409)
    const mismatchPaymentRes = await orderRepo.placeOrderTransactionally(custId, {
      ...orderData,
      paymentMethod: 'COD'
    });
    assert.strictEqual(mismatchPaymentRes.ok, false);
    assert.strictEqual(mismatchPaymentRes.httpStatus, 409);
    assert.strictEqual(mismatchPaymentRes.error, 'IDEMPOTENCY_KEY_REUSE_MISMATCH');

    // 5d. Idempotent Replay Test (Same Key + Same Payload -> 200 Replay)
    const replayIdemRes = await orderRepo.placeOrderTransactionally(custId, orderData);
    assert.strictEqual(replayIdemRes.ok, true, 'Same idempotency key with same payload must return 200 replay');
    assert.strictEqual(replayIdemRes.isIdempotentReplay, true, 'Must flag as idempotent replay');

    // 5e. Test Canonical Duplicate Line Merging (1x via SKU + 1x via productId -> merged to 2x)
    const prodId = 'prod_' + timestamp;
    const mergeTestKey = 'idem_merge_' + timestamp;
    const mergeDecision = new FulfillmentDecision({
      storeId,
      distanceKm: 0.5,
      slaMinutes: 10,
      decisionSource: 'SERVICEABILITY_ENGINE'
    });
    const mergeRes = await orderRepo.placeOrderTransactionally(custId, {
      customerId: custId,
      storeId: storeId,
      addressId: addressId,
      fulfillmentDecision: mergeDecision,
      idempotencyKey: mergeTestKey,
      paymentMethod: 'UPI_INSTANT',
      items: [
        { sku: sku, quantity: 1 },
        { productId: prodId, quantity: 1 } // Same product referenced by productId
      ]
    });
    assert.strictEqual(mergeRes.ok, true, 'Canonical duplicate resolution must succeed');
    const parsedItems = typeof mergeRes.order.items === 'string' ? JSON.parse(mergeRes.order.items) : mergeRes.order.items;
    assert.strictEqual(parsedItems.length, 1, 'Duplicate SKU and productId references must merge into exactly 1 line item');
    assert.strictEqual(parsedItems[0].quantity, 2, 'Merged line item quantity must be 2');

    // 6. Assertions on Database State
    // A. Orders Table
    const orderDbRes = await pool.query(`SELECT * FROM orders WHERE id = $1`, [actualOrderId]);
    assert.strictEqual(orderDbRes.rows.length, 1, 'Order must exist in database');
    const orderRow = orderDbRes.rows[0];
    assert.strictEqual(orderRow.order_id, actualOrderId);
    assert.strictEqual(orderRow.status, 'PLACED');
    assert.strictEqual(orderRow.payment_status, 'PAYMENT_PENDING', 'DB payment_status must be PAYMENT_PENDING');
    assert.ok(orderRow.delivery_otp_hash, 'delivery_otp_hash must be present');
    assert.strictEqual(orderRow.delivery_otp_hash.length, 64, 'delivery_otp_hash must be 64-character SHA-256 hash');
    assert.notStrictEqual(orderRow.delivery_otp_hash, 'fake_client_hash_that_must_be_ignored', 'Client-supplied fake OTP hash must be ignored');
    assert.strictEqual(orderRow.delivery_otp, undefined, 'Plaintext delivery_otp must NOT exist');

    // B. Delivery Sessions Table
    const sessionDbRes = await pool.query(`SELECT * FROM delivery_sessions WHERE id = $1`, [actualDeliveryId]);
    assert.strictEqual(sessionDbRes.rows.length, 1, 'Delivery session must exist in database');
    const sessionRow = sessionDbRes.rows[0];
    assert.strictEqual(sessionRow.order_id, actualOrderId);
    assert.strictEqual(sessionRow.delivery_id, actualDeliveryId);
    assert.strictEqual(sessionRow.state, 'LOOKING_FOR_RIDER', 'Client state DELIVERED must be ignored; forced LOOKING_FOR_RIDER');
    assert.strictEqual(sessionRow.rider_id, null, 'Client rider assignment must be ignored; forced null');
    assert.strictEqual(sessionRow.otp_verified, false);
    assert.strictEqual(sessionRow.customer_address, 'Flat 402, Test Tower, Gurugram');
    assert.strictEqual(sessionRow.delivery_pin, undefined, 'Plaintext delivery_pin must NOT exist');

    // C. Inventory Table (Reserved = 3 (1 from order 1 + 2 from merge order), Available = 2, On-Hand = 5)
    const invDbRes = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE sku = $1 AND store_id = $2`, [sku, storeId]);
    assert.strictEqual(Number(invDbRes.rows[0].stock_count), 5, 'On-hand stock count must remain 5');
    assert.strictEqual(Number(invDbRes.rows[0].reserved_count), 3, 'Reserved count must be 3 (1 from order 1 + 2 from merge order)');
    assert.strictEqual(Number(invDbRes.rows[0].available_count), 2, 'Available count must be 2');

    // D. Inventory Ledger Table (Audit Trail)
    const ledgerRes = await pool.query(`SELECT reason, delta, new_stock FROM inventory_ledger WHERE store_id = $1 AND sku = $2`, [storeId, sku]);
    assert.strictEqual(ledgerRes.rows.length, 2, 'Must record 2 reservation ledger entries');
    assert.strictEqual(ledgerRes.rows[0].reason, 'RESERVATION_CREATED');
    assert.strictEqual(ledgerRes.rows[1].reason, 'RESERVATION_CREATED');

    // E. Outbox Events Table
    const outboxRes = await pool.query(
      `SELECT * FROM outbox_events WHERE aggregate_id = $1 AND event_type = 'DISPATCH_REQUESTED'`,
      [actualDeliveryId]
    );
    assert.strictEqual(outboxRes.rows.length, 1, 'DISPATCH_REQUESTED event must be written to outbox');
    outboxId = outboxRes.rows[0].id;
    assert.strictEqual(outboxRes.rows[0].status, 'PENDING');

    // F. Authoritative Payment Capture
    const { TransactionalPaymentRepository } = require('../repositories');
    const paymentRepo = new TransactionalPaymentRepository(pool);
    const paymentIntent = await paymentRepo.createOrGetPaymentIntent({
      orderId: actualOrderId,
      amount: 120.00,
      paymentMethod: 'UPI_INSTANT'
    });
    assert.ok(paymentIntent, 'Payment intent must be created in payments table');
    const paymentResult = await paymentRepo.capturePaymentTransactionally(actualOrderId, 120.00);
    assert.strictEqual(paymentResult.status, 'CAPTURED', 'Payment status must transition to CAPTURED');

    console.log('  ✅ PASS: Atomic Order Placement Transaction (FulfillmentDecision, Request Hash, Idempotency & Payment verified)\n');
  } finally {
    // Teardown
    if (outboxId) {
      await pool.query(`DELETE FROM outbox_events WHERE id = $1`, [outboxId]);
    }
    await pool.query(`DELETE FROM payments WHERE order_id IN (SELECT order_id FROM orders WHERE customer_id = $1) OR order_id IN (SELECT id FROM orders WHERE customer_id = $1)`, [custId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM orders WHERE customer_id = $1`, [custId]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM inventory WHERE sku = $1`, [sku]);
    await pool.query(`DELETE FROM products WHERE sku = $1`, [sku]);
    await pool.query(`DELETE FROM customer_addresses WHERE id = $1`, [addressId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for order-transaction.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Order Transaction Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
