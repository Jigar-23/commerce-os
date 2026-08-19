/**
 * Commerce OS — Dedicated Live PostgreSQL Integration Runner
 * 
 * Strict Production Gate:
 * Requires an active PostgreSQL instance via DATABASE_URL.
 * Runs complete test suite against PostgreSQL 16+:
 * 1. Schema Contract Verification (schema-contract.test.js)
 * 2. Real Order Placement Transaction (order-transaction.test.js)
 * 3. Concurrent Inventory Debit Race (inventory-race.test.js)
 * 4. Concurrent Offer Acceptance Race (offer-race.test.js)
 * 5. Seller Multi-Tenant Isolation (seller-isolation.test.js)
 * 6. Customer Data Isolation (customer-isolation.test.js)
 * 7. Process Crash & Durable Outbox Recovery (crash-outbox.test.js)
 */

const { runTest: runSchemaContract } = require('./schema-contract.test');
const { runTest: runOrderTransaction } = require('./order-transaction.test');
const { runTest: runInventoryRace } = require('./inventory-race.test');
const { runTest: runOfferRace } = require('./offer-race.test');
const { runTest: runSellerIsolation } = require('./seller-isolation.test');
const { runTest: runCustomerIsolation } = require('./customer-isolation.test');
const { runTest: runCrashOutbox } = require('./crash-outbox.test');

async function main() {
  const databaseUrl = process.env.DATABASE_URL;

  console.log('================================================================');
  console.log('🧪 RUNNING PRODUCTION LIVE POSTGRESQL INTEGRATION SUITE');
  console.log('================================================================\n');

  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for Live PostgreSQL Test Suite.');
    console.error('   Live tests require a genuine PostgreSQL instance.');
    console.error('   Usage: DATABASE_URL=postgresql://user:pass@host:5432/db node platform/test-postgres-live/run-live-postgres.js\n');
    process.exit(1);
  }

  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });

  try {
    await runSchemaContract(pool);
    await runOrderTransaction(pool);
    await runInventoryRace(pool);
    await runOfferRace(pool);
    await runSellerIsolation(pool);
    await runCustomerIsolation(pool);
    await runCrashOutbox(pool);

    console.log('================================================================');
    console.log('🏆 ALL 7 LIVE POSTGRESQL TESTS PASSED (100% SUCCESS)');
    console.log('================================================================\n');
    process.exit(0);
  } catch (err) {
    console.error('❌ Live PostgreSQL test failure:', err);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error('Fatal runner error:', err);
  process.exit(1);
});
