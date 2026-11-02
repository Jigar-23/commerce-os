const fs = require('fs');
const { Pool } = require('pg');

const dotenv = fs.readFileSync('.env', 'utf8');
let dbUrl = '';
dotenv.split('\n').forEach(line => {
  if (line.startsWith('DATABASE_URL=')) {
    dbUrl = line.substring('DATABASE_URL='.length).trim().replace(/^["']|["']$/g, '');
  }
});

const pool = new Pool({ connectionString: dbUrl, ssl: { rejectUnauthorized: false } });

async function run() {
  const stores = await pool.query('SELECT id, store_name, is_active FROM stores');
  console.log('Stores:', stores.rows);
  const prods = await pool.query('SELECT id, sku, name, category, price, is_active FROM products');
  console.log('Products count:', prods.rows.length);
  console.log('Products:', prods.rows);
  const inv = await pool.query('SELECT store_id, product_id, sku, product_name, stock_count, reserved_count, available_count FROM inventory');
  console.log('Inventory count:', inv.rows.length);
  console.log('Inventory:', inv.rows);
  await pool.end();
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
