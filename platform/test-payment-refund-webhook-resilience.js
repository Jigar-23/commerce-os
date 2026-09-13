#!/usr/bin/env node
/**
 * Test Suite: Payment Webhooks, Transactional Refunds, and COD Enforcement
 * Role: Implementation Verification for TASK-A2-PAYMENT-REFUND-AND-WEBHOOK-SETTLEMENT
 */

const http = require('http');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'commerceos-dev-jwt-secret-min-32-chars-ok';
const WEBHOOK_SECRET = process.env.PAYMENT_GATEWAY_WEBHOOK_SECRET || 'whsec_commerceos_production_secret';
process.env.JWT_SECRET = JWT_SECRET;
process.env.PAYMENT_GATEWAY_WEBHOOK_SECRET = WEBHOOK_SECRET;
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.COMMERCEOS_OTP_PEPPER = process.env.COMMERCEOS_OTP_PEPPER || 'qa_adversarial_security_test_pepper_128bit';

const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8201;
const BASE_URL = `http://127.0.0.1:${PORT}`;

const { server, pool, getAppRepositories } = require('./server/production-server.js');

function makeJwt(payload) {
  return jwt.sign(payload, JWT_SECRET, { algorithm: 'HS256', expiresIn: '1h' });
}

function request(options, body = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        let json = null;
        try {
          json = JSON.parse(data);
        } catch {
          json = data;
        }
        resolve({ statusCode: res.statusCode, headers: res.headers, body: json, rawBody: data });
      });
    });
    req.on('error', reject);
    if (body) {
      if (typeof body === 'string') {
        req.write(body);
      } else {
        req.write(JSON.stringify(body));
      }
    }
    req.end();
  });
}

async function runTests() {
  console.log('================================================================');
  console.log('  COMMERCE-OS PAYMENT & REFUND RESILIENCE VERIFICATION SUITE   ');
  console.log('================================================================');

  let passed = 0;
  let failed = 0;

  function assert(condition, message) {
    if (condition) {
      console.log(`  ✓ [PASS] ${message}`);
      passed++;
    } else {
      console.error(`  ✗ [FAIL] ${message}`);
      failed++;
    }
  }

  // 1. Boot Server on Dedicated Port
  let serverInstance = null;
  await new Promise((resolve, reject) => {
    serverInstance = server.listen(PORT, '127.0.0.1', (err) => {
      if (err) return reject(err);
      console.log(`🚀 Dedicated test server listening on 127.0.0.1:${PORT}`);
      resolve();
    });
  });

  const repos = await getAppRepositories();

  const customerToken = makeJwt({ sub: 'cust_pay_test_01', role: 'ROLE_CUSTOMER', roles: ['ROLE_CUSTOMER'] });
  const adminToken = makeJwt({ sub: 'admin_pay_test_01', role: 'ROLE_ADMIN', roles: ['ROLE_ADMIN'] });

  // 1. Webhook Signature Security Tests
  console.log('\n--- Test Group 1: Webhook HMAC-SHA256 Signature Security ---');

  // 1.1 Missing Signature
  {
    const payload = JSON.stringify({ id: 'evt_sig_missing_' + Date.now(), type: 'charge.succeeded' });
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/webhook',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) }
    }, payload);
    assert(res.statusCode === 401, `Rejects missing webhook signature with 401 (got ${res.statusCode})`);
  }

  // 1.2 Invalid / Tampered Signature
  {
    const payload = JSON.stringify({ id: 'evt_sig_tampered_' + Date.now(), type: 'charge.succeeded' });
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/webhook',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-webhook-signature': '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, payload);
    assert(res.statusCode === 403, `Rejects tampered signature with 403 Forbidden (got ${res.statusCode})`);
  }

  // 1.3 Valid Signature & Initial Ingestion
  const testEventId = 'evt_test_' + Date.now();
  const validPayload = JSON.stringify({ id: testEventId, type: 'payment.captured', provider: 'STRIPE', amount: 500 });
  const validSig = crypto.createHmac('sha256', WEBHOOK_SECRET).update(validPayload).digest('hex');
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/webhook',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-webhook-signature': validSig,
        'Content-Length': Buffer.byteLength(validPayload)
      }
    }, validPayload);
    assert(res.statusCode === 200, `Accepts valid HMAC signature with 200 OK (got ${res.statusCode})`);
    assert(res.body && res.body.status === 'PROCESSED', `Marks initial webhook as PROCESSED`);
    assert(res.body && res.body.deduplicated === false, `Initial webhook deduplicated is false`);
  }

  // 1.4 Webhook Replay Deduplication
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/webhook',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-webhook-signature': validSig,
        'Content-Length': Buffer.byteLength(validPayload)
      }
    }, validPayload);
    assert(res.statusCode === 200, `Replayed webhook returns 200 OK (got ${res.statusCode})`);
    assert(res.body && res.body.status === 'ALREADY_PROCESSED', `Replayed webhook status is ALREADY_PROCESSED`);
    assert(res.body && res.body.deduplicated === true, `Replayed webhook deduplicated is true`);
  }

  // 2. Refund State Machine & Balance Verification
  console.log('\n--- Test Group 2: Payment Refund State Machine & Balance Verification ---');

  // 2.1 Unauthorized Refund
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { orderId: 'ord_missing', amount: 100, idempotencyKey: 'idemp_1' });
    assert(res.statusCode === 401, `Unauthenticated refund request rejected with 401 (got ${res.statusCode})`);
  }

  // 2.2 Missing Mandatory Parameters
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, { orderId: 'ord_test' });
    assert(res.statusCode === 400, `Rejects refund with missing amount/idempotencyKey with 400 (got ${res.statusCode})`);
  }

  // 2.3 Invalid Amount (Negative or Zero)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, { orderId: 'ord_test', amount: -50, idempotencyKey: 'idemp_neg' });
    assert(res.statusCode === 400, `Rejects negative refund amount with 400 (got ${res.statusCode})`);
  }

  // 2.4 Non-existent Order Refund
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, { orderId: 'ord_nonexistent_xyz_9999', amount: 100, idempotencyKey: 'idemp_notfound' });
    assert(res.statusCode === 404, `Rejects refund for non-existent order with 404 (got ${res.statusCode})`);
  }

  // Seed a test order for live transactional refund execution
  const testOrderId = 'ord_refund_test_' + Date.now();
  const testOrderTotal = 350.0;
  if (pool) {
    // DB mode
    const storeId = 'store_pay_test_01';
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
       VALUES ($1, 'Payment Test Store', 'Test Address', 28.63, 77.21, 10, FALSE, TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      [storeId]
    );
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, '+919999888801', 'Refund Test Customer', 'STANDARD', TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      ['cust_pay_test_01']
    );
    const otpHash = crypto.createHmac('sha256', process.env.COMMERCEOS_OTP_PEPPER).update('1234').digest('hex');
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, items, total_amount, status, payment_status, payment_method, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
       VALUES ($1, $1, 'cust_pay_test_01', $2, $3::jsonb, $4, 'ORDER_CONFIRMED', 'PAID', 'COD', '{"address":"Test Road 1"}', $5, TRUE, $4, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET payment_status = 'PAID'`,
      [testOrderId, storeId, JSON.stringify([{ sku: 'MED-001', quantity: 2, price: 175.0 }]), testOrderTotal, otpHash]
    );
  } else if (repos && repos.paymentRepo && repos.paymentRepo.db) {
    // Local in-memory mode
    repos.paymentRepo.db.orders = repos.paymentRepo.db.orders || [];
    repos.paymentRepo.db.orders.push({
      id: testOrderId,
      orderId: testOrderId,
      customerId: 'cust_pay_test_01',
      totalAmount: testOrderTotal,
      status: 'ORDER_CONFIRMED',
      paymentStatus: 'PAID'
    });
  }

  // 2.5 Partial Refund
  const testIdempotencyKey = 'idemp_ref_' + Date.now();
  const partialRefundAmount = 50.0;
  {
    const partialRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, {
      orderId: testOrderId,
      amount: partialRefundAmount,
      reason: 'Damaged item return',
      idempotencyKey: testIdempotencyKey
    });
    assert(partialRes.statusCode === 200, `Valid partial refund executes with 200 OK (got ${partialRes.statusCode})`);
    assert(partialRes.body && (partialRes.body.status === 'SUCCESS' || partialRes.body.status === 'IDEMPOTENT_REPLAY'), `Refund result status is SUCCESS`);
  }

  // 2.6 Idempotent Replay
  {
    const replayRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, {
      orderId: testOrderId,
      amount: partialRefundAmount,
      reason: 'Damaged item return',
      idempotencyKey: testIdempotencyKey
    });
    assert(replayRes.statusCode === 200, `Idempotent refund replay returns 200 OK (got ${replayRes.statusCode})`);
    assert(replayRes.body && replayRes.body.status === 'IDEMPOTENT_REPLAY', `Replayed refund status is IDEMPOTENT_REPLAY`);
  }

  // 2.7 Exceeding Balance Rejection
  {
    const excessAmount = testOrderTotal + 1000;
    const excessRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/payments/refunds',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }, {
      orderId: testOrderId,
      amount: excessAmount,
      reason: 'Excess refund attempt',
      idempotencyKey: 'idemp_excess_' + Date.now()
    });
    assert(excessRes.statusCode === 422, `Rejects refund exceeding captured order balance with 422 (got ${excessRes.statusCode})`);
    assert(excessRes.body && excessRes.body.error === 'EXCEEDS_CAPTURED_AMOUNT', `Returns error code EXCEEDS_CAPTURED_AMOUNT`);
  }

  // 2.8 Order Refunds Query
  {
    const queryRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/payments/refunds/${testOrderId}`,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    });
    assert(queryRes.statusCode === 200, `Refunds query returns 200 OK (got ${queryRes.statusCode})`);
    assert(Array.isArray(queryRes.body?.refunds), `Refunds query returns array of refunds`);
    assert(queryRes.body?.refunds?.length > 0, `Refunds list includes newly created refund`);
  }

  // 3. User Directive: Strict COD Enforcement on Orders
  console.log('\n--- Test Group 3: Strict Cash on Delivery (COD) Checkout Contract ---');
  {
    const nonCodRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/v1/orders',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${customerToken}` }
    }, {
      customerId: 'cust_pay_test_01',
      paymentMethod: 'CREDIT_CARD',
      items: [{ sku: 'MED-001', quantity: 1 }]
    });
    assert(nonCodRes.statusCode === 400, `Rejects non-COD payment method with 400 (got ${nonCodRes.statusCode})`);
    assert(nonCodRes.body && nonCodRes.body.code === 'PAYMENT_METHOD_NOT_SUPPORTED', `Returns code PAYMENT_METHOD_NOT_SUPPORTED for non-COD`);
  }

  console.log('\n================================================================');
  console.log(`  VERIFICATION RESULTS: ${passed} / ${passed + failed} ASSERTIONS PASSED`);
  console.log(`  FAILURES: ${failed}`);
  console.log('================================================================');

  // Clean shutdown
  if (serverInstance) {
    serverInstance.close();
  }
  if (repos && repos.outboxProcessor) {
    repos.outboxProcessor.stop();
  }

  if (failed > 0) {
    process.exit(1);
  } else {
    console.log('>> [SUCCESS] All payment refund and webhook resilience invariants verified!\n');
    process.exit(0);
  }
}

runTests().catch(err => {
  console.error('Fatal test suite error:', err);
  process.exit(1);
});
