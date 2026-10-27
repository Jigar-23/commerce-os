#!/usr/bin/env node

/**
 * Commerce OS — Turnkey Database Tenant Seeder
 * 
 * Given a client configuration profile (e.g. clients/grocery-template.json),
 * connects to the target PostgreSQL database and seeds:
 * 1. Base relational schema (platform/schema.sql) if tables are not yet initialized
 * 2. Primary Dark Store / Fulfillment Hub record
 * 3. Primary Store Seller admin account with scrypt KDF hashed credentials
 * 4. Vertical catalog taxonomy and seed products (Grocery, Pharmacy, or Quick Commerce)
 * 5. Initial store inventory balances with available_count
 * 
 * Usage:
 *   node scripts/seed-tenant.js <path/to/client.json>
 *   pnpm seed:tenant clients/grocery-template.json
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Pool } = require('pg');

const ROOT_DIR = path.resolve(__dirname, '..');

const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m'
};

function logStep(step, title) {
  console.log(`\n${colors.cyan}[Step ${step}]${colors.reset} ${colors.bright}${title}${colors.reset}`);
}

function logSuccess(msg) {
  console.log(`  ${colors.green}✔ ${msg}${colors.reset}`);
}

function logError(msg) {
  console.log(`  ${colors.red}✖ ${msg}${colors.reset}`);
}

// Scrypt KDF password hashing
function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(password, salt, 64).toString('hex');
  return `scrypt:${salt}:${derived}`;
}

// Catalog seed definitions by vertical
const VERTICAL_CATALOGS = {
  grocery: [
    {
      id: 'prod_milk_whole_1l',
      sku: 'GROC-MILK-1L',
      name: 'Farm Fresh Organic Whole Milk',
      brand_name: 'Meadow Farms',
      pack_size: '1 Litre Pouch',
      mrp: 72.00,
      price: 68.00,
      discounted_price: 68.00,
      rx_requirement: 'OTC',
      cold_chain_required: true,
      category: 'Dairy & Breakfast',
      description: 'Pasteurized, homogenized fresh whole milk sourced from local farms daily.'
    },
    {
      id: 'prod_eggs_country_12pk',
      sku: 'GROC-EGGS-12PK',
      name: 'Free-Range Country Brown Eggs',
      brand_name: 'NutriFarm',
      pack_size: '12 Pieces Pack',
      mrp: 130.00,
      price: 110.00,
      discounted_price: 110.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Dairy & Breakfast',
      description: 'Farm-fresh free-range brown eggs rich in Omega-3 and natural protein.'
    },
    {
      id: 'prod_bread_sourdough_400g',
      sku: 'GROC-BREAD-400G',
      name: 'Handcrafted Artisanal Sourdough Bread',
      brand_name: 'The Baker Craft',
      pack_size: '400g Loaf',
      mrp: 95.00,
      price: 85.00,
      discounted_price: 85.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Bakery & Bread',
      description: 'Slow-fermented artisan sourdough bread with a crispy crust and soft airy crumb.'
    },
    {
      id: 'prod_mangoes_alphonso_1kg',
      sku: 'GROC-MANGO-1KG',
      name: 'Premium Ratnagiri Alphonso Mangoes',
      brand_name: 'Nature Select',
      pack_size: '1 kg Box (3-4 pcs)',
      mrp: 399.00,
      price: 340.00,
      discounted_price: 340.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Fresh Fruits',
      description: 'GI-tagged authentic Ratnagiri Alphonso mangoes, naturally ripened and sweet.'
    },
    {
      id: 'prod_butter_salted_500g',
      sku: 'GROC-BUTTER-500G',
      name: 'Pure Dairy Salted Table Butter',
      brand_name: 'Golden Creamery',
      pack_size: '500g Block',
      mrp: 290.00,
      price: 275.00,
      discounted_price: 275.00,
      rx_requirement: 'OTC',
      cold_chain_required: true,
      category: 'Dairy & Breakfast',
      description: 'Rich and creamy churned table butter prepared with fresh cream and a pinch of salt.'
    },
    {
      id: 'prod_rice_basmati_5kg',
      sku: 'GROC-RICE-5KG',
      name: 'Royal Aged Long-Grain Basmati Rice',
      brand_name: 'Heritage Select',
      pack_size: '5 kg Bag',
      mrp: 599.00,
      price: 499.00,
      discounted_price: 499.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Staples & Grains',
      description: 'Aged for 2 years, extra-long grain aromatic basmati rice for biryanis and daily meals.'
    }
  ],
  pharmacy: [
    {
      id: 'prod_paracetamol_650mg',
      sku: 'PHARM-PCM-650',
      name: 'Paracetamol 650mg Fast-Relief Tablets',
      brand_name: 'DoloCure',
      pack_size: 'Strip of 15 Tablets',
      mrp: 40.00,
      price: 35.00,
      discounted_price: 35.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Analgesics & Pain Relief',
      description: 'Fast-acting paracetamol for fever reduction, mild-to-moderate aches, and body pain.'
    },
    {
      id: 'prod_amoxicillin_500mg',
      sku: 'PHARM-AMOX-500',
      name: 'Amoxicillin 500mg Broad-Spectrum Capsules',
      brand_name: 'Moxipen',
      pack_size: 'Strip of 10 Capsules',
      mrp: 145.00,
      price: 120.00,
      discounted_price: 120.00,
      rx_requirement: 'RX_REQUIRED',
      cold_chain_required: false,
      category: 'Antibiotics',
      description: 'Prescription broad-spectrum penicillin antibiotic for bacterial infections.'
    },
    {
      id: 'prod_vitaminc_zinc_500',
      sku: 'PHARM-VITC-500',
      name: 'Vitamin C + Zinc Chewable Immune Booster',
      brand_name: 'ImmunoVita',
      pack_size: 'Bottle of 30 Chewable Tablets',
      mrp: 120.00,
      price: 95.00,
      discounted_price: 95.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Vitamins & Supplements',
      description: 'Antioxidant daily defense with 500mg Vitamin C and elemental Zinc for immune health.'
    },
    {
      id: 'prod_digital_thermometer',
      sku: 'PHARM-THERMO-DIG',
      name: 'Digital High-Precision Infrared Thermometer',
      brand_name: 'MediScan Pro',
      pack_size: '1 Device with Battery',
      mrp: 1200.00,
      price: 850.00,
      discounted_price: 850.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Medical Devices',
      description: 'Non-contact infrared body thermometer with 1-second temperature readout and LCD display.'
    },
    {
      id: 'prod_first_aid_kit_compact',
      sku: 'PHARM-FAK-COMPACT',
      name: 'Compact Travel & Home First Aid Kit',
      brand_name: 'SafeCare',
      pack_size: '42 Pieces Travel Pouch',
      mrp: 550.00,
      price: 450.00,
      discounted_price: 450.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'First Aid & Wound Care',
      description: 'Complete home emergency kit including antiseptic wipes, sterile gauze, bandages, and burn gel.'
    },
    {
      id: 'prod_insulin_glargine_100iu',
      sku: 'PHARM-INS-GLAR-100',
      name: 'Recombinant Human Insulin Glargine 100IU/ml',
      brand_name: 'LantuStar',
      pack_size: '3ml Cartridge',
      mrp: 750.00,
      price: 680.00,
      discounted_price: 680.00,
      rx_requirement: 'RX_REQUIRED',
      cold_chain_required: true,
      category: 'Diabetes Care',
      description: 'Long-acting basal insulin analog requiring uninterrupted 2-8°C cold chain transport.'
    }
  ],
  general: [
    {
      id: 'prod_electrolyte_energy_500ml',
      sku: 'QC-ELECTRO-500ML',
      name: 'Active Electrolyte Hydration Drink (Citrus)',
      brand_name: 'HydroFuel',
      pack_size: '500ml Bottle',
      mrp: 50.00,
      price: 45.00,
      discounted_price: 45.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Beverages',
      description: 'Fast cellular rehydration with potassium, magnesium, and essential electrolytes.'
    },
    {
      id: 'prod_roasted_nuts_250g',
      sku: 'QC-NUTS-250G',
      name: 'Dry-Roasted Almonds & Cashew Gourmet Mix',
      brand_name: 'NutriCrunch',
      pack_size: '250g Pouch',
      mrp: 350.00,
      price: 299.00,
      discounted_price: 299.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Snacks & Munchies',
      description: 'Lightly salted oven-roasted California almonds and whole Goan cashews.'
    },
    {
      id: 'prod_fast_cable_typec_1m',
      sku: 'QC-CABLE-TYPEC',
      name: 'Braided 65W Fast Charging USB-C to USB-C Cable',
      brand_name: 'VoltSpeed',
      pack_size: '1.5m Cable',
      mrp: 399.00,
      price: 249.00,
      discounted_price: 249.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Electronics & Accessories',
      description: 'Ultra-durable nylon braided fast charging cable compatible with all modern smartphones and laptops.'
    },
    {
      id: 'prod_pocket_tissues_6pk',
      sku: 'QC-TISSUE-6PK',
      name: 'Ultra-Soft 3-Ply Pocket Tissues (Pack of 6)',
      brand_name: 'PuffSoft',
      pack_size: '6 Pocket Packs',
      mrp: 75.00,
      price: 60.00,
      discounted_price: 60.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Daily Essentials',
      description: '100% virgin pulp hypoallergenic pocket facial tissues for on-the-go convenience.'
    },
    {
      id: 'prod_arabica_coffee_100g',
      sku: 'QC-COFFEE-100G',
      name: 'Pure Freeze-Dried Arabica Instant Coffee',
      brand_name: 'RoastMaster',
      pack_size: '100g Glass Jar',
      mrp: 220.00,
      price: 180.00,
      discounted_price: 180.00,
      rx_requirement: 'OTC',
      cold_chain_required: false,
      category: 'Beverages',
      description: 'Rich and aromatic premium freeze-dried coffee granules crafted from 100% Arabica beans.'
    }
  ]
};

async function seedTenant(configPath) {
  const resolvedPath = path.isAbsolute(configPath) ? configPath : path.resolve(ROOT_DIR, configPath);
  if (!fs.existsSync(resolvedPath)) {
    logError(`Config file not found: ${resolvedPath}`);
    process.exit(1);
  }

  const config = JSON.parse(fs.readFileSync(resolvedPath, 'utf8'));
  const dbUrl = process.env.DATABASE_URL || config.database?.databaseUrl;

  if (!dbUrl) {
    logError('Missing DATABASE_URL in client configuration or environment');
    process.exit(1);
  }

  console.log(`\n==============================================================================`);
  console.log(`  SEEDING DATABASE TENANT: ${config.clientName.toUpperCase()}`);
  console.log(`  Client ID: ${config.clientId} | Vertical: ${(config.vertical || 'general').toUpperCase()}`);
  console.log(`  Target Database: ${dbUrl.replace(/:[^:@]+@/, ':****@')}`);
  console.log(`==============================================================================`);

  const pool = new Pool({
    connectionString: dbUrl,
    ssl: dbUrl.includes('render.com') || dbUrl.includes('neon.tech') || dbUrl.includes('aws')
      ? { rejectUnauthorized: false }
      : false
  });

  const client = await pool.connect();

  try {
    // 1. Verify / Apply Base Schema
    logStep(1, 'Verifying & Applying Base Relational Schema');
    const tableCheck = await client.query(
      `SELECT to_regclass('public.stores') as stores_table, to_regclass('public.products') as products_table`
    );
    const hasTables = Boolean(tableCheck.rows[0]?.stores_table && tableCheck.rows[0]?.products_table);

    if (!hasTables) {
      const schemaPath = path.join(ROOT_DIR, 'platform/schema.sql');
      if (fs.existsSync(schemaPath)) {
        const schemaSql = fs.readFileSync(schemaPath, 'utf8');
        try {
          await client.query(schemaSql);
        } catch (schemaErr) {
          const recheck = await client.query(
            `SELECT to_regclass('public.stores') as stores_table`
          );
          if (!recheck.rows[0]?.stores_table) {
            throw schemaErr;
          }
        }
      } else {
        logError('platform/schema.sql not found');
        process.exit(1);
      }
    }
    // Ensure schema migrations on pre-existing databases
    await client.query(`
      ALTER TABLE products ADD COLUMN IF NOT EXISTS description TEXT;
      ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url TEXT;
      ALTER TABLE products ADD COLUMN IF NOT EXISTS images JSONB NOT NULL DEFAULT '[]'::jsonb;
      ALTER TABLE products ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(64);
      ALTER TABLE products ADD COLUMN IF NOT EXISTS manufacturing_date VARCHAR(64);
      ALTER TABLE products ADD COLUMN IF NOT EXISTS discounted_price NUMERIC(10, 2);
      ALTER TABLE products ADD COLUMN IF NOT EXISTS rx_requirement VARCHAR(32) NOT NULL DEFAULT 'OTC';
      ALTER TABLE products ADD COLUMN IF NOT EXISTS cold_chain_required BOOLEAN NOT NULL DEFAULT FALSE;
      ALTER TABLE products ADD COLUMN IF NOT EXISTS store_id VARCHAR(64);
    `);
    logSuccess('PostgreSQL authoritative schema verified and active');

    // 2. Seed Primary Dark Store / Fulfillment Hub
    logStep(2, 'Seeding Primary Dark Store / Hub');
    const storeId = config.merchant?.defaultStoreId || 'store_main_01';
    const storeName = config.merchant?.upiName || `${config.clientName} Central Fulfillment Hub`;
    const storeAddress = config.merchant?.address || 'Building 4, Central Logistics Corridor';
    const storeLat = config.merchant?.latitude || 28.4595;
    const storeLng = config.merchant?.longitude || 77.0266;
    const slaMinutes = config.merchant?.slaMinutes || 10;
    const serviceRadiusKm = config.merchant?.serviceRadiusKm || 10.0;

    await client.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, FALSE, TRUE, NOW())
       ON CONFLICT (id) DO UPDATE SET
         store_name = EXCLUDED.store_name,
         address = EXCLUDED.address,
         latitude = EXCLUDED.latitude,
         longitude = EXCLUDED.longitude,
         sla_minutes = EXCLUDED.sla_minutes,
         is_active = TRUE,
         updated_at = NOW()`,
      [storeId, storeName, storeAddress, storeLat, storeLng, slaMinutes]
    );
    logSuccess(`Primary fulfillment hub created/updated: ${storeId} ("${storeName}")`);

    // 3. Seed Primary Store Seller Account
    logStep(3, 'Seeding Primary Store Seller Account');
    const sellerId = `seller_${config.clientId.replace(/[^a-zA-Z0-9]/g, '_')}`;
    const sellerEmail = config.merchant?.supportEmail || `seller@${config.clientId}.com`;
    const sellerPhone = config.merchant?.supportPhone || '+1800000000';
    const defaultPassword = 'SellerSecurePass2026!';
    const passwordHash = hashPassword(defaultPassword);

    await client.query(
      `INSERT INTO sellers (id, seller_id, email, phone, password_hash, store_id, store_name, merchant_name, is_primary, roles, status, updated_at)
       VALUES ($1, $1, $2, $3, $4, $5, $6, $7, TRUE, '["ROLE_SELLER"]'::jsonb, 'ACTIVE', NOW())
       ON CONFLICT (id) DO UPDATE SET
         email = EXCLUDED.email,
         phone = EXCLUDED.phone,
         store_id = EXCLUDED.store_id,
         store_name = EXCLUDED.store_name,
         merchant_name = EXCLUDED.merchant_name,
         status = 'ACTIVE',
         updated_at = NOW()`,
      [sellerId, sellerEmail, sellerPhone, passwordHash, storeId, storeName, config.clientName]
    );
    logSuccess(`Primary seller initialized: ${sellerEmail} (store: ${storeId})`);

    // 4. Seed Vertical Catalog Taxonomy & Products
    logStep(4, 'Seeding Vertical Catalog Products & Initial Inventory');
    const verticalKey = config.vertical === 'pharmacy' ? 'pharmacy' : config.vertical === 'grocery' ? 'grocery' : 'general';
    const catalogItems = VERTICAL_CATALOGS[verticalKey] || VERTICAL_CATALOGS.general;

    for (const item of catalogItems) {
      // Upsert global catalog product
      await client.query(
        `INSERT INTO products (
           id, sku, name, brand_name, pack_size, mrp, price, discounted_price,
           rx_requirement, cold_chain_required, category, description, store_id, is_active, updated_at
         )
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, TRUE, NOW())
         ON CONFLICT (id) DO UPDATE SET
           sku = EXCLUDED.sku,
           name = EXCLUDED.name,
           brand_name = EXCLUDED.brand_name,
           pack_size = EXCLUDED.pack_size,
           mrp = EXCLUDED.mrp,
           price = EXCLUDED.price,
           discounted_price = EXCLUDED.discounted_price,
           rx_requirement = EXCLUDED.rx_requirement,
           cold_chain_required = EXCLUDED.cold_chain_required,
           category = EXCLUDED.category,
           description = EXCLUDED.description,
           store_id = EXCLUDED.store_id,
           is_active = TRUE,
           updated_at = NOW()`,
        [
          item.id,
          item.sku,
          item.name,
          item.brand_name,
          item.pack_size,
          item.mrp,
          item.price,
          item.discounted_price,
          item.rx_requirement,
          item.cold_chain_required,
          item.category,
          item.description,
          storeId
        ]
      );

      // Upsert store-scoped inventory record
      await client.query(
        `INSERT INTO inventory (store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
         VALUES ($1, $2, $3, $4, 100, 0, 5, NOW())
         ON CONFLICT (store_id, sku) DO UPDATE SET
           product_id = EXCLUDED.product_id,
           product_name = EXCLUDED.product_name,
           stock_count = 100,
           reserved_count = 0,
           updated_at = NOW()`,
        [storeId, item.id, item.sku, item.name]
      );
    }

    logSuccess(`Seeded ${catalogItems.length} authoritative catalog items and inventory records for [${verticalKey.toUpperCase()}]`);

    console.log(`\n==============================================================================`);
    console.log(`  🎉 TENANT SEEDING COMPLETE FOR: ${config.clientName.toUpperCase()}`);
    console.log(`  Store Hub ID : ${storeId}`);
    console.log(`  Seller Login : ${sellerEmail}`);
    console.log(`  Products     : ${catalogItems.length} active in catalog & inventory`);
    console.log(`==============================================================================\n`);

  } catch (err) {
    logError(`Seeding failed: ${err.message}`);
    console.error(err);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

const targetConfig = process.argv[2] || 'clients/default-commerceos.json';
seedTenant(targetConfig);
