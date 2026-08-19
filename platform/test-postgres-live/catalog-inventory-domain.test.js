/**
 * Commerce OS — Live PostgreSQL Master Catalog & Multi-Store Inventory Domain Contract Test
 * 
 * Permanent Guard for Domain Invariants:
 * 1. Global Master Product: One authoritative product row with unique SKU across the platform.
 * 2. Multi-Store Association: Store A and Store B independently associate with the same global SKU via inventory table.
 * 3. Store-Scoped 3-State Inventory Isolation:
 *    - Store A starts with 15 on-hand, Store B starts with 25 on-hand.
 *    - Reserve 5 from Store A -> Store A (on-hand 15, reserved 5, available 10); Store B completely unaffected (on-hand 25, reserved 0, available 25).
 *    - Reserve 10 from Store B -> Store B (on-hand 25, reserved 10, available 15); Store A completely unaffected (on-hand 15, reserved 5, available 10).
 * 4. Fulfillment Transition:
 *    - Fulfill 5 in Store A -> Store A (on-hand 10, reserved 0, available 10); Ledger records -5 delta.
 *    - Fulfill 10 in Store B -> Store B (on-hand 15, reserved 0, available 15); Ledger records -10 delta.
 * 5. Release Transition:
 *    - Reserve 2 in Store A (on-hand 10, reserved 2, available 8).
 *    - Release 2 in Store A -> Store A (on-hand 10, reserved 0, available 10); Ledger records 0 delta with RESERVATION_RELEASED.
 */

const assert = require('assert');
const { Pool } = require('pg');
const { TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Permanent Master Catalog & Store-Scoped 3-State Inventory Contract...');

  const timestamp = Date.now();
  const storeAId = 'store_cat_a_' + timestamp;
  const storeBId = 'store_cat_b_' + timestamp;
  const prodId = 'prod_master_' + timestamp;
  const skuGlobal = 'SKU_PARACETAMOL_650_' + timestamp;

  const invRepo = new TransactionalInventoryRepository(pool);

  try {
    // 1. Seed Stores
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Hub North', 'Sector 14, Gurugram', 28.4700, 77.0300, 10, TRUE),
       ($2, 'Hub South', 'Sector 48, Gurugram', 28.4200, 77.0400, 15, TRUE)`,
      [storeAId, storeBId]
    );

    // 2. Seed Single Master Product in Global Catalog (sku UNIQUE)
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, rx_requirement, is_active)
       VALUES ($1, $2, 'Paracetamol 650mg Tablets', 'PharmaCare', 40.00, 32.00, 'OTC', TRUE)`,
      [prodId, skuGlobal]
    );

    // 3. Seed Multi-Store Inventory Rows for the same global product
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, stock_count, reserved_count)
       VALUES 
       ($1, $3, $4, 15, 0),
       ($2, $3, $4, 25, 0)`,
      [storeAId, storeBId, prodId, skuGlobal]
    );

    // 4. Reserve 5 in Store A -> Verify Store A state & Store B isolation
    const resA = await invRepo.reserveStockTransactionally(null, storeAId, [{ sku: skuGlobal, productId: prodId, quantity: 5 }]);
    assert.strictEqual(resA.ok, true, 'Store A reservation must succeed');

    const invA1 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuGlobal]
    );
    assert.strictEqual(Number(invA1.rows[0].stock_count), 15, 'Store A on-hand must stay 15');
    assert.strictEqual(Number(invA1.rows[0].reserved_count), 5, 'Store A reserved must become 5');
    assert.strictEqual(Number(invA1.rows[0].available_count), 10, 'Store A available must become 10');

    const invB1 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeBId, skuGlobal]
    );
    assert.strictEqual(Number(invB1.rows[0].stock_count), 25, 'Store B on-hand must remain untouched at 25');
    assert.strictEqual(Number(invB1.rows[0].reserved_count), 0, 'Store B reserved must remain untouched at 0');
    assert.strictEqual(Number(invB1.rows[0].available_count), 25, 'Store B available must remain untouched at 25');

    // 5. Reserve 10 in Store B -> Verify Store B state & Store A isolation
    const resB = await invRepo.reserveStockTransactionally(null, storeBId, [{ sku: skuGlobal, productId: prodId, quantity: 10 }]);
    assert.strictEqual(resB.ok, true, 'Store B reservation must succeed');

    const invB2 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeBId, skuGlobal]
    );
    assert.strictEqual(Number(invB2.rows[0].stock_count), 25, 'Store B on-hand must stay 25');
    assert.strictEqual(Number(invB2.rows[0].reserved_count), 10, 'Store B reserved must become 10');
    assert.strictEqual(Number(invB2.rows[0].available_count), 15, 'Store B available must become 15');

    const invA2 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuGlobal]
    );
    assert.strictEqual(Number(invA2.rows[0].stock_count), 15, 'Store A on-hand must remain 15');
    assert.strictEqual(Number(invA2.rows[0].reserved_count), 5, 'Store A reserved must remain 5');
    assert.strictEqual(Number(invA2.rows[0].available_count), 10, 'Store A available must remain 10');

    // 6. Fulfill 5 in Store A -> on-hand = 10, reserved = 0, available = 10
    const fulA = await invRepo.fulfillStockTransactionally(null, storeAId, [{ sku: skuGlobal, productId: prodId, quantity: 5 }]);
    assert.strictEqual(fulA.ok, true, 'Store A fulfillment must succeed');

    const invA3 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuGlobal]
    );
    assert.strictEqual(Number(invA3.rows[0].stock_count), 10, 'Store A on-hand must decrease to 10');
    assert.strictEqual(Number(invA3.rows[0].reserved_count), 0, 'Store A reserved must return to 0');
    assert.strictEqual(Number(invA3.rows[0].available_count), 10, 'Store A available must be 10');

    // 7. Fulfill 10 in Store B -> on-hand = 15, reserved = 0, available = 15
    const fulB = await invRepo.fulfillStockTransactionally(null, storeBId, [{ sku: skuGlobal, productId: prodId, quantity: 10 }]);
    assert.strictEqual(fulB.ok, true, 'Store B fulfillment must succeed');

    const invB3 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeBId, skuGlobal]
    );
    assert.strictEqual(Number(invB3.rows[0].stock_count), 15, 'Store B on-hand must decrease to 15');
    assert.strictEqual(Number(invB3.rows[0].reserved_count), 0, 'Store B reserved must return to 0');
    assert.strictEqual(Number(invB3.rows[0].available_count), 15, 'Store B available must be 15');

    // 8. Test Reservation + Release Cycle in Store A: Reserve 2, then Release 2
    await invRepo.reserveStockTransactionally(null, storeAId, [{ sku: skuGlobal, productId: prodId, quantity: 2 }]);
    const invA4 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuGlobal]
    );
    assert.strictEqual(Number(invA4.rows[0].stock_count), 10);
    assert.strictEqual(Number(invA4.rows[0].reserved_count), 2);
    assert.strictEqual(Number(invA4.rows[0].available_count), 8);

    const relA = await invRepo.releaseStockTransactionally(null, storeAId, [{ sku: skuGlobal, productId: prodId, quantity: 2 }]);
    assert.strictEqual(relA.ok, true, 'Release must succeed');

    const invA5 = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count FROM inventory WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuGlobal]
    );
    assert.strictEqual(Number(invA5.rows[0].stock_count), 10, 'On-hand must stay 10 after release');
    assert.strictEqual(Number(invA5.rows[0].reserved_count), 0, 'Reserved count must return to 0 after release');
    assert.strictEqual(Number(invA5.rows[0].available_count), 10, 'Available count must be restored to 10 after release');

    // 9. Test Global SKU Uniqueness Invariant: Attempt to insert a second master product with skuGlobal -> Must fail on PostgreSQL unique constraint
    let duplicateFailed = false;
    try {
      await pool.query(
        `INSERT INTO products (id, sku, name, brand_name, mrp, price, rx_requirement, is_active)
         VALUES ($1, $2, 'Duplicate Paracetamol', 'PharmaFake', 50.00, 40.00, 'OTC', TRUE)`,
        ['prod_dup_' + timestamp, skuGlobal]
      );
    } catch (err) {
      duplicateFailed = true;
      assert.ok(
        err.message.includes('products_sku') || err.code === '23505',
        `Expected unique constraint violation on products.sku, got: ${err.message}`
      );
    }
    assert.strictEqual(duplicateFailed, true, 'PostgreSQL must reject duplicate master product rows with the same global SKU');

    console.log('  ✅ PASS: Permanent Master Catalog & Store-Scoped 3-State Inventory Contract\n');
  } finally {
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id IN ($1, $2)`, [prodId, 'prod_dup_' + timestamp]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for catalog-inventory-domain.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Catalog Inventory Domain Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
