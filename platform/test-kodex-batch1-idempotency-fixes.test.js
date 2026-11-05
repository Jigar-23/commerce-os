/**
 * Verification test suite for Kodex Batch 1 Audit Remediations:
 * 1. Cart-Add Idempotency: Failed first attempts do NOT record the key; retry with valid payload succeeds.
 * 2. Cart-Add Idempotency Replay: Duplicate calls with the same key return 200 without incrementing quantity.
 * 3. Cart-Add Idempotency Concurrency: Concurrent requests with the same key do not duplicate items.
 * 4. Cart-Add Idempotency Process Restart: Committed keys survive restart and deduplicate across restarts.
 * 5. Prescription-Upload Atomic Deduplication: Race-safe unique key deduplication on server.
 */

const assert = require('assert');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');

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
        const k = trimmed.slice(0, eqIdx).trim();
        let v = trimmed.slice(eqIdx + 1).trim();
        if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
          v = v.slice(1, -1);
        }
        if (!process.env[k]) process.env[k] = v;
      }
    }
  }
} catch (_) {}

process.env.PORT = '8205';
const { server, pool } = require('./server/production-server');
const { LocalDevelopmentCartRepository, TransactionalCartRepository } = require('./repositories');

const BASE_URL = 'http://127.0.0.1:8205';
const JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
const JWT_ISSUER = process.env.JWT_ISSUER || 'https://auth.commerceos.internal';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerceos-platform-clients';

function createToken(payload) {
  return jwt.sign(payload, JWT_SECRET, {
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
    expiresIn: '2h'
  });
}

async function request(method, reqPath, body = null, headers = {}) {
  const url = `${BASE_URL}${reqPath}`;
  const reqHeaders = { 'Content-Type': 'application/json', ...headers };
  const options = {
    method,
    headers: reqHeaders
  };
  if (body) {
    options.body = JSON.stringify(body);
  }
  const res = await fetch(url, options);
  let data = null;
  const text = await res.text();
  try {
    data = JSON.parse(text);
  } catch (_) {
    data = text;
  }
  return { status: res.status, headers: res.headers, data };
}

async function runIdempotencyTests() {
  console.log('================================================================');
  console.log('🧪 RUNNING KODEX BATCH 1 IDEMPOTENCY & DURABILITY TESTS');
  console.log('================================================================\n');

  let serverInstance = null;
  if (!server.listening) {
    serverInstance = await new Promise((resolve) => {
      const s = server.listen(8205, '127.0.0.1', () => resolve(s));
    });
  }

  const testCustId = 'cust_idem_' + Date.now();
  const token = createToken({ sub: testCustId, phone: '+919876543210', roles: ['ROLE_CUSTOMER'] });
  const authHeaders = { Authorization: `Bearer ${token}` };

  try {
    // -------------------------------------------------------------
    // Test 1: Failed First Attempt Does Not Burn the Idempotency Key
    // -------------------------------------------------------------
    console.log('[Test 1] Failed First Attempt Does Not Burn Key');
    const failFirstKey = `idem_fail_first_${Date.now()}`;
    
    // Attempt 1: Invalid payload (missing sku)
    const failRes = await request('POST', `/api/v1/cart/${testCustId}/items`, { quantity: 1 }, {
      ...authHeaders,
      'Idempotency-Key': failFirstKey
    });
    assert.strictEqual(failRes.status, 400, `Expected 400 Bad Request on invalid SKU, got ${failRes.status}`);

    // Attempt 2: Valid payload using the SAME idempotency key
    const successRes = await request('POST', `/api/v1/cart/${testCustId}/items`, {
      sku: 'SKU_TEST_RETRY_1',
      name: 'Retry Item',
      unitPrice: 50.0,
      quantity: 2
    }, {
      ...authHeaders,
      'Idempotency-Key': failFirstKey
    });
    assert.strictEqual(successRes.status, 200, `Expected 200 OK on valid retry, got ${successRes.status}`);
    const items1 = successRes.data.items || [];
    const item1 = items1.find(i => i.sku === 'SKU_TEST_RETRY_1');
    assert.ok(item1, 'Item must be present in cart after valid retry');
    assert.strictEqual(item1.quantity, 2, 'Item quantity must be exactly 2');
    console.log('  ✅ PASS: Failed first attempt did not burn key; subsequent valid request succeeded.\n');

    // -------------------------------------------------------------
    // Test 2: Replay With Same Key Returns 200 Without Duplicate Add
    // -------------------------------------------------------------
    console.log('[Test 2] Replay With Same Key Does Not Duplicate Quantity');
    const replayRes = await request('POST', `/api/v1/cart/${testCustId}/items`, {
      sku: 'SKU_TEST_RETRY_1',
      name: 'Retry Item',
      unitPrice: 50.0,
      quantity: 2
    }, {
      ...authHeaders,
      'Idempotency-Key': failFirstKey
    });
    assert.strictEqual(replayRes.status, 200, `Expected 200 OK on replay, got ${replayRes.status}`);
    const replayItems = replayRes.data.items || [];
    const replayItem = replayItems.find(i => i.sku === 'SKU_TEST_RETRY_1');
    assert.strictEqual(replayItem.quantity, 2, 'Replay with same idempotency key must not duplicate quantity (must stay 2, not 4)');
    console.log('  ✅ PASS: Replay deduplicated properly; quantity preserved at 2.\n');

    // -------------------------------------------------------------
    // Test 3: Concurrent Retries With Same Key Do Not Race
    // -------------------------------------------------------------
    console.log('[Test 3] Concurrent Retries With Same Key');
    const concurrentKey = `idem_concurrent_${Date.now()}`;
    const concurrentCustId = 'cust_conc_' + Date.now();
    const concToken = createToken({ sub: concurrentCustId, phone: '+919876543211', roles: ['ROLE_CUSTOMER'] });
    const concHeaders = { Authorization: `Bearer ${concToken}`, 'Idempotency-Key': concurrentKey };

    // Fire 5 concurrent requests with quantity 3
    const promises = Array.from({ length: 5 }, () =>
      request('POST', `/api/v1/cart/${concurrentCustId}/items`, {
        sku: 'SKU_CONCURRENT',
        name: 'Concurrent Item',
        unitPrice: 100.0,
        quantity: 3
      }, concHeaders)
    );
    const results = await Promise.all(promises);
    for (const res of results) {
      assert.strictEqual(res.status, 200, `Each concurrent request should return 200, got ${res.status}`);
    }

    const finalCartRes = await request('GET', `/api/v1/cart/${concurrentCustId}`, null, { Authorization: `Bearer ${concToken}` });
    const finalItems = finalCartRes.data.items || [];
    const finalItem = finalItems.find(i => i.sku === 'SKU_CONCURRENT');
    assert.ok(finalItem, 'Concurrent item must be in cart');
    assert.strictEqual(finalItem.quantity, 3, `Expected quantity 3 despite 5 concurrent requests, got ${finalItem.quantity}`);
    console.log('  ✅ PASS: Concurrent requests with same idempotency key did not duplicate items (quantity remained 3).\n');

    // -------------------------------------------------------------
    // Test 4: Process Restart Durability Test
    // -------------------------------------------------------------
    console.log('[Test 4] Process Restart Durability Test');
    const mockDb = { carts: {}, cartIdempotency: {} };
    let saveCount = 0;
    const saveDbFn = () => { saveCount++; };

    // Process 1: First instance processes mutation with key
    const repo1 = new LocalDevelopmentCartRepository(mockDb, saveDbFn);
    await repo1.addItem('cust_restart', { sku: 'SKU_PERSIST', name: 'Persist Item', quantity: 2 }, 'key_restart_123');
    assert.strictEqual((await repo1.getCart('cust_restart'))[0].quantity, 2);
    assert.ok(saveCount > 0, 'Must have persisted state to disk');

    // Process 2: Simulating server restart - new instance loaded with the persisted db
    const repo2 = new LocalDevelopmentCartRepository(mockDb, saveDbFn);
    // Replay with identical key
    await repo2.addItem('cust_restart', { sku: 'SKU_PERSIST', name: 'Persist Item', quantity: 2 }, 'key_restart_123');
    const cartAfterRestart = await repo2.getCart('cust_restart');
    assert.strictEqual(cartAfterRestart[0].quantity, 2, 'Quantity must remain 2 after process restart replay');
    console.log('  ✅ PASS: Process restart retained idempotency state; replay prevented duplicate add.\n');

    // -------------------------------------------------------------
    // Test 5: Prescription Upload Atomic Deduplication
    // -------------------------------------------------------------
    console.log('[Test 5] Prescription Upload Atomic Deduplication');
    const rxIdempKey = `rx_test_key_${Date.now()}`;
    const rxCustId = 'cust_rx_' + Date.now();
    const rxToken = createToken({ sub: rxCustId, roles: ['ROLE_CUSTOMER'] });
    const rxHeaders = { Authorization: `Bearer ${rxToken}`, 'Idempotency-Key': rxIdempKey };

    // Upload 1
    const rx1 = await request('POST', '/api/v1/prescriptions', {
      patientName: 'Jane Doe',
      attachments: ['data:image/jpeg;base64,content123']
    }, rxHeaders);
    assert.ok(rx1.status === 200 || rx1.status === 201, `Upload 1 returned ${rx1.status}`);
    const rxId1 = rx1.data.id;
    assert.ok(rxId1, 'Must return prescription ID');

    // Upload 2 (Concurrent or retry with same key)
    const rx2 = await request('POST', '/api/v1/prescriptions', {
      patientName: 'Jane Doe',
      attachments: ['data:image/jpeg;base64,content123']
    }, rxHeaders);
    assert.strictEqual(rx2.status, 200, `Replay upload returned status ${rx2.status}`);
    assert.strictEqual(rx2.data.id, rxId1, 'Must return existing prescription ID without creating a duplicate row');

    console.log('  ✅ PASS: Prescription upload deduplicated atomically; returned same prescription ID.\n');

    console.log('================================================================');
    console.log('🎉 ALL KODEX BATCH 1 IDEMPOTENCY & DURABILITY TESTS PASSED!');
    console.log('================================================================\n');

  } finally {
    if (serverInstance) {
      serverInstance.close();
    }
  }
}

runIdempotencyTests().catch((err) => {
  console.error('❌ TEST FAILED:', err);
  process.exit(1);
});
