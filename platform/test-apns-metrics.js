'use strict';

const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');

const { PrometheusMetrics } = require('./services/prometheus-metrics');
const { ApnsDispatcher, ApnsPayloadBuilder } = require('./services/apns-dispatcher');

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@127.0.0.1:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8199';

const TEST_PORT = 8199;
const TEST_BASE_URL = `http://127.0.0.1:${TEST_PORT}`;
const DATABASE_URL = process.env.DATABASE_URL;

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
        resolve({ status: res.statusCode, headers: res.headers, data: parsed, text: data });
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
  console.log('  COMMERCE OS — PROMETHEUS APNs METRICS & OBSERVABILITY TEST SUITE     ');
  console.log('======================================================================\n');

  // -------------------------------------------------------------------------
  // SECTION 1: Unit Tests for PrometheusMetrics Engine
  // -------------------------------------------------------------------------
  console.log('--- Section 1: PrometheusMetrics Class Unit Verification ---');

  const unitMetrics = new PrometheusMetrics();

  // Initial render format check
  let initialOutput = await unitMetrics.renderMetrics();
  assert(initialOutput.includes('# TYPE apns_dispatches_total counter'), 'Renders # TYPE apns_dispatches_total counter');
  assert(initialOutput.includes('# TYPE apns_registered_tokens_total gauge'), 'Renders # TYPE apns_registered_tokens_total gauge');
  assert(initialOutput.includes('# TYPE apns_delivery_latency_ms histogram'), 'Renders # TYPE apns_delivery_latency_ms histogram');
  assert(initialOutput.includes('apns_registered_tokens_total{platform="iOS"} 0'), 'Initial iOS token gauge is 0');
  assert(initialOutput.includes('apns_registered_tokens_total{platform="iOS-Rider"} 0'), 'Initial iOS-Rider token gauge is 0');

  // Record dispatches
  unitMetrics.recordApnsDispatch({
    platform: 'iOS',
    status: 'DELIVERED',
    push_type: 'alert',
    durationMs: 42.5
  });

  unitMetrics.recordApnsDispatch({
    platform: 'iOS-Rider',
    status: 'DELIVERED',
    push_type: 'alert',
    durationMs: 18.2
  });

  unitMetrics.recordApnsDispatch({
    platform: 'iOS',
    status: 'REJECTED',
    push_type: 'alert',
    durationMs: 9.1
  });

  unitMetrics.recordApnsDispatch({
    platform: 'iOS',
    status: 'DELIVERED',
    push_type: 'background',
    durationMs: 35.0
  });

  let dispatchOutput = await unitMetrics.renderMetrics();
  assert(dispatchOutput.includes('apns_dispatches_total{platform="iOS",status="DELIVERED",push_type="alert"} 1'), 'Tracks iOS alert DELIVERED counter');
  assert(dispatchOutput.includes('apns_dispatches_total{platform="iOS-Rider",status="DELIVERED",push_type="alert"} 1'), 'Tracks iOS-Rider alert DELIVERED counter');
  assert(dispatchOutput.includes('apns_dispatches_total{platform="iOS",status="REJECTED",push_type="alert"} 1'), 'Tracks iOS alert REJECTED counter');
  assert(dispatchOutput.includes('apns_dispatches_total{platform="iOS",status="DELIVERED",push_type="background"} 1'), 'Tracks iOS background DELIVERED counter');

  // Histogram checks
  assert(dispatchOutput.includes('apns_delivery_latency_ms_count{platform="iOS",push_type="alert"} 2'), 'Histogram counts total observations (iOS alert count = 2)');
  assert(dispatchOutput.includes('apns_delivery_latency_ms_sum{platform="iOS",push_type="alert"} 51.6'), 'Histogram sums observed latencies accurately (42.5 + 9.1 = 51.6)');
  assert(dispatchOutput.includes('apns_delivery_latency_ms_bucket{platform="iOS",push_type="alert",le="10"} 1'), 'Histogram bucket le=10 contains 1 event (9.1ms)');
  assert(dispatchOutput.includes('apns_delivery_latency_ms_bucket{platform="iOS",push_type="alert",le="50"} 2'), 'Histogram bucket le=50 contains 2 cumulative events');
  assert(dispatchOutput.includes('apns_delivery_latency_ms_bucket{platform="iOS",push_type="alert",le="+Inf"} 2'), 'Histogram bucket le=+Inf equals total observation count');

  // Gauge checks
  unitMetrics.setRegisteredTokens('iOS', 12);
  unitMetrics.recordTokenRegistered('iOS-Rider');
  unitMetrics.recordTokenRegistered('iOS-Rider');

  let gaugeOutput = await unitMetrics.renderMetrics();
  assert(gaugeOutput.includes('apns_registered_tokens_total{platform="iOS"} 12'), 'Directly sets registered tokens gauge for iOS');
  assert(gaugeOutput.includes('apns_registered_tokens_total{platform="iOS-Rider"} 2'), 'Increments registered tokens gauge for iOS-Rider');

  unitMetrics.recordTokenUnregistered('iOS-Rider');
  let decOutput = await unitMetrics.renderMetrics();
  assert(decOutput.includes('apns_registered_tokens_total{platform="iOS-Rider"} 1'), 'Decrements registered tokens gauge for iOS-Rider');

  // -------------------------------------------------------------------------
  // SECTION 2: ApnsDispatcher with Metrics Instrumentation
  // -------------------------------------------------------------------------
  console.log('\n--- Section 2: ApnsDispatcher Metrics Integration ---');

  const dispatcherMetrics = new PrometheusMetrics();
  const testDispatcher = new ApnsDispatcher({
    riderTopic: 'io.commerceos.rider',
    metrics: dispatcherMetrics,
    transport: async (token, payloadJson, headers) => {
      // Simulate artificial transport delay
      await new Promise(r => setTimeout(r, 15));
      if (token.startsWith('dead')) {
        return { status: 'REJECTED', httpStatus: 410, reason: 'Unregistered' };
      }
      return { status: 'DELIVERED', httpStatus: 200, apnsId: crypto.randomUUID() };
    }
  });

  const validToken = '1122334455667788990011223344556677889900112233445566778899001122';
  const testPayload = testDispatcher.createRiderOfferPayload({
    offerId: 'off_metrics_01',
    earningsAmount: 50,
    totalDistanceKm: 2.0
  });

  await testDispatcher.send(validToken, testPayload, { topic: 'io.commerceos.rider' });
  await testDispatcher.send('dead' + validToken.slice(4), testPayload, { topic: 'io.commerceos.rider' });

  const dispMetricsOutput = await dispatcherMetrics.renderMetrics();
  assert(dispMetricsOutput.includes('apns_dispatches_total{platform="iOS-Rider",status="DELIVERED",push_type="alert"} 1'), 'ApnsDispatcher records DELIVERED dispatch in metrics');
  assert(dispMetricsOutput.includes('apns_dispatches_total{platform="iOS-Rider",status="REJECTED",push_type="alert"} 1'), 'ApnsDispatcher records REJECTED dispatch in metrics');
  assert(dispMetricsOutput.includes('apns_delivery_latency_ms_count{platform="iOS-Rider",push_type="alert"} 2'), 'ApnsDispatcher observes latency histogram for all dispatches');

  // -------------------------------------------------------------------------
  // SECTION 3: Live Production Server /metrics Scrape & PostgreSQL Verification
  // -------------------------------------------------------------------------
  console.log('\n--- Section 3: Live Server /metrics Scrape (Zero Mocks) ---');

  const pool = new Pool({ connectionString: DATABASE_URL });
  let serverInstance = null;

  try {
    const prodServer = require('./server/production-server');
    serverInstance = prodServer.server;

    await new Promise((resolve) => {
      serverInstance.listen(TEST_PORT, '127.0.0.1', () => {
        console.log(` 🚀 Live production server running on ${TEST_BASE_URL}`);
        resolve();
      });
    });

    const repos = await prodServer.getAppRepositories();
    assert(Boolean(repos), 'Resolved production repositories');
    assert(Boolean(prodServer.prometheusMetricsInstance), 'Server exports active prometheusMetricsInstance');

    // 1. HTTP GET /metrics scrape
    const scrapeRes1 = await apiRequest('GET', '/metrics');
    assert(scrapeRes1.status === 200, `GET /metrics -> 200 (got ${scrapeRes1.status})`);
    assert(scrapeRes1.headers['content-type'].includes('text/plain'), 'Content-Type is text/plain; version=0.0.4');
    assert(scrapeRes1.text.includes('# HELP apns_dispatches_total'), 'Contains apns_dispatches_total help metadata');
    assert(scrapeRes1.text.includes('# HELP apns_registered_tokens_total'), 'Contains apns_registered_tokens_total help metadata');
    assert(scrapeRes1.text.includes('# HELP apns_delivery_latency_ms'), 'Contains apns_delivery_latency_ms help metadata');

    // 2. Register Rider Device Token with iOS platform header
    const riderPhone = '9811223355';
    const riderId = 'rdr_metrics_test_01';
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, tier)
       VALUES ($1, $1, $2, 'Observability iOS Rider', 'DL-01-METRICS', 'TWO_WHEELER', 'ACTIVE', 'GOLD')
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
    assert(otpVerify.status === 200 && Boolean(riderJwt), 'POST /api/v1/auth/rider/otp/verify -> 200');

    const apnsToken = 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2';

    const regRes = await apiRequest('POST', '/api/v1/delivery/rider/device-token', {
      deviceToken: apnsToken,
      platform: 'iOS'
    }, {
      'Authorization': `Bearer ${riderJwt}`,
      'X-Client-Platform': 'iOS-Rider'
    });
    assert(regRes.status === 200, 'Device token registered via API');

    // 3. Scrape /metrics to verify token gauge increased
    const scrapeRes2 = await apiRequest('GET', '/metrics');
    const hasActiveTokenGauge = /apns_registered_tokens_total\{platform="iOS-Rider"\}\s+([1-9]\d*)/.test(scrapeRes2.text);
    assert(hasActiveTokenGauge, 'Scraped /metrics reflects registered iOS-Rider token count >= 1');

    // 4. Trigger dispatch and verify apns_dispatches_total counter updates
    const storeId = 'store_metrics_01';
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES ($1, 'Observability Store', 'CP Delhi', 28.6315, 77.2167, TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, email)
       VALUES ('cust_metrics_01', '+919988112233', 'Metrics Customer', 'metrics@commerceos.io')
       ON CONFLICT (id) DO NOTHING`
    );

    const orderId = 'ord_metrics_01';
    const deliveryId = 'del_metrics_01';
    const offerId = 'off_metrics_live_01';

    const { DeliveryOtpService } = require('./repositories');
    const otpHash = DeliveryOtpService.hashDeliveryOtp('123456', process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value');

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
       VALUES ($1, $1, 'cust_metrics_01', $2, 'READY_FOR_PICKUP', 200.00, '[]'::jsonb, '{}'::jsonb, $3, TRUE, 200.00, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET status = 'READY_FOR_PICKUP', delivery_otp_hash = $3`,
      [orderId, storeId, otpHash]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (
         id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
         state, merchant_name, merchant_address, merchant_lat, merchant_lng,
         customer_name, customer_phone, customer_address, customer_lat, customer_lng,
         distance_km, is_cod, cod_amount, created_at, updated_at
       ) VALUES (
         $1, $1, $2, $3, $4, 'Observability iOS Rider', '+919811223355', 'DL-01-METRICS',
         'LOOKING_FOR_RIDER', 'Observability Store', 'CP Delhi', 28.6315, 77.2167,
         'Metrics Customer', '+919988112233', 'Connaught Place', 28.6320, 77.2180,
         1.5, TRUE, 200.00, NOW(), NOW()
       ) ON CONFLICT (id) DO UPDATE SET state = 'LOOKING_FOR_RIDER', rider_id = $4, order_id = $2`,
      [deliveryId, orderId, storeId, riderId]
    );

    const testLiveOffer = {
      offerId: offerId,
      deliveryId: deliveryId,
      orderId: orderId,
      riderId: riderId,
      earningsAmount: 60.00,
      totalDistanceKm: 2.5,
      estimatedDurationMins: 12,
      merchantName: 'Observability Store',
      customerAddress: 'Connaught Place, Delhi',
      offerExpiresAt: Date.now() + 60000
    };

    await pool.query(
      `INSERT INTO offers (
         id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
         status, offer_created_at, offer_expires_at, earnings_amount,
         delivery_distance_km, total_distance_km, estimated_duration_mins
       ) VALUES (
         $1, $1, 'evt_m_01', 'notif_m_01', $2, $3, $4,
         'OFFERED', $6, $5, 60.00, 2.0, 2.5, 12
       ) ON CONFLICT (id) DO UPDATE SET status = 'OFFERED', offer_expires_at = $5, rider_id = $4, delivery_id = $2, order_id = $3`,
      [offerId, deliveryId, orderId, riderId, testLiveOffer.offerExpiresAt, Date.now()]
    );

    const notifService = repos.notificationService;
    const dispatchRes = await notifService.dispatchOfferNotification(testLiveOffer);
    assert(dispatchRes.ok === true && dispatchRes.apnsOk === true, 'Dispatched offer notification to registered iOS device');

    // Scrape /metrics after dispatch
    const scrapeRes3 = await apiRequest('GET', '/metrics');
    assert(scrapeRes3.text.includes('apns_dispatches_total{platform="iOS-Rider",status="DELIVERED",push_type="alert"}'), 'Scraped /metrics contains incremented apns_dispatches_total counter for iOS-Rider');
    assert(scrapeRes3.text.includes('apns_delivery_latency_ms_count{platform="iOS-Rider",push_type="alert"}'), 'Scraped /metrics contains apns_delivery_latency_ms histogram counts');

    // 5. Test logout decrement
    const logoutRes = await apiRequest('POST', '/api/v1/delivery/rider/device-token/logout', {}, {
      'Authorization': `Bearer ${riderJwt}`
    });
    assert(logoutRes.status === 200, 'POST /api/v1/delivery/rider/device-token/logout -> 200');

    const scrapeRes4 = await apiRequest('GET', '/metrics');
    assert(scrapeRes4.status === 200, 'Scrape /metrics after logout successful');

    // Clean up
    await pool.query(`DELETE FROM rider_notifications WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM offers WHERE id = $1`, [offerId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM orders WHERE order_id = $1`, [orderId]);
    await pool.query(`DELETE FROM rider_device_tokens WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE id = $1`, [riderId]);

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
  console.error('FATAL_METRICS_TEST_ERROR:', err);
  process.exit(1);
});
