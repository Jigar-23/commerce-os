'use strict';

const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');

const {
  ApnsPayloadBuilder,
  ApnsDispatcher,
  ApnsDeliveryResult,
  normalizeApnsToken,
  APNS_MAX_PAYLOAD_BYTES
} = require('./services/apns-dispatcher');

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@127.0.0.1:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8198';

const TEST_PORT = 8198;
const TEST_BASE_URL = `http://127.0.0.1:${TEST_PORT}`;
const DATABASE_URL = process.env.DATABASE_URL;

// Test tracking
let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

function assert(condition, message) {
  totalTests++;
  if (condition) {
    passedTests++;
    console.log(` ✔ PASS: ${message}`);
  } else {
    failedTests++;
    console.error(` ✘ FAIL: ${message}`);
  }
}

function assertThrows(fn, expectedErrSnippet, message) {
  totalTests++;
  try {
    fn();
    failedTests++;
    console.error(` ✘ FAIL: ${message} (expected error containing '${expectedErrSnippet}', but none was thrown)`);
  } catch (err) {
    if (!expectedErrSnippet || err.message.includes(expectedErrSnippet)) {
      passedTests++;
      console.log(` ✔ PASS: ${message}`);
    } else {
      failedTests++;
      console.error(` ✘ FAIL: ${message} (threw '${err.message}', expected '${expectedErrSnippet}')`);
    }
  }
}

function makeJwt(payload, secret = 'dev_jwt_secret_do_not_use_in_prod_minimum_32_chars') {
  const header = { alg: 'HS256', typ: 'JWT' };
  const encode = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const unsigned = `${encode(header)}.${encode(payload)}`;
  const signature = crypto.createHmac('sha256', secret).update(unsigned).digest('base64url');
  return `${unsigned}.${signature}`;
}

async function apiRequest(method, path, body = null, headers = {}) {
  return new Promise((resolve) => {
    const url = new URL(path, TEST_BASE_URL);
    const reqHeaders = {
      'Content-Type': 'application/json',
      ...headers
    };
    const req = http.request(url, { method, headers: reqHeaders }, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        let parsed = null;
        try { parsed = JSON.parse(data); } catch { parsed = data; }
        resolve({ status: res.statusCode, headers: res.headers, data: parsed });
      });
    });
    req.on('error', (err) => {
      resolve({ status: 0, error: err.message });
    });
    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

async function runAllTests() {
  console.log('======================================================================');
  console.log('  COMMERCE OS — NATIVE APNs DISPATCHER & PAYLOAD PIPELINE TEST SUITE  ');
  console.log('======================================================================\n');

  // -------------------------------------------------------------------------
  // SECTION 1: Token Normalization & Format Validation
  // -------------------------------------------------------------------------
  console.log('--- Section 1: APNs Device Token Normalization ---');
  const validHex64 = '1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b';
  
  assert(
    normalizeApnsToken(validHex64) === validHex64,
    'Preserves standard 64-character hex device token'
  );

  const formattedToken = '<1a2b3c4d 5e6f7a8b 9c0d1e2f 3a4b5c6d 7e8f9a0b 1c2d3e4f 5a6b7c8d 9e0f1a2b>';
  assert(
    normalizeApnsToken(formattedToken) === validHex64,
    'Strips angle brackets and spaces from Apple native token representation'
  );

  const dashedToken = '1a2b3c4d-5e6f7a8b-9c0d1e2f-3a4b5c6d-7e8f9a0b-1c2d3e4f-5a6b7c8d-9e0f1a2b';
  assert(
    normalizeApnsToken(dashedToken) === validHex64,
    'Strips hyphens from formatted device token'
  );

  assertThrows(
    () => normalizeApnsToken(''),
    'APNS_INVALID_TOKEN',
    'Rejects empty device token string'
  );

  assertThrows(
    () => normalizeApnsToken('1234abcd'),
    'APNS_INVALID_TOKEN',
    'Rejects short non-64-character token'
  );

  assertThrows(
    () => normalizeApnsToken(validHex64.replace('1a', 'zz')),
    'APNS_INVALID_TOKEN',
    'Rejects invalid non-hexadecimal characters'
  );

  // -------------------------------------------------------------------------
  // SECTION 2: Native APNs Payload Builder & Specification Schema
  // -------------------------------------------------------------------------
  console.log('\n--- Section 2: APNs Payload Builder & Schema Validation ---');

  // Simple string alert
  const simpleAlert = new ApnsPayloadBuilder().setAlert('Simple message').build();
  assert(simpleAlert.payload.aps.alert === 'Simple message', 'Builds simple string alert payload');

  // Full dictionary alert
  const fullAlertBuilder = new ApnsPayloadBuilder()
    .setTitle('Emergency Alert')
    .setSubtitle('Immediate Dispatch')
    .setBody('Please report to merchant immediately.')
    .setSound({ critical: 1, name: 'critical_alert.aiff', volume: 0.9 })
    .setBadge(3)
    .setContentAvailable(true)
    .setMutableContent(true)
    .setCategory('RIDER_ACTION')
    .setThreadId('delivery_thread_101')
    .setInterruptionLevel('critical')
    .setRelevanceScore(0.95)
    .setCustomFields({
      orderId: 'ord_123',
      deliveryId: 'del_456',
      actionUrl: 'commerceos://rider/deliveries/del_456'
    });

  const fullAlert = fullAlertBuilder.build();
  const aps = fullAlert.payload.aps;

  assert(aps.alert.title === 'Emergency Alert', 'Sets aps.alert.title');
  assert(aps.alert.subtitle === 'Immediate Dispatch', 'Sets aps.alert.subtitle');
  assert(aps.alert.body === 'Please report to merchant immediately.', 'Sets aps.alert.body');
  assert(aps.sound.critical === 1 && aps.sound.name === 'critical_alert.aiff' && aps.sound.volume === 0.9, 'Sets aps.sound critical alert dictionary');
  assert(aps.badge === 3, 'Sets numeric aps.badge');
  assert(aps['content-available'] === 1, 'Sets aps.content-available: 1 for background wake');
  assert(aps['mutable-content'] === 1, 'Sets aps.mutable-content: 1 for Notification Service Extension');
  assert(aps.category === 'RIDER_ACTION', 'Sets aps.category for actionable UI');
  assert(aps['thread-id'] === 'delivery_thread_101', 'Sets aps.thread-id for notification grouping');
  assert(aps['interruption-level'] === 'critical', 'Sets aps.interruption-level to critical');
  assert(aps['relevance-score'] === 0.95, 'Sets aps.relevance-score to 0.95');
  assert(fullAlert.payload.orderId === 'ord_123' && fullAlert.payload.actionUrl === 'commerceos://rider/deliveries/del_456', 'Preserves custom domain properties outside aps dictionary');

  // Badge validation
  assertThrows(
    () => new ApnsPayloadBuilder().setBadge(-1),
    'APNS_INVALID_BADGE',
    'Rejects negative badge counter'
  );

  // Reserved key protection
  assertThrows(
    () => new ApnsPayloadBuilder().setCustomData('aps', { hack: true }),
    'APNS_RESERVED_KEY',
    "Prohibits setting reserved key 'aps' via setCustomData"
  );

  // Interruption level validation
  assertThrows(
    () => new ApnsPayloadBuilder().setInterruptionLevel('extreme'),
    'APNS_INVALID_INTERRUPTION_LEVEL',
    'Rejects invalid interruption levels'
  );

  // Size limit enforcement (4096 bytes)
  const hugeData = 'X'.repeat(4200);
  assertThrows(
    () => new ApnsPayloadBuilder().setBody(hugeData).build(),
    'APNS_PAYLOAD_TOO_LARGE',
    'Enforces strict APNs 4096-byte payload size ceiling'
  );

  // -------------------------------------------------------------------------
  // SECTION 3: HTTP/2 Headers & Protocol Parameters
  // -------------------------------------------------------------------------
  console.log('\n--- Section 3: APNs HTTP/2 Request Headers ---');
  const dispatcher = new ApnsDispatcher({
    riderTopic: 'io.commerceos.rider',
    customerTopic: 'io.commerceos.customer'
  });

  const alertHeaders = dispatcher.buildRequestHeaders({
    topic: 'io.commerceos.rider',
    pushType: 'alert',
    priority: 10,
    collapseId: 'order_1001'
  });

  assert(alertHeaders[':method'] === 'POST', 'Sets HTTP/2 :method to POST');
  assert(alertHeaders['apns-topic'] === 'io.commerceos.rider', 'Sets apns-topic header');
  assert(alertHeaders['apns-push-type'] === 'alert', 'Sets apns-push-type: alert');
  assert(alertHeaders['apns-priority'] === '10', 'Sets apns-priority: 10 for immediate push');
  assert(Boolean(alertHeaders['apns-id']), 'Generates unique apns-id UUID');
  assert(alertHeaders['apns-collapse-id'] === 'order_1001', 'Sets apns-collapse-id for notification coalescing');

  const silentHeaders = dispatcher.buildRequestHeaders({
    topic: 'io.commerceos.customer',
    isSilent: true
  });

  assert(silentHeaders['apns-push-type'] === 'background', 'Infers apns-push-type: background for silent push');
  assert(silentHeaders['apns-priority'] === '5', 'Sets apns-priority: 5 for power-conservative background push');

  // -------------------------------------------------------------------------
  // SECTION 4: Commerce OS Pre-Configured Templates
  // -------------------------------------------------------------------------
  console.log('\n--- Section 4: Commerce OS APNs Templates ---');

  // 1. Rider Offer Template
  const testOffer = {
    offerId: 'off_ios_apns_test_01',
    deliveryId: 'del_ios_apns_01',
    orderId: 'ord_ios_apns_01',
    earningsAmount: 65.50,
    totalDistanceKm: 2.8,
    estimatedDurationMins: 14,
    merchantName: 'Apollo Pharmacy Koramangala',
    customerAddress: 'Flat 402, Green Glen Layout, Bellandur, Bangalore',
    offerExpiresAt: Date.now() + 30000
  };

  const riderPayload = dispatcher.createRiderOfferPayload(testOffer).build();
  assert(riderPayload.payload.aps.alert.title.includes('65'), 'Rider offer title formats earnings amount correctly');
  assert(riderPayload.payload.aps.sound.critical === 1, 'Rider offer sound is configured as critical alert');
  assert(riderPayload.payload.aps['content-available'] === 1 && riderPayload.payload.aps['mutable-content'] === 1, 'Rider offer has background wake and rich media extension flags');
  assert(riderPayload.payload.actionUrl === 'commerceos://rider/offer/off_ios_apns_test_01', 'Rider offer includes native deep link actionUrl');
  assert(riderPayload.payload.aps['interruption-level'] === 'time-sensitive', 'Rider offer has time-sensitive interruption level');

  // 2. Customer Order Status Template (Delivered)
  const orderDelivered = dispatcher.createOrderStatusPayload({ orderId: 'ord_apns_8899' }, 'DELIVERED').build();
  assert(orderDelivered.payload.aps.alert.title.includes('Delivered'), 'Delivered status template contains celebration title');
  assert(orderDelivered.payload.aps.badge === 0, 'Delivered status clears application badge to 0');
  assert(orderDelivered.payload.actionUrl === 'commerceos://orders/ord_apns_8899', 'Customer status contains order tracking deep link');

  // 3. Silent Sync Template
  const silentSync = dispatcher.createSilentSyncPayload('ETA_UPDATE', { etaMins: 7, riderLat: 12.9716, riderLng: 77.5946 }).build();
  assert(silentSync.payload.aps['content-available'] === 1, 'Silent sync has content-available: 1');
  assert(!silentSync.payload.aps.alert && !silentSync.payload.aps.sound, 'Silent sync has zero visual alert or audible sound');
  assert(silentSync.payload.etaMins === 7 && silentSync.payload.syncType === 'ETA_UPDATE', 'Silent sync contains structured telemetry');

  // -------------------------------------------------------------------------
  // SECTION 5: Dispatcher Transport & Statistics
  // -------------------------------------------------------------------------
  console.log('\n--- Section 5: Dispatcher Transport & Response Handling ---');

  // Successful dispatch
  const sendResSuccess = await dispatcher.send(validHex64, riderPayload, { topic: 'io.commerceos.rider' });
  assert(sendResSuccess.status === 'DELIVERED', 'Transport returns DELIVERED on valid device token');
  assert(sendResSuccess.httpStatus === 200, 'HTTP status is 200');
  assert(Boolean(sendResSuccess.apnsId), 'Generates apnsId on delivery');

  // Rejected BadDeviceToken
  const sendResBadToken = await dispatcher.send('invalid_hex_token_123', riderPayload);
  assert(sendResBadToken.status === 'FAILED', 'Fails on invalid token format');

  // Rejected Unregistered Token simulation
  const deadToken = 'dead' + validHex64.slice(4);
  const sendResUnregistered = await dispatcher.send(deadToken, riderPayload);
  assert(sendResUnregistered.status === 'REJECTED' && sendResUnregistered.reason === 'Unregistered', 'Handles 410 Unregistered provider response');

  // Verify internal stats
  const stats = dispatcher.getStats();
  assert(stats.totalDispatched >= 3, `Tracks total dispatched count (${stats.totalDispatched})`);
  assert(stats.totalDelivered >= 1, `Tracks delivered count (${stats.totalDelivered})`);
  assert(stats.totalRejected >= 1, `Tracks rejected count (${stats.totalRejected})`);

  // -------------------------------------------------------------------------
  // SECTION 6: Live Production Server & PostgreSQL Outbox Integration (Zero Mocks)
  // -------------------------------------------------------------------------
  console.log('\n--- Section 6: Live Production Server & Outbox Integration ---');

  const pool = new Pool({ connectionString: DATABASE_URL });
  let serverInstance = null;

  try {
    // 1. Start live production server on TEST_PORT
    process.env.COMMERCEOS_PORT = String(TEST_PORT);
    process.env.PORT = String(TEST_PORT);
    process.env.COMMERCEOS_ENV = 'test';
    process.env.DATABASE_URL = DATABASE_URL;

    const prodServer = require('./server/production-server');
    serverInstance = prodServer.server;

    await new Promise((resolve) => {
      serverInstance.listen(TEST_PORT, '127.0.0.1', () => {
        console.log(` 🚀 Live production server running on ${TEST_BASE_URL}`);
        resolve();
      });
    });

    const repos = await prodServer.getAppRepositories();
    assert(Boolean(repos), 'Successfully resolved authoritative production repositories');
    assert(Boolean(prodServer.apnsDispatcherInstance), 'production-server exports active apnsDispatcherInstance');

    // 2. Authenticate Rider and Register Device Token via HTTP API with iOS platform header
    const riderPhone = '9811223344';
    const riderId = 'rdr_9811223344';
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, tier)
       VALUES ($1, $1, $2, 'Vikram iOS Rider', 'DL-01-XY-9999', 'TWO_WHEELER', 'ACTIVE', 'GOLD')
       ON CONFLICT (id) DO UPDATE SET phone = $2, status = 'ACTIVE'`,
      [riderId, `+91${riderPhone}`]
    );

    const otpSend = await apiRequest('POST', '/api/v1/auth/rider/otp/send', { phone: riderPhone }, {
      'X-Client-Platform': 'iOS-Rider'
    });
    const challengeId = otpSend.data?.challengeId || '';
    assert(otpSend.status === 200 && Boolean(challengeId), 'POST /api/v1/auth/rider/otp/send -> 200');

    const otpVerify = await apiRequest('POST', '/api/v1/auth/rider/otp/verify', {
      challengeId,
      phone: riderPhone,
      otp: '123456'
    }, { 'X-Client-Platform': 'iOS-Rider' });
    const riderJwt = otpVerify.data?.accessToken || '';
    assert(otpVerify.status === 200 && Boolean(riderJwt), 'POST /api/v1/auth/rider/otp/verify -> 200 with accessToken');

    const apnsToken = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';

    const regRes = await apiRequest('POST', '/api/v1/delivery/rider/device-token', {
      deviceToken: apnsToken,
      platform: 'iOS'
    }, {
      'Authorization': `Bearer ${riderJwt}`,
      'X-Client-Platform': 'iOS-Rider'
    });

    assert(regRes.status === 200, `POST /api/v1/delivery/rider/device-token -> 200 (got ${regRes.status})`);
    assert(regRes.data?.platform === 'IOS', 'Normalized platform stored as IOS');

    // Verify token record in PostgreSQL
    const tokenDbRes = await pool.query(`SELECT * FROM rider_device_tokens WHERE rider_id = $1`, [riderId]);
    assert(tokenDbRes.rows.length === 1, 'Found registered token in rider_device_tokens table');
    assert(tokenDbRes.rows[0].platform === 'IOS', `rider_device_tokens.platform is strictly 'IOS' (got ${tokenDbRes.rows[0].platform})`);
    assert(tokenDbRes.rows[0].token === apnsToken, 'Stored device token matches input APNs token');

    // 3. Trigger Offer Notification through ProductionNotificationService wired to APNs
    const notifService = repos.notificationService;
    const testLiveOffer = {
      offerId: 'off_ios_apns_live_01',
      offer_id: 'off_ios_apns_live_01',
      id: 'off_ios_apns_live_01',
      riderId: riderId,
      rider_id: riderId,
      orderId: 'ord_ios_apns_live_01',
      deliveryId: 'del_ios_apns_live_01',
      earningsAmount: 55.00,
      totalDistanceKm: 2.1,
      estimatedDurationMins: 11,
      merchantName: 'Metro Pharmacy Indiranagar',
      customerAddress: '12th Main Road, HAL 2nd Stage, Bangalore',
      offerExpiresAt: Date.now() + 60000
    };

    // Seed required dependencies for offers FK constraints
    const storeId = 'store_ios_apns_01';
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES ($1, 'iOS Test Store', 'Connaught Place', 28.6315, 77.2167, TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, email)
       VALUES ('cust_ios_apns', '+919988776655', 'APNs Test Customer', 'apns_cust@commerceos.io')
       ON CONFLICT (id) DO NOTHING`
    );

    const { DeliveryOtpService } = require('./repositories');
    const otpHash = DeliveryOtpService.hashDeliveryOtp('123456', process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value');

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
       VALUES ($1, $1, 'cust_ios_apns', $2, 'READY_FOR_PICKUP', 240.00, '[]'::jsonb, '{}'::jsonb, $3, TRUE, 240.00, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET status = 'READY_FOR_PICKUP', delivery_otp_hash = $3`,
      [testLiveOffer.orderId, storeId, otpHash]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (
         id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
         state, merchant_name, merchant_address, merchant_lat, merchant_lng,
         customer_name, customer_phone, customer_address, customer_lat, customer_lng,
         distance_km, is_cod, cod_amount, created_at, updated_at
       ) VALUES (
         $1, $1, $2, $3, $4, 'Vikram iOS Rider', '+919811223344', 'DL-01-XY-9999',
         'LOOKING_FOR_RIDER', 'iOS Test Store', 'Connaught Place', 28.6315, 77.2167,
         'Ananya Sharma', '+919876501234', 'Connaught Residency', 28.6320, 77.2180,
         1.5, TRUE, 240.00, NOW(), NOW()
       ) ON CONFLICT (id) DO UPDATE SET state = 'LOOKING_FOR_RIDER', rider_id = $4, order_id = $2`,
      [testLiveOffer.deliveryId, testLiveOffer.orderId, storeId, riderId]
    );

    // Insert dummy offer into PostgreSQL offers table to satisfy DB constraints
    await pool.query(
      `INSERT INTO offers (
         id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
         status, offer_created_at, offer_expires_at, earnings_amount,
         delivery_distance_km, total_distance_km, estimated_duration_mins
       ) VALUES (
         $1, $1, 'evt_apns_01', 'notif_apns_01', $2, $3, $4,
         'OFFERED', $6, $5, 55.00, 1.8, 2.1, 11
       ) ON CONFLICT (id) DO UPDATE SET status = 'OFFERED', offer_expires_at = $5, rider_id = $4, delivery_id = $2, order_id = $3`,
      [testLiveOffer.offerId, testLiveOffer.deliveryId, testLiveOffer.orderId, riderId, testLiveOffer.offerExpiresAt, Date.now()]
    );

    const dispatchResult = await notifService.dispatchOfferNotification(testLiveOffer);
    assert(dispatchResult.ok === true, 'Notification service dispatchOfferNotification succeeded');
    assert(dispatchResult.apnsOk === true, 'Successfully dispatched through APNs delivery channel');
    assert(dispatchResult.deliveryMode === 'DELIVERED_PRIMARY_APNS', 'Primary delivery mode recorded as DELIVERED_PRIMARY_APNS');

    // Verify notifications table record
    const notifDbRes = await pool.query(
      `SELECT * FROM rider_notifications WHERE rider_id = $1 ORDER BY created_at DESC LIMIT 1`,
      [riderId]
    );
    assert(notifDbRes.rows.length === 1, 'Notification record written to rider_notifications table');
    assert(notifDbRes.rows[0].status === 'DELIVERED_APNS', `rider_notifications status is DELIVERED_APNS (got ${notifDbRes.rows[0].status})`);

    // Verify offers fcm_delivery_status in PostgreSQL
    const offerDbRes = await pool.query(`SELECT fcm_delivery_status FROM offers WHERE id = $1`, [testLiveOffer.offerId]);
    assert(offerDbRes.rows[0].fcm_delivery_status === 'APNS_ACCEPTED', `offers.fcm_delivery_status updated to APNS_ACCEPTED (got ${offerDbRes.rows[0].fcm_delivery_status})`);

    // Clean up test records
    await pool.query(`DELETE FROM rider_notifications WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM offers WHERE id = $1`, [testLiveOffer.offerId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE delivery_id = $1`, [testLiveOffer.deliveryId]);
    await pool.query(`DELETE FROM orders WHERE order_id = $1`, [testLiveOffer.orderId]);
    await pool.query(`DELETE FROM rider_device_tokens WHERE rider_id = $1`, [riderId]);

  } finally {
    if (serverInstance && serverInstance.listening) {
      await new Promise(resolve => serverInstance.close(resolve));
    }
    await pool.end();
  }

  // -------------------------------------------------------------------------
  // FINAL REPORT
  // -------------------------------------------------------------------------
  console.log('\n======================================================================');
  console.log(`TOTAL TESTS RUN : ${totalTests}`);
  console.log(`PASSED          : ${passedTests}`);
  console.log(`FAILED          : ${failedTests}`);
  console.log(`SUCCESS RATE    : ${Math.round((passedTests / totalTests) * 100)}%`);
  console.log('======================================================================\n');

  if (failedTests > 0) {
    process.exit(1);
  } else {
    process.exit(0);
  }
}

runAllTests().catch((err) => {
  console.error('FATAL_TEST_ERROR:', err);
  process.exit(1);
});
