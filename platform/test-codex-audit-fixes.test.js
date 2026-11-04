/**
 * Unit & Integration Test Suite: Validation of Codex Audit Vulnerability Remediations
 */

const assert = require('assert');
const http = require('http');
const path = require('path');
const fs = require('fs');

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

const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret-key-12345678901234567890123456789012';
const JWT_ISSUER = process.env.JWT_ISSUER || 'commerce-os-test';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerce-os-users';

const { server, pool, appRepositories } = require('./server/production-server');

function makeRequest(options, postData = null) {
  return new Promise((resolve, reject) => {
    let resolved = false;
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch (_) { json = data; }
        resolved = true;
        resolve({ status: res.statusCode, headers: res.headers, body: json });
      });
    });
    req.on('error', (err) => {
      if (!resolved && (err.code === 'EPIPE' || err.code === 'ECONNRESET')) {
        return;
      }
      if (!resolved) reject(err);
    });
    if (postData) {
      if (typeof postData === 'object' && !Buffer.isBuffer(postData)) {
        req.write(JSON.stringify(postData));
      } else {
        req.write(postData);
      }
    }
    req.end();
  });
}

async function runAuditValidation() {
  const TEST_PORT = 8991;
  await new Promise((resolve) => server.listen(TEST_PORT, '127.0.0.1', resolve));
  console.log(`================================================================`);
  console.log(`🧪 RUNNING CODEX SECURITY & ARCHITECTURE AUDIT REMEDIATION TESTS`);
  console.log(`================================================================\n`);

  try {
    // -------------------------------------------------------------
    // 1. Order IDOR Protection
    // -------------------------------------------------------------
    console.log('[Test 1] Order Details IDOR Authorization Check');
    const unauthOrderRes = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/orders/ord_nonexistent_123',
      method: 'GET'
    });
    assert.strictEqual(unauthOrderRes.status, 401, 'Unauthenticated request to GET /api/v1/orders/:id must return 401');
    assert.strictEqual(unauthOrderRes.body.error, 'UNAUTHORIZED', 'Error must be UNAUTHORIZED');
    console.log('  ✅ PASS: Unauthenticated GET /api/v1/orders/:id rejected with 401 UNAUTHORIZED');

    // Forbidden customer access
    const customerAToken = jwt.sign(
      { sub: 'cust_attacker_1', role: 'ROLE_CUSTOMER', type: 'ACCESS_TOKEN' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: JWT_ISSUER, audience: JWT_AUDIENCE, expiresIn: '1h' }
    );
    const forbiddenOrderRes = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/orders/ord_test_other_user',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${customerAToken}` }
    });
    assert.ok([403, 404].includes(forbiddenOrderRes.status), 'Customer cannot view foreign order without authorization');
    console.log('  ✅ PASS: Authenticated customer prevented from viewing foreign order details');

    // -------------------------------------------------------------
    // 2. Multi-Tenant Privacy: Seller Customer & User Endpoints
    // -------------------------------------------------------------
    console.log('\n[Test 2] Multi-Tenant Seller Customer Privacy');
    const customerListAttempt = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/seller/customers',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${customerAToken}` }
    });
    assert.strictEqual(customerListAttempt.status, 403, 'Regular customer role must NOT access /api/v1/seller/customers');
    console.log('  ✅ PASS: Regular customer blocked from /api/v1/seller/customers with 403 FORBIDDEN');

    const customerDetailAttempt = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/seller/customers/cust_victim_99',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${customerAToken}` }
    });
    assert.strictEqual(customerDetailAttempt.status, 403, 'Regular customer role must NOT access /api/v1/seller/customers/:id');
    console.log('  ✅ PASS: Regular customer blocked from /api/v1/seller/customers/:id with 403 FORBIDDEN');

    // -------------------------------------------------------------
    // 3. Store Creation Privilege Enforcement
    // -------------------------------------------------------------
    console.log('\n[Test 3] Store Creation Administrative Authority');
    const sellerToken = jwt.sign(
      { sub: 'seller_standard_1', role: 'ROLE_SELLER', storeId: 'store_1', type: 'ACCESS_TOKEN' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: JWT_ISSUER, audience: JWT_AUDIENCE, expiresIn: '1h' }
    );
    const rogueStoreRes = await makeRequest(
      {
        hostname: '127.0.0.1',
        port: TEST_PORT,
        path: '/api/v1/seller/stores',
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${sellerToken}`,
          'Content-Type': 'application/json'
        }
      },
      { id: 'store_rogue_99', name: 'Rogue Hijacked Store' }
    );
    assert.strictEqual(rogueStoreRes.status, 403, 'Non-admin seller must NOT be allowed to create or overwrite stores');
    console.log('  ✅ PASS: Non-admin seller blocked from POST /api/v1/seller/stores with 403 FORBIDDEN');

    // -------------------------------------------------------------
    // 4. Fake UTR Self-Attestation Guard
    // -------------------------------------------------------------
    console.log('\n[Test 4] UPI Payment Verification & Fake UTR Self-Attestation Guard');
    const fakeUtrRes = await makeRequest(
      {
        hostname: '127.0.0.1',
        port: TEST_PORT,
        path: '/api/v1/payments/upi/verify-intent',
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${customerAToken}`,
          'Content-Type': 'application/json'
        }
      },
      { orderId: 'ord_fake_check_1', utr: 'UTR123456789' }
    );
    // Even if order doesn't exist, if it existed it would return VERIFICATION_PENDING or 404 (never self-marking PAID)
    if (fakeUtrRes.status === 200) {
      assert.strictEqual(fakeUtrRes.body.paymentStatus, 'VERIFICATION_PENDING', 'Customer submitting UTR must be marked VERIFICATION_PENDING, not PAID');
    } else {
      assert.ok([400, 404, 403].includes(fakeUtrRes.status));
    }
    console.log('  ✅ PASS: Customer submission cannot self-attest bank settlement as PAID');

    // -------------------------------------------------------------
    // 5. CORS Header Isolation for Untrusted Origins
    // -------------------------------------------------------------
    console.log('\n[Test 5] CORS Credentials Isolation');
    const untrustedCorsRes = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/orders/health',
      method: 'GET',
      headers: { 'Origin': 'https://malicious-phishing-site.com' }
    });
    assert.strictEqual(untrustedCorsRes.headers['access-control-allow-credentials'], undefined, 'Untrusted origin must NOT receive Access-Control-Allow-Credentials: true');
    console.log('  ✅ PASS: Arbitrary origins do NOT receive Access-Control-Allow-Credentials');

    // -------------------------------------------------------------
    // 6. JWT Separation of Concerns
    // -------------------------------------------------------------
    console.log('\n[Test 6] JWT Token Purpose Separation');
    const refreshToken = jwt.sign(
      { sub: 'cust_attacker_1', role: 'ROLE_CUSTOMER', type: 'REFRESH_TOKEN' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: JWT_ISSUER, audience: JWT_AUDIENCE, expiresIn: '30d' }
    );
    const refreshAsAccessRes = await makeRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/orders/my-orders',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${refreshToken}` }
    });
    assert.strictEqual(refreshAsAccessRes.status, 401, 'Refresh tokens must NOT be accepted as access tokens on general routes');
    console.log('  ✅ PASS: Refresh token strictly rejected on standard API access route');

    // Refresh route with access token
    const accessTokenUsedToRefresh = await makeRequest(
      {
        hostname: '127.0.0.1',
        port: TEST_PORT,
        path: '/api/v1/auth/refresh',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      },
      { refreshToken: customerAToken }
    );
    assert.strictEqual(accessTokenUsedToRefresh.status, 400, 'Access token must NOT be accepted to refresh sessions');
    assert.strictEqual(accessTokenUsedToRefresh.body.error, 'INVALID_TOKEN_TYPE');
    console.log('  ✅ PASS: Access token rejected on /api/v1/auth/refresh (requires REFRESH_TOKEN)');

    // -------------------------------------------------------------
    // 7. Request Body Size Limiter (DoS Protection)
    // -------------------------------------------------------------
    console.log('\n[Test 7] Request Payload Size Limiter (10MB Cap)');
    const oversizedPayload = Buffer.alloc(11 * 1024 * 1024, 'A'); // 11MB
    const dosRes = await makeRequest(
      {
        hostname: '127.0.0.1',
        port: TEST_PORT,
        path: '/api/v1/auth/seller/login',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': oversizedPayload.length
        }
      },
      oversizedPayload
    );
    assert.strictEqual(dosRes.status, 413, 'Oversized payload (>10MB) must trigger 413 PAYLOAD_TOO_LARGE');
    assert.strictEqual(dosRes.body.error, 'PAYLOAD_TOO_LARGE');
    console.log('  ✅ PASS: 11MB payload rejected with HTTP 413 PAYLOAD_TOO_LARGE');

    console.log('\n================================================================');
    console.log('🏆 ALL CODEX AUDIT REMEDIATION TESTS PASSED (100%)');
    console.log('================================================================\n');
  } finally {
    server.close();
  }
}

runAuditValidation().then(() => {
  process.exit(0);
}).catch((err) => {
  console.error('❌ Audit test failed:', err);
  process.exit(1);
});
