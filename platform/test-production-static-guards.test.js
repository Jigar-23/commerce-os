/**
 * Commerce OS — Production Static Guards & Invariant Enforcement Test
 * 
 * Verifies that production repositories and dispatch services contain ZERO:
 * - Fallback store identifiers (STORE_PRIMARY_01, STORE_GURUGRAM_01)
 * - Placeholder names ('Express Fulfillment Hub', 'Fulfillment Center')
 * - Hardcoded rider candidate arrays ('rider_881', 'rider_active_1', 'rider_online_1')
 * - Synthetic ETA / distance fallbacks ('3.5', '12')
 * - Plaintext SHA-256 fallback in seller password verification
 * - Non-existent 'is_rx_required' column references
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('================================================================');
console.log('🧪 RUNNING PRODUCTION STATIC GUARDS & ANTI-CORRUPTION AUDIT');
console.log('================================================================\n');

const REPO_FILE = path.join(__dirname, 'repositories/index.js');
const SCHEMA_FILE = path.join(__dirname, 'schema.sql');
const PROD_SERVER_FILE = path.join(__dirname, 'server/production-server.js');
const repoContent = fs.readFileSync(REPO_FILE, 'utf8');
const schemaContent = fs.readFileSync(SCHEMA_FILE, 'utf8');
const prodServerContent = fs.readFileSync(PROD_SERVER_FILE, 'utf8');

// 1. Extract Production Classes Only (excluding LocalDevelopment* harnesses)
const productionClasses = [
  'TransactionalCatalogRepository',
  'TransactionalCustomerRepository',
  'TransactionalSellerRepository',
  'TransactionalInventoryRepository',
  'TransactionalDeliveryRepository',
  'TransactionalOrderRepository',
  'TransactionalPresenceRepository',
  'TransactionalDeviceTokenRepository',
  'TransactionalOfferRepository',
  'TransactionalTelemetryRepository',
  'TransactionalCartRepository',
  'TransactionalPaymentRepository',
  'TransactionalPrescriptionRepository',
  'TransactionalAuditRepository',
  'TransactionalCodLedgerRepository',
  'TransactionalStoreRepository',
  'TransactionalServiceabilityRepository',
  'DispatchService',
  'OutboxProcessor',
  'ProductionNotificationService'
];

let passed = 0;
let failed = 0;

function checkGuard(name, fn) {
  try {
    fn();
    console.log(`  ✅ PASS: ${name}`);
    passed++;
  } catch (e) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     ${e.message}`);
    failed++;
  }
}

// Guard 1: Zero is_rx_required references across all production code and schema
checkGuard('No nonexistent is_rx_required references in repositories or schema', () => {
  assert.strictEqual(repoContent.includes('is_rx_required'), false, 'Found is_rx_required in repositories/index.js');
  assert.strictEqual(schemaContent.includes('is_rx_required'), false, 'Found is_rx_required in schema.sql');
  assert.ok(schemaContent.includes('rx_requirement'), 'schema.sql must contain rx_requirement');
});

// Guard 2: No SHA-256 fallback in TransactionalSellerRepository.verifyPassword
checkGuard('Seller password verification strictly uses Scrypt KDF without SHA-256 fallback', () => {
  const sellerClassMatch = repoContent.match(/class TransactionalSellerRepository[\s\S]*?class LocalDevelopmentSellerRepository/);
  assert.ok(sellerClassMatch, 'TransactionalSellerRepository not found');
  const sellerCode = sellerClassMatch[0];
  assert.strictEqual(sellerCode.includes("createHash('sha256')"), false, 'Found legacy SHA-256 in TransactionalSellerRepository');
  assert.ok(sellerCode.includes('crypto.scryptSync'), 'Must use scryptSync');
  assert.ok(sellerCode.includes('crypto.timingSafeEqual'), 'Must use timingSafeEqual');
});

// Guard 3: No hardcoded candidate rider IDs in DispatchService
checkGuard('DispatchService candidate discovery strictly queries PresenceRepository without hardcoded candidate IDs', () => {
  const dispatchMatch = repoContent.match(/class DispatchService[\s\S]*?class TransactionalCartRepository/);
  assert.ok(dispatchMatch, 'DispatchService not found');
  const dispatchCode = dispatchMatch[0];
  assert.strictEqual(dispatchCode.includes("'rider_881'"), false, 'Found hardcoded rider_881 in DispatchService');
  assert.strictEqual(dispatchCode.includes("'rider_active_1'"), false, 'Found hardcoded rider_active_1 in DispatchService');
  assert.strictEqual(dispatchCode.includes("'rider_online_1'"), false, 'Found hardcoded rider_online_1 in DispatchService');
  assert.ok(dispatchCode.includes('NO_RIDERS_AVAILABLE'), 'Must throw NO_RIDERS_AVAILABLE when no fleet rider is eligible');
});

// Guard 4: No synthetic 3.5 km / 12 min route fallbacks in DispatchService
checkGuard('DispatchService requires real OSRM route and never invents synthetic distance or duration', () => {
  const dispatchMatch = repoContent.match(/class DispatchService[\s\S]*?class TransactionalCartRepository/);
  const dispatchCode = dispatchMatch[0];
  assert.strictEqual(dispatchCode.includes('deliveryRoute.ok ? deliveryRoute.distanceKm : 3.5'), false, 'Found synthetic 3.5 km fallback');
  assert.strictEqual(dispatchCode.includes('deliveryRoute.ok ? deliveryRoute.durationMins : 12'), false, 'Found synthetic 12 min fallback');
  assert.ok(dispatchCode.includes('ROUTE_UNAVAILABLE'), 'Must throw ROUTE_UNAVAILABLE when route calculation fails');
});

// Guard 5: No GREATEST(0, ...) in inventory mutations or schema generated column
checkGuard('Inventory mutations verify state and never use GREATEST(0, ...) to mask underflow corruption', () => {
  const invMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invMatch, 'TransactionalInventoryRepository not found');
  const invCode = invMatch[0];
  assert.strictEqual(invCode.includes('reserved_count = GREATEST(0'), false, 'Found GREATEST(0, ...) corruption mask in inventory update');
  assert.ok(invCode.includes('CORRUPT_INVENTORY_STATE'), 'Must throw CORRUPT_INVENTORY_STATE on underflow / double release');
  assert.strictEqual(schemaContent.includes('GREATEST(0, stock_count - reserved_count)'), false, 'Found GREATEST(0, ...) in schema available_count');
});

// Guard 6: All inventory mutations strictly require storeId
checkGuard('TransactionalInventoryRepository strictly enforces storeId requirement', () => {
  const invMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  const invCode = invMatch[0];
  assert.ok(invCode.includes('STORE_ID_REQUIRED'), 'Must check STORE_ID_REQUIRED in inventory mutations');
  assert.strictEqual(invCode.includes('WHERE (sku = $1 OR product_id = $1) FOR UPDATE'), false, 'Found unscoped global inventory lock');
});

// Guard 7: Order creation strictly requires authoritative addressId
checkGuard('TransactionalOrderRepository strictly requires addressId and rejects raw client addresses', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderMatch, 'TransactionalOrderRepository not found');
  const orderCode = orderMatch[0];
  assert.ok(orderCode.includes('ADDRESS_ID_REQUIRED'), 'Must enforce ADDRESS_ID_REQUIRED in TransactionalOrderRepository');
  assert.ok(orderCode.includes('FROM customer_addresses'), 'Must resolve address from customer_addresses table');
});

// Guard 8: Pricing engine calculation path is delegated
checkGuard('Order pricing calculation is delegated to authoritative pricing domain', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  const orderCode = orderMatch[0];
  assert.ok(orderCode.includes('calculateCustomerOrderPricing'), 'Must delegate calculation to calculateCustomerOrderPricing');
});

// Guard 9: Zero fallback store identifiers or placeholder names in Production Repositories & DispatchService
checkGuard('Production classes contain ZERO hardcoded fallback stores or placeholder hub names', () => {
  for (const clsName of productionClasses) {
    const regex = new RegExp(`class ${clsName}[\\s\\S]*?(?=class |module\\.exports)`);
    const match = repoContent.match(regex);
    if (match) {
      const clsCode = match[0];
      assert.strictEqual(clsCode.includes('STORE_PRIMARY_01'), false, `Found STORE_PRIMARY_01 in ${clsName}`);
      assert.strictEqual(clsCode.includes('STORE_GURUGRAM_01'), false, `Found STORE_GURUGRAM_01 in ${clsName}`);
      assert.strictEqual(clsCode.includes("'Express Fulfillment Hub'"), false, `Found 'Express Fulfillment Hub' in ${clsName}`);
      assert.strictEqual(clsCode.includes("'Fulfillment Center'"), false, `Found 'Fulfillment Center' in ${clsName}`);
    }
  }
});

// Guard 10: Zero direct db.* or saveDb() mutations in Production Repositories & DispatchService
checkGuard('Production classes contain ZERO direct db.* access or saveDb() mutations', () => {
  for (const clsName of productionClasses) {
    const regex = new RegExp(`class ${clsName}[\\s\\S]*?(?=class |module\\.exports)`);
    const match = repoContent.match(regex);
    if (match) {
      const clsCode = match[0];
      assert.strictEqual(clsCode.includes('db.products'), false, `Found db.products in ${clsName}`);
      assert.strictEqual(clsCode.includes('db.orders'), false, `Found db.orders in ${clsName}`);
      assert.strictEqual(clsCode.includes('db.deliverySessions'), false, `Found db.deliverySessions in ${clsName}`);
      assert.strictEqual(clsCode.includes('saveDb('), false, `Found saveDb() in ${clsName}`);
    }
  }
});

// Guard 11: Production Wiring Factory verifies isolation
checkGuard('createProductionRepositories strictly instantiates Transactional* repositories only', () => {
  const { createProductionRepositories } = require('./repositories');
  const dummyPool = { query: async () => ({ rows: [] }), connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) };
  const prodRepos = createProductionRepositories(dummyPool, {
    fcmSender: async () => true,
    sseBroadcaster: async () => true,
    routeResolver: async () => ({ ok: true, distanceKm: 2.5, durationMins: 8 })
  });
  assert.strictEqual(prodRepos.isProduction, true);
  assert.strictEqual(prodRepos.catalogRepo.constructor.name, 'TransactionalCatalogRepository');
  assert.strictEqual(prodRepos.customerRepo.constructor.name, 'TransactionalCustomerRepository');
  assert.strictEqual(prodRepos.sellerRepo.constructor.name, 'TransactionalSellerRepository');
  assert.strictEqual(prodRepos.inventoryRepo.constructor.name, 'TransactionalInventoryRepository');
  assert.strictEqual(prodRepos.deliveryRepo.constructor.name, 'TransactionalDeliveryRepository');
  assert.strictEqual(prodRepos.orderRepo.constructor.name, 'TransactionalOrderRepository');
  assert.strictEqual(prodRepos.presenceRepo.constructor.name, 'TransactionalPresenceRepository');
  assert.strictEqual(prodRepos.deviceTokenRepo.constructor.name, 'TransactionalDeviceTokenRepository');
  assert.strictEqual(prodRepos.offerRepo.constructor.name, 'TransactionalOfferRepository');
  assert.strictEqual(prodRepos.telemetryRepo.constructor.name, 'TransactionalTelemetryRepository');
  assert.strictEqual(prodRepos.prescriptionRepo.constructor.name, 'TransactionalPrescriptionRepository');
  assert.strictEqual(prodRepos.codLedgerRepo.constructor.name, 'TransactionalCodLedgerRepository');
  assert.strictEqual(prodRepos.storeRepo.constructor.name, 'TransactionalStoreRepository');
});

// Guard 12: Zero customer/merchant placeholder fallbacks in DispatchService
checkGuard('DispatchService contains ZERO customer or merchant placeholder fallbacks', () => {
  const dispatchMatch = repoContent.match(/class DispatchService[\s\S]*?class TransactionalCartRepository/);
  assert.ok(dispatchMatch, 'DispatchService not found');
  const dispatchCode = dispatchMatch[0];
  assert.strictEqual(dispatchCode.includes("|| 'Customer'"), false, "Found 'Customer' fallback in DispatchService");
  assert.strictEqual(dispatchCode.includes("|| 'Customer Address'"), false, "Found 'Customer Address' fallback in DispatchService");
  assert.ok(dispatchCode.includes('INTEGRITY_ERROR'), 'Must throw INTEGRITY_ERROR if customer/merchant details are missing');
});

// Guard 13: Zero client OTP hash acceptance in TransactionalOrderRepository
checkGuard('TransactionalOrderRepository generates OTP server-side and never accepts client OTP hash', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderMatch, 'TransactionalOrderRepository not found');
  const orderCode = orderMatch[0];
  assert.strictEqual(orderCode.includes('orderData.deliveryOtpHash'), false, 'Found orderData.deliveryOtpHash acceptance');
  assert.strictEqual(orderCode.includes('orderData.deliveryOtp'), false, 'Found orderData.deliveryOtp acceptance');
  assert.ok(orderCode.includes('DeliveryOtpService.generateSecureOtp()'), 'Must call DeliveryOtpService.generateSecureOtp()');
  assert.ok(orderCode.includes('DeliveryOtpService.hashOtp('), 'Must call DeliveryOtpService.hashOtp()');
});

// Guard 14: Zero client delivery session state or rider assignment in TransactionalOrderRepository
checkGuard('TransactionalOrderRepository forces LOOKING_FOR_RIDER and null rider for new orders', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  const orderCode = orderMatch[0];
  assert.strictEqual(orderCode.includes('deliverySessionData.riderId'), false, 'Found deliverySessionData.riderId assignment');
  assert.strictEqual(orderCode.includes('deliverySessionData.state'), false, 'Found deliverySessionData.state assignment');
  assert.ok(orderCode.includes("'LOOKING_FOR_RIDER'"), 'Must enforce initial LOOKING_FOR_RIDER state');
});

// Guard 15: Zero automatic prepaid order creation with PAID payment status
checkGuard('TransactionalOrderRepository sets PAYMENT_PENDING for prepaid orders upon creation', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  const orderCode = orderMatch[0];
  assert.ok(orderCode.includes("isCod ? 'COD_PENDING' : 'PAYMENT_PENDING'"), 'Must set PAYMENT_PENDING for prepaid orders');
  assert.strictEqual(orderCode.includes("isCod ? 'COD_PENDING' : 'PAID'"), false, 'Found automatic PAID assignment for prepaid orders');
});

// Guard 16: Invalid order type must fail with 400 INVALID_ORDER_TYPE
checkGuard('TransactionalOrderRepository rejects invalid order types with INVALID_ORDER_TYPE', () => {
  const orderMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  const orderCode = orderMatch[0];
  assert.ok(orderCode.includes('INVALID_ORDER_TYPE'), 'Must reject invalid order types');
});

// Guard 17: Production Server Entrypoint contains ZERO legacy mock database references
checkGuard('Production server entrypoint contains ZERO db.json, saveDb(), or LocalDevelopmentRepository references', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  assert.ok(fs.existsSync(prodServerPath), 'production-server.js must exist');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');

  assert.strictEqual(prodServerContent.includes('db.json'), false, 'Found db.json in production-server.js');
  assert.strictEqual(prodServerContent.includes('saveDb('), false, 'Found saveDb() in production-server.js');
  assert.strictEqual(prodServerContent.includes('LocalDevelopment'), false, 'Found LocalDevelopment* in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.products'), false, 'Found db.products in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.orders'), false, 'Found db.orders in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.users'), false, 'Found db.users in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.riders'), false, 'Found db.riders in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.prescriptions'), false, 'Found db.prescriptions in production-server.js');
  assert.strictEqual(prodServerContent.includes('db.carts'), false, 'Found db.carts in production-server.js');
});

// Guard 18: Production Server Entrypoint enforces mandatory DATABASE_URL fail-fast
checkGuard('Production server entrypoint enforces fail-fast on missing DATABASE_URL', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: DATABASE_URL is strictly required'), 'Must fail fast when DATABASE_URL is missing');
});

// Guard 19: Production Server Entrypoint contains ZERO hardcoded JWT secret fallbacks
checkGuard('Production server entrypoint contains ZERO hardcoded JWT secret fallbacks', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.strictEqual(prodServerContent.includes('commerceos_master_jwt_secret_key'), false, 'Found hardcoded JWT secret fallback in production-server.js');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: JWT_SECRET is strictly required'), 'Must enforce JWT_SECRET requirement');
});

// Guard 20: Production Server Entrypoint contains ZERO hardcoded OSRM URL fallbacks
checkGuard('Production server entrypoint contains ZERO hardcoded OSRM URL fallbacks', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.strictEqual(prodServerContent.includes("process.env.OSRM_BASE_URL || 'http"), false, 'Found hardcoded OSRM fallback in production-server.js');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: OSRM_BASE_URL is strictly required'), 'Must enforce OSRM_BASE_URL requirement');
});

// Guard 21: Production Notification wiring contains real adapters and zero async () => true stubs
checkGuard('Production notification wiring contains real adapters and zero fake stubs', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.strictEqual(prodServerContent.includes('fcmSender: async (token, payload) => {\n    return true;\n  }'), false, 'Found fake FCM stub in production-server.js');
  assert.strictEqual(prodServerContent.includes('sseBroadcaster: async (channel, event, payload) => {\n    return true;\n  }'), false, 'Found fake SSE stub in production-server.js');
  assert.ok(prodServerContent.includes('class ProductionFcmSender'), 'Must implement ProductionFcmSender');
  assert.ok(prodServerContent.includes('class ProductionSseBroadcaster'), 'Must implement ProductionSseBroadcaster');
});

// Guard 22: Production FCM adapter strictly enforces DATA-ONLY payload (ZERO notification object)
checkGuard('Production FCM adapter strictly enforces DATA-ONLY payload (zero notification object)', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  const fcmClassMatch = prodServerContent.match(/class ProductionFcmSender[\s\S]*?class ProductionSseBroadcaster/);
  assert.ok(fcmClassMatch, 'ProductionFcmSender class must exist in production-server.js');
  const fcmCode = fcmClassMatch[0];
  assert.strictEqual(fcmCode.includes('notification: {'), false, 'Found forbidden notification object inside ProductionFcmSender');
  assert.strictEqual(fcmCode.includes('notification:'), false, 'Found forbidden notification property in ProductionFcmSender payload');
  assert.ok(fcmCode.includes('data: {'), 'Must include data payload object');
});

// Guard 23: Production server contains ZERO FCM_MOCK_ALLOWED or fake bypasses
checkGuard('Production server contains ZERO FCM_MOCK_ALLOWED or fake bypasses', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.strictEqual(prodServerContent.includes('FCM_MOCK_ALLOWED'), false, 'Found forbidden FCM_MOCK_ALLOWED in production-server.js');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: FCM_SERVER_KEY is strictly required'), 'Must enforce FCM_SERVER_KEY requirement');
});

// Guard 24: TransactionalPresenceRepository maps last_known_lat and last_known_lng correctly without nonexistent r.latitude
checkGuard('TransactionalPresenceRepository maps schema columns last_known_lat and last_known_lng', () => {
  const presMatch = repoContent.match(/class TransactionalPresenceRepository[\s\S]*?class LocalDevelopmentPresenceRepository/);
  assert.ok(presMatch, 'TransactionalPresenceRepository must exist');
  const presCode = presMatch[0];
  assert.strictEqual(presCode.includes('latitude: r.latitude,'), false, 'Found broken r.latitude mapping in TransactionalPresenceRepository');
  assert.strictEqual(presCode.includes('longitude: r.longitude,'), false, 'Found broken r.longitude mapping in TransactionalPresenceRepository');
  assert.ok(presCode.includes('r.last_known_lat'), 'Must map from r.last_known_lat');
  assert.ok(presCode.includes('r.last_known_lng'), 'Must map from r.last_known_lng');
});

// Guard 25: Production server contains ZERO hardcoded FCM endpoint fallback URLs
checkGuard('Production server contains ZERO hardcoded FCM endpoint fallback URLs', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.strictEqual(prodServerContent.includes("process.env.FCM_ENDPOINT_URL || 'https"), false, 'Found hardcoded FCM fallback URL in production-server.js');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: FCM_ENDPOINT_URL is strictly required'), 'Must enforce FCM_ENDPOINT_URL requirement');
});

// Guard 26: Production server returns sanitized explicit DTOs and never leaks delivery_otp_hash
checkGuard('Production server sanitizes responses and never returns delivery_otp_hash in active tracking or seller routes', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('activeTrackingDto'), 'Must define explicit activeTrackingDto');
  assert.ok(prodServerContent.includes('sellerOrdersDto'), 'Must define explicit sellerOrdersDto');
  assert.ok(prodServerContent.includes('customerOrderDto'), 'Must define explicit customerOrderDto');
});

// Guard 27: Production server enforces mandatory fail-fast on missing JWT_ISSUER and JWT_AUDIENCE
checkGuard('Production server enforces mandatory fail-fast on missing JWT_ISSUER and JWT_AUDIENCE', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: JWT_ISSUER is strictly required'), 'Must enforce JWT_ISSUER requirement');
  assert.ok(prodServerContent.includes('FATAL_CONFIGURATION_ERROR: JWT_AUDIENCE is strictly required'), 'Must enforce JWT_AUDIENCE requirement');
});

// Guard 28: TransactionalServiceabilityRepository does NOT use Euclidean degree distance sorting
checkGuard('TransactionalServiceabilityRepository does NOT use Euclidean degree distance sorting', () => {
  const servRepoMatch = repoContent.match(/class TransactionalServiceabilityRepository[\s\S]*?class LocalDevelopmentServiceabilityRepository/);
  assert.ok(servRepoMatch, 'TransactionalServiceabilityRepository must exist');
  const servCode = servRepoMatch[0];
  assert.strictEqual(servCode.includes('(latitude - $1) * (latitude - $1)'), false, 'Found Euclidean degree sorting in TransactionalServiceabilityRepository');
  assert.ok(servCode.includes('ServiceabilityService.calculateDistanceKm'), 'Must use authoritative ServiceabilityService.calculateDistanceKm');
});

// Guard 29: TransactionalOrderRepository does NOT use arbitrary LIMIT 1 for COD seller resolution
checkGuard('TransactionalOrderRepository does NOT use arbitrary LIMIT 1 for COD seller resolution', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.strictEqual(orderCode.includes("SELECT seller_id, id FROM sellers WHERE store_id = $1 AND status = 'ACTIVE' LIMIT 1"), false, 'Found arbitrary LIMIT 1 seller query');
  assert.ok(orderCode.includes('is_primary = TRUE'), 'Must enforce is_primary = TRUE on seller query');
});

// Guard 30: Production server uses standard mature jsonwebtoken library for verification
checkGuard('Production server uses standard mature jsonwebtoken library for verification', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes("require('jsonwebtoken')"), 'Must import mature jsonwebtoken library');
  assert.ok(prodServerContent.includes('jwt.verify'), 'Must use jwt.verify');
});

// Guard 31: Schema enforces 3-state inventory integrity constraints
checkGuard('Authoritative schema enforces 3-state inventory non-negative and integrity constraints', () => {
  const schemaPath = path.join(__dirname, 'schema.sql');
  const schemaContent = fs.readFileSync(schemaPath, 'utf8');
  assert.ok(schemaContent.includes('CONSTRAINT chk_stock_non_negative CHECK (stock_count >= 0)'), 'Must enforce stock >= 0');
  assert.ok(schemaContent.includes('CONSTRAINT chk_reserved_non_negative CHECK (reserved_count >= 0)'), 'Must enforce reserved >= 0');
  assert.ok(schemaContent.includes('CONSTRAINT chk_reserved_lte_stock CHECK (reserved_count <= stock_count)'), 'Must enforce reserved <= stock');
});

// Guard 32: Production module graph strictly cannot instantiate LocalDevelopmentRepository
checkGuard('Production repository factory strictly instantiates Transactional* repositories only', () => {
  const prodFactoryMatch = repoContent.match(/function createProductionRepositories[\s\S]*?return \{[\s\S]*?\};/);
  assert.ok(prodFactoryMatch, 'createProductionRepositories must exist');
  const factoryCode = prodFactoryMatch[0];
  assert.strictEqual(factoryCode.includes('new LocalDevelopment'), false, 'Found LocalDevelopment repository instantiation in createProductionRepositories');
  assert.ok(factoryCode.includes('new TransactionalOrderRepository'), 'Must instantiate TransactionalOrderRepository');
  assert.ok(factoryCode.includes('new TransactionalInventoryRepository'), 'Must instantiate TransactionalInventoryRepository');
});

// Guard 33: Production server passes FulfillmentDecision into order placement
checkGuard('Production server passes FulfillmentDecision into order placement', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('fulfillmentDecision: fulfillmentDecision.decision'), 'Must pass fulfillmentDecision into placeOrderTransactionally');
});

// Guard 34: TransactionalOrderRepository enforces mandatory FulfillmentDecision
checkGuard('TransactionalOrderRepository enforces mandatory FulfillmentDecision', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('FULFILLMENT_DECISION_REQUIRED'), 'Must fail fast on missing FulfillmentDecision');
});

// Guard 35: TransactionalOrderRepository validates request hash fingerprint on idempotency key
checkGuard('TransactionalOrderRepository validates request hash fingerprint on idempotency key', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('IDEMPOTENCY_KEY_REUSE_MISMATCH'), 'Must enforce IDEMPOTENCY_KEY_REUSE_MISMATCH on modified payload');
  assert.ok(orderCode.includes('request_hash'), 'Must compare request_hash');
});

// Guard 36: TransactionalOrderRepository asserts exactly 1 primary seller for COD
checkGuard('TransactionalOrderRepository asserts exactly 1 primary seller for COD', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('STORE_MERCHANT_AUTHORITY_CORRUPT'), 'Must reject corrupted multiple primary seller state');
  assert.ok(orderCode.includes('STORE_MERCHANT_AUTHORITY_MISSING'), 'Must reject missing primary seller state');
});

// Guard 37: Production server implements real seller transition and rider offer endpoints
checkGuard('Production server implements real seller transition and rider offer endpoints', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('/pack'), 'Must register /pack route');
  assert.ok(prodServerContent.includes('/ready-for-pickup'), 'Must register /ready-for-pickup route');
  assert.ok(prodServerContent.includes('/rider/offers/'), 'Must register /rider/offers/:id/accept route');
});

// Guard 38: TransactionalOrderRepository contains ZERO references to explicitDecision
checkGuard('TransactionalOrderRepository contains ZERO references to explicitDecision', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.strictEqual(orderCode.includes('explicitDecision'), false, 'Found illegal explicitDecision variable in TransactionalOrderRepository');
});

// Guard 39: TransactionalOrderRepository contains ZERO fallback decision aliases (data.decision, data.storeId)
checkGuard('TransactionalOrderRepository contains ZERO fallback decision aliases', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.strictEqual(orderCode.includes('data.decision'), false, 'Found illegal data.decision alias');
  assert.strictEqual(orderCode.includes('data.authoritativeStoreId'), false, 'Found illegal data.authoritativeStoreId alias');
  assert.ok(orderCode.includes('const decision = data.fulfillmentDecision || null'), 'Must strictly read data.fulfillmentDecision only');
});

// Guard 40: TransactionalOrderRepository does NOT recompute serviceability heuristic
checkGuard('TransactionalOrderRepository does NOT recompute serviceability heuristic', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.strictEqual(orderCode.includes('ServiceabilityService.calculateDistanceKm'), false, 'TransactionalOrderRepository must NOT recalculate distance');
});

// Guard 41: TransactionalOrderRepository resolves catalog before merging duplicate lines
checkGuard('TransactionalOrderRepository resolves catalog before merging duplicate lines', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('SELECT id, sku, name, brand_name, price, mrp, discounted_price, rx_requirement, is_active'), 'Must query catalog');
  assert.ok(orderCode.includes('mergedBySku'), 'Must merge by canonical SKU after catalog resolution');
});

// Guard 42: TransactionalInventoryRepository validates productId and sku identity
checkGuard('TransactionalInventoryRepository validates productId and sku identity', () => {
  const invRepoMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class TransactionalOfferRepository/);
  assert.ok(invRepoMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invRepoMatch[0];
  assert.ok(invCode.includes('product_id = $2 AND sku = $3'), 'Must support and enforce product_id and sku verification in inventory queries');
});

// Guard 43: Schema enforces composite product identity constraint
checkGuard('Schema enforces composite product identity constraint', () => {
  const schemaPath = path.join(__dirname, 'schema.sql');
  const schemaContent = fs.readFileSync(schemaPath, 'utf8');
  assert.ok(schemaContent.includes('CONSTRAINT uq_products_id_sku UNIQUE (id, sku)'), 'Must enforce unique (id, sku) on products');
  assert.ok(schemaContent.includes('CONSTRAINT fk_inventory_product_sku FOREIGN KEY (product_id, sku) REFERENCES products(id, sku)'), 'Must enforce composite foreign key on inventory');
});

// Guard 44: Production server omits raw deliveryOtp on idempotent replay
checkGuard('Production server omits raw deliveryOtp on idempotent replay', () => {
  const prodServerPath = path.join(__dirname, 'server/production-server.js');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');
  assert.ok(prodServerContent.includes('deliveryOtp: placeResult.isIdempotentReplay ? undefined :'), 'Must omit deliveryOtp on idempotent replay');
});

// Guard 45: TransactionalOrderRepository validates payment method against strict allowlist
checkGuard('TransactionalOrderRepository validates payment method against strict allowlist', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('ALLOWED_PAYMENT_METHODS'), 'Must define ALLOWED_PAYMENT_METHODS allowlist');
  assert.ok(orderCode.includes('INVALID_PAYMENT_METHOD'), 'Must return INVALID_PAYMENT_METHOD on unknown methods');
});

// Guard 46: TransactionalOrderRepository persists storeId inside canonical item snapshot
checkGuard('TransactionalOrderRepository persists storeId inside canonical item snapshot', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('storeId: targetStoreId'), 'Item snapshot must bind targetStoreId');
});

// Guard 47: TransactionalOrderRepository cancelOrder passes authoritative order.store_id to releaseStock
checkGuard('TransactionalOrderRepository cancelOrder passes authoritative order.store_id to releaseStock', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('this.inventoryRepo.releaseStockTransactionally(client, storeId, items)'), 'Must pass order.store_id to releaseStockTransactionally');
});

// Guard 48: TransactionalInventoryRepository getStoreInventory joins products by product_id without store_id coupling
checkGuard('TransactionalInventoryRepository getStoreInventory joins products by product_id without store_id coupling', () => {
  const invRepoMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invRepoMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invRepoMatch[0];
  assert.ok(invCode.includes('LEFT JOIN products p ON i.product_id = p.id'), 'Must join products by canonical product_id');
  assert.strictEqual(invCode.includes('AND i.store_id = p.store_id'), false, 'Must NOT couple products by store_id in inventory join');
});

// Guard 49: TransactionalCatalogRepository getActiveProducts derives store catalog via inventory join
checkGuard('TransactionalCatalogRepository getActiveProducts derives store catalog via inventory join', () => {
  const catRepoMatch = repoContent.match(/class TransactionalCatalogRepository[\s\S]*?class LocalDevelopmentCatalogRepository/);
  assert.ok(catRepoMatch, 'TransactionalCatalogRepository must exist');
  const catCode = catRepoMatch[0];
  assert.ok(catCode.includes('JOIN products p ON i.product_id = p.id'), 'Must join products by product_id for store catalog');
});

// Guard 50: TransactionalOrderRepository cancelOrder validates item store context and canonical product presence
checkGuard('TransactionalOrderRepository cancelOrder validates item store context and canonical product presence', () => {
  const orderRepoMatch = repoContent.match(/class TransactionalOrderRepository[\s\S]*?class LocalDevelopmentOrderRepository/);
  assert.ok(orderRepoMatch, 'TransactionalOrderRepository must exist');
  const orderCode = orderRepoMatch[0];
  assert.ok(orderCode.includes('CORRUPT_ORDER_INVENTORY_CONTEXT'), 'Must check item storeId matches order.store_id');
  assert.ok(orderCode.includes('CANONICAL_PRODUCT_ID_REQUIRED'), 'Must require productId for canonical item release');
});

// Guard 51: catalog_admins DB-backed GLOBAL_CATALOG_WRITE membership table exists in schema
checkGuard('catalog_admins DB-backed GLOBAL_CATALOG_WRITE membership table exists in schema', () => {
  assert.ok(schemaContent.includes('CREATE TABLE IF NOT EXISTS catalog_admins'), 'catalog_admins table must exist in schema.sql');
  assert.ok(schemaContent.includes('GLOBAL_CATALOG_WRITE'), 'catalog_admins permissions JSONB must include GLOBAL_CATALOG_WRITE');
});

// Guard 52: Production seller product mutation is gated by GLOBAL_CATALOG_WRITE (never silent global mutation)
checkGuard('Production seller product mutation is gated by GLOBAL_CATALOG_WRITE', () => {
  assert.ok(prodServerContent.includes('hasCatalogWriteAuth(authClaims.sub)'), 'Catalog mutation routes must resolve DB-backed GLOBAL_CATALOG_WRITE authority');
  assert.ok(repoContent.includes('permissions FROM catalog_admins'), 'Catalog authority must be resolved from the catalog_admins table');
  assert.ok(prodServerContent.includes('GLOBAL_CATALOG_WRITE_REQUIRED'), 'Seller global-catalog mutation attempts must be rejected with GLOBAL_CATALOG_WRITE_REQUIRED');
  assert.ok(prodServerContent.includes('Creating new global catalog identities requires GLOBAL_CATALOG_WRITE authority'), 'Unknown-SKU seller create must be rejected');
});

// Guard 53: deleteProductTransactionally must NEVER deactivate a global product scoped by store_id
checkGuard('deleteProductTransactionally must never deactivate a global product scoped by store_id', () => {
  const catRepoMatch = repoContent.match(/class TransactionalCatalogRepository[\s\S]*?class LocalDevelopmentCatalogRepository/);
  assert.ok(catRepoMatch, 'TransactionalCatalogRepository must exist');
  const catCode = catRepoMatch[0];
  assert.strictEqual(catCode.includes('store_id = $2'), false, 'deleteProductTransactionally must NOT scope deactivation by store_id (global-product model)');
});

// Guard 54: Production DELETE product route requires GLOBAL_CATALOG_WRITE
checkGuard('Production DELETE product route requires GLOBAL_CATALOG_WRITE', () => {
  assert.ok(prodServerContent.includes("method === 'DELETE'"), 'DELETE product route must exist');
  const deleteBlock = prodServerContent.match(/productDeleteMatch && method === 'DELETE'[\s\S]*?return sendJson\(res, 200/);
  assert.ok(deleteBlock, 'DELETE product route block must exist');
  assert.ok(deleteBlock[0].includes('hasCatalogWriteAuth'), 'DELETE must verify GLOBAL_CATALOG_WRITE');
  assert.ok(deleteBlock[0].includes('GLOBAL_CATALOG_WRITE_REQUIRED'), 'DELETE must reject non-catalog operators');
});

// Guard 55: Production seller product PATCH rejects any global catalog field mutation (read-only for sellers)
checkGuard('Production seller PATCH rejects global catalog field mutation (name/price/mrp/sku/rx)', () => {
  const patchBlock = prodServerContent.match(/Seller Product Update: PATCH \/api\/v1\/catalog\/products\/:id[\s\S]*?return sendJson\(res, 200/);
  assert.ok(patchBlock, 'PATCH product block must exist');
  const patchCode = patchBlock[0];
  assert.ok(patchCode.includes('globalFieldAttempts'), 'Seller PATCH must inspect attempted global fields');
  assert.ok(patchCode.includes("'sku'"), 'Seller PATCH must treat sku as a global immutable field');
  assert.ok(patchCode.includes("'rxRequirement'"), 'Seller PATCH must treat rxRequirement as a global immutable field');
  assert.ok(patchCode.includes("'mrp'") && patchCode.includes("'price'"), 'Seller PATCH must treat mrp/price as global immutable fields');
});

// Guard 56: Production seller POST product resolves global product first and enforces catalog authority for new identities
checkGuard('Production seller POST resolves existing global SKU and requires catalog authority for new identities', () => {
  const postBlock = prodServerContent.slice(
    prodServerContent.indexOf('Seller Product Create'),
    prodServerContent.indexOf('Seller Product Update')
  );
  assert.ok(postBlock.length > 0, 'POST product block must exist');
  assert.ok(postBlock.includes('existingRes.rows[0]'), 'POST must resolve the global product first');
  assert.ok(postBlock.includes('isCatalogOperator'), 'POST must resolve catalog operator authority');
  assert.ok(postBlock.includes('Existing global product'), 'Existing-SKU path must be distinct from global-create path');
  assert.ok(postBlock.includes('Creating new global catalog identities requires GLOBAL_CATALOG_WRITE authority'), 'Unknown-SKU seller create must be rejected with GLOBAL_CATALOG_WRITE_REQUIRED');
});

// Guard 57: Production server must NEVER mutate inventory rows directly (all mutations via the inventory domain)
checkGuard('Production server contains ZERO direct inventory row mutations', () => {
  const forbidden = [
    'INSERT INTO inventory',
    'UPDATE inventory',
    'DELETE FROM inventory',
    'ON CONFLICT (store_id, sku)'
  ];
  for (const pattern of forbidden) {
    assert.strictEqual(prodServerContent.includes(pattern), false, `Found "${pattern}" in production-server.js — inventory must only be mutated through the repository domain`);
  }
});

// Guard 58: The authoritative absolute-stock domain method must exist and be enforced by production routes
checkGuard('setStockForStore is defined and used by all catalog stock routes', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes('async setStockForStore'), 'TransactionalInventoryRepository must define setStockForStore');
  assert.ok(invCode.includes('FOR UPDATE'), 'setStockForStore must take a row lock');
  assert.ok(invCode.includes('inventory_ledger'), 'setStockForStore must write the inventory ledger in the same transaction');
  assert.ok(invCode.includes('INSUFFICIENT_STOCK'), 'setStockForStore must reject requested stock below active reservations');
  assert.strictEqual((prodServerContent.match(/inventoryRepo\.setStockForStore\(/g) || []).length, 3, 'All three catalog stock paths (POST create, POST link, PATCH) must route through setStockForStore');
});

// Guard 59: saveProductTransactionally writes store_id = NULL for global catalog identity
checkGuard('saveProductTransactionally writes store_id = NULL for global catalog identity', () => {
  const catClassMatch = repoContent.match(/class TransactionalCatalogRepository[\s\S]*?class LocalDevelopmentCatalogRepository/);
  assert.ok(catClassMatch, 'TransactionalCatalogRepository must exist');
  const catCode = catClassMatch[0];
  assert.ok(catCode.includes('store_id, created_at, updated_at\n      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, TRUE, NULL, NOW(), NOW())') || catCode.includes('TRUE, NULL, NOW(), NOW()'), 'saveProductTransactionally must insert store_id as NULL');
});

// Guard 59: Inventory stock application requires a SELLER STORE context, never catalog-operator authority
checkGuard('Inventory stock sends are gated by seller authorized store, not global-catalog permission', () => {
  assert.strictEqual((prodServerContent.match(/setStockForStore\(/g) || []).length, 3, 'All three catalog stock paths must route through setStockForStore');
  const gatedStocks = [...prodServerContent.matchAll(/if \(authorizedStoreId[\s\S]*?setStockForStore\(/g)];
  assert.strictEqual(gatedStocks.length, 3, 'Each setStockForStore call must be guarded by the seller authorizedStoreId');
  // A DB-backed catalog operator WITHOUT a seller store has no authorizedStoreId => cannot touch store inventory.
  assert.strictEqual(
    prodServerContent.includes('authorizedStoreId && body.stockCount'),
    true,
    'Stock application must be conditioned on an authenticated seller store'
  );
});

// Guard 60: Schema strictly enforces inventory.store_id NOT NULL and inventory.product_id NOT NULL
checkGuard('inventory schema strictly enforces store_id NOT NULL and product_id NOT NULL', () => {
  const invTableMatch = schemaContent.match(/CREATE TABLE IF NOT EXISTS inventory \([\s\S]*?\);/);
  assert.ok(invTableMatch, 'inventory table must exist in schema.sql');
  const invTableSql = invTableMatch[0];
  assert.ok(invTableSql.includes('store_id VARCHAR(64) NOT NULL REFERENCES stores'), 'inventory.store_id must be NOT NULL');
  assert.ok(invTableSql.includes('product_id VARCHAR(64) NOT NULL REFERENCES products'), 'inventory.product_id must be NOT NULL');
  assert.ok(invTableSql.includes('sku VARCHAR(64) NOT NULL'), 'inventory.sku must be NOT NULL');
});

// Guard 61: Schema strictly enforces composite foreign key (product_id, sku) and store-sku uniqueness
checkGuard('inventory schema enforces composite FK and store-sku uniqueness', () => {
  const invTableMatch = schemaContent.match(/CREATE TABLE IF NOT EXISTS inventory \([\s\S]*?\);/);
  assert.ok(invTableMatch, 'inventory table must exist in schema.sql');
  const invTableSql = invTableMatch[0];
  assert.ok(invTableSql.includes('CONSTRAINT uq_store_sku UNIQUE') && invTableSql.includes('store_id, sku'), 'inventory must enforce uq_store_sku');
  assert.ok(invTableSql.includes('FOREIGN KEY (product_id, sku) REFERENCES products(id, sku)'), 'inventory must enforce composite FK fk_inventory_product_sku');
});

// Guard 62: Schema strictly enforces inventory_ledger.store_id NOT NULL
checkGuard('inventory_ledger schema strictly enforces store_id NOT NULL', () => {
  const ledgerTableMatch = schemaContent.match(/CREATE TABLE IF NOT EXISTS inventory_ledger \([\s\S]*?\);/);
  assert.ok(ledgerTableMatch, 'inventory_ledger table must exist in schema.sql');
  const ledgerTableSql = ledgerTableMatch[0];
  assert.ok(ledgerTableSql.includes('store_id VARCHAR(64) NOT NULL REFERENCES stores'), 'inventory_ledger.store_id must be NOT NULL');
});

// Guard 63: setStockForStore and adjustStockForStore write controlled reason codes from allowlist
checkGuard('inventory mutation methods enforce controlled ledger reason codes', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes('INITIAL_STOCK_LINK'), 'Must use INITIAL_STOCK_LINK on first link');
  assert.ok(invCode.includes('SELLER_RESTOCK'), 'Must support SELLER_RESTOCK');
  assert.ok(invCode.includes('SELLER_ADJUSTMENT'), 'Must support SELLER_ADJUSTMENT');
  assert.ok(invCode.includes('RESERVATION_CREATED'), 'Must use RESERVATION_CREATED on reserve');
  assert.ok(invCode.includes('RESERVATION_RELEASED'), 'Must use RESERVATION_RELEASED on release');
  assert.ok(invCode.includes('STOCK_CONSUMED'), 'Must use STOCK_CONSUMED on fulfill');
});

// Guard 64: adjustStockForStore and setStockForStore verify product is_active and return PRODUCT_INACTIVE if deactivated
checkGuard('inventory mutation methods verify product is_active and reject inactive products', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes('PRODUCT_INACTIVE'), 'TransactionalInventoryRepository must reject inactive products with PRODUCT_INACTIVE');
});

// Guard 65: Production server rejects stockCount with STORE_INVENTORY_WRITE_REQUIRED when caller has no authorized store
checkGuard('Production server rejects stockCount with STORE_INVENTORY_WRITE_REQUIRED when caller has no authorized store', () => {
  assert.ok(prodServerContent.includes('STORE_INVENTORY_WRITE_REQUIRED'), 'Production server must enforce STORE_INVENTORY_WRITE_REQUIRED');
  assert.ok(prodServerContent.includes('stockCount != null && !authorizedStoreId'), 'Must check stockCount without authorizedStoreId');
});

// Guard 66: Production inventory mutation methods strictly require canonical productId
checkGuard('inventory mutation methods strictly require canonical productId', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes('CANONICAL_PRODUCT_ID_REQUIRED'), 'Must enforce CANONICAL_PRODUCT_ID_REQUIRED');
  assert.ok(prodServerContent.includes('CANONICAL_PRODUCT_ID_REQUIRED'), 'Production server must require CANONICAL_PRODUCT_ID_REQUIRED on adjust');
});

// Guard 67: Inventory mutation methods strictly gate ADMIN_ADJUSTMENT by actor authority
checkGuard('inventory mutation methods gate ADMIN_ADJUSTMENT by actor authority', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes("trimmedReason === 'ADMIN_ADJUSTMENT' && !isAdmin"), 'Must reject ADMIN_ADJUSTMENT for non-admin actors');
});

// Guard 68: Inventory mutation methods strictly require explicit reason and NEVER synthesize audit reasons
checkGuard('inventory mutation methods strictly require explicit reason and forbid synthetic reasons', () => {
  const invClassMatch = repoContent.match(/class TransactionalInventoryRepository[\s\S]*?class LocalDevelopmentInventoryRepository/);
  assert.ok(invClassMatch, 'TransactionalInventoryRepository must exist');
  const invCode = invClassMatch[0];
  assert.ok(invCode.includes("!reason || typeof reason !== 'string' || !reason.trim()"), 'Must reject missing, non-string, or whitespace-only reason');
  assert.ok(!invCode.includes("delta >= 0 ? 'SELLER_RESTOCK' : 'SELLER_ADJUSTMENT'"), 'Must NOT synthesize SELLER_RESTOCK/SELLER_ADJUSTMENT when reason is omitted');
});

// Guard 69: Production server catalog mutation routes enforce mandatory reason
checkGuard('Production server catalog routes enforce mandatory explicit reason', () => {
  assert.ok(prodServerContent.includes("!body.reason || typeof body.reason !== 'string' || !body.reason.trim()"), 'Production server must enforce explicit reason check');
});

// Guard 70: Customer Web api-client.ts contains zero DEFAULT_MEDICINES and DEFAULT_ADDRESSES fake runtime fallbacks
checkGuard('Customer Web api-client contains ZERO DEFAULT_MEDICINES and DEFAULT_ADDRESSES fake fallbacks', () => {
  const apiClientPath = path.join(__dirname, '../apps/web/src/lib/api-client.ts');
  const apiClientContent = fs.readFileSync(apiClientPath, 'utf8');
  assert.ok(!apiClientContent.includes('DEFAULT_MEDICINES'), 'api-client.ts must not contain DEFAULT_MEDICINES fake fallback');
  assert.ok(!apiClientContent.includes('DEFAULT_ADDRESSES'), 'api-client.ts must not contain DEFAULT_ADDRESSES fake fallback');
});

// Guard 71: Customer Web Checkout and Orders contain zero fake OTP literals (e.g. 8492)
checkGuard('Customer Web contains ZERO fake OTP literals (8492)', () => {
  const checkoutPath = path.join(__dirname, '../apps/web/src/app/checkout/page.tsx');
  const ordersPath = path.join(__dirname, '../apps/web/src/app/orders/page.tsx');
  const checkoutContent = fs.readFileSync(checkoutPath, 'utf8');
  const ordersContent = fs.readFileSync(ordersPath, 'utf8');
  assert.ok(!checkoutContent.includes('8492'), 'Checkout must not contain fake 8492 OTP fallback');
  assert.ok(!ordersContent.includes('8492'), 'Orders must not contain fake 8492 OTP fallback');
});

// Guard 72: Customer Web Checkout and Orders contain zero hardcoded customer address fallbacks
checkGuard('Customer Web contains ZERO hardcoded customer address fallbacks', () => {
  const checkoutPath = path.join(__dirname, '../apps/web/src/app/checkout/page.tsx');
  const ordersPath = path.join(__dirname, '../apps/web/src/app/orders/page.tsx');
  const checkoutContent = fs.readFileSync(checkoutPath, 'utf8');
  const ordersContent = fs.readFileSync(ordersPath, 'utf8');
  assert.ok(!checkoutContent.includes('Apex Residency'), 'Checkout must not contain hardcoded Apex Residency fallback');
  assert.ok(!ordersContent.includes('Apex Residency'), 'Orders must not contain hardcoded Apex Residency fallback');
});

// Guard 73: Customer Web Checkout does not submit client-invented deliverySlaMins
checkGuard('Customer Web Checkout does not submit client-invented deliverySlaMins', () => {
  const checkoutPath = path.join(__dirname, '../apps/web/src/app/checkout/page.tsx');
  const checkoutContent = fs.readFileSync(checkoutPath, 'utf8');
  assert.ok(!checkoutContent.includes('deliverySlaMins: 10'), 'Checkout must not send client-invented deliverySlaMins: 10');
});

// Guard 74: Customer Web Login contains ZERO demo session bypass or synthetic fallback user
checkGuard('Customer Web Login contains ZERO demo session bypass or synthetic user', () => {
  const loginPath = path.join(__dirname, '../apps/web/src/app/auth/login/page.tsx');
  const loginContent = fs.readFileSync(loginPath, 'utf8');
  assert.ok(!loginContent.includes('using demo session'), 'Login must not contain demo session bypass');
  assert.ok(!loginContent.includes('usr_'), 'Login must not create synthetic usr_ fallback identities');
  assert.ok(!loginContent.includes('8f921ab0-0012-4412-9901-112233445566'), 'Login must not hardcode demo user ID on failure');
});

// Guard 75: Android SecurityGate contains ZERO embedded active demo access token
checkGuard('Android SecurityGate contains ZERO embedded active demo access token', () => {
  const securityGatePath = path.join(__dirname, '../apps/android/app/src/main/java/com/commerceos/android/security/SecurityGate.kt');
  const securityGateContent = fs.readFileSync(securityGatePath, 'utf8');
  assert.ok(!securityGateContent.includes('"jwt_access_token_demo"'), 'SecurityGate must not embed jwt_access_token_demo');
  assert.ok(securityGateContent.includes('private var activeAccessToken: String? = null'), 'SecurityGate must initialize activeAccessToken to null');
});

// Guard 76: Order Service contains ZERO seller_demo_001 production fallbacks
checkGuard('Order Service contains ZERO seller_demo_001 production fallbacks', () => {
  const orderControllerPath = path.join(__dirname, '../services/order/src/main/java/com/commerceos/order/controller/OrderController.java');
  const orderControllerContent = fs.readFileSync(orderControllerPath, 'utf8');
  assert.ok(!orderControllerContent.includes('seller_demo_001'), 'OrderController must not contain seller_demo_001 fallback');
});

// Guard 77: Identity Service AuthController strictly fails closed when SMS gateway is unconfigured
checkGuard('Identity Service AuthController strictly fails closed when SMS gateway is unconfigured', () => {
  const authControllerPath = path.join(__dirname, '../services/identity/src/main/java/com/commerceos/identity/controller/AuthController.java');
  const authControllerContent = fs.readFileSync(authControllerPath, 'utf8');
  assert.ok(!authControllerContent.includes('Offline/demo fallback when no SMS gateway is configured'), 'AuthController must not have offline OTP challenge fallback');
  assert.ok(authControllerContent.includes('!twoFactorSmsClient.isEnabled()'), 'AuthController must check if SMS gateway is enabled');
  assert.ok(authControllerContent.includes('HttpStatus.SERVICE_UNAVAILABLE') || authControllerContent.includes('status(503)'), 'Must return 503 when SMS gateway is missing');
});

// Guard 78: Production OrderController contains ZERO pricing constants/formulas
checkGuard('Production OrderController contains ZERO pricing constants/formulas', () => {
  const orderControllerPath = path.join(__dirname, '../services/order/src/main/java/com/commerceos/order/controller/OrderController.java');
  const orderControllerContent = fs.readFileSync(orderControllerPath, 'utf8');
  assert.ok(!orderControllerContent.includes('0.05'), 'OrderController must not contain 5% tax formula');
  assert.ok(!orderControllerContent.includes('new BigDecimal("2.50")'), 'OrderController must not contain 2.50 delivery fee formula');
  assert.ok(!orderControllerContent.includes('new BigDecimal("1.50")'), 'OrderController must not contain 1.50 cold chain formula');
  assert.ok(!orderControllerContent.includes('subtotal.add(calculatedTax)'), 'OrderController must not calculate grand totals; must consume authoritative PricingQuote');
});

console.log('\n================================================================');
console.log(`🏆 STATIC GUARD AUDIT COMPLETE: ${passed} PASSED, ${failed} FAILED`);
console.log('================================================================\n');

if (failed > 0) {
  process.exit(1);
}
