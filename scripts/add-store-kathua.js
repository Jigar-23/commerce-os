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

if (!dbUrl) {
  console.error('DATABASE_URL not found in .env');
  process.exit(1);
}

const STORE_ID = 'STORE_KATHUA_02';
const STORE_NAME = 'Commerce OS Kathua Fulfillment Store';
const STORE_ADDRESS = 'Main Market, College Road, Kathua, Jammu and Kashmir 184104';
const STORE_LAT = 32.405605;
const STORE_LNG = 75.491600;
const SLA_MINUTES = 10;
const SERVICE_RADIUS_KM = 15.0;

async function main() {
  console.log(`🚀 [1/3] Connecting to PostgreSQL to register second store: ${STORE_ID}...`);
  const pool = new Pool({
    connectionString: dbUrl,
    ssl: { rejectUnauthorized: false }
  });

  try {
    // 1. Insert or update the store in stores table
    const storeRes = await pool.query(
      `INSERT INTO stores (
        id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active, service_radius_km, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, FALSE, TRUE, $7, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        store_name = EXCLUDED.store_name,
        address = EXCLUDED.address,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        sla_minutes = EXCLUDED.sla_minutes,
        is_active = TRUE,
        service_radius_km = EXCLUDED.service_radius_km,
        updated_at = NOW()
      RETURNING *`,
      [STORE_ID, STORE_NAME, STORE_ADDRESS, STORE_LAT, STORE_LNG, SLA_MINUTES, SERVICE_RADIUS_KM]
    );
    console.log('✅ Store created / updated in PostgreSQL:', storeRes.rows[0]);

    // 2. Ensure seller account exists for Kathua Store
    const sellerRes = await pool.query(
      `INSERT INTO sellers (
        id, seller_id, email, phone, password_hash, store_id, store_name, merchant_name, is_primary, roles, status, created_at, updated_at
      ) VALUES (
        'seller_kathua_02',
        'seller_kathua_02',
        'seller.kathua@commerceos.io',
        '+919817916181',
        'scrypt:f705407c1293100fc553e87a1a7a137f:10c4ed876ff42cad1b58cdf9206b0514121037577d5e8d35080046829b021844317dd002129556dca8b5a54651477c0da68a649ace0855ec59d56c19feb0ad4b',
        $1, $2, 'Kathua Primary Merchant', TRUE, ARRAY['ROLE_SELLER'], 'ACTIVE', NOW(), NOW()
      ) ON CONFLICT (id) DO UPDATE SET
        store_id = EXCLUDED.store_id,
        store_name = EXCLUDED.store_name,
        updated_at = NOW()
      RETURNING id, seller_id, store_id, store_name`,
      [STORE_ID, STORE_NAME]
    );
    console.log('✅ Seller account registered:', sellerRes.rows[0]);

    // 3. Populate inventory for Kathua Store
    const invRes = await pool.query(
      `INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, available_count, min_threshold, updated_at)
       SELECT
         'inv_' || substr(md5(random()::text || p.sku || '${STORE_ID}'), 1, 24),
         $1,
         p.id,
         p.sku,
         p.name,
         100,
         0,
         100,
         5,
         NOW()
       FROM products p
       WHERE NOT EXISTS (
         SELECT 1 FROM inventory i WHERE i.store_id = $1 AND i.sku = p.sku
       )`,
      [STORE_ID]
    );
    console.log(`✅ Seeded inventory items for ${STORE_ID}: ${invRes.rowCount} rows added.`);

    // 4. Update platform/db.json
    console.log('📁 [2/3] Updating platform/db.json...');
    if (fs.existsSync(dbJsonPath)) {
      const db = JSON.parse(fs.readFileSync(dbJsonPath, 'utf8'));
      db.stores = db.stores || [];
      const idx = db.stores.findIndex(s => s.id === STORE_ID || s.storeId === STORE_ID);
      const storeObj = {
        id: STORE_ID,
        storeId: STORE_ID,
        name: STORE_NAME,
        storeName: STORE_NAME,
        merchantName: 'Kathua Primary Merchant',
        address: STORE_ADDRESS,
        streetAddress: 'Main Market, College Road',
        city: 'Kathua',
        state: 'Jammu and Kashmir',
        pincode: '184104',
        latitude: STORE_LAT,
        longitude: STORE_LNG,
        status: 'ACTIVE',
        sellerApprovalRequired: false,
        slaMinutes: SLA_MINUTES,
        serviceRadiusKm: SERVICE_RADIUS_KM
      };
      if (idx >= 0) {
        db.stores[idx] = storeObj;
      } else {
        db.stores.push(storeObj);
      }
      fs.writeFileSync(dbJsonPath, JSON.stringify(db, null, 2), 'utf8');
      console.log('✅ platform/db.json successfully updated.');
    }

    console.log('🎉 [3/3] Second Store (Kathua) is LIVE and verified!');
  } catch (err) {
    console.error('❌ Error pushing store:', err);
  } finally {
    await pool.end();
  }
}

main();
