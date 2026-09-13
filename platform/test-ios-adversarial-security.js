#!/usr/bin/env node

/**
 * Commerce OS — Native iOS Backend Adversarial Security & Penetration Suite
 * 
 * Aggressive Security & Adversarial Verification Suite:
 * 1. Cryptographic OTP PIN tampering & brute-force rejection (max 5 attempts threshold)
 * 2. Telemetry out-of-order sequence attack, negative packets, replay attacks, and unassigned rider rejection
 * 3. Concurrent offer claim race conditions (double-accept, multi-rider concurrency, 409 Conflict handling, expiry)
 * 4. Expired, malformed, forged, and algorithm-none JWT attacks on native iOS endpoints
 * 5. End-to-End Adversarial Penetration Simulation
 * 
 * Authored by: AGENT-4 (QA / ADVERSARIAL REVIEW / VERIFICATION AUTHORITY)
 * Task: TASK-A4-ADVERSARIAL-SECURITY-SUITE
 */

const assert = require('assert');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');

// Ensure production security environment configuration
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8198';

const { server, pool, getAppRepositories } = require('./server/production-server');
const { DeliveryOtpService, TransactionalSellerRepository } = require('./repositories');

const PORT = 8198;
const BASE_URL = `http://127.0.0.1:${PORT}`;

let serverInstance = null;
let passedCount = 0;
let totalCount = 0;
const testResults = [];

function recordResult(name, method, endpoint, status, ok, details = '') {
  totalCount++;
  if (ok) passedCount++;
  testResults.push({ name, method, endpoint, status, ok, details });
  const statusStr = ok ? '\x1b[32m✔ PASS\x1b[0m' : '\x1b[31m✘ FAIL\x1b[0m';
  console.log(` ${statusStr} [${method}] ${endpoint} -> ${status} | ${name} ${details ? `(${details})` : ''}`);
}

async function apiRequest(method, endpoint, body = null, headers = {}) {
  const url = `${BASE_URL}${endpoint.startsWith('/') ? endpoint : '/' + endpoint}`;
  const options = {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...headers
    }
  };
  if (body) {
    options.body = typeof body === 'string' ? body : JSON.stringify(body);
  }

  const res = await fetch(url, options);
  let data = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      data = await res.json();
    } catch (_) {
      data = null;
    }
  } else {
    data = await res.text();
  }

  return {
    status: res.status,
    headers: res.headers,
    data
  };
}

async function run() {
  console.log('\x1b[1m\x1b[31m======================================================================\x1b[0m');
  console.log('\x1b[1m\x1b[31m  COMMERCE OS — NATIVE iOS ADVERSARIAL PENETRATION & SECURITY SUITE   \x1b[0m');
  console.log('\x1b[1m\x1b[31m  AGENT-4 (QA / ADVERSARIAL REVIEW / VERIFICATION AUTHORITY)          \x1b[0m');
  console.log('\x1b[1m\x1b[31m======================================================================\x1b[0m\n');

  // 1. Boot Dedicated Verification Server Instance on PORT 8198
  await new Promise((resolve, reject) => {
    serverInstance = server.listen(PORT, '127.0.0.1', (err) => {
      if (err) return reject(err);
      console.log(`🛡️  Adversarial Security Sandbox active on ${BASE_URL} (Port ${PORT})\n`);
      resolve();
    });
  });

  const appRepositories = getAppRepositories();
  const pepper = process.env.COMMERCEOS_OTP_PEPPER;

  // 2. Database Fixtures Setup (Clean Test Boundary)
  await pool.query('DELETE FROM rider_telemetry');
  await pool.query('DELETE FROM offers');
  await pool.query('DELETE FROM delivery_sessions');
  await pool.query('DELETE FROM orders');
  await pool.query('DELETE FROM auth_challenges');
  await pool.query('DELETE FROM rider_device_tokens');

  const storeId = 'store_adv_sec_01';
  await pool.query(
    `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
     VALUES ($1, 'Adversarial Security Test Store', 'Cyber Security Center, New Delhi', 28.6315, 77.2167, 10, FALSE, TRUE)
     ON CONFLICT (id) DO UPDATE SET is_active = TRUE, seller_approval_required = FALSE`,
    [storeId]
  );

  const customerId = 'cust_sec_01';
  await pool.query(
    `INSERT INTO customers (id, phone, full_name, tier, is_active)
     VALUES ($1, '+919876500001', 'Security Test Customer', 'STANDARD', TRUE)
     ON CONFLICT (id) DO UPDATE SET phone = '+919876500001', is_active = TRUE`,
    [customerId]
  );

  // Helper function to insert test riders cleanly and idempotently
  async function setupRider(riderId, phone, name, vehicle) {
    const formattedPhone = `+91${phone}`;
    await pool.query('DELETE FROM riders WHERE id = $1 OR phone = $2', [riderId, formattedPhone]);
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, tier)
       VALUES ($1, $1, $2, $3, $4, 'TWO_WHEELER', 'ACTIVE', 'PLATINUM')`,
      [riderId, formattedPhone, name, vehicle]
    );
  }

  // Helper function to generate test JWTs
  function createTestJwt(claims, secret = process.env.JWT_SECRET, options = {}) {
    return jwt.sign(claims, secret, {
      algorithm: 'HS256',
      issuer: process.env.JWT_ISSUER,
      audience: process.env.JWT_AUDIENCE,
      expiresIn: '1h',
      ...options
    });
  }

  function getRiderHeaders(riderId, phone) {
    const token = createTestJwt({ sub: riderId, role: 'RIDER', name: 'Test Rider', phone: `+91${phone}` });
    return {
      'Authorization': `Bearer ${token}`,
      'X-Client-Platform': 'iOS-Rider',
      'X-Client-Version': '2.1.0'
    };
  }

  // Setup distinct riders for isolated test scopes
  const riderId1A = 'rdr_otp_alpha';
  const riderPhone1A = '9811000001';
  await setupRider(riderId1A, riderPhone1A, 'Rider OTP Alpha', 'DL-01-SEC-0001');
  const headers1A = getRiderHeaders(riderId1A, riderPhone1A);

  const riderId1B = 'rdr_otp_beta';
  const riderPhone1B = '9811000002';
  await setupRider(riderId1B, riderPhone1B, 'Rider OTP Beta', 'DL-02-ATT-9999');
  const headers1B = getRiderHeaders(riderId1B, riderPhone1B);

  // ==========================================================================
  // SECTION 1: Cryptographic OTP PIN Tampering & Brute-Force Rejection
  // ==========================================================================
  console.log('\x1b[1m\x1b[33m--- Section 1: Cryptographic OTP PIN Tampering & Brute-Force Rejection ---\x1b[0m');

  // 1.1 Cryptographic hashing ground truth verification
  const truePin = '849201';
  const expectedHash = DeliveryOtpService.hashDeliveryOtp(truePin, pepper);
  const correctHmac = crypto.createHmac('sha256', pepper).update(truePin).digest('hex');
  const hmacIntegrityOk = (expectedHash === correctHmac);
  recordResult('HMAC-SHA256 Cryptographic Hash Integrity', 'UNIT', 'DeliveryOtpService.hashDeliveryOtp', 200, hmacIntegrityOk, `Hash: ${expectedHash.slice(0, 16)}...`);

  // Setup Order and Delivery Session with truePin hash
  const orderId1 = 'ord_sec_otp_01';
  const deliveryId1 = 'deliv_sec_otp_01';
  await pool.query(
    `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, otp_attempts, is_cod, cod_amount)
     VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 250.00, '[]'::jsonb, '{"address": "Connaught Place 1", "lat": 28.6320, "lng": 77.2180}'::jsonb, $4, 0, FALSE, 0.00)
     ON CONFLICT (id) DO UPDATE SET delivery_otp_hash = $4, otp_attempts = 0, status = 'READY_FOR_PICKUP'`,
    [orderId1, customerId, storeId, expectedHash]
  );

  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount, otp_verified
     ) VALUES (
       $1, $1, $2, $3, $4, 'Rider Alpha', $5, 'DL-01-SEC-0001',
       'OUT_FOR_DELIVERY', 'Security Test Store', 'Cyber Security Center', 28.6315, 77.2167,
       'Security Test Customer', '+919876500001', 'Connaught Place 1', 28.6320, 77.2180,
       1.5, FALSE, 0.00, FALSE
     ) ON CONFLICT (id) DO UPDATE SET state = 'OUT_FOR_DELIVERY', rider_id = $4, otp_verified = FALSE`,
    [deliveryId1, orderId1, storeId, riderId1A, `+91${riderPhone1A}`]
  );

  // 1.2 Format tampering: Short PIN (< 4 digits) rejected with 400
  const shortPinRes = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: '12' }, headers1A);
  const shortPinOk = shortPinRes.status === 400 && shortPinRes.data?.error === 'INVALID_OTP';
  recordResult('Short PIN (<4 digits) Rejected', 'POST', `/api/v1/orders/:id/deliver-with-otp`, shortPinRes.status, shortPinOk, shortPinRes.data?.message);

  // 1.3 Format tampering: Empty / whitespace PIN rejected with 400
  const emptyPinRes = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: '   ' }, headers1A);
  const emptyPinOk = emptyPinRes.status === 400 && emptyPinRes.data?.error === 'INVALID_OTP';
  recordResult('Empty / Whitespace PIN Rejected', 'POST', `/api/v1/orders/:id/deliver-with-otp`, emptyPinRes.status, emptyPinOk);

  // Reset attempts before executing strictly tracked brute-force sequence
  await pool.query('UPDATE orders SET otp_attempts = 0 WHERE order_id = $1', [orderId1]);

  // 1.4 Single Tampered / Incorrect PIN Attempt (Attempt 1)
  const wrongPinRes1 = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: '999999' }, headers1A);
  const wrongPinOk1 = wrongPinRes1.status === 400 && wrongPinRes1.data?.error === 'INVALID_OTP';
  recordResult('Tampered / Incorrect PIN Rejected (Attempt 1)', 'POST', `/api/v1/orders/:id/deliver-with-otp`, wrongPinRes1.status, wrongPinOk1);

  // Verify DB attempts counter incremented to 1
  const attemptRow1 = await pool.query('SELECT otp_attempts FROM orders WHERE order_id = $1', [orderId1]);
  const attemptCount1 = attemptRow1.rows[0]?.otp_attempts;
  const dbAttempt1Ok = attemptCount1 === 1;
  recordResult('DB otp_attempts strictly incremented to 1', 'SQL', 'orders.otp_attempts', 200, dbAttempt1Ok, `Attempts: ${attemptCount1}`);

  // 1.5 Adversarial Brute-Force Exhaustion: Submit attempts 2 through 5
  let bruteForceOk = true;
  for (let i = 2; i <= 5; i++) {
    const bfRes = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: `11111${i}` }, headers1A);
    if (bfRes.status !== 400) {
      bruteForceOk = false;
      break;
    }
  }
  const attemptRow5 = await pool.query('SELECT otp_attempts FROM orders WHERE order_id = $1', [orderId1]);
  const attemptCount5 = attemptRow5.rows[0]?.otp_attempts;
  recordResult('Brute-force attempts 2 through 5 rejected (400)', 'POST', `/api/v1/orders/:id/deliver-with-otp`, 400, bruteForceOk && attemptCount5 === 5, `Total attempts recorded: ${attemptCount5}`);

  // 1.6 CRITICAL BRUTE-FORCE LOCKOUT TEST:
  // Now submit the GENUINE CORRECT PIN ('849201') on attempt 6!
  // It MUST BE REJECTED because max attempts (5) has been exceeded!
  const lockoutRes = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: truePin }, headers1A);
  const lockoutOk = lockoutRes.status === 400 && (
    lockoutRes.data?.error === 'INVALID_OTP' || 
    lockoutRes.data?.error === 'MAX_ATTEMPTS_EXCEEDED' ||
    (lockoutRes.data?.message && lockoutRes.data.message.includes('Too many incorrect attempts'))
  );
  recordResult('Max 5 Attempts Lockout: True PIN REJECTED after threshold', 'POST', `/api/v1/orders/:id/deliver-with-otp`, lockoutRes.status, lockoutOk, lockoutRes.data?.message);

  // 1.7 Verify Invariant: Delivery session MUST NOT be marked DELIVERED
  const dbDelivSession = await pool.query('SELECT state, otp_verified FROM delivery_sessions WHERE delivery_id = $1', [deliveryId1]);
  const notDeliveredOk = dbDelivSession.rows[0]?.state !== 'DELIVERED' && dbDelivSession.rows[0]?.otp_verified !== true;
  recordResult('Invariant Enforced: Delivery session state not DELIVERED', 'SQL', 'delivery_sessions.state != DELIVERED', 200, notDeliveredOk, `State: ${dbDelivSession.rows[0]?.state}`);

  // 1.8 Unauthorized Rider Impersonation on OTP completion
  // Terminalize delivery 1 to allow clean subsequent active session tests
  await pool.query(`UPDATE delivery_sessions SET state = 'CANCELLED' WHERE delivery_id = $1`, [deliveryId1]);

  const orderId2 = 'ord_sec_otp_02';
  const deliveryId2 = 'deliv_sec_otp_02';
  await pool.query(
    `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, otp_attempts, is_cod, cod_amount)
     VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 100.00, '[]'::jsonb, '{"address": "Connaught Place 2", "lat": 28.6320, "lng": 77.2180}'::jsonb, $4, 0, FALSE, 0.00)
     ON CONFLICT (id) DO UPDATE SET delivery_otp_hash = $4, otp_attempts = 0`,
    [orderId2, customerId, storeId, expectedHash]
  );
  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount, otp_verified
     ) VALUES (
       $1, $1, $2, $3, $4, 'Rider Alpha', $5, 'DL-01-SEC-0001',
       'OUT_FOR_DELIVERY', 'Security Test Store', 'Cyber Security Center', 28.6315, 77.2167,
       'Security Test Customer', '+919876500001', 'Connaught Place 2', 28.6320, 77.2180,
       1.5, FALSE, 0.00, FALSE
     ) ON CONFLICT (id) DO UPDATE SET state = 'OUT_FOR_DELIVERY', rider_id = $4`,
    [deliveryId2, orderId2, storeId, riderId1A, `+91${riderPhone1A}`]
  );

  const unauthorizedRiderRes = await apiRequest('POST', `/api/v1/orders/${deliveryId2}/deliver-with-otp`, { otp: truePin }, headers1B);
  const unauthOk = (unauthorizedRiderRes.status === 403 || unauthorizedRiderRes.status === 401);
  recordResult('Unauthorized Rider (Rider Beta) Cannot Deliver Rider Alpha Order', 'POST', `/api/v1/orders/:id/deliver-with-otp`, unauthorizedRiderRes.status, unauthOk, unauthorizedRiderRes.data?.message);

  // ==========================================================================
  // SECTION 2: Telemetry Out-of-Order Sequence Attack
  // ==========================================================================
  console.log('\n\x1b[1m\x1b[33m--- Section 2: Telemetry Out-of-Order Sequence Attack ---\x1b[0m');

  // Setup fresh rider for telemetry tests
  const riderId2 = 'rdr_telem_alpha';
  const riderPhone2 = '9811000003';
  await setupRider(riderId2, riderPhone2, 'Rider Telem Alpha', 'DL-03-TEL-0001');
  const headers2 = getRiderHeaders(riderId2, riderPhone2);

  const orderIdTelem = 'ord_sec_telem_01';
  const deliveryIdTelem = 'deliv_sec_telem_01';
  await pool.query(
    `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
     VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 120.00, '[]'::jsonb, '{"address": "Telemetry Point", "lat": 28.6320, "lng": 77.2180}'::jsonb, 'hash', FALSE, 0.00)
     ON CONFLICT (id) DO NOTHING`,
    [orderIdTelem, customerId, storeId]
  );
  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount
     ) VALUES (
       $1, $1, $2, $3, $4, 'Rider Telem Alpha', $5, 'DL-03-TEL-0001',
       'OUT_FOR_DELIVERY', 'Security Test Store', 'Cyber Security Center', 28.6315, 77.2167,
       'Security Test Customer', '+919876500001', 'Telemetry Point', 28.6320, 77.2180,
       1.5, FALSE, 0.00
     ) ON CONFLICT (id) DO UPDATE SET state = 'OUT_FOR_DELIVERY', rider_id = $4`,
    [deliveryIdTelem, orderIdTelem, storeId, riderId2, `+91${riderPhone2}`]
  );

  // 2.1 Missing sequence number rejection
  const telemMissingSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6315,
    longitude: 77.2167,
    speedKmh: 25.0
  }, headers2);
  const telemMissingSeqOk = telemMissingSeq.status === 400 && telemMissingSeq.data?.error === 'INVALID_TELEMETRY_SEQUENCE';
  recordResult('Missing Telemetry sequenceNumber Rejected', 'POST', `/api/v1/delivery/:id/telemetry`, telemMissingSeq.status, telemMissingSeqOk, telemMissingSeq.data?.message);

  // 2.2 Negative or zero sequence number rejection
  const telemZeroSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6315,
    longitude: 77.2167,
    sequenceNumber: 0
  }, headers2);
  const telemZeroSeqOk = telemZeroSeq.status === 400 && telemZeroSeq.data?.error === 'INVALID_TELEMETRY_SEQUENCE';
  recordResult('Zero sequenceNumber (<=0) Rejected', 'POST', `/api/v1/delivery/:id/telemetry`, telemZeroSeq.status, telemZeroSeqOk);

  const telemNegSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6315,
    longitude: 77.2167,
    sequenceNumber: -42
  }, headers2);
  const telemNegSeqOk = telemNegSeq.status === 400 && telemNegSeq.data?.error === 'INVALID_TELEMETRY_SEQUENCE';
  recordResult('Negative sequenceNumber Rejected', 'POST', `/api/v1/delivery/:id/telemetry`, telemNegSeq.status, telemNegSeqOk);

  // 2.3 Non-integer sequence number rejection
  const telemStrSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6315,
    longitude: 77.2167,
    sequenceNumber: 'corrupted_sequence_packet'
  }, headers2);
  const telemStrSeqOk = telemStrSeq.status === 400 && telemStrSeq.data?.error === 'INVALID_TELEMETRY_SEQUENCE';
  recordResult('Corrupted String sequenceNumber Rejected', 'POST', `/api/v1/delivery/:id/telemetry`, telemStrSeq.status, telemStrSeqOk);

  // 2.4 Valid packet ingestion (sequence 100)
  const telemSeq100 = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6315,
    longitude: 77.2167,
    heading: 90,
    speedKmh: 30,
    sequenceNumber: 100
  }, headers2);
  const telemSeq100Ok = telemSeq100.status === 200 && telemSeq100.data?.accepted === true && telemSeq100.data?.duplicate === false;
  recordResult('Valid Initial Telemetry Packet (seq 100)', 'POST', `/api/v1/delivery/:id/telemetry`, telemSeq100.status, telemSeq100Ok);

  // 2.5 Replay / Duplicate Packet Attack (Same sequence 100 with spoofed coordinates)
  const telemReplay = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.9999,
    longitude: 77.9999,
    heading: 180,
    speedKmh: 120,
    sequenceNumber: 100
  }, headers2);
  const telemReplayOk = telemReplay.status === 200 && telemReplay.data?.duplicate === true && telemReplay.data?.accepted === false;
  recordResult('Duplicate sequenceNumber Replay Attack Flagged (duplicate: true)', 'POST', `/api/v1/delivery/:id/telemetry`, telemReplay.status, telemReplayOk);

  // Verify PostgreSQL unique constraint: exactly 1 row for (delivery_id, sequence_number=100)
  const dupCheck = await pool.query('SELECT count(*) FROM rider_telemetry WHERE delivery_id = $1 AND sequence_number = 100', [deliveryIdTelem]);
  const singleRowOk = Number(dupCheck.rows[0]?.count) === 1;
  recordResult('DB Invariant: ON CONFLICT prevents duplicate telemetry row', 'SQL', 'rider_telemetry UNIQUE(delivery_id, sequence_number)', 200, singleRowOk);

  // 2.6 Out-of-Order Packet Injection & Monotonic Invariant Check
  await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, { latitude: 28.6320, longitude: 77.2170, sequenceNumber: 150 }, headers2);
  await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, { latitude: 28.6310, longitude: 77.2160, sequenceNumber: 120 }, headers2);
  await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, { latitude: 28.6330, longitude: 77.2180, sequenceNumber: 200 }, headers2);
  await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, { latitude: 28.6325, longitude: 77.2175, sequenceNumber: 180 }, headers2);

  // Query latest telemetry from repository
  const latestTelem = await appRepositories.telemetryRepo.getLatestTelemetryForDelivery(deliveryIdTelem);
  const latestSeqOk = latestTelem && latestTelem.sequenceNumber === 200;
  recordResult('Latest Telemetry strictly returns highest sequenceNumber (200), ignoring out-of-order packets', 'REPO', 'getLatestTelemetryForDelivery', 200, latestSeqOk, `Found seq: ${latestTelem?.sequenceNumber}`);

  // 2.7 Unassigned Rider Telemetry Hijack Attack (Rider Beta submits telemetry to Rider Telem Alpha delivery)
  const hijackTelem = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, {
    latitude: 28.6350,
    longitude: 77.2190,
    sequenceNumber: 300
  }, headers1B);
  const hijackOk = hijackTelem.status === 403;
  recordResult('Unassigned Rider Telemetry Injection Rejected with 403', 'POST', `/api/v1/delivery/:id/telemetry`, hijackTelem.status, hijackOk, hijackTelem.data?.message);

  // ==========================================================================
  // SECTION 3: Concurrent Offer Claim Race Condition (409 Conflict)
  // ==========================================================================
  console.log('\n\x1b[1m\x1b[33m--- Section 3: Concurrent Offer Claim Race Condition (409 Conflict) ---\x1b[0m');

  const riderId3A = 'rdr_offer_alpha';
  const riderPhone3A = '9811000005';
  await setupRider(riderId3A, riderPhone3A, 'Rider Offer Alpha', 'DL-05-OFF-0001');
  const headers3A = getRiderHeaders(riderId3A, riderPhone3A);

  const riderId3B = 'rdr_offer_beta';
  const riderPhone3B = '9811000006';
  await setupRider(riderId3B, riderPhone3B, 'Rider Offer Beta', 'DL-06-OFF-0002');
  const headers3B = getRiderHeaders(riderId3B, riderPhone3B);

  const orderId3 = 'ord_sec_offer_03';
  const deliveryId3 = 'deliv_sec_offer_03';
  const offerId3 = 'off_sec_offer_03';
  await pool.query(
    `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
     VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 300.00, '[]'::jsonb, '{"address": "Connaught Place 3", "lat": 28.6320, "lng": 77.2180}'::jsonb, 'dummy_hash', FALSE, 0.00)
     ON CONFLICT (id) DO NOTHING`,
    [orderId3, customerId, storeId]
  );
  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount
     ) VALUES (
       $1, $1, $2, $3,
       'LOOKING_FOR_RIDER', 'Security Test Store', 'Cyber Security Center', 28.6315, 77.2167,
       'Security Test Customer', '+919876500001', 'Connaught Place 3', 28.6320, 77.2180,
       2.0, FALSE, 0.00
     ) ON CONFLICT (id) DO UPDATE SET state = 'LOOKING_FOR_RIDER', rider_id = NULL`,
    [deliveryId3, orderId3, storeId]
  );

  const futureExpiresAt = Date.now() + 600000;
  await pool.query(
    `INSERT INTO offers (
       id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
       status, offer_created_at, offer_expires_at, earnings_amount,
       delivery_distance_km, total_distance_km, estimated_duration_mins
     ) VALUES (
       $1, $1, 'evt_sec_01', 'notif_sec_01', $2, $3, $4,
       'OFFERED', $5, $6, 50.00,
       2.0, 2.5, 15
     ) ON CONFLICT (id) DO UPDATE SET status = 'OFFERED', offer_expires_at = $6, rider_id = $4`,
    [offerId3, deliveryId3, orderId3, riderId3A, Date.now(), futureExpiresAt]
  );

  // 3.1 Unauthorized Rider Claim Rejection (Rider Beta attempts to claim Rider Alpha's offer)
  const unauthOfferClaim = await apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3B);
  const unauthClaimOk = unauthOfferClaim.status === 403;
  recordResult('Unauthorized Rider (Rider Beta) Cannot Accept Rider Alpha Offer (403)', 'POST', `/api/v1/rider/offers/:id/accept`, unauthOfferClaim.status, unauthClaimOk, unauthOfferClaim.data?.message);

  // 3.2 High-Concurrency Race Condition: 5 Simultaneous Acceptance Calls
  const concurrentCalls = [
    apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3A),
    apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3A),
    apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3A),
    apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3A),
    apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3A)
  ];

  const raceResults = await Promise.all(concurrentCalls);
  const successfulAccepts = raceResults.filter(r => r.status === 200 && (r.data?.accepted === true || (r.data?.ok === true && !r.data?.idempotencyReplay)));
  const idempotentReplays = raceResults.filter(r => r.status === 200 && r.data?.idempotencyReplay === true);
  const conflicts = raceResults.filter(r => r.status === 409);

  const raceOk = successfulAccepts.length >= 1 && (successfulAccepts.length + idempotentReplays.length + conflicts.length === 5);
  recordResult('Concurrent 5x Offer Accept Race Handled Atomically (Zero corruption)', 'POST', `/api/v1/rider/offers/:id/accept`, 200, raceOk, `Wins: ${successfulAccepts.length}, Replays: ${idempotentReplays.length}, Conflicts: ${conflicts.length}`);

  // Check DB state: Exactly 1 delivery session accepted by Rider Alpha
  const sessionAfterRace = await pool.query('SELECT state, rider_id FROM delivery_sessions WHERE delivery_id = $1', [deliveryId3]);
  const sessionDbOk = sessionAfterRace.rows[0]?.state === 'ACCEPTED' && sessionAfterRace.rows[0]?.rider_id === riderId3A;
  recordResult('DB Invariant: Delivery session transitioned to ACCEPTED by Rider Alpha', 'SQL', 'delivery_sessions.state', 200, sessionDbOk);

  // 3.3 Post-Claim Conflict Handling (409 OFFER_CLAIMED / 403)
  const postClaimAttempt = await apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3B);
  const postClaimOk = (postClaimAttempt.status === 409 || postClaimAttempt.status === 403);
  recordResult('Post-Acceptance Second Rider Claim Blocked (403/409)', 'POST', `/api/v1/rider/offers/:id/accept`, postClaimAttempt.status, postClaimOk, postClaimAttempt.data?.message);

  // 3.4 Expired Offer Claim Handling (409 OFFER_EXPIRED)
  const orderId4 = 'ord_sec_offer_04';
  const deliveryId4 = 'deliv_sec_offer_04';
  const offerId4 = 'off_sec_offer_04';
  await pool.query(
    `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
     VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 150.00, '[]'::jsonb, '{"address": "Connaught Place 4", "lat": 28.6320, "lng": 77.2180}'::jsonb, 'dummy_hash', FALSE, 0.00)
     ON CONFLICT (id) DO NOTHING`,
    [orderId4, customerId, storeId]
  );
  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount
     ) VALUES (
       $1, $1, $2, $3,
       'LOOKING_FOR_RIDER', 'Security Test Store', 'Cyber Security Center', 28.6315, 77.2167,
       'Security Test Customer', '+919876500001', 'Connaught Place 4', 28.6320, 77.2180,
       1.8, FALSE, 0.00
     ) ON CONFLICT (id) DO UPDATE SET state = 'LOOKING_FOR_RIDER'`,
    [deliveryId4, orderId4, storeId]
  );

  const pastExpiresAt = Date.now() - 30000;
  await pool.query(
    `INSERT INTO offers (
       id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
       status, offer_created_at, offer_expires_at, earnings_amount,
       delivery_distance_km, total_distance_km, estimated_duration_mins
     ) VALUES (
       $1, $1, 'evt_sec_02', 'notif_sec_02', $2, $3, $4,
       'OFFERED', $5, $6, 40.00,
       1.8, 2.2, 12
     ) ON CONFLICT (id) DO UPDATE SET status = 'OFFERED', offer_expires_at = $6`,
    [offerId4, deliveryId4, orderId4, riderId3A, Date.now() - 60000, pastExpiresAt]
  );

  const expiredClaimRes = await apiRequest('POST', `/api/v1/rider/offers/${offerId4}/accept`, {}, headers3A);
  const expiredClaimOk = (expiredClaimRes.status === 409 && (expiredClaimRes.data?.error === 'OFFER_EXPIRED' || expiredClaimRes.data?.message?.includes('expired')));
  recordResult('Expired Offer Claim Rejected with 409 OFFER_EXPIRED', 'POST', `/api/v1/rider/offers/:id/accept`, expiredClaimRes.status, expiredClaimOk, expiredClaimRes.data?.message);

  // ==========================================================================
  // SECTION 4: Expired, Malformed, and Forged JWT on iOS Endpoints
  // ==========================================================================
  console.log('\n\x1b[1m\x1b[33m--- Section 4: Expired, Malformed, and Forged JWT on iOS Endpoints ---\x1b[0m');

  const testProtectedEndpoint = '/api/v1/delivery/rider/profile';

  // 4.1 Missing Authorization header
  const noAuthRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'X-Client-Platform': 'iOS-Rider'
  });
  const noAuthOk = noAuthRes.status === 401;
  recordResult('Missing Authorization Header -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, noAuthRes.status, noAuthOk);

  // 4.2 Malformed Bearer token format
  const malformedRes1 = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': 'Bearer not_a_real_jwt_token',
    'X-Client-Platform': 'iOS-Rider'
  });
  const malformedOk1 = malformedRes1.status === 401;
  recordResult('Malformed Random String Bearer -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, malformedRes1.status, malformedOk1);

  const malformedRes2 = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': 'Bearer undefined',
    'X-Client-Platform': 'iOS-Rider'
  });
  const malformedOk2 = malformedRes2.status === 401;
  recordResult('Literal "Bearer undefined" -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, malformedRes2.status, malformedOk2);

  // 4.3 Expired JWT Attack
  const expiredJwt = jwt.sign(
    { sub: riderId1A, role: 'RIDER', iat: Math.floor(Date.now() / 1000) - 7200 },
    process.env.JWT_SECRET,
    {
      algorithm: 'HS256',
      issuer: process.env.JWT_ISSUER,
      audience: process.env.JWT_AUDIENCE,
      expiresIn: '-10m'
    }
  );
  const expiredJwtRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': `Bearer ${expiredJwt}`,
    'X-Client-Platform': 'iOS-Rider'
  });
  const expiredJwtOk = expiredJwtRes.status === 401;
  recordResult('Expired JWT (exp in past) -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, expiredJwtRes.status, expiredJwtOk);

  // 4.4 Forged Signature Attack (Signed with attacker's rogue secret)
  const rogueSecret = 'attacker_rogue_secret_key_that_is_32_chars!';
  const forgedJwt = jwt.sign(
    { sub: riderId1A, role: 'RIDER' },
    rogueSecret,
    {
      algorithm: 'HS256',
      issuer: process.env.JWT_ISSUER,
      audience: process.env.JWT_AUDIENCE,
      expiresIn: '1h'
    }
  );
  const forgedJwtRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': `Bearer ${forgedJwt}`,
    'X-Client-Platform': 'iOS-Rider'
  });
  const forgedJwtOk = forgedJwtRes.status === 401;
  recordResult('Forged Signature (Wrong Secret) -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, forgedJwtRes.status, forgedJwtOk);

  // 4.5 Algorithm "none" Vulnerability Exploit Attempt
  const algNoneHeader = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const algNonePayload = Buffer.from(JSON.stringify({
    sub: riderId1A,
    role: 'RIDER',
    iss: process.env.JWT_ISSUER,
    aud: process.env.JWT_AUDIENCE,
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString('base64url');
  const algNoneToken = `${algNoneHeader}.${algNonePayload}.`;

  const algNoneRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': `Bearer ${algNoneToken}`,
    'X-Client-Platform': 'iOS-Rider'
  });
  const algNoneOk = algNoneRes.status === 401;
  recordResult('Algorithm "none" Token Stripping Attack -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, algNoneRes.status, algNoneOk);

  // 4.6 Spoofed Issuer / Audience Attack
  const spoofedAudJwt = jwt.sign(
    { sub: riderId1A, role: 'RIDER' },
    process.env.JWT_SECRET,
    {
      algorithm: 'HS256',
      issuer: process.env.JWT_ISSUER,
      audience: 'untrusted-rogue-audience-client',
      expiresIn: '1h'
    }
  );
  const spoofedAudRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': `Bearer ${spoofedAudJwt}`,
    'X-Client-Platform': 'iOS-Rider'
  });
  const spoofedAudOk = spoofedAudRes.status === 401;
  recordResult('Spoofed Audience Claim -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, spoofedAudRes.status, spoofedAudOk);

  // 4.7 Empty Subject Claim Attack
  const emptySubJwt = jwt.sign(
    { sub: '', role: 'RIDER' },
    process.env.JWT_SECRET,
    {
      algorithm: 'HS256',
      issuer: process.env.JWT_ISSUER,
      audience: process.env.JWT_AUDIENCE,
      expiresIn: '1h'
    }
  );
  const emptySubRes = await apiRequest('GET', testProtectedEndpoint, null, {
    'Authorization': `Bearer ${emptySubJwt}`,
    'X-Client-Platform': 'iOS-Rider'
  });
  const emptySubOk = emptySubRes.status === 401;
  recordResult('Empty / Whitespace Subject Claim -> 401 UNAUTHORIZED', 'GET', testProtectedEndpoint, emptySubRes.status, emptySubOk);

  // ==========================================================================
  // SECTION 5: End-to-End Adversarial Penetration Chain Simulation
  // ==========================================================================
  console.log('\n\x1b[1m\x1b[33m--- Section 5: End-to-End Adversarial Penetration Chain Simulation ---\x1b[0m');

  // Vector 1: Attacker attempts to forge rider JWT -> Rejected (401)
  const chain1 = await apiRequest('GET', '/api/v1/delivery/rider/profile', null, { 'Authorization': `Bearer ${forgedJwt}` });
  const chain1Ok = chain1.status === 401;
  recordResult('Kill Chain 1: Forged Token Infiltration Blocked', 'GET', '/api/v1/delivery/rider/profile', chain1.status, chain1Ok);

  // Vector 2: Attacker with legitimate token attempts to hijack unassigned offer -> Rejected (403)
  const chain2 = await apiRequest('POST', `/api/v1/rider/offers/${offerId3}/accept`, {}, headers3B);
  const chain2Ok = (chain2.status === 403 || chain2.status === 409);
  recordResult('Kill Chain 2: Cross-Rider Offer Theft Blocked', 'POST', `/api/v1/rider/offers/:id/accept`, chain2.status, chain2Ok);

  // Vector 3: Attacker attempts telemetry sequence corruption -> Rejected (400)
  const chain3 = await apiRequest('POST', `/api/v1/delivery/${deliveryIdTelem}/telemetry`, { sequenceNumber: -99, latitude: 28.1, longitude: 77.1 }, headers2);
  const chain3Ok = chain3.status === 400;
  recordResult('Kill Chain 3: Telemetry Negative Packet Poisoning Blocked', 'POST', `/api/v1/delivery/:id/telemetry`, chain3.status, chain3Ok);

  // Vector 4: Attacker attempts to tamper OTP -> Blocked (400)
  const chain4 = await apiRequest('POST', `/api/v1/orders/${deliveryId1}/deliver-with-otp`, { otp: '000000' }, headers1A);
  const chain4Ok = chain4.status === 400;
  recordResult('Kill Chain 4: Brute-Force Locked OTP Verification Blocked', 'POST', `/api/v1/orders/:id/deliver-with-otp`, chain4.status, chain4Ok);

  // ==========================================================================
  // FINAL EVALUATION & SUMMARY
  // ==========================================================================
  console.log('\n\x1b[1m\x1b[36m======================================================================\x1b[0m');
  console.log(`\x1b[1m\x1b[36m  ADVERSARIAL SECURITY AUDIT COMPLETE: ${passedCount} / ${totalCount} ASSERTIONS PASSED\x1b[0m`);
  console.log('\x1b[1m\x1b[36m======================================================================\x1b[0m\n');

  // Teardown server
  if (serverInstance) {
    await new Promise(resolve => serverInstance.close(resolve));
    console.log('🔒 Verification server shut down cleanly.');
  }

  // Final exit code
  if (passedCount === totalCount && totalCount > 0) {
    console.log('\x1b[1m\x1b[32m✔ ALL ADVERSARIAL PENETRATION TESTS PASSED: SYSTEM RESILIENT.\x1b[0m\n');
    return { ok: true, passedCount, totalCount, testResults };
  } else {
    console.error(`\x1b[1m\x1b[31m✘ ADVERSARIAL SECURITY FAILURES DETECTED (${totalCount - passedCount} failed).\x1b[0m\n`);
    throw new Error(`Adversarial security audit failed: ${passedCount}/${totalCount} passed.`);
  }
}

if (require.main === module) {
  run().catch(err => {
    console.error('FATAL ADVERSARIAL TEST ERROR:', err);
    if (serverInstance) {
      serverInstance.close();
    }
    process.exit(1);
  });
}

module.exports = { run };
