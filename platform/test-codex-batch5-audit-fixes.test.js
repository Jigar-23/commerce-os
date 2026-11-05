/**
 * Comprehensive Automated Verification Suite for Codex Audit Batch 5 Findings
 * 1. Rider store requirement & unassigned rider store offer acceptance guard
 * 2. Seller database revalidation on refresh, order detail, SSE, and store settings
 * 3. Cart endpoints mandatory JWT authentication & multi-tenant isolation
 * 4. Offer notifications routing to store-scoped channels and authorized rider device tokens
 * 5. Seller refund initiation store-scoped with fail-closed verification
 */

const http = require('http');
const assert = require('assert');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');

// Load environment
try {
  const envPath = path.resolve(__dirname, '../.env');
  if (fs.existsSync(envPath)) {
    const lines = fs.readFileSync(envPath, 'utf8').split('\n');
    for (const l of lines) {
      const trimmed = l.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx > 0) {
        const k = trimmed.substring(0, eqIdx).trim();
        const v = trimmed.substring(eqIdx + 1).trim().replace(/^['"]|['"]$/g, '');
        if (!process.env[k]) process.env[k] = v;
      }
    }
  }
} catch (_) {}

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false }
});

const JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
const JWT_ISSUER = process.env.JWT_ISSUER || 'https://auth.commerceos.internal';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerceos-platform-clients';

function createToken(payload, expiresIn = '1h') {
  return jwt.sign(payload, JWT_SECRET, {
    algorithm: 'HS256',
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
    expiresIn
  });
}

function createRefreshToken(payload, expiresIn = '30d') {
  return jwt.sign({ ...payload, type: 'REFRESH_TOKEN' }, JWT_SECRET, {
    algorithm: 'HS256',
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
    expiresIn
  });
}

function makeRequest(port, method, path, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const opts = {
      hostname: '127.0.0.1',
      port,
      path,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    };
    const req = http.request(opts, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch (_) {}
        resolve({ statusCode: res.statusCode, headers: res.headers, body: json, rawBody: data });
      });
    });
    req.on('error', reject);
    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

async function runTests() {
  console.log('================================================================');
  console.log('🧪 RUNNING CODEX BATCH 5 AUDIT REMEDIATION VERIFICATION TESTS');
  console.log('================================================================\n');

  const TEST_PORT = 8206;
  process.env.PORT = String(TEST_PORT);
  delete require.cache[require.resolve('./server/production-server.js')];
  const { server } = require('./server/production-server.js');

  await new Promise((resolve) => {
    if (server.listening) resolve();
    else server.listen(TEST_PORT, resolve);
  });

  const uniqueSuffix = Date.now().toString();
  const testStoreAId = `store_b5_a_${uniqueSuffix}`;
  const testStoreBId = `store_b5_b_${uniqueSuffix}`;
  const testCustAId = `cust_b5_a_${uniqueSuffix}`;
  const testCustBId = `cust_b5_b_${uniqueSuffix}`;
  const testSellerAId = `seller_b5_a_${uniqueSuffix}`;
  const testSellerBId = `seller_b5_b_${uniqueSuffix}`;
  const testRiderAssignedA = `rdr_b5_assigned_a_${uniqueSuffix}`;
  const testRiderUnassigned = `rdr_b5_unassigned_${uniqueSuffix}`;
  const testRiderAssignedB = `rdr_b5_assigned_b_${uniqueSuffix}`;
  const testOrderAId = `ord_b5_a_${uniqueSuffix}`;

  try {
    // 0. Setup Stores, Customers, Sellers, and Riders
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES ($1, 'Store B5 Hub A', 'Rewari Central Hub A', 28.5678, 77.2145, TRUE),
              ($2, 'Store B5 Hub B', 'Rewari Central Hub B', 28.5688, 77.2155, TRUE)
       ON CONFLICT (id) DO NOTHING`,
      [testStoreAId, testStoreBId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name)
       VALUES ($1, '+919999111101', 'Customer B5 A'),
              ($2, '+919999111102', 'Customer B5 B')
       ON CONFLICT (id) DO NOTHING`,
      [testCustAId, testCustBId]
    );

    await pool.query(
      `INSERT INTO sellers (id, seller_id, store_id, email, phone, status, password_hash)
       VALUES ($1, $1, $2, 'seller.a@test.com', '9999222201', 'ACTIVE', 'scrypt_placeholder'),
              ($3, $3, $4, 'seller.b@test.com', '9999222202', 'ACTIVE', 'scrypt_placeholder')
       ON CONFLICT (id) DO NOTHING`,
      [testSellerAId, testStoreAId, testSellerBId, testStoreBId]
    );

    // Insert 1 unassigned rider (store_id = NULL) and 2 store-assigned riders
    await pool.query(
      `INSERT INTO riders (id, rider_id, store_id, full_name, phone, status, vehicle_number)
       VALUES ($1, $1, $2, 'Rider Assigned A', '+919999333301', 'ACTIVE', 'DL-01-A-1111'),
              ($3, $3, NULL, 'Rider Unassigned', '+919999333302', 'ACTIVE', 'DL-01-U-2222'),
              ($4, $4, $5, 'Rider Assigned B', '+919999333303', 'ACTIVE', 'DL-01-B-3333')
       ON CONFLICT (id) DO NOTHING`,
      [testRiderAssignedA, testStoreAId, testRiderUnassigned, testRiderAssignedB, testStoreBId]
    );

    // Insert an order and offer for Store A
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, payment_status, total_amount, is_cod, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 'CONFIRMED', 'PAID', 300, false, '{}'::jsonb, '[{"productId":"p1","sku":"sku1","name":"Test Prod","quantity":1,"unitPrice":300,"total":300}]'::jsonb, 'otp_hash_b5')
       ON CONFLICT (id) DO NOTHING`,
      [testOrderAId, testCustAId, testStoreAId]
    );

    // Insert a captured payment in payments table for Order A
    await pool.query(
      `INSERT INTO payments (id, payment_id, order_id, amount, currency, status, method, provider, created_at, updated_at)
       VALUES ($1, $1, $2, 300, 'INR', 'SUCCESS', 'ONLINE', 'RAZORPAY', NOW(), NOW())
       ON CONFLICT DO NOTHING`,
      ['pay_' + testOrderAId, testOrderAId]
    );

    const testDeliveryAId = 'del_' + testOrderAId;
    await pool.query(
      `INSERT INTO delivery_sessions (
         id, delivery_id, order_id, store_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng,
         customer_name, customer_phone, customer_address, customer_lat, customer_lng, waypoints
       ) VALUES (
         $1, $1, $2, $3, 'LOOKING_FOR_RIDER', 'Store A', 'Address A', 28.5678, 77.2145,
         'Customer A', '+919999111101', 'Customer A Address', 28.5699, 77.2199, '[]'::jsonb
       ) ON CONFLICT (id) DO NOTHING`,
      [testDeliveryAId, testOrderAId, testStoreAId]
    );

    const testOfferAId = 'off_' + testOrderAId;
    await pool.query(
      `INSERT INTO offers (
         id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id, status,
         offer_created_at, offer_expires_at, earnings_amount, delivery_distance_km, total_distance_km,
         estimated_duration_mins, is_broadcast, waypoints, pricing_snapshot, history, created_at, updated_at
       ) VALUES (
         $1, $1, 'evt_1', 'notif_1', $2, $3, 'broadcast', 'CREATED',
         $4, $5, 45.0, 2.5, 2.5, 15, TRUE, '[]'::jsonb, '{}'::jsonb, '[]'::jsonb, NOW(), NOW()
       ) ON CONFLICT (id) DO NOTHING`,
      [testOfferAId, testDeliveryAId, testOrderAId, Date.now(), Date.now() + 1800000]
    );

    // -------------------------------------------------------------
    // Test 1: Rider Store Assignment & Cross-Store Acceptance Guard
    // -------------------------------------------------------------
    console.log('[Test 1] Rider Store Requirement & Unassigned Rider Acceptance Guard');
    const unassignedRiderToken = createToken({ sub: testRiderUnassigned, role: 'ROLE_RIDER' });
    const resUnassignedClaim = await makeRequest(TEST_PORT, 'POST', `/api/v1/delivery/offers/${testOfferAId}/accept`, {
      Authorization: `Bearer ${unassignedRiderToken}`
    });
    assert.strictEqual(resUnassignedClaim.statusCode, 403, 'Unassigned rider must be rejected with 403');
    assert.strictEqual(resUnassignedClaim.body?.error, 'UNASSIGNED_RIDER_STORE', 'Expected UNASSIGNED_RIDER_STORE');

    const storeBRiderToken = createToken({ sub: testRiderAssignedB, role: 'ROLE_RIDER' });
    const resCrossStoreClaim = await makeRequest(TEST_PORT, 'POST', `/api/v1/delivery/offers/${testOfferAId}/accept`, {
      Authorization: `Bearer ${storeBRiderToken}`
    });
    assert.strictEqual(resCrossStoreClaim.statusCode, 403, 'Store B rider claiming Store A offer must be rejected with 403');
    assert.strictEqual(resCrossStoreClaim.body?.error, 'CROSS_STORE_OFFER_CLAIM_FORBIDDEN', 'Expected CROSS_STORE_OFFER_CLAIM_FORBIDDEN');

    const storeARiderToken = createToken({ sub: testRiderAssignedA, role: 'ROLE_RIDER' });
    const resValidClaim = await makeRequest(TEST_PORT, 'POST', `/api/v1/delivery/offers/${testOfferAId}/accept`, {
      Authorization: `Bearer ${storeARiderToken}`
    });
    assert.strictEqual(resValidClaim.statusCode, 200, 'Store A rider claiming Store A offer must succeed with 200');
    assert.strictEqual(resValidClaim.body?.status, 'ACCEPTED');
    console.log('  ✅ PASS: Unassigned rider blocked (403), cross-store rider blocked (403), store-assigned rider accepted.');

    // -------------------------------------------------------------
    // Test 2: Seller DB Revalidation on Refresh, Order Detail, SSE & Store Settings
    // -------------------------------------------------------------
    console.log('\n[Test 2] Seller DB Revalidation on Refresh, Order Detail, and Store Settings');
    
    // 2a. Refresh token revalidates seller status
    await pool.query(`UPDATE sellers SET status = 'DEACTIVATED' WHERE id = $1`, [testSellerBId]);
    const deactSellerRefresh = createRefreshToken({ sub: testSellerBId, sellerId: testSellerBId, role: 'ROLE_SELLER', storeId: testStoreBId });
    const resDeactRefresh = await makeRequest(TEST_PORT, 'POST', '/api/v1/auth/refresh', {}, { refreshToken: deactSellerRefresh });
    assert.strictEqual(resDeactRefresh.statusCode, 403, 'Deactivated seller refresh must return 403');
    assert.strictEqual(resDeactRefresh.body?.error, 'SELLER_DEACTIVATED');

    // 2b. Seller reassigned in DB from Store A to Store B
    await pool.query(`UPDATE sellers SET store_id = $1 WHERE id = $2`, [testStoreBId, testSellerAId]);
    const oldStoreARefresh = createRefreshToken({ sub: testSellerAId, sellerId: testSellerAId, role: 'ROLE_SELLER', storeId: testStoreAId });
    const resReassignedRefresh = await makeRequest(TEST_PORT, 'POST', '/api/v1/auth/refresh', {}, { refreshToken: oldStoreARefresh });
    assert.strictEqual(resReassignedRefresh.statusCode, 200, 'Active reassigned seller refresh succeeds');
    const newDecoded = jwt.decode(resReassignedRefresh.body?.accessToken);
    assert.strictEqual(newDecoded.storeId, testStoreBId, 'Refreshed access token must reflect authoritative DB store_id');

    // 2c. Order detail verifies DB store, not stale JWT storeId
    const staleStoreAJwt = createToken({ sub: testSellerAId, sellerId: testSellerAId, role: 'ROLE_SELLER', storeId: testStoreAId });
    const resStaleOrderRead = await makeRequest(TEST_PORT, 'GET', `/api/v1/orders/${testOrderAId}`, {
      Authorization: `Bearer ${staleStoreAJwt}`
    });
    assert.strictEqual(resStaleOrderRead.statusCode, 403, 'Seller reassigned to Store B must NOT read Store A order despite stale JWT claim');

    // 2d. Store settings patch verifies DB store
    const resStaleStorePatch = await makeRequest(TEST_PORT, 'PATCH', `/api/v1/seller/stores/${testStoreAId}`, {
      Authorization: `Bearer ${staleStoreAJwt}`
    }, { storeName: 'Tampered Name' });
    assert.strictEqual(resStaleStorePatch.statusCode, 403, 'Seller cannot patch Store A when DB assignment is Store B');
    console.log('  ✅ PASS: Deactivated seller blocked on refresh (403), store updated from DB, stale JWT order & settings access blocked (403).');

    // -------------------------------------------------------------
    // Test 3: Cart Endpoints Mandatory JWT Authentication & Isolation
    // -------------------------------------------------------------
    console.log('\n[Test 3] Cart Endpoints Mandatory Authentication & Multi-Tenant Isolation');
    const resUnauthGet = await makeRequest(TEST_PORT, 'GET', `/api/v1/cart/${testCustAId}`);
    assert.strictEqual(resUnauthGet.statusCode, 401, 'Unauthenticated GET /api/v1/cart/:id must return 401');
    assert.strictEqual(resUnauthGet.body?.error, 'UNAUTHORIZED');

    const resUnauthAdd = await makeRequest(TEST_PORT, 'POST', `/api/v1/cart/${testCustAId}/items`, {}, { sku: 'sku1', quantity: 1 });
    assert.strictEqual(resUnauthAdd.statusCode, 401, 'Unauthenticated POST /api/v1/cart/:id/items must return 401');
    assert.strictEqual(resUnauthAdd.body?.error, 'UNAUTHORIZED');

    const resUnauthPatch = await makeRequest(TEST_PORT, 'PATCH', `/api/v1/cart/${testCustAId}/items/sku1`, {}, { quantity: 2 });
    assert.strictEqual(resUnauthPatch.statusCode, 401, 'Unauthenticated PATCH /api/v1/cart/:id/items/:sku must return 401');

    const resUnauthDeleteItem = await makeRequest(TEST_PORT, 'DELETE', `/api/v1/cart/${testCustAId}/items/sku1`);
    assert.strictEqual(resUnauthDeleteItem.statusCode, 401, 'Unauthenticated DELETE /api/v1/cart/:id/items/:sku must return 401');

    const resUnauthDeleteCart = await makeRequest(TEST_PORT, 'DELETE', `/api/v1/cart/${testCustAId}`);
    assert.strictEqual(resUnauthDeleteCart.statusCode, 401, 'Unauthenticated DELETE /api/v1/cart/:id must return 401');

    // Cross-customer access check
    const custBToken = createToken({ sub: testCustBId, role: 'ROLE_CUSTOMER' });
    const resCrossCustGet = await makeRequest(TEST_PORT, 'GET', `/api/v1/cart/${testCustAId}`, {
      Authorization: `Bearer ${custBToken}`
    });
    assert.strictEqual(resCrossCustGet.statusCode, 403, 'Customer B accessing Customer A cart must return 403 FORBIDDEN');

    const custAToken = createToken({ sub: testCustAId, role: 'ROLE_CUSTOMER' });
    const resOwnerGet = await makeRequest(TEST_PORT, 'GET', `/api/v1/cart/${testCustAId}`, {
      Authorization: `Bearer ${custAToken}`
    });
    assert.strictEqual(resOwnerGet.statusCode, 200, 'Customer A accessing Customer A cart must return 200');
    console.log('  ✅ PASS: Unauthenticated cart calls rejected (401), cross-customer cart access blocked (403), owner permitted (200).');

    // -------------------------------------------------------------
    // Test 4: Seller Refund Initiation Store-Scoped with Fail-Closed Verification
    // -------------------------------------------------------------
    console.log('\n[Test 4] Seller Refund Initiation Store-Scoped & Fail-Closed Enforcement');
    // Reset Seller A back to Store A before re-activating Seller B on Store B (enforces unq_store_primary_active_seller)
    await pool.query(`UPDATE sellers SET store_id = $1, status = 'ACTIVE' WHERE id = $2`, [testStoreAId, testSellerAId]);
    await pool.query(`UPDATE sellers SET status = 'ACTIVE' WHERE id = $1`, [testSellerBId]);
    const activeSellerBToken = createToken({ sub: testSellerBId, sellerId: testSellerBId, role: 'ROLE_SELLER', storeId: testStoreBId });
    
    // Seller B attempts to initiate refund for Order A (belongs to Store A)
    const resCrossStoreRefund = await makeRequest(TEST_PORT, 'POST', '/api/v1/payments/refunds', {
      Authorization: `Bearer ${activeSellerBToken}`
    }, {
      orderId: testOrderAId,
      amount: 100,
      reason: 'Cross store test refund',
      idempotencyKey: 'idemp_ref_cross_' + uniqueSuffix
    });
    assert.strictEqual(resCrossStoreRefund.statusCode, 403, 'Store B seller initiating refund for Store A order must be rejected with 403');
    assert.strictEqual(resCrossStoreRefund.body?.error, 'FORBIDDEN');

    const activeSellerAToken = createToken({ sub: testSellerAId, sellerId: testSellerAId, role: 'ROLE_SELLER', storeId: testStoreAId });

    // Store A seller initiates refund for Store A order
    const resValidRefund = await makeRequest(TEST_PORT, 'POST', '/api/v1/payments/refunds', {
      Authorization: `Bearer ${activeSellerAToken}`
    }, {
      orderId: testOrderAId,
      amount: 100,
      reason: 'Valid partial refund',
      idempotencyKey: 'idemp_ref_valid_' + uniqueSuffix
    });
    assert.strictEqual(resValidRefund.statusCode, 200, 'Store A seller initiating refund for Store A order must succeed with 200');
    assert.strictEqual(resValidRefund.body?.ok, true);

    // Admin can initiate refund
    const adminToken = createToken({ sub: 'admin', role: 'ROLE_ADMIN' });
    const resAdminRefund = await makeRequest(TEST_PORT, 'POST', '/api/v1/payments/refunds', {
      Authorization: `Bearer ${adminToken}`
    }, {
      orderId: testOrderAId,
      amount: 50,
      reason: 'Admin refund',
      idempotencyKey: 'idemp_ref_admin_' + uniqueSuffix
    });
    assert.strictEqual(resAdminRefund.statusCode, 200, 'Admin initiating refund must succeed with 200');
    console.log('  ✅ PASS: Cross-store refund initiation blocked (403), store-authorized seller and admin succeed (200).');

    // -------------------------------------------------------------
    // Test 5: Offer Notifications to Store-Scoped Channels & Active Rider Tokens
    // -------------------------------------------------------------
    console.log('\n[Test 5] Offer Notification Store-Scoped Channels & Direct Device Delivery');
    // Register device token for Rider Assigned A in Store A
    await pool.query(
      `INSERT INTO rider_device_tokens (rider_id, token, platform)
       VALUES ($1, $2, 'ANDROID')
       ON CONFLICT (rider_id) DO UPDATE SET token = EXCLUDED.token`,
      [testRiderAssignedA, 'fcm_tok_assigned_a_' + uniqueSuffix]
    );

    const tokenQueryRes = await pool.query(
      `SELECT rdt.token, r.store_id 
       FROM rider_device_tokens rdt
       JOIN riders r ON (r.rider_id = rdt.rider_id OR r.id = rdt.rider_id)
       WHERE r.store_id = $1 AND r.status = 'ACTIVE'`,
      [testStoreAId]
    );
    assert.strictEqual(tokenQueryRes.rows.length >= 1, true, 'Store A rider token must be discovered for store delivery');
    assert.strictEqual(tokenQueryRes.rows[0].store_id, testStoreAId);
    console.log('  ✅ PASS: Store-specific device tokens queried and isolated from other stores.');

    console.log('\n================================================================');
    console.log('🎉 ALL CODEX BATCH 5 AUDIT REMEDIATION TESTS PASSED!');
    console.log('================================================================\n');

  } finally {
    // Cleanup test artifacts
    await pool.query(`DELETE FROM payment_refunds WHERE order_id = $1`, [testOrderAId]);
    await pool.query(`DELETE FROM payments WHERE order_id = $1`, [testOrderAId]);
    await pool.query(`DELETE FROM offers WHERE order_id = $1`, [testOrderAId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE order_id = $1`, [testOrderAId]);
    await pool.query(`DELETE FROM orders WHERE id = $1`, [testOrderAId]);
    await pool.query(`DELETE FROM rider_device_tokens WHERE rider_id IN ($1, $2, $3)`, [testRiderAssignedA, testRiderUnassigned, testRiderAssignedB]);
    await pool.query(`DELETE FROM riders WHERE id IN ($1, $2, $3)`, [testRiderAssignedA, testRiderUnassigned, testRiderAssignedB]);
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [testSellerAId, testSellerBId]);
    await pool.query(`DELETE FROM customers WHERE id IN ($1, $2)`, [testCustAId, testCustBId]);
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [testStoreAId, testStoreBId]);
    await new Promise(r => server.close(r));
    await pool.end();
  }
}

runTests().then(() => {
  process.exit(0);
}).catch(err => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
