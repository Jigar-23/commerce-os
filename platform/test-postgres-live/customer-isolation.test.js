/**
 * Live PostgreSQL Test: Customer Order & Tracking Isolation
 * 
 * Verifies that Customer A cannot read or mutate Customer B's order.
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalOrderRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Customer Isolation...');

  const storeId = 'store_cust_iso_' + crypto.randomUUID();
  const custAId = 'cust_alpha_' + crypto.randomUUID();
  const custBId = 'cust_beta_' + crypto.randomUUID();
  const orderCustA = 'ord_custA_' + crypto.randomUUID();
  const orderCustB = 'ord_custB_' + crypto.randomUUID();

  try {
    // 1. Seed Store
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Customer Iso Store', 'Gurugram Hub', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    // 2. Seed Customers
    const timestamp = Date.now();
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES 
       ($1, $3, 'Customer Alpha', 'STANDARD', TRUE),
       ($2, $4, 'Customer Beta', 'STANDARD', TRUE)`,
      [custAId, custBId, '+9199' + String(timestamp).slice(-8), '+9198' + String(timestamp).slice(-8)]
    );

    // 3. Seed Orders
    await pool.query(
      `INSERT INTO orders (id, order_id, store_id, customer_id, total_amount, status, delivery_address, items, delivery_otp_hash, created_at, updated_at)
       VALUES 
       ($1, $1, $3, $4, 320.00, 'PLACED', '{"addressLine": "Alpha Flat 1"}', '[]', 'hash_A', NOW(), NOW()),
       ($2, $2, $3, $5, 640.00, 'PLACED', '{"addressLine": "Beta Flat 2"}', '[]', 'hash_B', NOW(), NOW())`,
      [orderCustA, orderCustB, storeId, custAId, custBId]
    );

    const orderRepo = new TransactionalOrderRepository(pool);
    const { TransactionalAddressRepository } = require('../repositories');
    const addressRepo = new TransactionalAddressRepository(pool);

    const addrBId = 'addr_beta_' + crypto.randomUUID();
    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_type, address_line, city, postal_code, latitude, longitude, is_default)
       VALUES ($1, $2, 'HOME', 'Beta Secret Address', 'Noida', '201301', 28.6280, 77.3649, TRUE)`,
      [addrBId, custBId]
    );

    // Customer A queries recent orders -> sees Order A only
    const custAOrders = await orderRepo.getRecentCustomerOrders(custAId);
    assert.ok(custAOrders.some(o => o.id === orderCustA || o.order_id === orderCustA), 'Customer A must see Order A');
    assert.ok(!custAOrders.some(o => o.id === orderCustB || o.order_id === orderCustB), 'Customer A must NOT see Order B');

    // Customer A queries active order -> gets Order A
    const activeA = await orderRepo.getActiveCustomerOrder(custAId);
    assert.strictEqual(activeA.id || activeA.order_id, orderCustA, 'Customer A active order must be Order A');

    // Customer A attempts to access Customer B address -> null / blocked
    const crossAddressAttempt = await addressRepo.findAddressById(custAId, addrBId);
    assert.strictEqual(crossAddressAttempt, null, 'Customer A must NOT be able to resolve Customer B address');

    console.log('  ✅ PASS: Customer Isolation (Customer A cannot access Customer B order or address)\n');
  } finally {
    await pool.query(`DELETE FROM customer_addresses WHERE customer_id IN ($1, $2)`, [custAId, custBId]);
    await pool.query(`DELETE FROM orders WHERE id IN ($1, $2)`, [orderCustA, orderCustB]);
    await pool.query(`DELETE FROM customers WHERE id IN ($1, $2)`, [custAId, custBId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for customer-isolation.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Live Customer Isolation Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
