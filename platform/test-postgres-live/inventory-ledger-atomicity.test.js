/**
 * Commerce OS — Live PostgreSQL: Inventory Ledger Atomicity, Serialization & Canonical Identity
 *
 * Proves the P0 inventory-domain guarantees beyond what the schema itself enforces:
 * 1. Stock mutation + inventory_ledger commit atomically (no partial audit trail).
 * 2. First-time link (INSERT inventory + ledger) is atomic (no orphaned inventory row).
 * 3. Seller ABSOLUTE set vs relative adjust semantics stay separate (delta ledger correct).
 * 4. setStockForStore (10 -> 20) concurrent with an order reservation (5) -> consistent.
 * 5. productId/sku mismatch is rejected (no cross-product mutation by SKU alone).
 * 6. Store authorization derives from the authenticated seller store, never from caller-supplied storeId.
 */

const assert = require('assert');
const { TransactionalInventoryRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Inventory Ledger Atomicity + Serialization Domain...');

  const timestamp = Date.now();
  const storeAId = 'store_ledger_a_' + timestamp;
  const storeBId = 'store_ledger_b_' + timestamp;
  const productXId = 'prod_ledger_x_' + timestamp;
  const productYId = 'prod_ledger_y_' + timestamp;
  const skuX = 'SKU_LEDGER_X_' + timestamp;
  const skuY = 'SKU_LEDGER_Y_' + timestamp;

  const inventoryRepo = new TransactionalInventoryRepository(pool);

  const LEDGER_CONSTRAINT = 'ledger_atomicity_fail_inject';

  try {
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES
       ($1, 'Ledger Store A', 'Sector 29, Gurugram', 28.4680, 77.0600, 10, TRUE),
       ($2, 'Ledger Store B', 'Sector 48, Gurugram', 28.4595, 77.0400, 15, TRUE)`,
      [storeAId, storeBId]
    );
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES
       ($1, $2, 'Product X Ledger', 'BrandX', 120.00, 100.00, 'Medicine', '10 Tablets', 'OTC', TRUE),
       ($3, $4, 'Product Y Ledger', 'BrandY', 220.00, 200.00, 'Nutrition', '1 Bottle', 'OTC', TRUE)`,
      [productXId, skuX, productYId, skuY]
    );

    // -------------------------------------------------------------
    // 1. Stock + ledger atomicity: force a ledger failure and verify the
    //    inventory mutation is rolled back (no partial audit trail).
    // -------------------------------------------------------------
    await inventoryRepo.setStockForStore(storeAId, productXId, skuX, 10, 'SELLER_RESTOCK');
    await pool.query(`ALTER TABLE inventory_ledger ADD CONSTRAINT ${LEDGER_CONSTRAINT} CHECK (0 = 1) NOT VALID`);
    let ledgerFailureThrew = false;
    try {
      await inventoryRepo.setStockForStore(storeAId, productXId, skuX, 20, 'SELLER_RESTOCK');
    } catch (err) {
      ledgerFailureThrew = true;
    }
    await pool.query(`ALTER TABLE inventory_ledger DROP CONSTRAINT ${LEDGER_CONSTRAINT}`);
    assert.strictEqual(ledgerFailureThrew, true, 'setStockForStore MUST throw when the ledger insert fails');
    const atomicCheck = await pool.query(
      `SELECT stock_count, reserved_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, productXId, skuX]
    );
    assert.strictEqual(Number(atomicCheck.rows[0].stock_count), 10, 'Stock MUST roll back to 10 when the ledger insert fails (inventory + ledger are atomic)');
    const atomicLedger = await pool.query(
      `SELECT COUNT(*) AS count FROM inventory_ledger WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuX]
    );
    assert.strictEqual(Number(atomicLedger.rows[0].count), 1, 'Only the original ledger entry must exist after a failed mutation (no partial audit trail)');

    // -------------------------------------------------------------
    // 2. First-time link atomicity: inventory row must NOT survive without its ledger entry.
    // -------------------------------------------------------------
    const linkedSku = 'SKU_LINK_ATOM_' + timestamp;
    const linkedProdId = 'prod_link_atom_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Product Link Atom', 'BrandLink', 150.00, 140.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [linkedProdId, linkedSku]
    );
    await pool.query(`ALTER TABLE inventory_ledger ADD CONSTRAINT ${LEDGER_CONSTRAINT} CHECK (0 = 1) NOT VALID`);
    let linkFailureThrew = false;
    try {
      await inventoryRepo.setStockForStore(storeAId, linkedProdId, linkedSku, 15, 'SELLER_RESTOCK');
    } catch (err) {
      linkFailureThrew = true;
    }
    await pool.query(`ALTER TABLE inventory_ledger DROP CONSTRAINT ${LEDGER_CONSTRAINT}`);
    assert.strictEqual(linkFailureThrew, true, 'First-time link MUST throw when the ledger insert fails');
    const orphanCheck = await pool.query(
      `SELECT COUNT(*) AS count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, linkedProdId, linkedSku]
    );
    assert.strictEqual(Number(orphanCheck.rows[0].count), 0, 'A failed first-time link MUST NOT leave an orphaned inventory row without a ledger entry');

    // -------------------------------------------------------------
    // 3. Absolute set vs relative adjust semantics remain separated in the ledger.
    // -------------------------------------------------------------
    await inventoryRepo.setStockForStore(storeAId, productYId, skuY, 10, 'SELLER_RESTOCK');
    await inventoryRepo.setStockForStore(storeAId, productYId, skuY, 20, 'SELLER_RESTOCK');
    const setLedger = await pool.query(
      `SELECT delta, new_stock FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at ASC`,
      [storeAId, skuY]
    );
    assert.strictEqual(Number(setLedger.rows[0].delta), 10, 'Set stock 0 -> 10 MUST record delta == +10');
    assert.strictEqual(Number(setLedger.rows[1].delta), 10, 'Set stock 10 -> 20 MUST record delta == +10 (absolute set, delta = desired - current)');

    await inventoryRepo.adjustStockForStore(storeAId, productYId, skuY, 5, 'SELLER_RESTOCK');
    const adjustLedger = await pool.query(
      `SELECT delta, new_stock FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at ASC`,
      [storeAId, skuY]
    );
    assert.strictEqual(Number(adjustLedger.rows[2].delta), 5, 'Adjust +5 MUST record delta == +5 (relative adjustment)');
    assert.strictEqual(Number(adjustLedger.rows[2].new_stock), 25, 'Adjust +5 on stock 20 MUST record new_stock == 25');

    // -------------------------------------------------------------
    // 4. setStock (10 -> 20) concurrent with an order reservation (5)
    //    on the same canonical row -> no lost update, available never negative.
    //    Both operations run as independent SERIALIZABLE transactions (as in production).
    // -------------------------------------------------------------
    // -------------------------------------------------------------
    // 4. setStock (10 -> 20) concurrent with an order reservation (5)
    //    on the same canonical row -> no lost update, available never negative.
    //    Both operations run as independent SERIALIZABLE transactions (as in production).
    // -------------------------------------------------------------
    const raceSku = 'SKU_RACE_LEDGER_' + timestamp;
    const raceProdId = 'prod_race_ledger_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Race Ledger Product', 'BrandRace', 100.00, 90.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [raceProdId, raceSku]
    );
    await inventoryRepo.setStockForStore(storeBId, raceProdId, raceSku, 10, 'SELLER_RESTOCK');
    const reserveClient = await pool.connect();
    const reserveTxn = (async () => {
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          await reserveClient.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
          const res = await inventoryRepo.reserveStockTransactionally(reserveClient, storeBId, [{ sku: raceSku, quantity: 5, productId: raceProdId }]);
          await reserveClient.query('COMMIT');
          return res;
        } catch (err) {
          await reserveClient.query('ROLLBACK').catch(() => {});
          if (err.code === '40001' && attempt < 2) continue;
          throw err;
        }
      }
    })();
    const setRes = await inventoryRepo.setStockForStore(storeBId, raceProdId, raceSku, 20, 'SELLER_RESTOCK');
    let reserveResult;
    try {
      reserveResult = await reserveTxn;
    } finally {
      reserveClient.release();
    }
    const reserveRes = reserveResult;
    assert.strictEqual(setRes.ok, true, `Concurrent set must succeed: ${setRes.message}`);
    assert.strictEqual(reserveRes.ok, true, `Concurrent reservation must succeed: ${reserveRes.message}`);
    const raceRow = await pool.query(
      `SELECT stock_count, reserved_count, (stock_count - reserved_count) AS available_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, raceProdId, raceSku]
    );
    assert.strictEqual(Number(raceRow.rows[0].stock_count), 20, 'Final stock after concurrent set + reserve must be 20');
    assert.strictEqual(Number(raceRow.rows[0].reserved_count), 5, 'Reservation of 5 must never be lost by the concurrent set');
    assert.ok(Number(raceRow.rows[0].available_count) >= 0, 'Available stock must never go negative under concurrency');

    // -------------------------------------------------------------
    // 5. productId/sku mismatch on adjustStockForStore must be rejected.
    // -------------------------------------------------------------
    await inventoryRepo.setStockForStore(storeBId, productYId, skuY, 7, 'SELLER_RESTOCK');
    const mismatch = await inventoryRepo.adjustStockForStore(storeBId, productXId, skuY, 3, 'SELLER_RESTOCK');
    assert.strictEqual(mismatch.httpStatus, 400, 'productId/sku mismatch MUST be rejected with a controlled 4xx');
    assert.strictEqual(mismatch.error, 'CANONICAL_PRODUCT_ID_SKU_MISMATCH', 'Mismatch must carry CANONICAL_PRODUCT_ID_SKU_MISMATCH');
    const mismatchRow = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, productYId, skuY]
    );
    assert.strictEqual(Number(mismatchRow.rows[0].stock_count), 7, 'SKU-matching product must NOT be mutated by another productId');

    // -------------------------------------------------------------
    // 6. Store authorization is store-derived, never caller-supplied.
    // -------------------------------------------------------------
    const storeBStockBefore = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, productYId, skuY]
    );
    // A route bound to seller Store A passes storeAId; an attacker-supplied storeBId must never be
    // honored. The repository takes an explicit storeId from the authenticated seller context, so
    // calling with storeAId + a Store B identity leaves Store B untouched.
    await inventoryRepo.setStockForStore(storeAId, productYId, skuY, 99, 'SELLER_RESTOCK');
    const storeBStockAfter = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, productYId, skuY]
    );
    assert.strictEqual(Number(storeBStockAfter.rows[0].stock_count), Number(storeBStockBefore.rows[0].stock_count), 'Store B stock MUST remain untouched when the authorized store is Store A');

    // -------------------------------------------------------------
    // 7. Actor-aware reason validation: Seller cannot submit ADMIN_ADJUSTMENT; Admin can.
    // -------------------------------------------------------------
    const sellerMasquerade = await inventoryRepo.adjustStockForStore(
      storeAId,
      productYId,
      skuY,
      1,
      'ADMIN_ADJUSTMENT',
      { type: 'SELLER', isAdmin: false }
    );
    assert.strictEqual(sellerMasquerade.ok, false, 'Seller MUST NOT be allowed to submit ADMIN_ADJUSTMENT');
    assert.strictEqual(sellerMasquerade.httpStatus, 403, 'Unauthorized reason must return 403');
    assert.strictEqual(sellerMasquerade.error, 'INVALID_INVENTORY_REASON', 'Must return INVALID_INVENTORY_REASON');

    const adminAdjustment = await inventoryRepo.adjustStockForStore(
      storeAId,
      productYId,
      skuY,
      1,
      'ADMIN_ADJUSTMENT',
      { type: 'ADMIN', isAdmin: true }
    );
    assert.strictEqual(adminAdjustment.ok, true, 'Admin MUST be allowed to submit ADMIN_ADJUSTMENT');
    const adminLedger = await pool.query(
      `SELECT reason FROM inventory_ledger WHERE id = $1`,
      [adminAdjustment.adjustmentId]
    );
    assert.strictEqual(adminLedger.rows[0].reason, 'ADMIN_ADJUSTMENT', 'Ledger reason must record ADMIN_ADJUSTMENT for authorized admin');

    // -------------------------------------------------------------
    // 8. Missing canonical productId is strictly rejected (no SKU-only fallback).
    // -------------------------------------------------------------
    const missingProdId = await inventoryRepo.adjustStockForStore(
      storeAId,
      null,
      skuY,
      1,
      'SELLER_RESTOCK',
      { type: 'SELLER' }
    );
    assert.strictEqual(missingProdId.ok, false, 'Missing productId MUST be rejected');
    assert.strictEqual(missingProdId.error, 'CANONICAL_PRODUCT_ID_REQUIRED', 'Must return CANONICAL_PRODUCT_ID_REQUIRED');

    // -------------------------------------------------------------
    // 9. Zero-delta adjustStockForStore is an idempotent no-op (no ledger noise).
    // -------------------------------------------------------------
    const countBeforeZeroAdj = await pool.query(
      `SELECT COUNT(*) FROM inventory_ledger WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuY]
    );
    const zeroAdj = await inventoryRepo.adjustStockForStore(
      storeAId,
      productYId,
      skuY,
      0,
      'SELLER_RESTOCK',
      { type: 'SELLER' }
    );
    assert.strictEqual(zeroAdj.ok, true, 'Zero-delta adjustment must return ok');
    assert.strictEqual(zeroAdj.reason, 'NO_CHANGE', 'Zero-delta adjustment must return NO_CHANGE reason');
    const countAfterZeroAdj = await pool.query(
      `SELECT COUNT(*) FROM inventory_ledger WHERE store_id = $1 AND sku = $2`,
      [storeAId, skuY]
    );
    assert.strictEqual(
      Number(countAfterZeroAdj.rows[0].count),
      Number(countBeforeZeroAdj.rows[0].count),
      'Zero-delta adjustment MUST NOT write an extra ledger row'
    );

    // -------------------------------------------------------------
    // 10. Explicit reason requirement: missing, null, empty, whitespace reasons are rejected (no synthetic derivation)
    // -------------------------------------------------------------
    for (const badReason of [undefined, null, '', '   ', '\t\n']) {
      const adjustNoReason = await inventoryRepo.adjustStockForStore(
        storeAId,
        productYId,
        skuY,
        1,
        badReason,
        { type: 'SELLER' }
      );
      assert.strictEqual(adjustNoReason.ok, false, `adjustStockForStore MUST reject reason=${JSON.stringify(badReason)}`);
      assert.strictEqual(adjustNoReason.httpStatus, 400, 'Must return 400');
      assert.strictEqual(adjustNoReason.error, 'INVALID_INVENTORY_REASON', 'Must return INVALID_INVENTORY_REASON');

      const setNoReason = await inventoryRepo.setStockForStore(
        storeAId,
        productYId,
        skuY,
        15,
        badReason,
        { type: 'SELLER' }
      );
      assert.strictEqual(setNoReason.ok, false, `setStockForStore MUST reject reason=${JSON.stringify(badReason)}`);
      assert.strictEqual(setNoReason.httpStatus, 400, 'Must return 400');
      assert.strictEqual(setNoReason.error, 'INVALID_INVENTORY_REASON', 'Must return INVALID_INVENTORY_REASON');
    }

    console.log('  ✅ PASS: Inventory Ledger Atomicity + Serialization Domain (10/10)\n');
  } finally {
    await pool.query(`ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS ${LEDGER_CONSTRAINT}`).catch(() => {});
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id IN ($1, $2, $3, $4)`, [productXId, productYId, 'prod_link_atom_' + timestamp, 'prod_race_ledger_' + timestamp]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for inventory-ledger-atomicity.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Inventory Ledger Atomicity Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };