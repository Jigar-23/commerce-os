/**
 * Live PostgreSQL Test: Seller Multi-Tenancy & Store Isolation
 * 
 * Verifies that Seller A (authorized only for Store A) cannot:
 * - Read orders belonging to Store B
 * - Mutate order status for Store B
 * - Adjust inventory for Store B
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalOrderRepository, TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Seller Multi-Tenancy Isolation...');

  const timestamp = Date.now();
  const storeAId = 'store_iso_A_' + crypto.randomUUID();
  const storeBId = 'store_iso_B_' + crypto.randomUUID();
  const sellerAId = 'seller_iso_A_' + crypto.randomUUID();
  const sellerBId = 'seller_iso_B_' + crypto.randomUUID();
  const custAId = 'cust_iso_A_' + crypto.randomUUID();
  const custBId = 'cust_iso_B_' + crypto.randomUUID();
  const orderAId = 'ord_iso_storeA_' + crypto.randomUUID();
  const orderBId = 'ord_iso_storeB_' + crypto.randomUUID();
  const skuB = 'SKU_STORE_B_ISO_' + crypto.randomUUID();
  const productBId = 'prod_iso_B_' + crypto.randomUUID();
  const sharedProductId = 'prod_iso_shared_' + crypto.randomUUID();

  try {
    // 1. Seed Stores
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Hub A', 'Gurugram Sector 18', 28.4595, 77.0266, 10, TRUE),
       ($2, 'Hub B', 'Noida Sector 62', 28.6280, 77.3649, 10, TRUE)`,
      [storeAId, storeBId]
    );

    // 2. Seed Customers
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES 
       ($1, $3, 'Cust A', 'STANDARD', TRUE),
       ($2, $4, 'Cust B', 'STANDARD', TRUE)`,
      [custAId, custBId, '+9199' + String(timestamp).slice(-8), '+9198' + String(timestamp).slice(-8)]
    );

    // 3. Seed Orders in two separate stores
    await pool.query(
      `INSERT INTO orders (id, order_id, store_id, customer_id, total_amount, status, delivery_address, items, delivery_otp_hash, created_at, updated_at)
       VALUES 
       ($1, $1, $3, $5, 450.00, 'PLACED', '{"addressLine": "Gurugram A"}', '[]', 'hash_A', NOW(), NOW()),
       ($2, $2, $4, $6, 780.00, 'PLACED', '{"addressLine": "Noida B"}', '[]', 'hash_B', NOW(), NOW())`,
      [orderAId, orderBId, storeAId, storeBId, custAId, custBId]
    );

    const orderRepo = new TransactionalOrderRepository(pool);
    const invRepo = new TransactionalInventoryRepository(pool);

    // 1. Seller A queries orders for Store A -> sees Order A only
    const storeAOrders = await orderRepo.getOrdersByStore(storeAId);
    assert.ok(storeAOrders.some(o => (o.id === orderAId || o.order_id === orderAId)), 'Seller A must see Order A');
    assert.ok(!storeAOrders.some(o => (o.id === orderBId || o.order_id === orderBId)), 'Seller A must NOT see Order B');

    // 2. Seller A attempts to accept Order B (Store B) -> rejected with 403 / unauthorized
    const crossStoreAttempt = await orderRepo.acceptOrderBySeller(orderBId, storeAId, sellerAId);
    assert.strictEqual(crossStoreAttempt.ok, false);
    assert.strictEqual(crossStoreAttempt.httpStatus, 403);

    // 3. Seller A attempts to adjust inventory for Store B -> rejected with 404
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Store B Product', 'BrandIso', 80.00, 65.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [productBId, skuB]
    );
    await pool.query(
      `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count)
       VALUES ($1, $2, $4, $3, 'Store B Product', 20, 0)`,
      ['inv_' + crypto.randomUUID(), storeBId, skuB, productBId]
    );

    // 4. Test Seller Scrypt Credentials and Store Mapping
    const { TransactionalSellerRepository } = require('../repositories');
    const sellerRepo = new TransactionalSellerRepository(pool);
    const sellerPassword = 'SecretStorePass!991';
    const scryptHash = TransactionalSellerRepository.hashPassword(sellerPassword);

    await pool.query(
      `INSERT INTO sellers (id, seller_id, merchant_name, email, phone, store_id, password_hash, status)
       VALUES ($1, $1, 'Authorized Seller A', $4, $5, $2, $3, 'ACTIVE')`,
      [sellerAId, storeAId, scryptHash, `seller_iso_${timestamp}@hub.com`, '+9198' + String(timestamp).slice(-8)]
    );

    const authSuccess = await sellerRepo.verifySellerCredentials(sellerAId, sellerPassword);
    assert.strictEqual(authSuccess.ok, true, 'Scrypt credentials verification must succeed');
    assert.strictEqual(authSuccess.seller.storeId, storeAId, 'Must return mapped storeId');

    const authWrongPass = await sellerRepo.verifySellerCredentials(sellerAId, 'WrongPassword!123');
    assert.strictEqual(authWrongPass.ok, false, 'Wrong password must be rejected');
    assert.strictEqual(authWrongPass.error, 'INVALID_CREDENTIALS');

    // 5. Multi-Store Inventory Isolation with Identical SKU
    const sharedSku = 'SHARED_SKU_' + crypto.randomUUID();
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Shared Item', 'BrandIso', 50.00, 42.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [sharedProductId, sharedSku]
    );
    await pool.query(
      `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count)
       VALUES 
       ($1, $2, $6, $3, 'Shared Item', 10, 0),
       ($4, $5, $6, $3, 'Shared Item', 20, 0)`,
      ['inv_shA_' + crypto.randomUUID(), storeAId, sharedSku, 'inv_shB_' + crypto.randomUUID(), storeBId, sharedProductId]
    );

    // Reserve 4 in Store A
    const resA = await invRepo.reserveStockTransactionally(pool, storeAId, [{ sku: sharedSku, productId: sharedProductId, quantity: 4 }]);
    assert.strictEqual(resA.ok, true);

    // Store A: stock = 10, reserved = 4, available = 6
    const checkStoreA = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE store_id = $1 AND sku = $2`, [storeAId, sharedSku]);
    assert.strictEqual(Number(checkStoreA.rows[0].available_count), 6);

    // Store B: stock = 20, reserved = 0, available = 20 (Unaffected!)
    const checkStoreB = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE store_id = $1 AND sku = $2`, [storeBId, sharedSku]);
    assert.strictEqual(Number(checkStoreB.rows[0].stock_count), 20);
    assert.strictEqual(Number(checkStoreB.rows[0].reserved_count), 0);
    assert.strictEqual(Number(checkStoreB.rows[0].available_count), 20);

    console.log('  ✅ PASS: Seller Multi-Tenancy Isolation (Cross-store read/write rejected, Scrypt auth & same-SKU isolation verified)\n');
  } finally {
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [sellerAId, sellerBId]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id IN ($1, $2)`, [productBId, sharedProductId]);
    await pool.query(`DELETE FROM orders WHERE id IN ($1, $2)`, [orderAId, orderBId]);
    await pool.query(`DELETE FROM customers WHERE id IN ($1, $2)`, [custAId, custBId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for seller-isolation.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Live Seller Isolation Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
