/**
 * Commerce OS — Live PostgreSQL Global Catalog + Store-Scoped Inventory Seller & Serviceability Contract
 *
 * Permanent guard for the FINALIZED Commerce OS catalog model:
 *   products  = global catalog identity (globally unique SKU, product-level metadata)
 *   inventory = store-scoped availability (store_id, product_id, sku) via composite FK
 *
 * Verifies with real PostgreSQL:
 * 1. getStoreInventory(Store A) displays the REAL global product metadata (name/price/mrp/category)
 *    for a store-scoped inventory row — NOT the sku/0/General fallback produced by the legacy
 *    `i.sku = p.sku AND i.store_id = p.store_id` join.
 * 2. getActiveProducts(storeId) derives the store product list through inventory joined to the
 *    global catalog; Store A sees only Product X, Store B sees only Product Y, with zero duplicate
 *    product rows and zero cross-store leakage.
 * 3. Serviceability resolves the GLOBAL product first and then verifies the EXACT store inventory
 *    row via (store_id, product_id, sku); when the preferred store lacks available stock, the
 *    server selects the alternate serviceable store.
 * 4. adjustStockForStore resolves the canonical product identity through the global catalog by SKU.
 * 5. One global SKU is never duplicated per store.
 */

const assert = require('assert');
const { Pool } = require('pg');
const {
  TransactionalCatalogRepository,
  TransactionalInventoryRepository,
  ServiceabilityService
} = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Global Catalog + Store-Scoped Inventory Seller & Serviceability Contract...');

  const timestamp = Date.now();
  const storeAId = 'store_gcat_a_' + timestamp;
  const storeBId = 'store_gcat_b_' + timestamp;
  const sellerAId = 'seller_gcat_a_' + timestamp;
  const productXId = 'prod_gcat_x_' + timestamp;
  const productYId = 'prod_gcat_y_' + timestamp;
  const skuX = 'SKU_GLOBAL_X_' + timestamp;
  const skuY = 'SKU_GLOBAL_Y_' + timestamp;

  const catalogRepo = new TransactionalCatalogRepository(pool);
  const inventoryRepo = new TransactionalInventoryRepository(pool);

  try {
    // 1. Seed Stores (both inside the 20km serviceable radius of the test customer)
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES 
       ($1, 'Global Catalog Store A', 'Sector 29, Gurugram', 28.4680, 77.0600, 10, TRUE),
       ($2, 'Global Catalog Store B', 'Sector 48, Gurugram', 28.4595, 77.0400, 15, TRUE)`,
      [storeAId, storeBId]
    );

    // Seed an ACTIVE seller for Store A (verifies sellers never hold GLOBAL_CATALOG_WRITE)
    await pool.query(
      `INSERT INTO sellers (id, seller_id, merchant_name, email, phone, password_hash, store_id, is_primary, status)
       VALUES ($1, $1, 'Global Catalog Seller A', $4, $5, $2, $3, TRUE, 'ACTIVE')`,
      [sellerAId, 'hash_placeholder_gcat_seller', storeAId, `gcat_seller_${timestamp}@hub.com`, '+9198' + String(timestamp).slice(-8)]
    );

    // 2. Seed TWO GLOBAL products with globally unique SKUs (zero store scoping on products)
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES 
       ($1, $2, 'Product X', 'BrandX', 120.00, 100.00, 'Medicine', '10 Tablets', 'OTC', TRUE),
       ($3, $4, 'Product Y', 'BrandY', 220.00, 200.00, 'Nutrition', '1 Bottle', 'OTC', TRUE)`,
      [productXId, skuX, productYId, skuY]
    );

    // 3. Seed STORE-SCOPED inventory (canonical triple): Store A stocks X only, Store B stocks Y only
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, product_name, stock_count, reserved_count)
       VALUES 
       ($1, $2, $3, 'Product X', 10, 0),
       ($4, $5, $6, 'Product Y', 20, 0)`,
      [storeAId, productXId, skuX, storeBId, productYId, skuY]
    );

    // -------------------------------------------------------------
    // Scenario 1: Seller inventory join must surface GLOBAL product data (not the sku/0 fallback)
    // -------------------------------------------------------------
    const invStoreA = await inventoryRepo.getStoreInventory(storeAId);
    assert.ok(invStoreA.length >= 1, 'Store A inventory must contain its stock rows');
    const rowX = invStoreA.find(r => r.sku === skuX);
    assert.ok(rowX, 'Store A inventory must contain the canonical SKU_X row');
    assert.strictEqual(rowX.name, 'Product X', `getStoreInventory must return the real product name, got '${rowX.name}'`);
    assert.strictEqual(Number(rowX.price), 100, `getStoreInventory must return the real global price, got ${rowX.price}`);
    assert.strictEqual(Number(rowX.mrp), 120, `getStoreInventory must return the real global MRP, got ${rowX.mrp}`);
    assert.strictEqual(rowX.category, 'Medicine', `getStoreInventory must return the real category, got '${rowX.category}'`);
    assert.strictEqual(rowX.packSize, '10 Tablets', `getStoreInventory must return the real pack size, got '${rowX.packSize}'`);
    assert.strictEqual(rowX.productId, productXId, 'getStoreInventory must expose the canonical product id');
    assert.strictEqual(Number(rowX.onHand), 10, 'Store A on-hand must be 10');
    assert.strictEqual(Number(rowX.available), 10, 'Store A available must be 10');
    assert.ok(!invStoreA.some(r => r.sku === skuY), 'Store A inventory must NOT leak Store B product Y');

    const invStoreB = await inventoryRepo.getStoreInventory(storeBId);
    const rowY = invStoreB.find(r => r.sku === skuY);
    assert.ok(rowY, 'Store B inventory must contain the canonical SKU_Y row');
    assert.strictEqual(rowY.name, 'Product Y', `getStoreInventory must return real product data for Store B, got '${rowY.name}'`);
    assert.ok(!invStoreB.some(r => r.sku === skuX), 'Store B inventory must NOT leak Store A product X');

    // -------------------------------------------------------------
    // Scenario 2: Store-scoped seller product list derived through inventory, zero duplication/leakage
    // -------------------------------------------------------------
    const productsA = await catalogRepo.getActiveProducts(storeAId);
    assert.ok(productsA.length >= 1, 'Store A product list must be non-empty');
    const prodAIds = productsA.filter(p => p.sku === skuX || p.sku === skuY);
    assert.strictEqual(prodAIds.length, 1, 'Store A must list exactly ONE global product (X)');
    assert.strictEqual(prodAIds[0].sku, skuX, 'Store A product list must contain only Product X');
    assert.strictEqual(Number(prodAIds[0].price), 100, 'Store A product list must carry real global price for X');
    assert.strictEqual(Number(prodAIds[0].available_count), 10, 'Store A product list must carry store-scoped availability');

    const productsB = await catalogRepo.getActiveProducts(storeBId);
    const prodBIds = productsB.filter(p => p.sku === skuX || p.sku === skuY);
    assert.strictEqual(prodBIds.length, 1, 'Store B must list exactly ONE global product (Y)');
    assert.strictEqual(prodBIds[0].sku, skuY, 'Store B product list must contain only Product Y');
    assert.ok(!prodBIds.some(p => p.sku === skuX), 'Store B must have zero cross-store leakage of Product X');

    // No duplicated global product rows: exactly one 'Product X' and one 'Product Y' in catalog
    const dupCheck = await pool.query(
      `SELECT COUNT(*) as count FROM products WHERE id IN ($1, $2)`,
      [productXId, productYId]
    );
    assert.strictEqual(Number(dupCheck.rows[0].count), 2, 'Exactly two global product rows exist (no duplication per store)');

    // -------------------------------------------------------------
    // Scenario 3: Serviceability resolves GLOBAL product first, then EXACT store inventory (store_id, product_id, sku)
    //             Preferred Store A is out of stock (0 available) -> server must select Store B.
    // -------------------------------------------------------------
    await pool.query(
      `UPDATE inventory SET stock_count = 0 WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, productXId, skuX]
    );
    await pool.query(
      `INSERT INTO inventory (store_id, product_id, sku, product_name, stock_count, reserved_count)
       VALUES ($1, $2, $3, 'Serviceability Product X', 5, 0)`,
      [storeBId, productXId, skuX]
    );

    const decision = await ServiceabilityService.resolveAuthoritativeFulfillmentStore({
      address: { latitude: 28.4610, longitude: 77.0310 },
      items: [{ sku: skuX, quantity: 1 }],
      preferredStoreId: storeAId,
      pool
    });

    assert.strictEqual(decision.ok, true, `Serviceability must succeed: ${decision.message}`);
    assert.strictEqual(decision.storeId, storeBId, 'Server must select Store B when preferred Store A lacks available inventory');

    // -------------------------------------------------------------
    // Scenario 4: adjustStockForStore validates canonical productId and updates store inventory
    // -------------------------------------------------------------
    const adjustRes = await inventoryRepo.adjustStockForStore(storeBId, productXId, skuX, 3, 'SELLER_RESTOCK');
    assert.strictEqual(adjustRes.ok, true, `adjustStockForStore must succeed: ${adjustRes.message}`);
    const adjustCheck = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, productXId, skuX]
    );
    assert.strictEqual(Number(adjustCheck.rows[0].stock_count), 8, 'Store B X stock must become 8 after +3 adjustment');

    // Scenario 4b: DB-backed GLOBAL_CATALOG_WRITE authority (catalog_admins membership, NOT JWT role)
    await pool.query(
      `INSERT INTO catalog_admins (id, operator_id, email, full_name, permissions, status)
       VALUES ($1, $1, $3, 'Catalog Operator', $2, 'ACTIVE')`,
      ['catop_gcat_' + timestamp, JSON.stringify(['GLOBAL_CATALOG_WRITE']), `cat_op_gcat_${timestamp}@hub.com`]
    );
    assert.strictEqual(await catalogRepo.hasCatalogWriteAuth('catop_gcat_' + timestamp), true, 'Active catalog_admins member must hold GLOBAL_CATALOG_WRITE');
    assert.strictEqual(await catalogRepo.hasCatalogWriteAuth(sellerAId), false, 'A normal seller must NOT hold GLOBAL_CATALOG_WRITE (DB-backed, not role claim)');
    assert.strictEqual(await catalogRepo.hasCatalogWriteAuth('unknown_sub_' + timestamp), false, 'Unknown JWT.sub must never hold catalog write');

    // -------------------------------------------------------------
    // Scenario 5: Global SKU is never duplicated under the finalized model
    // -------------------------------------------------------------
    let duplicateFailed = false;
    try {
      await pool.query(
        `INSERT INTO products (id, sku, name, brand_name, mrp, price, is_active)
         VALUES ($1, $2, 'Duplicate X Copy', 'BrandFake', 90.00, 80.00, TRUE)`,
        ['prod_gcat_dup_' + timestamp, skuX]
      );
    } catch (err) {
      duplicateFailed = true;
      assert.ok(err.code === '23505', `Expected products.sku unique violation, got: ${err.message}`);
    }
    assert.strictEqual(duplicateFailed, true, 'PostgreSQL must reject a duplicate global SKU (no per-store product duplication)');

    // -------------------------------------------------------------
    // Scenario 6: setStockForStore — the authoritative ABSOLUTE-stock path used by seller catalog
    //             links. Must write a ledger entry, reject stock below active reservations, and
    //             survive concurrent first-time links without surfacing a 500.
    // -------------------------------------------------------------
    const linkedSku = 'SKU_LINK_' + timestamp;
    const linkedProdId = 'prod_link_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Linked Global Product', 'BrandLink', 100.00, 90.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [linkedProdId, linkedSku]
    );

    // 6a. First-time link creates the store row AND a delta==stock ledger entry.
    const linkA = await inventoryRepo.setStockForStore(storeAId, linkedProdId, linkedSku, 25, 'SELLER_RESTOCK');
    assert.strictEqual(linkA.ok, true, `setStockForStore first-time link must succeed: ${linkA.message}`);
    assert.strictEqual(Number(linkA.delta), 25, 'First-time link must record delta == 25');
    assert.strictEqual(Number(linkA.newStock), 25, 'First-time link must record newStock == 25');
    const linkCheckA = await pool.query(
      `SELECT stock_count, reserved_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, linkedProdId, linkedSku]
    );
    assert.strictEqual(Number(linkCheckA.rows[0].stock_count), 25, 'Store A linked stock must be 25');
    const linkLedgerA = await pool.query(
      `SELECT delta, new_stock, reason FROM inventory_ledger WHERE store_id = $1 AND sku = $2`,
      [storeAId, linkedSku]
    );
    assert.ok(linkLedgerA.rows.length >= 1, 'setStockForStore MUST write inventory_ledger');
    assert.strictEqual(Number(linkLedgerA.rows[0].delta), 25, 'Ledger delta must equal 25 for first-time link');
    assert.strictEqual(linkLedgerA.rows[0].reason, 'SELLER_RESTOCK', 'Ledger must carry the explicit reason');

    // 6b. Cross-store isolation: linking the same SKU in Store B must not affect Store A.
    const linkB = await inventoryRepo.setStockForStore(storeBId, linkedProdId, linkedSku, 40, 'SELLER_RESTOCK');
    assert.strictEqual(linkB.ok, true, `setStockForStore cross-store link must succeed: ${linkB.message}`);
    const storeAUntouched = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, linkedProdId, linkedSku]
    );
    assert.strictEqual(Number(storeAUntouched.rows[0].stock_count), 25, 'Store A stock must remain 25 after Store B link');

    // 6c. Lowering stock below active reservations must be rejected (INSUFFICIENT_STOCK).
    await pool.query(
      `UPDATE inventory SET reserved_count = 15 WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, linkedProdId, linkedSku]
    );
    const belowRes = await inventoryRepo.setStockForStore(storeAId, linkedProdId, linkedSku, 10, 'SELLER_ADJUSTMENT');
    assert.strictEqual(belowRes.ok, false, 'Lowering stock below active reservations must fail');
    assert.strictEqual(belowRes.httpStatus, 409, 'Must return 409 INSUFFICIENT_STOCK');
    assert.strictEqual(belowRes.error, 'INSUFFICIENT_STOCK');

    // 6d. Raising stock above the reservation succeeds via a delta-based update + new ledger entry.
    await pool.query(
      `UPDATE inventory SET reserved_count = 0 WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeAId, linkedProdId, linkedSku]
    );
    const raiseRes = await inventoryRepo.setStockForStore(storeAId, linkedProdId, linkedSku, 30, 'SELLER_RESTOCK');
    assert.strictEqual(raiseRes.ok, true, `Raising stock above reservation must succeed: ${raiseRes.message}`);
    assert.strictEqual(Number(raiseRes.delta), 5, 'Raise ledger must record delta == +5');
    assert.strictEqual(Number(raiseRes.newStock), 30, 'Raise ledger must record newStock == 30');
    const raiseLedger = await pool.query(
      `SELECT delta, new_stock FROM inventory_ledger WHERE store_id = $1 AND sku = $2 ORDER BY created_at ASC`,
      [storeAId, linkedSku]
    );
    assert.strictEqual(Number(raiseLedger.rows[1].delta), 5, 'Second ledger entry must record delta == +5');
    assert.strictEqual(Number(raiseLedger.rows[1].new_stock), 30, 'Second ledger entry must record new_stock == 30');

    // 6e. Concurrent first-time link race: two simultaneous setStockForStore calls on the SAME
    //     (store_id, sku) triple must both resolve (retried, not 500), and the final stock must be
    //     one of the two requested absolute values (never a torn write).
    const raceSku = 'SKU_RACE_' + timestamp;
    const raceProdId = 'prod_race_' + timestamp;
    await pool.query(
      `INSERT INTO products (id, sku, name, brand_name, mrp, price, category, pack_size, rx_requirement, is_active)
       VALUES ($1, $2, 'Race Global Product', 'BrandRace', 100.00, 90.00, 'Medicine', '1 Strip', 'OTC', TRUE)`,
      [raceProdId, raceSku]
    );
    const [raceRes1, raceRes2] = await Promise.all([
      inventoryRepo.setStockForStore(storeBId, raceProdId, raceSku, 11, 'SELLER_RESTOCK'),
      inventoryRepo.setStockForStore(storeBId, raceProdId, raceSku, 22, 'SELLER_RESTOCK')
    ]);
    assert.strictEqual(raceRes1.ok, true, `Race attempt 1 must resolve, got: ${raceRes1.message}`);
    assert.strictEqual(raceRes2.ok, true, `Race attempt 2 must resolve, got: ${raceRes2.message}`);
    const raceRow = await pool.query(
      `SELECT stock_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
      [storeBId, raceProdId, raceSku]
    );
    assert.ok([11, 22].includes(Number(raceRow.rows[0].stock_count)), 'Final raced stock must be one of the two requested absolute values');

    console.log('  ✅ PASS: Global Catalog + Store-Scoped Inventory Seller & Serviceability Contract\n');
  } finally {
    await pool.query(`DELETE FROM catalog_admins WHERE id = $1`, ['catop_gcat_' + timestamp]);
    await pool.query(`DELETE FROM inventory_ledger WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM inventory WHERE store_id IN ($1, $2)`, [storeAId, storeBId]);
    await pool.query(`DELETE FROM products WHERE id IN ($1, $2, $3, $4, $5)`, [productXId, productYId, 'prod_gcat_dup_' + timestamp, 'prod_link_' + timestamp, 'prod_race_' + timestamp]);
    await pool.query(`DELETE FROM sellers WHERE id = $1`, [sellerAId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [storeAId, storeBId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for seller-catalog-global-product.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Seller Catalog Global Product Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };