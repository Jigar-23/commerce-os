/**
 * Live PostgreSQL Test: Multi-Store Same-SKU Isolation & Race Condition
 * 
 * Verifies with real PostgreSQL transactions:
 * 1. Store A and Store B both provision identical SKU 'PARACETAMOL_650' with stock = 1 each.
 * 2. Simultaneous order on Store A and Store B succeed independently without cross-store lock contention.
 * 3. Two competing simultaneous orders on Store A SKU 'PARACETAMOL_650': exactly ONE succeeds (200), ONE fails (409 OUT_OF_STOCK).
 * 4. Store B stock remains untouched at 1, proving absolute multi-store inventory isolation.
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Multi-Store Same-SKU Isolation & Concurrency...');

  const timestamp = Date.now();
  const storeAId = 'store_multiA_' + timestamp;
  const storeBId = 'store_multiB_' + timestamp;
  const sharedSku = 'MED_PARACETAMOL_650_' + timestamp;
  const sharedProductId = 'prod_multi_shared_' + timestamp;

  const invRepo = new TransactionalInventoryRepository(pool);

  try {
    // 1. Seed Store A & Store B
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Hub A Sector 18', 'Gurugram Sector 18', 28.4595, 77.0266, 10, TRUE),
       ($2, 'Hub B Sector 62', 'Noida Sector 62', 28.6280, 77.3649, 10, TRUE)`,
      [storeAId, storeBId]
    );

    // 2. Provision Identical SKU in both stores with stock = 1 each (backed by ONE global product)
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Shared Paracetamol 650', 'BrandMulti', 40.00, 32.00, 'Medicine', '10 Tablets', 'OTC', TRUE)`,
      [sharedProductId, sharedSku]
    );
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES 
       ($1, $3, $2, 1, 0),
       ($4, $3, $2, 1, 0)`,
      [storeAId, sharedSku, sharedProductId, storeBId]
    );

    // 3. Test Independent Multi-Store Reservations (Store A and Store B concurrently)
    const [resStoreA, resStoreB] = await Promise.all([
      invRepo.reserveStockTransactionally(pool, storeAId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }]),
      invRepo.reserveStockTransactionally(pool, storeBId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }])
    ]);

    assert.strictEqual(resStoreA.ok, true, 'Store A reservation must succeed');
    assert.strictEqual(resStoreB.ok, true, 'Store B reservation must succeed');

    // Verify both stores have stock=1, reserved=1, available=0
    const checkA1 = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE store_id = $1 AND sku = $2`, [storeAId, sharedSku]);
    const checkB1 = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE store_id = $1 AND sku = $2`, [storeBId, sharedSku]);
    assert.strictEqual(Number(checkA1.rows[0].available_count), 0);
    assert.strictEqual(Number(checkB1.rows[0].available_count), 0);

    // Release reservations back to available
    await invRepo.releaseStockTransactionally(pool, storeAId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }]);
    await invRepo.releaseStockTransactionally(pool, storeBId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }]);

    // 4. Test Competing Reservations on Store A (2 clients competing for 1 unit in Store A)
    const client1 = await pool.connect();
    const client2 = await pool.connect();

    let client1Res = null;
    let client2Res = null;

    try {
      await Promise.all([
        (async () => {
          try {
            await client1.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
            client1Res = await invRepo.reserveStockTransactionally(client1, storeAId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }]);
            if (client1Res.ok) await client1.query('COMMIT');
            else await client1.query('ROLLBACK');
          } catch (e) {
            await client1.query('ROLLBACK');
            client1Res = { ok: false, error: e.message };
          }
        })(),
        (async () => {
          try {
            await client2.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
            client2Res = await invRepo.reserveStockTransactionally(client2, storeAId, [{ sku: sharedSku, productId: sharedProductId, quantity: 1 }]);
            if (client2Res.ok) await client2.query('COMMIT');
            else await client2.query('ROLLBACK');
          } catch (e) {
            await client2.query('ROLLBACK');
            client2Res = { ok: false, error: e.message };
          }
        })()
      ]);
    } finally {
      client1.release();
      client2.release();
    }

    const successesA = [client1Res, client2Res].filter(r => r.ok);
    const failuresA = [client1Res, client2Res].filter(r => !r.ok);

    assert.strictEqual(successesA.length, 1, 'Exactly ONE client must successfully reserve Store A unit');
    assert.strictEqual(failuresA.length, 1, 'The competing client must fail with OUT_OF_STOCK or concurrency conflict');

    // 5. Verify Store B stock is completely intact (stock=1, reserved=0, available=1)
    const checkB2 = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE store_id = $1 AND sku = $2`, [storeBId, sharedSku]);
    assert.strictEqual(Number(checkB2.rows[0].stock_count), 1, 'Store B stock must remain 1');
    assert.strictEqual(Number(checkB2.rows[0].reserved_count), 0, 'Store B reserved count must remain 0');
    assert.strictEqual(Number(checkB2.rows[0].available_count), 1, 'Store B available count must remain 1');

    console.log('  ✅ PASS: Multi-Store Same-SKU Isolation & Concurrency (Zero cross-store bleed, exact single-winner on Store A)\n');
  } finally {
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [sharedProductId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for multi-store-same-sku.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Multi-Store Same-SKU Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
