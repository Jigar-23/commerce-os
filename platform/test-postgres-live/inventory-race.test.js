/**
 * Live PostgreSQL Test: Concurrent 3-State Inventory Reservation & Fulfillment Race
 * 
 * Verifies with 2 isolated DB connections:
 * When stock_count = 1, reserved_count = 0 (available = 1), two simultaneous reservations of qty = 1 result in:
 * - Exactly 1 success (ok: true, reserved_count -> 1, available_count -> 0)
 * - Exactly 1 failure (ok: false, error: OUT_OF_STOCK)
 * - Available count never negative
 * - Fulfillment decrements both stock_count and reserved_count to 0
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Concurrent 3-State Inventory Reservation Race...');

  const timestamp = Date.now();
  const testStoreId = 'store_race_' + crypto.randomUUID();
  const testSku = 'SKU_LIVE_RACE_' + crypto.randomUUID();
  const prodId = 'prod_race_' + crypto.randomUUID();

  // 1. Seed Store and Product
  await pool.query(
    `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
     VALUES ($1, 'Race Fulfillment Hub', 'Cyber City Hub', 28.4595, 77.0266, 10, TRUE)`,
    [testStoreId]
  );

  await pool.query(
    `INSERT INTO products (id, sku, name, brand_name, price, mrp, store_id, is_active)
     VALUES ($1, $2, 'Race Test Product', 'BrandX', 99.00, 120.00, $3, TRUE)`,
    [prodId, testSku, testStoreId]
  );

  // 2. Seed Initial Inventory: on_hand (stock_count) = 1, reserved_count = 0 -> available_count = 1
  await pool.query(
    `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count)
     VALUES ($1, $2, $3, $4, 'Race Test Product', 1, 0)`,
    ['inv_' + crypto.randomUUID(), testStoreId, prodId, testSku]
  );

  const invRepo = new TransactionalInventoryRepository(pool);

  const clientA = await pool.connect();
  const clientB = await pool.connect();

  try {
    // 3. Two concurrent clients race to reserve the last 1 available unit
    const runReservationTx = async (client) => {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
      try {
        const res = await invRepo.reserveStockTransactionally(client, testStoreId, [{ sku: testSku, productId: prodId, quantity: 1 }]);
        if (res.ok) {
          await client.query('COMMIT');
        } else {
          await client.query('ROLLBACK');
        }
        return res;
      } catch (err) {
        await client.query('ROLLBACK');
        if (err.code === '40001') {
          return { ok: false, error: 'OUT_OF_STOCK' };
        }
        throw err;
      }
    };

    const [resA, resB] = await Promise.all([
      runReservationTx(clientA),
      runReservationTx(clientB)
    ]);

    const successes = [resA, resB].filter(r => r.ok === true).length;
    const outOfStock = [resA, resB].filter(r => r.ok === false && r.error === 'OUT_OF_STOCK').length;

    assert.strictEqual(successes, 1, 'Exactly one concurrent client must succeed in reserving last unit');
    assert.strictEqual(outOfStock, 1, 'Competing client must be rejected with OUT_OF_STOCK');

    // 4. Assert Reservation State in PostgreSQL (stock_count = 1, reserved_count = 1, available_count = 0)
    const checkRes = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE sku = $1 AND store_id = $2`, [testSku, testStoreId]);
    assert.strictEqual(Number(checkRes.rows[0].stock_count), 1, 'Stock count on-hand must remain 1 during reservation');
    assert.strictEqual(Number(checkRes.rows[0].reserved_count), 1, 'Reserved count must be incremented to 1');
    assert.strictEqual(Number(checkRes.rows[0].available_count), 0, 'Available count must be 0');

    // 5. Winning Client Fulfills Order -> Consumes Reserved Stock
    await invRepo.fulfillStockTransactionally(clientA, testStoreId, [{ sku: testSku, productId: prodId, quantity: 1 }]);

    const finalRes = await pool.query(`SELECT stock_count, reserved_count, available_count FROM inventory WHERE sku = $1 AND store_id = $2`, [testSku, testStoreId]);
    assert.strictEqual(Number(finalRes.rows[0].stock_count), 0, 'Final stock count on-hand must be 0 after fulfillment');
    assert.strictEqual(Number(finalRes.rows[0].reserved_count), 0, 'Final reserved count must be 0');
    assert.strictEqual(Number(finalRes.rows[0].available_count), 0, 'Final available count must be 0');

    // 6. Assert Ledger Audit Trail
    const ledgerRes = await pool.query(`SELECT reason, delta, new_stock FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at ASC`, [testStoreId, testSku]);
    assert.strictEqual(ledgerRes.rows.length, 2, 'Must record 2 ledger entries (reservation + fulfillment)');
    assert.strictEqual(ledgerRes.rows[0].reason, 'RESERVATION_CREATED');
    assert.strictEqual(ledgerRes.rows[1].reason, 'STOCK_CONSUMED');

    console.log('  ✅ PASS: Concurrent 3-State Inventory Race (1 reserved, 1 OUT_OF_STOCK, fulfilled to 0, ledger validated)\n');
  } finally {
    clientA.release();
    clientB.release();
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id = $1`, [testStoreId]);
    await pool.query(`DELETE FROM inventory WHERE sku = $1`, [testSku]);
    await pool.query(`DELETE FROM products WHERE id = $1`, [prodId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [testStoreId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for inventory-race.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Live Inventory Race Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
