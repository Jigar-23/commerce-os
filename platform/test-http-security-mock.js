/**
 * Commerce OS — HTTP Security Suite [Profile: Mock Server Development Runtime]
 * 
 * Verifies that the mock development gateway adheres to identical multi-tenant authorization contracts.
 */

const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const { spawn } = require('child_process');
const path = require('path');

console.log('================================================================');
console.log('🧪 RUNNING HTTP_SECURITY_MOCK GATE SUITE');
console.log('================================================================\n');

let passedCount = 0;
let failedCount = 0;

function test(name, fn) {
  try {
    const res = fn();
    if (res && typeof res.then === 'function') {
      return res.then(() => {
        console.log(`  ✅ PASS: [MOCK] ${name}`);
        passedCount++;
      }).catch((err) => {
        console.error(`  ❌ FAIL: [MOCK] ${name}`);
        console.error(`     ${err.stack || err.message}`);
        failedCount++;
      });
    }
    console.log(`  ✅ PASS: [MOCK] ${name}`);
    passedCount++;
    return Promise.resolve();
  } catch (err) {
    console.error(`  ❌ FAIL: [MOCK] ${name}`);
    console.error(`     ${err.stack || err.message}`);
    failedCount++;
    return Promise.resolve();
  }
}

const JWT_SECRET = 'commerceos_master_jwt_secret_key_2026_production';
const JWT_ISSUER = 'commerce-os-auth';
const JWT_AUDIENCE = 'commerce-os-api';

function makeJwt(payload, secret = JWT_SECRET) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({
    iss: JWT_ISSUER,
    aud: JWT_AUDIENCE,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
    ...payload
  })).toString('base64url');
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

function httpRequest(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        let parsed = null;
        try { parsed = JSON.parse(data); } catch { parsed = data; }
        resolve({ status: res.statusCode, headers: res.headers, data: parsed });
      });
    });
    req.on('error', reject);
    if (postData) {
      req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    req.end();
  });
}

const TEST_PORT = 8083;

async function waitForServerReady(port = TEST_PORT, retries = 40) {
  for (let i = 0; i < retries; i++) {
    try {
      const res = await httpRequest({ hostname: '127.0.0.1', port, path: '/api/v1/orders/health', method: 'GET' });
      return true;
    } catch {
      await new Promise(r => setTimeout(r, 100));
    }
  }
  return false;
}

async function runMockHttpSecurityTests() {
  let serverProcess = null;

  serverProcess = spawn(process.execPath, [path.join(__dirname, 'mock-server.js')], {
    env: { ...process.env, PORT: String(TEST_PORT), JWT_SECRET, COMMERCEOS_ENV: 'local_test', COMMERCEOS_PERSISTENCE_MODE: 'local' },
    stdio: 'pipe'
  });
  const isReady = await waitForServerReady(TEST_PORT, 40);
  if (!isReady) {
    throw new Error('Mock server failed to start on port ' + TEST_PORT);
  }

  try {
    const jwtCustA = makeJwt({ sub: 'cust_alpha_01', userId: 'cust_alpha_01', role: 'ROLE_CUSTOMER', roles: ['ROLE_CUSTOMER'] });

    await test('Unauthenticated call returns 401', async () => {
      const res = await httpRequest({
        hostname: '127.0.0.1', port: TEST_PORT, path: '/api/v1/orders', method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, { items: [] });
      assert.strictEqual(res.status, 401);
    });

    await test('Unsupported payment method returns 400 INVALID_PAYMENT_METHOD', async () => {
      const res = await httpRequest({
        hostname: '127.0.0.1', port: TEST_PORT, path: '/api/v1/orders', method: 'POST',
        headers: { 'Authorization': `Bearer ${jwtCustA}`, 'Content-Type': 'application/json' }
      }, {
        addressId: 'addr_cust_alpha_01_home',
        paymentMethod: 'DOGECOIN_MEME_PAY',
        items: [{ sku: 'SKU-DOLO-650', quantity: 1 }]
      });
      assert.ok([400, 422].includes(res.status), `Expected 400/422, got ${res.status}`);
    });

    console.log(`\n🏆 MOCK HTTP SECURITY TESTS COMPLETE: ${passedCount} PASSED, ${failedCount} FAILED\n`);
    if (failedCount > 0) process.exit(1);
  } finally {
    if (serverProcess) {
      try { serverProcess.kill('SIGKILL'); } catch (_) {}
    }
  }
}

if (require.main === module) {
  runMockHttpSecurityTests().then(() => process.exit(0)).catch(err => {
    console.error('Fatal Mock HTTP security test error:', err);
    process.exit(1);
  });
}

module.exports = { runMockHttpSecurityTests };
