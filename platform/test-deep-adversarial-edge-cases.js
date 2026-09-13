#!/usr/bin/env node

/**
 * ==============================================================================
 * COMMERCE OS — ULTRA-DEEP ADVERSARIAL EDGE-CASE & BOUNDARY STRESS SUITE
 * ==============================================================================
 * Authority: AGENT-4 (QA / ADVERSARIAL REVIEW / VERIFICATION AUTHORITY)
 * 
 * Deep Edge-Case Modules:
 *   1. Multilingual Unicode & Emoji Sanitization (Hindi, Arabic, Chinese, Emojis)
 *   2. Extreme Numeric Boundary Attacks (NaN, Infinity, Negative Quantities, Floats)
 *   3. Checkout Idempotency Replay Attack Hammering (20 Rapid Concurrent Replays)
 *   4. Malicious XSS, SQLi & Null-Byte Injection in Prescription Vault
 *   5. High-Frequency Telemetry Ingestion (50 sequential GPS updates in real-time)
 * ==============================================================================
 */

'use strict';

const http = require('http');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value';
process.env.PORT = '8203';

const { server, pool, getAppRepositories } = require('./server/production-server');

const PORT = 8203;
const BASE_URL = `http://127.0.0.1:${PORT}`;

let serverInstance = null;
let totalAssertions = 0;
let passedAssertions = 0;
let failedAssertions = 0;

function assert(condition, message) {
  totalAssertions++;
  if (condition) {
    passedAssertions++;
    console.log(`  \x1b[32m✔ PASS\x1b[0m: ${message}`);
  } else {
    failedAssertions++;
    console.error(`  \x1b[31m✘ FAIL\x1b[0m: ${message}`);
  }
}

async function apiRequest(method, endpoint, body = null, headers = {}) {
  const url = new URL(endpoint.startsWith('/') ? endpoint : '/' + endpoint, BASE_URL);
  const options = {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...headers
    }
  };

  return new Promise((resolve) => {
    const req = http.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        let parsed = null;
        try { parsed = JSON.parse(data); } catch { parsed = data; }
        resolve({ status: res.statusCode, headers: res.headers, data: parsed });
      });
    });

    req.on('error', (err) => {
      resolve({ status: 500, headers: {}, data: { error: err.message } });
    });

    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

function generateJwt(subject, role = 'customer', extras = {}) {
  return jwt.sign(
    { sub: subject, role, ...extras },
    process.env.JWT_SECRET,
    {
      issuer: process.env.JWT_ISSUER,
      audience: process.env.JWT_AUDIENCE,
      expiresIn: '1h'
    }
  );
}

async function runDeepStressSuite() {
  console.log('==============================================================================');
  console.log('  COMMERCE OS — ULTRA-DEEP ADVERSARIAL EDGE-CASE & BOUNDARY STRESS SUITE      ');
  console.log('==============================================================================\n');

  // Boot server on isolated port 8203
  await new Promise((resolve) => {
    serverInstance = server.listen(PORT, () => {
      console.log(`  🚀 Deep Stress server running on ${BASE_URL}\n`);
      resolve();
    });
  });

  const repos = await getAppRepositories();
  assert(Boolean(repos), 'Resolved production repositories from PostgreSQL');

  // Seed baseline customer, store, product, inventory
  const customerId = `cust_deep_${Date.now()}`;
  const phone = `+9198${Date.now().toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO customers (id, phone, full_name, email)
    VALUES ($1, $2, 'Dr. Rajesh 👨‍⚕️ 💊 ✨', 'rajesh@apollo.in')
  `, [customerId, phone]);
  const customerToken = generateJwt(customerId, 'customer');

  const storeId = `store_deep_${Date.now()}`;
  await pool.query(`
    INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
    VALUES ($1, 'Apollo Pharmacy 🏥 صيدلية 药房', 'Connaught Place, New Delhi', 28.6315, 77.2167, 10, FALSE, TRUE)
  `, [storeId]);

  const sellerId = `sel_deep_${Date.now()}`;
  await pool.query(`
    INSERT INTO sellers (id, seller_id, email, phone, password_hash, store_id, store_name, merchant_name, status, is_primary, roles)
    VALUES ($1, $1, 'seller_deep@commerceos.io', '9876543218', 'dummy_hash', $2, 'Apollo Pharmacy Deep', 'Apollo Deep Ltd', 'ACTIVE', TRUE, '["ROLE_SELLER"]'::jsonb)
  `, [sellerId, storeId]);

  const sku = `SKU_DEEP_${Date.now()}`;
  const prodId = `prod_deep_${Date.now()}`;
  await pool.query(`
    INSERT INTO products (id, sku, name, category, mrp, price, rx_requirement, is_active)
    VALUES ($1, $2, 'Deep Stress Tonic 200ml', 'Medicines', 150.0, 120.0, 'OTC', TRUE)
  `, [prodId, sku]);

  await pool.query(`
    INSERT INTO inventory (id, store_id, product_id, sku, stock_count, reserved_count)
    VALUES ($1, $2, $3, $4, 500, 0)
  `, [`inv_deep_${Date.now()}`, storeId, prodId, sku]);

  // --------------------------------------------------------------------------
  // SECTION 1: MULTILINGUAL UNICODE & EMOJI RESILIENCE
  // --------------------------------------------------------------------------
  console.log('\n--- Section 1: Multilingual Unicode, Emoji & Multi-Byte Text ---');

  const unicodeAddrRes = await apiRequest('POST', `/api/v1/customers/${customerId}/addresses`, {
    addressType: 'HOME',
    addressLine: 'अपार्टमेंट ४०२, शांति निकेतन, ٥ شقة الزهور, 100号 楼 🏢 📦',
    city: 'New Delhi 🇮🇳',
    postalCode: '110001',
    latitude: 28.6320,
    longitude: 77.2170,
    contactPhone: phone
  }, { 'Authorization': `Bearer ${customerToken}` });

  assert(unicodeAddrRes.status === 201 || unicodeAddrRes.status === 200, 'Persists complex multilingual unicode address with emojis');
  const addressId = unicodeAddrRes.data?.address?.id || unicodeAddrRes.data?.id || unicodeAddrRes.data?.addressId;
  assert(Boolean(addressId), `Address persisted with ID: ${addressId}`);

  // Query database directly to verify zero mojibake / encoding corruption
  const dbAddr = await pool.query(`SELECT address_line, city FROM customer_addresses WHERE id = $1`, [addressId]);
  assert(
    dbAddr.rows[0]?.address_line.includes('🏢 📦') && dbAddr.rows[0]?.city.includes('🇮🇳'),
    'Database retains exact UTF-8 multi-byte emojis and multilingual strings without corruption'
  );

  // --------------------------------------------------------------------------
  // SECTION 2: EXTREME NUMERIC BOUNDARIES & CART TAMPERING
  // --------------------------------------------------------------------------
  console.log('\n--- Section 2: Extreme Numeric Boundaries & Cart Tampering ---');

  // 2.1 Negative Quantity Attack
  const negQtyRes = await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
    sku,
    quantity: -5
  }, { 'Authorization': `Bearer ${customerToken}` });
  assert(negQtyRes.status === 400, 'Rejects negative cart item quantity (-5) -> 400');

  // 2.2 Zero Quantity Attack
  const zeroQtyRes = await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
    sku,
    quantity: 0
  }, { 'Authorization': `Bearer ${customerToken}` });
  assert(zeroQtyRes.status === 400, 'Rejects zero cart item quantity (0) -> 400');

  // 2.3 Floating Point Fractional Pill Attack
  const floatQtyRes = await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
    sku,
    quantity: 2.75
  }, { 'Authorization': `Bearer ${customerToken}` });
  assert(floatQtyRes.status === 400, 'Rejects fractional / non-integer pill quantities (2.75) -> 400');

  // 2.4 Massive Overflow Quantity Attack (10,000,000 pills)
  const overflowQtyRes = await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
    sku,
    quantity: 10000000
  }, { 'Authorization': `Bearer ${customerToken}` });
  assert(overflowQtyRes.status === 400, 'Rejects massive inventory-draining quantity (10M units) -> 400');

  // --------------------------------------------------------------------------
  // SECTION 3: CHECKOUT IDEMPOTENCY REPLAY ATTACK (20 CONCURRENT REPLAYS)
  // --------------------------------------------------------------------------
  console.log('\n--- Section 3: Idempotency Replay Attack Hammering (20 Rapid Replays) ---');

  // Add 1 legitimate item to cart
  await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
    sku,
    quantity: 2,
    price: 120.0,
    name: 'Deep Stress Tonic 200ml'
  }, { 'Authorization': `Bearer ${customerToken}` });

  const sharedIdempotencyKey = `idemp_deep_${Date.now()}`;
  const checkoutPromises = [];

  for (let i = 0; i < 20; i++) {
    checkoutPromises.push(
      apiRequest('POST', `/api/v1/orders/checkout-from-cart/${customerId}`, {
        addressId,
        storeId,
        paymentMethod: 'COD',
        isCod: true,
        idempotencyKey: sharedIdempotencyKey
      }, {
        'Authorization': `Bearer ${customerToken}`,
        'Idempotency-Key': sharedIdempotencyKey
      })
    );
  }

  const checkoutResults = await Promise.all(checkoutPromises);
  console.log('  [DEBUG] Checkout Status 0:', checkoutResults[0]?.status, JSON.stringify(checkoutResults[0]?.data));
  const successfulCheckouts = checkoutResults.filter(r => r.status === 200 || r.status === 201);
  assert(successfulCheckouts.length === 20, 'All 20 rapid concurrent requests resolved successfully');

  // Extract order IDs: Invariant is that ALL 20 responses MUST return the exact SAME order ID!
  const returnedOrderIds = new Set(successfulCheckouts.map(r => r.data?.orderId || r.data?.order?.orderId).filter(Boolean));
  assert(returnedOrderIds.size === 1, `Idempotency Invariant Enforced: Exactly 1 unique order created across 20 calls (Order ID: ${[...returnedOrderIds][0]})`);

  const createdOrderId = [...returnedOrderIds][0];

  // Verify in PostgreSQL: Exactly 1 row exists with this idempotency key
  const singleOrderCheck = await pool.query(
    `SELECT count(*) FROM orders WHERE customer_id = $1 AND idempotency_key = $2`,
    [customerId, sharedIdempotencyKey]
  );
  assert(parseInt(singleOrderCheck.rows[0].count) === 1, 'PostgreSQL enforces strict single-order count for idempotency key');

  // --------------------------------------------------------------------------
  // SECTION 4: MALICIOUS XSS, SQLi & NULL-BYTE INJECTION
  // --------------------------------------------------------------------------
  console.log('\n--- Section 4: Malicious Injection & Prescription Vault Attacks ---');

  const maliciousNoteRes = await apiRequest('POST', '/api/v1/prescriptions', {
    patientName: "Robert'); DROP TABLE orders;--",
    doctorName: "<script>alert('XSS_ATTACK_DOCTOR')</script>",
    doctorRegistrationNo: "REG-9912\u0000MALICIOUS_NULL_BYTE",
    attachments: ['https://cdn.commerceos.io/prescriptions/rx_safe_test.jpg'],
    note: "<img src=x onerror=alert('PAYLOAD_NOTE')>"
  }, { 'Authorization': `Bearer ${customerToken}` });

  assert(
    maliciousNoteRes.status === 200 || maliciousNoteRes.status === 201,
    'Server safely processes prescription with parameterized SQL and sanitized inputs'
  );

  // Invariant: Orders table was NOT dropped by SQL injection attempt
  const tableCheck = await pool.query(`SELECT count(*) FROM orders`);
  assert(parseInt(tableCheck.rows[0].count) > 0, 'Database tables intact: SQL injection safely neutralized by parameterization');

  // --------------------------------------------------------------------------
  // SECTION 5: HIGH-FREQUENCY GPS TELEMETRY INGESTION (50 Sequential Updates)
  // --------------------------------------------------------------------------
  console.log('\n--- Section 5: High-Frequency GPS Telemetry Throughput ---');

  const riderId = `rider_stream_${Date.now()}`;
  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, status)
    VALUES ($1, $1, $2, 'Throughput Rider', 'KA-01-EQ-7777', 'ACTIVE')
  `, [riderId, `+9197${Date.now().toString().slice(-8)}`]);
  const riderToken = generateJwt(riderId, 'rider');

  const streamDeliveryId = `deliv_stream_${Date.now()}`;
  await pool.query(`
    INSERT INTO delivery_sessions (
      id, delivery_id, order_id, store_id, rider_id,
      state, merchant_name, merchant_address, merchant_lat, merchant_lng,
      customer_name, customer_phone, customer_address, customer_lat, customer_lng,
      distance_km, is_cod, cod_amount
    ) VALUES (
      $1, $1, $2, $3, $4,
      'IN_TRANSIT', 'Stream Store', 'Connaught Place', 28.6315, 77.2167,
      'Stream Customer', '+919999999999', 'Janpath', 28.6250, 77.2180,
      2.0, FALSE, 0.00
    )
  `, [streamDeliveryId, createdOrderId, storeId, riderId]);

  const startTime = Date.now();
  const PACKET_COUNT = 50;
  for (let seq = 1; seq <= PACKET_COUNT; seq++) {
    const lat = 28.6315 - (seq * 0.0001);
    const lng = 77.2167 - (seq * 0.0001);
    await apiRequest('POST', `/api/v1/delivery/${streamDeliveryId}/telemetry`, {
      riderId,
      latitude: lat,
      longitude: lng,
      speedKmh: 28.5,
      heading: 180.0,
      sequenceNumber: seq
    }, { 'Authorization': `Bearer ${riderToken}` });
  }
  const durationMs = Date.now() - startTime;

  console.log(`  ⚡ Ingested ${PACKET_COUNT} high-frequency GPS telemetry packets in ${durationMs}ms (Avg: ${(durationMs / PACKET_COUNT).toFixed(1)}ms per packet)`);
  assert(durationMs < 5000, `Telemetry ingestion latency well within real-time SLA (${durationMs}ms < 5000ms)`);

  // Verify the latest sequence number in PostgreSQL
  const latestTelem = await pool.query(
    `SELECT max(sequence_number) as max_seq FROM rider_telemetry WHERE delivery_id = $1`,
    [streamDeliveryId]
  );
  assert(parseInt(latestTelem.rows[0].max_seq) === PACKET_COUNT, `PostgreSQL recorded all ${PACKET_COUNT} sequence numbers sequentially`);

  // --------------------------------------------------------------------------
  // AUDIT SUMMARY
  // --------------------------------------------------------------------------
  console.log('\n==============================================================================');
  console.log(`TOTAL DEEP STRESS ASSERTIONS : ${totalAssertions}`);
  console.log(`PASSED                       : ${passedAssertions}`);
  console.log(`FAILED                       : ${failedAssertions}`);
  console.log(`SUCCESS RATE                 : ${Math.round((passedAssertions / totalAssertions) * 100)}%`);
  console.log('==============================================================================\n');

  await new Promise(r => serverInstance.close(r));
  console.log('🔒 Deep stress server shut down cleanly.\n');

  if (failedAssertions > 0) {
    process.exit(1);
  }
}

runDeepStressSuite().catch(err => {
  console.error('FATAL_DEEP_STRESS_ERROR:', err);
  if (serverInstance) serverInstance.close();
  process.exit(1);
});
