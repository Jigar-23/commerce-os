const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

const ROOT_DIR = path.resolve(__dirname, '..');
const envPath = path.join(ROOT_DIR, '.env');
const dbJsonPath = path.join(ROOT_DIR, 'platform/db.json');

let dbUrl = '';
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');
  for (const line of envContent.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed.startsWith('DATABASE_URL=')) {
      dbUrl = trimmed.slice('DATABASE_URL='.length).trim();
      if ((dbUrl.startsWith('"') && dbUrl.endsWith('"')) || (dbUrl.startsWith("'") && dbUrl.endsWith("'"))) {
        dbUrl = dbUrl.slice(1, -1);
      }
    }
  }
}

const NEW_LAT = 32.405605;
const NEW_LNG = 75.491600;

async function run() {
  console.log(`Connecting to Postgres...`);
  const pool = new Pool({
    connectionString: dbUrl,
    ssl: { rejectUnauthorized: false }
  });

  try {
    // 1. Fetch primary store details
    const primRes = await pool.query(
      `SELECT * FROM stores WHERE id = 'store_rewari_hub_01' OR is_active = TRUE ORDER BY created_at ASC LIMIT 1`
    );
    const prim = primRes.rows[0];
    console.log('Found primary store template:', prim);

    const storeId = 'store_hub_02';
    const storeName = prim.store_name ? prim.store_name.replace('Rewari Central', 'Second').replace('Hub', 'Hub 2') : 'Commerce OS Second Fulfillment Hub';
    const storeAddress = prim.address || 'Store 2 Logistics Corridor';
    const slaMinutes = prim.sla_minutes || 10;
    const sellerApproval = prim.seller_approval_required !== undefined ? prim.seller_approval_required : true;
    const serviceRadius = prim.service_radius_km || 10.0;

    // 2. Insert Store 2 with all same parameters but new coordinates
    const insertRes = await pool.query(
      `INSERT INTO stores (
        id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active, service_radius_km, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, TRUE, $8, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        store_name = EXCLUDED.store_name,
        address = EXCLUDED.address,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        sla_minutes = EXCLUDED.sla_minutes,
        seller_approval_required = EXCLUDED.seller_approval_required,
        is_active = TRUE,
        service_radius_km = EXCLUDED.service_radius_km,
        updated_at = NOW()
      RETURNING *`,
      [storeId, storeName, storeAddress, NEW_LAT, NEW_LNG, slaMinutes, sellerApproval, serviceRadius]
    );
    console.log('✅ Store 2 created/updated in Postgres:', insertRes.rows[0]);

    // Also support STORE_02 ID alias if queried by uppercase convention
    await pool.query(
      `INSERT INTO stores (
        id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active, service_radius_km, created_at, updated_at
      ) VALUES ('STORE_02', $1, $2, $3, $4, $5, $6, TRUE, $7, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        store_name = EXCLUDED.store_name,
        address = EXCLUDED.address,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        sla_minutes = EXCLUDED.sla_minutes,
        seller_approval_required = EXCLUDED.seller_approval_required,
        is_active = TRUE,
        service_radius_km = EXCLUDED.service_radius_km,
        updated_at = NOW()`,
      [storeName, storeAddress, NEW_LAT, NEW_LNG, slaMinutes, sellerApproval, serviceRadius]
    );

    // 3. Clone all inventory from primary store to store 2
    const invCount = await pool.query(
      `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
       SELECT
         'inv_' || substr(md5(random()::text || i.sku || 'store_hub_02'), 1, 24),
         'store_hub_02',
         i.product_id,
         i.sku,
         i.product_name,
         i.stock_count,
         0,
         i.min_threshold,
         NOW()
       FROM inventory i
       WHERE i.store_id = $1
       ON CONFLICT DO NOTHING`,
      [prim.id]
    );
    console.log(`✅ Cloned inventory to store_hub_02: ${invCount.rowCount} items.`);

    // Also clone for STORE_02 alias
    await pool.query(
      `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
       SELECT
         'inv_' || substr(md5(random()::text || i.sku || 'STORE_02'), 1, 24),
         'STORE_02',
         i.product_id,
         i.sku,
         i.product_name,
         i.stock_count,
         0,
         i.min_threshold,
         NOW()
       FROM inventory i
       WHERE i.store_id = $1
       ON CONFLICT DO NOTHING`,
      [prim.id]
    );

    // 4. Update platform/db.json with identical structure
    if (fs.existsSync(dbJsonPath)) {
      const db = JSON.parse(fs.readFileSync(dbJsonPath, 'utf8'));
      db.stores = db.stores || [];
      const store1 = db.stores[0] || {};
      const newStoreJson = {
        ...store1,
        id: 'store_hub_02',
        storeId: 'store_hub_02',
        name: storeName,
        storeName: storeName,
        latitude: NEW_LAT,
        longitude: NEW_LNG,
        status: 'ACTIVE'
      };
      
      const existingIdx = db.stores.findIndex(s => s.id === 'store_hub_02');
      if (existingIdx >= 0) {
        db.stores[existingIdx] = newStoreJson;
      } else {
        db.stores.push(newStoreJson);
      }
      fs.writeFileSync(dbJsonPath, JSON.stringify(db, null, 2), 'utf8');
      console.log('✅ platform/db.json updated with identical store object at new coords.');
    }

    console.log('🎉 Successfully added second store at 32.405605, 75.491600 with all identical configurations!');
  } catch (err) {
    console.error('Error:', err);
  } finally {
    await pool.end();
  }
}

run();
