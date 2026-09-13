#!/usr/bin/env node
/**
 * Test Suite: Android Backend Rider Telemetry Stream & Ingestion
 * Role: Verification for TASK-A2-BACKEND-RIDER-TELEMETRY-STREAM-INGESTION-REFINEMENT
 */

const http = require('http');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'commerceos-dev-jwt-secret-min-32-chars-ok';
process.env.JWT_SECRET = JWT_SECRET;
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.COMMERCEOS_OTP_PEPPER = process.env.COMMERCEOS_OTP_PEPPER || 'qa_adversarial_security_test_pepper_128bit';

const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8202;
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
  console.log('  COMMERCE-OS ANDROID RIDER TELEMETRY STREAM VERIFICATION SUITE ');
  console.log('================================================================\n');

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
      console.log(`🚀 Telemetry test server active on 127.0.0.1:${PORT}\n`);
      resolve();
    });
  });

  const repos = await getAppRepositories();

  const assignedRiderId = 'rdr_telem_test_01';
  const rogueRiderId = 'rdr_telem_rogue_01';
  const deliveryId = 'del_telem_test_' + Date.now();
  const orderId = 'ord_telem_test_' + Date.now();
  const storeId = 'store_telem_01';
  const customerId = 'cust_telem_01';

  const assignedToken = makeJwt({ sub: assignedRiderId, role: 'ROLE_RIDER', roles: ['ROLE_RIDER'] });
  const rogueToken = makeJwt({ sub: rogueRiderId, role: 'ROLE_RIDER', roles: ['ROLE_RIDER'] });

  // 2. Setup Fixtures in PostgreSQL
  if (pool) {
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
       VALUES ($1, 'Telemetry Test Store', 'Test Address', 28.63, 77.21, 10, FALSE, TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      [storeId]
    );
    const uniquePhoneCust = '+9198' + Date.now().toString().slice(-8);
    const uniquePhoneRider = '+9197' + Date.now().toString().slice(-8);
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'Telemetry Customer', 'STANDARD', TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      [customerId, uniquePhoneCust]
    );
    const otpHash = crypto.createHmac('sha256', process.env.COMMERCEOS_OTP_PEPPER).update('1234').digest('hex');
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, items, total_amount, status, payment_status, payment_method, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
       VALUES ($1, $1, $2, $3, '[]'::jsonb, 100.0, 'DISPATCHED', 'PAID', 'COD', '{"address":"Delhi"}', $4, FALSE, 0.0, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET status = 'DISPATCHED'`,
      [orderId, customerId, storeId, otpHash]
    );
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
       VALUES ($1, $1, $2, 'Assigned Telemetry Rider', 'DL01AB1234', 'TWO_WHEELER', 'ACTIVE')
       ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE'`,
      [assignedRiderId, uniquePhoneRider]
    );
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng, created_at, updated_at)
       VALUES ($1, $1, $2, $3, 'EN_ROUTE_CUSTOMER', 'Test Store', 'Store Road 1', 28.6300, 77.2100, 'Test Customer', 'Customer Road 1', 28.6400, 77.2200, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET state = 'EN_ROUTE_CUSTOMER', rider_id = $3`,
      [deliveryId, orderId, assignedRiderId]
    );
  }

  // --- Test Group 1: Security & Guard Invariants ---
  console.log('--- Test Group 1: Security & Identity Invariants ---');

  // 1.1 Missing Authentication
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { latitude: 28.6310, longitude: 77.2110, sequenceNumber: 1 });
    assert(res.statusCode === 401, `Rejects unauthenticated telemetry packet with 401 (got ${res.statusCode})`);
  }

  // 1.2 Rogue Rider (Not Assigned)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${rogueToken}` }
    }, { latitude: 28.6310, longitude: 77.2110, sequenceNumber: 1 });
    assert(res.statusCode === 403, `Rejects unassigned rider telemetry injection with 403 (got ${res.statusCode})`);
  }

  // 1.3 Missing Sequence Number
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, { latitude: 28.6310, longitude: 77.2110 });
    assert(res.statusCode === 400, `Rejects packet without sequenceNumber with 400 (got ${res.statusCode})`);
    assert(res.body && res.body.error === 'INVALID_TELEMETRY_SEQUENCE', `Returns INVALID_TELEMETRY_SEQUENCE error`);
  }

  // 1.4 Invalid Coordinates (NaN or Missing)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, { sequenceNumber: 1 });
    assert(res.statusCode === 400, `Rejects packet with missing coordinates with 400 (got ${res.statusCode})`);
  }

  // --- Test Group 2: High-Frequency Telemetry Ingestion & ACK ---
  console.log('\n--- Test Group 2: Ingestion, Monotonic ACK & Deduplication ---');

  // 2.1 First Packet (seq 1)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, {
      latitude: 28.6310,
      longitude: 77.2110,
      speedKmh: 24.5,
      heading: 90.0,
      accuracyMeters: 4.2,
      sequenceNumber: 1
    });
    assert(res.statusCode === 200, `Accepts valid telemetry packet with 200 OK (got ${res.statusCode})`);
    assert(res.body && res.body.ackSequenceNumber === 1, `Returns ackSequenceNumber: 1`);
    assert(res.body && res.body.deliveryId === deliveryId, `Matches deliveryId in ACK response`);
  }

  // 2.2 Monotonic Sequence (seq 2)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, {
      latitude: 28.6320,
      longitude: 77.2120,
      speedKmh: 28.0,
      heading: 95.0,
      accuracyMeters: 3.8,
      sequenceNumber: 2
    });
    assert(res.statusCode === 200, `Accepts monotonic sequence packet (seq 2) with 200 OK (got ${res.statusCode})`);
    assert(res.body && res.body.ackSequenceNumber === 2, `Returns ackSequenceNumber: 2`);
  }

  // 2.3 Duplicate Packet Replay (seq 2 replayed)
  {
    const res = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, {
      latitude: 28.6320,
      longitude: 77.2120,
      speedKmh: 28.0,
      heading: 95.0,
      accuracyMeters: 3.8,
      sequenceNumber: 2
    });
    assert(res.statusCode === 200, `Replayed packet safely acknowledged with 200 OK (got ${res.statusCode})`);
    assert(res.body && (res.body.ackSequenceNumber === 2 || res.body.duplicate === true), `Handles replay idempotently without sequence corruption`);
  }

  // 2.4 Rapid Burst Telemetry Stream (seq 3, 4, 5 in rapid succession)
  {
    const burstPromises = [3, 4, 5].map(seq => request({
      hostname: '127.0.0.1',
      port: PORT,
      path: `/api/v1/delivery/${deliveryId}/telemetry`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${assignedToken}` }
    }, {
      latitude: 28.6320 + (seq * 0.001),
      longitude: 77.2120 + (seq * 0.001),
      speedKmh: 30.0,
      sequenceNumber: seq
    }));
    const burstResults = await Promise.all(burstPromises);
    const all200 = burstResults.every(r => r.statusCode === 200);
    assert(all200, `High-frequency burst packets (seq 3, 4, 5) all ingested successfully`);
  }

  console.log('\n================================================================');
  console.log(`  VERIFICATION RESULTS: ${passed} / ${passed + failed} ASSERTIONS PASSED`);
  console.log(`  FAILURES: ${failed}`);
  console.log('================================================================');

  if (serverInstance) serverInstance.close();
  if (repos && repos.outboxProcessor) repos.outboxProcessor.stop();

  if (failed > 0) {
    process.exit(1);
  } else {
    console.log('>> [SUCCESS] Backend rider telemetry stream & ingestion invariants verified!\n');
    process.exit(0);
  }
}

runTests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
