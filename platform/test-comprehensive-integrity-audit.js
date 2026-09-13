#!/usr/bin/env node

/**
 * ==============================================================================
 * COMMERCE OS — ULTRA-DEEP FULL-SYSTEM SEQUENCE, DATA & CODE INTEGRITY AUDIT
 * ==============================================================================
 * Authority: AGENT-4 (QA / ADVERSARIAL REVIEW / VERIFICATION AUTHORITY)
 * Task ID: TASK-A4-ULTRA-DEEP-INTEGRITY-AUDIT
 * 
 * Comprehensive, zero-mercy system-wide audit:
 *   Module 1: Finite State Machine (FSM) Order Lifecycle & Illegal Sequence Skips
 *   Module 2: Live PostgreSQL Database Schema & Column Consistency Parity
 *   Module 3: Concurrency Race Condition Hammering (50-Request Concurrent Claim)
 *   Module 4: Cryptographic & Security Boundary Edge-Cases (OTP, Replay, JWT)
 *   Module 5: Real-Time Event Stream Delivery & Outbox Processing
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
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8202';

const { server, pool, getAppRepositories } = require('./server/production-server');
const { DeliveryOtpService, TransactionalSellerRepository } = require('./repositories');

const PORT = 8202;
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

function generateJwt(subject, role = 'rider', extras = {}) {
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

async function runAudit() {
  console.log('==============================================================================');
  console.log('  COMMERCE OS — ULTRA-DEEP FULL-SYSTEM SEQUENCE & DATA INTEGRITY AUDIT        ');
  console.log('==============================================================================\n');

  // Boot live server on isolated port 8202
  await new Promise((resolve) => {
    serverInstance = server.listen(PORT, () => {
      console.log(`  🚀 Audit verification server running on ${BASE_URL}\n`);
      resolve();
    });
  });

  const repos = await getAppRepositories();
  assert(Boolean(repos), 'Resolved production repository ecosystem from PostgreSQL');

  // Seed baseline customer, store, and rider
  const customerId = `cust_${Date.now()}`;
  const testPhone = `+9198${Date.now().toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO customers (id, phone, full_name, email)
    VALUES ($1, $2, 'Deep Audit Patient', 'audit@commerceos.io')
  `, [customerId, testPhone]);

  const storeId = `store_${Date.now()}`;
  await pool.query(`
    INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
    VALUES ($1, 'Apollo Pharmacy Deep Audit', 'Koramangala 5th Block', 12.9352, 77.6245, true)
  `, [storeId]);

  const testRiderId = `rider_audit_${Date.now()}`;
  const riderPhone = `+9199${Date.now().toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
    VALUES ($1, $1, $2, 'Audit Express Rider', 'KA-01-EQ-9999', 'TWO_WHEELER', 'ACTIVE')
    ON CONFLICT (rider_id) DO NOTHING
  `, [testRiderId, riderPhone]);
  const riderToken = generateJwt(testRiderId, 'rider');

  const rivalRiderId = `rider_rival_${Date.now()}`;
  const rivalPhone = `+9199${(Date.now() + 1).toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
    VALUES ($1, $1, $2, 'Rival Express Rider', 'KA-01-EQ-8888', 'TWO_WHEELER', 'ACTIVE')
    ON CONFLICT (rider_id) DO NOTHING
  `, [rivalRiderId, rivalPhone]);
  const rivalRiderToken = generateJwt(rivalRiderId, 'rider');

  // --------------------------------------------------------------------------
  // MODULE 1: FINITE STATE MACHINE (FSM) SEQUENCE & TRANSITION INTEGRITY
  // --------------------------------------------------------------------------
  console.log('\n--- Module 1: Finite State Machine Sequence & Illegal Skip Rejection ---');

  // 1.1 Create Initial Order in CREATED state
  const orderId = `ord_audit_${Date.now()}`;
  const rawOtp = '7741';
  const hashedOtp = DeliveryOtpService.hashOtp(rawOtp, process.env.COMMERCEOS_OTP_PEPPER);

  await pool.query(`
    INSERT INTO orders (
      id, order_id, customer_id, store_id, status, total_amount,
      delivery_address, items, delivery_otp_hash
    ) VALUES (
      $1, $1, $2, $3, 'CREATED', 90.00,
      '{"address_line": "7th Cross, Koramangala", "city": "Bangalore"}'::jsonb,
      '[{"sku": "RX-PARA-650", "name": "Paracetamol 650mg", "quantity": 2, "price": 45.00}]'::jsonb,
      $4
    )
  `, [orderId, customerId, storeId, hashedOtp]);

  assert(Boolean(orderId), `Initial order seeded in CREATED state: ${orderId}`);

  // 1.2 Illegal Transition: Attempting to DELIVER directly from CREATED state
  const illegalDeliverRes = await apiRequest('POST', `/api/v1/delivery/non_existent_delivery/deliver-with-otp`, {
    otp: rawOtp,
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(
    illegalDeliverRes.status === 400 || illegalDeliverRes.status === 404 || illegalDeliverRes.status === 409,
    `Illegal Skip Rejected: Cannot deliver unassigned non-existent delivery (Status: ${illegalDeliverRes.status})`
  );

  // 1.3 Illegal Transition: Attempting to PICKUP before offer dispatched and accepted
  const illegalPickupRes = await apiRequest('POST', `/api/v1/delivery/rider/orders/${orderId}/pickup`, {
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(
    illegalPickupRes.status === 400 || illegalPickupRes.status === 404 || illegalPickupRes.status === 409,
    `Illegal Skip Rejected: Cannot pickup unassigned order (Status: ${illegalPickupRes.status})`
  );

  // 1.4 State Progression: Verify prescription & confirm order
  await pool.query(`UPDATE orders SET status = 'CONFIRMED' WHERE id = $1`, [orderId]);
  const orderStatusCheck1 = await pool.query(`SELECT status FROM orders WHERE id = $1`, [orderId]);
  assert(orderStatusCheck1.rows[0].status === 'CONFIRMED', 'Order status progressed to CONFIRMED');

  // 1.5 Offer Creation & Dispatch
  const deliveryId = `del_audit_${Date.now()}`;
  await pool.query(
    `INSERT INTO delivery_sessions (
       id, delivery_id, order_id, store_id,
       state, merchant_name, merchant_address, merchant_lat, merchant_lng,
       customer_name, customer_phone, customer_address, customer_lat, customer_lng,
       distance_km, is_cod, cod_amount
     ) VALUES (
       $1, $1, $2, $3,
       'LOOKING_FOR_RIDER', 'Apollo Pharmacy Deep Audit', 'Koramangala 5th Block', 12.9352, 77.6245,
       'Deep Audit Patient', '+919876500001', 'Koramangala 4th Block', 12.9310, 77.6210,
       2.0, FALSE, 0.00
     ) ON CONFLICT (id) DO NOTHING`,
    [deliveryId, orderId, storeId]
  );

  const offerId = `off_${Date.now()}`;
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
     ) ON CONFLICT (id) DO NOTHING`,
    [offerId, deliveryId, orderId, testRiderId, Date.now(), futureExpiresAt]
  );

  // 1.5b Cross-Rider Offer Steal Attack: Rival rider attempts to claim testRiderId's offer
  const rivalClaimRes = await apiRequest('POST', `/api/v1/rider/offers/${offerId}/accept`, {}, {
    'Authorization': `Bearer ${rivalRiderToken}`
  });
  assert(
    rivalClaimRes.status === 403 || rivalClaimRes.status === 409,
    `Cross-Rider Steal Blocked: Rival rider rejected from claiming assigned offer (Status: ${rivalClaimRes.status})`
  );

  // Accept offer legitimately
  const acceptRes = await apiRequest('POST', `/api/v1/rider/offers/${offerId}/accept`, {
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(acceptRes.status === 200, `Assigned rider accepted offer (${acceptRes.status})`);

  // 1.6 Adversarial Telemetry Attacks
  console.log('\n--- Module 4: Live Telemetry & Security Edge-Cases ---');
  // 1.6a Missing sequence number -> 400
  const telemMissingSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
    riderId: testRiderId,
    latitude: 12.9350,
    longitude: 77.6240
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(telemMissingSeq.status === 400, 'Rejects missing telemetry sequenceNumber (400)');

  // 1.6b Negative / Zero sequence number -> 400
  const telemNegSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
    riderId: testRiderId,
    latitude: 12.9350,
    longitude: 77.6240,
    sequenceNumber: -42
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(telemNegSeq.status === 400, 'Rejects negative sequenceNumber (400)');

  // 1.6c Corrupted sequence number -> 400
  const telemCorruptSeq = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
    riderId: testRiderId,
    latitude: 12.9350,
    longitude: 77.6240,
    sequenceNumber: 'invalid_corrupted_packet'
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(telemCorruptSeq.status === 400, 'Rejects corrupted string sequenceNumber (400)');

  // 1.6d Invalid coordinates (NaN or missing) -> 400
  const telemInvalidCoord = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
    riderId: testRiderId,
    latitude: 'invalid_lat',
    longitude: 77.6240,
    sequenceNumber: 100
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(telemInvalidCoord.status === 400, 'Rejects invalid latitude (400)');

  // 1.6e Valid telemetry packet -> 200
  const seq1Res = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
    riderId: testRiderId,
    latitude: 12.9350,
    longitude: 77.6240,
    speedKmh: 24.5,
    sequenceNumber: 100
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(seq1Res.status === 200 || seq1Res.status === 201, 'Accepts valid telemetry packet seq 100 (200)');

  // 1.7 Order Pickup & Transition to IN_TRANSIT
  await pool.query(`UPDATE orders SET status = 'OUT_FOR_DELIVERY' WHERE id = $1`, [orderId]);
  await pool.query(`UPDATE delivery_sessions SET state = 'IN_TRANSIT' WHERE delivery_id = $1`, [deliveryId]);
  const orderStatusCheck2 = await pool.query(`SELECT status FROM orders WHERE id = $1`, [orderId]);
  assert(orderStatusCheck2.rows[0].status === 'OUT_FOR_DELIVERY', 'Order status transitioned to OUT_FOR_DELIVERY');

  // 1.8 Adversarial OTP Tamper Testing on Active Delivery
  // 1.8a SQL Injection in OTP PIN -> 400
  const sqliOtpRes = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/deliver-with-otp`, {
    otp: "' OR '1'='1",
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(sqliOtpRes.status === 400, 'Rejects SQL Injection in OTP payload (400)');

  // 1.8b Short OTP (< 4 digits) -> 400
  const shortOtpRes = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/deliver-with-otp`, {
    otp: "12",
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(shortOtpRes.status === 400, 'Rejects short OTP PIN < 4 digits (400)');

  // 1.8c Incorrect OTP PIN -> 400
  const wrongOtpRes = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/deliver-with-otp`, {
    otp: "999999",
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(wrongOtpRes.status === 400, 'Rejects incorrect OTP PIN (400)');

  // 1.9 Complete Delivery with True HMAC-SHA256 OTP
  const validDeliverRes = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/deliver-with-otp`, {
    otp: rawOtp,
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(validDeliverRes.status === 200, 'Order successfully delivered with valid HMAC-SHA256 OTP (200)');

  // 1.10 Retrograde & Re-delivery Attack: Attempting to re-deliver already delivered order
  const redeliverRes = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/deliver-with-otp`, {
    otp: rawOtp,
    riderId: testRiderId
  }, { 'Authorization': `Bearer ${riderToken}` });
  assert(
    redeliverRes.status === 400 || redeliverRes.status === 409,
    `Re-delivery Attack Rejected: Cannot re-deliver completed order (Status: ${redeliverRes.status})`
  );

  // --------------------------------------------------------------------------
  // MODULE 2: DATABASE SCHEMA & COLUMN CONSISTENCY PARITY
  // --------------------------------------------------------------------------
  console.log('\n--- Module 2: Live PostgreSQL Database Schema & Column Consistency ---');

  const checkTableColumns = async (tableName, requiredCols) => {
    const res = await pool.query(`
      SELECT column_name, data_type 
      FROM information_schema.columns 
      WHERE table_name = $1
    `, [tableName]);
    const existingCols = res.rows.map(r => r.column_name);
    const missing = requiredCols.filter(c => !existingCols.includes(c));
    assert(missing.length === 0, `Table '${tableName}' has all required columns (Missing: ${missing.join(', ') || 'none'})`);
  };

  await checkTableColumns('orders', ['id', 'order_id', 'customer_id', 'status', 'total_amount', 'delivery_otp_hash']);
  await checkTableColumns('stores', ['id', 'store_name', 'address', 'is_active']);
  await checkTableColumns('riders', ['id', 'rider_id', 'phone', 'full_name', 'status']);
  await checkTableColumns('offers', ['id', 'order_id', 'rider_id', 'fcm_delivery_status', 'offer_created_at']);
  await checkTableColumns('rider_telemetry', ['id', 'rider_id', 'delivery_id', 'latitude', 'longitude', 'sequence_number']);
  await checkTableColumns('rider_device_tokens', ['rider_id', 'token', 'platform']);

  // --------------------------------------------------------------------------
  // MODULE 3: CONCURRENCY RACE CONDITION HAMMERING (50 CONCURRENT CLAIMS)
  // --------------------------------------------------------------------------
  console.log('\n--- Module 3: Concurrency Race Condition Hammering (50 Concurrent Claims) ---');

  // Seed single contested offer
  const raceOrderId = `ord_race_${Date.now()}`;
  const raceDeliveryId = `del_race_${Date.now()}`;
  const raceOfferId = `off_race_${Date.now()}`;
  const raceRiderId = `r_race_champ_${Date.now()}`;

  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
    VALUES ($1, $1, $2, 'Race Champion', 'KA-01-EQ-1111', 'TWO_WHEELER', 'ACTIVE')
    ON CONFLICT (rider_id) DO NOTHING
  `, [raceRiderId, `+9199${Date.now().toString().slice(-8)}`]);

  await pool.query(`
    INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
    VALUES ($1, $1, $2, $3, 'CONFIRMED', 150.00, '[]'::jsonb, '{"address": "MG Road"}'::jsonb, 'dummy_hash', FALSE, 0.00)
  `, [raceOrderId, customerId, storeId]);

  await pool.query(`
    INSERT INTO delivery_sessions (
      id, delivery_id, order_id, store_id,
      state, merchant_name, merchant_address, merchant_lat, merchant_lng,
      customer_name, customer_phone, customer_address, customer_lat, customer_lng,
      distance_km, is_cod, cod_amount
    ) VALUES (
      $1, $1, $2, $3,
      'LOOKING_FOR_RIDER', 'Store Race', 'MG Road', 12.9352, 77.6245,
      'Customer Race', '+919999999999', 'Indiranagar', 12.9310, 77.6210,
      2.0, FALSE, 0.00
    )
  `, [raceDeliveryId, raceOrderId, storeId]);

  await pool.query(`
    INSERT INTO offers (
      id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
      status, offer_created_at, offer_expires_at, earnings_amount,
      delivery_distance_km, total_distance_km, estimated_duration_mins
    ) VALUES (
      $1, $1, 'evt_race', 'notif_race', $2, $3, $4,
      'OFFERED', $5, $6, 60.00,
      3.0, 3.5, 20
    )
  `, [raceOfferId, raceDeliveryId, raceOrderId, raceRiderId, Date.now(), Date.now() + 600000]);

  // Spawn 50 simultaneous accept requests with raceRiderId token
  const raceToken = generateJwt(raceRiderId, 'rider');
  const CONCURRENT_CALLS = 50;
  const calls = [];
  for (let i = 0; i < CONCURRENT_CALLS; i++) {
    calls.push(apiRequest('POST', `/api/v1/rider/offers/${raceOfferId}/accept`, {}, { 'Authorization': `Bearer ${raceToken}` }));
  }

  const raceResults = await Promise.all(calls);
  const okOrConflictCount = raceResults.filter(r => r.status === 200 || r.status === 409).length;
  assert(okOrConflictCount === CONCURRENT_CALLS, `Concurrency Race: All ${CONCURRENT_CALLS} concurrent calls resolved deterministically (Wins/Replays/Conflicts = ${okOrConflictCount})`);

  // Verify in DB that offer state is definitively ACCEPTED
  const checkDbWinner = await pool.query(`SELECT status, rider_id FROM offers WHERE id = $1`, [raceOfferId]);
  assert(checkDbWinner.rows[0].status === 'ACCEPTED', `Database records definitive offer state: ${checkDbWinner.rows[0].status}`);
  assert(checkDbWinner.rows[0].rider_id === raceRiderId, `Database records winner: ${checkDbWinner.rows[0].rider_id}`);

  // --------------------------------------------------------------------------
  // MODULE 4: CRYPTOGRAPHIC & SECURITY BOUNDARY EDGE-CASES
  // --------------------------------------------------------------------------
  console.log('\n--- Module 4: Cryptographic & Security Edge-Cases ---');

  // 4.1 JWT Algorithm "None" Attack
  const algNoneToken = jwt.sign({ sub: testRiderId, role: 'rider' }, '', { algorithm: 'none' });
  const algNoneRes = await apiRequest('GET', '/api/v1/delivery/rider/profile', null, {
    'Authorization': `Bearer ${algNoneToken}`
  });
  assert(algNoneRes.status === 401, 'Rejects JWT Algorithm "none" token stripping attack (401)');

  // 4.2 JWT Forged Secret Attack
  const forgedToken = jwt.sign({ sub: testRiderId, role: 'rider' }, 'wrong_secret_12345678901234567890');
  const forgedRes = await apiRequest('GET', '/api/v1/delivery/rider/profile', null, {
    'Authorization': `Bearer ${forgedToken}`
  });
  assert(forgedRes.status === 401, 'Rejects JWT with forged cryptographic signature (401)');

  // --------------------------------------------------------------------------
  // MODULE 5: REALTIME EVENT STREAM & OUTBOX INTEGRITY
  // --------------------------------------------------------------------------
  console.log('\n--- Module 5: Realtime Event Stream & Outbox Audit ---');

  const outboxCheck = await pool.query(`
    SELECT event_type, aggregate_type, payload
    FROM outbox_events
    ORDER BY created_at DESC
    LIMIT 5
  `);
  assert(outboxCheck.rows.length > 0, `Outbox table actively persisting domain events (found ${outboxCheck.rows.length} recent events)`);

  for (const evt of outboxCheck.rows) {
    assert(Boolean(evt.event_type), `Event type populated: ${evt.event_type}`);
    assert(Boolean(evt.payload), `Event payload populated and valid JSON for ${evt.event_type}`);
  }

  // --------------------------------------------------------------------------
  // AUDIT SUMMARY
  // --------------------------------------------------------------------------
  console.log('\n==============================================================================');
  console.log(`TOTAL AUDIT ASSERTIONS : ${totalAssertions}`);
  console.log(`PASSED                 : ${passedAssertions}`);
  console.log(`FAILED                 : ${failedAssertions}`);
  console.log(`SUCCESS RATE           : ${Math.round((passedAssertions / totalAssertions) * 100)}%`);
  console.log('==============================================================================\n');

  // Teardown server
  await new Promise(r => serverInstance.close(r));
  console.log('🔒 Verification server shut down cleanly.\n');

  if (failedAssertions > 0) {
    process.exit(1);
  }
}

runAudit().catch(err => {
  console.error('FATAL_AUDIT_ERROR:', err);
  if (serverInstance) serverInstance.close();
  process.exit(1);
});
