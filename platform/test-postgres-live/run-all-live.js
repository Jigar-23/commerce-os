/**
 * Commerce OS — Live PostgreSQL Concurrency & Isolation Test Runner
 */

const { runTest: runSchemaContract } = require('./schema-contract.test');
const { runTest: runOrderTransaction } = require('./order-transaction.test');
const { runTest: runOrderCancellationAndFulfillment } = require('./order-cancellation-and-fulfillment.test');
const { runTest: runInventoryRace } = require('./inventory-race.test');
const { runTest: runOfferRace } = require('./offer-race.test');
const { runTest: runSellerIsolation } = require('./seller-isolation.test');
const { runTest: runCustomerIsolation } = require('./customer-isolation.test');
const { runTest: runCrashOutbox } = require('./crash-outbox.test');
const { runTest: runDispatchReal } = require('./dispatch-real.test');
const { runTest: runRiderRace } = require('./rider-race.test');
const { runTest: runRiderStateMachineMatrix } = require('./rider-state-machine-matrix.test');
const { runTest: runMultiStoreSameSku } = require('./multi-store-same-sku.test');
const { runTest: runMultiStoreFulfillment } = require('./multi-store-fulfillment.test');
const { runTest: runCatalogInventoryDomain } = require('./catalog-inventory-domain.test');
const { runTest: runServerFulfillmentSelection } = require('./server-fulfillment-selection.test');
const { runTest: runSellerStateMachine } = require('./seller-state-machine.test');
const { runTest: runCodSellerOwnership } = require('./cod-seller-ownership.test');
const { runTest: runJwtSecurityMatrix } = require('./jwt-security-matrix.test');
const { runTest: runProductionBoot } = require('./test-production-boot.test');
const { runTest: runOutboxLifecycle } = require('./outbox-lifecycle-live.test');
const { runTest: runOsrmIntegrationReal } = require('./osrm-integration-real.test');
const { runTest: runProductionHttpReal } = require('./test-production-http-real.test');
const { runTest: runSellerCatalogGlobalProduct } = require('./seller-catalog-global-product.test');
const { runTest: runInventoryLedgerAtomicity } = require('./inventory-ledger-atomicity.test');

async function main() {
  const databaseUrl = process.env.DATABASE_URL;

  console.log('================================================================');
  console.log('🧪 RUNNING DEDICATED LIVE POSTGRESQL CONCURRENCY & ISOLATION SUITE');
  console.log('================================================================\n');

  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for Live PostgreSQL Test Suite.');
    console.error('   Usage: DATABASE_URL=postgresql://user:pass@host:5432/db node platform/test-postgres-live/run-all-live.js\n');
    process.exit(1);
  }

  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });

  try {
    await runSchemaContract(pool);
    await runOrderTransaction(pool);
    await runOrderCancellationAndFulfillment(pool);
    await runInventoryRace(pool);
    await runOfferRace(pool);
    await runSellerIsolation(pool);
    await runCustomerIsolation(pool);
    await runCrashOutbox(pool);
    await runDispatchReal(pool);
    await runRiderRace(pool);
    await runRiderStateMachineMatrix(pool);
    await runMultiStoreSameSku(pool);
    await runMultiStoreFulfillment(pool);
    await runCatalogInventoryDomain(pool);
    await runServerFulfillmentSelection(pool);
    await runSellerStateMachine(pool);
    await runCodSellerOwnership(pool);
    await runJwtSecurityMatrix(pool);
    await runProductionBoot(pool);
    await runOutboxLifecycle(pool);
    await runOsrmIntegrationReal(pool);
    await runProductionHttpReal(pool);
    await runSellerCatalogGlobalProduct(pool);
    await runInventoryLedgerAtomicity(pool);

    console.log('================================================================');
    console.log('🏆 ALL 24 LIVE POSTGRESQL & PRODUCTION HTTP TESTS PASSED (100% SUCCESS)');
    console.log('================================================================\n');
  } catch (err) {
    console.error('❌ Live test failure:', err);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error('Fatal runner error:', err);
  process.exit(1);
});
