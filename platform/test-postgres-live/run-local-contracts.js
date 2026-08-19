/**
 * Commerce OS — Local Repository Contract Verification Suite
 */

const assert = require('assert');
const {
  LocalDevelopmentCatalogRepository,
  LocalDevelopmentInventoryRepository,
  LocalDevelopmentCustomerRepository,
  LocalDevelopmentPrescriptionRepository,
  LocalDevelopmentCodLedgerRepository,
  LocalDevelopmentSellerRepository,
  LocalDevelopmentAuditRepository,
  DeliveryOtpService
} = require('../repositories');

async function main() {
  console.log('================================================================');
  console.log('🧪 RUNNING LOCAL DEVELOPMENT REPOSITORY CONTRACT VERIFICATIONS');
  console.log('================================================================\n');

  const mockDb = {
    products: [],
    users: [],
    prescriptions: [],
    codLedger: [],
    auditLogs: [],
    inventoryHistory: [],
    deliverySessions: {},
    orders: []
  };

  const saveDb = () => {};

  // 1. Catalog & Inventory Contract
  const catalogRepo = new LocalDevelopmentCatalogRepository(mockDb, saveDb);
  const invRepo = new LocalDevelopmentInventoryRepository(mockDb, saveDb);

  await catalogRepo.saveProductTransactionally({ id: 'p_1', sku: 'SKU_1', name: 'Product 1', price: 100, inStock: true });
  const p1 = await catalogRepo.getSellableProductBySku('SKU_1');
  assert.strictEqual(p1.name, 'Product 1');
  console.log('  ✅ PASS: Local Catalog & Inventory Contract');

  // 2. OTP Cryptographic Hash Contract
  const rawPin = '4921';
  const hashedPin = DeliveryOtpService.hashOtp(rawPin);
  const validOtpRes = DeliveryOtpService.verifyOtp(rawPin, hashedPin, 0, 5);
  assert.strictEqual(validOtpRes.ok, true, 'DeliveryOtpService must verify matching SHA-256 OTP hashes');
  console.log('  ✅ PASS: DeliveryOtpService Cryptographic Hash Contract');

  // 3. Seller Authentication Contract
  const sellerRepo = new LocalDevelopmentSellerRepository(mockDb, saveDb);
  const sellerRes = await sellerRepo.verifySellerCredentials('seller_gurugram_01', 'gurugram_hub_sec_881');
  assert.strictEqual(sellerRes.ok, true, 'Seller credentials must verify successfully');
  console.log('  ✅ PASS: Local Seller Authentication Contract');

  // 4. Audit Logging Contract
  const auditRepo = new LocalDevelopmentAuditRepository(mockDb, saveDb);
  await auditRepo.recordLog('admin_1', 'TEST', 'Local test event', null);
  const logs = await auditRepo.getLogs({ role: 'ROLE_ADMIN' });
  assert.strictEqual(logs.length, 1);
  console.log('  ✅ PASS: Local Audit Logging Contract');

  console.log('\n================================================================');
  console.log('🏆 4/4 LOCAL CONTRACTS VALIDATED');
  console.log('================================================================\n');
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal local contract error:', err);
  process.exit(1);
});
