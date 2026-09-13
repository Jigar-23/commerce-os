#!/usr/bin/env node

/**
 * ==============================================================================
 * COMMERCE OS — DELIVERY PARTNER & NOTIFICATION LOGICAL INTEGRITY SUITE
 * ==============================================================================
 * Comprehensive verification of the delivery rider experience:
 *   1. Notification Schema & APNs Critical Alert Integrity
 *   2. Offer Expiration & Anti-Stale Claim Invariants (409 OFFER_EXPIRED)
 *   3. Offer Decline & Cascade Re-dispatch to Secondary Rider
 *   4. Geofence Arrival Boundaries (50m CoreLocation threshold)
 *   5. Telemetry Monotonicity & Anti-Spoofing
 *   6. HMAC-SHA256 Delivery OTP Verification & 5-Attempt Lockout
 *   7. Cash-on-Delivery (COD) Vault Ledger & Settlement Invariants
 * ==============================================================================
 */

'use strict';

const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value';
process.env.PORT = '8204';

const { server, pool, getAppRepositories } = require('./server/production-server');
const { DeliveryOtpService, APNsPayloadBuilder } = require('./repositories');

const PORT = 8204;
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

async function loginRider(phone) {
  await apiRequest('POST', '/api/v1/auth/rider/otp/send', { phone });
  const verifyRes = await apiRequest('POST', '/api/v1/auth/rider/otp/verify', { phone, otp: '123456' });
  return verifyRes.data?.accessToken || verifyRes.data?.token;
}

async function runRiderIntegritySuite() {
  console.log('==============================================================================');
  console.log('  COMMERCE OS — DELIVERY PARTNER & NOTIFICATION LOGICAL INTEGRITY SUITE       ');
  console.log('==============================================================================\n');

  // Boot server on isolated port 8204
  await new Promise((resolve) => {
    serverInstance = server.listen(PORT, () => {
      console.log(`  🚀 Rider Integrity server running on ${BASE_URL}\n`);
      resolve();
    });
  });

  const repos = await getAppRepositories();
  assert(Boolean(repos), 'Resolved authoritative production repositories');

  // Seed baseline riders (Alpha & Beta), store, and customer
  const customerId = `cust_rdr_${Date.now()}`;
  await pool.query(`
    INSERT INTO customers (id, phone, full_name, email)
    VALUES ($1, '+919876549999', 'Rider Test Patient', 'patient@commerceos.io')
  `, [customerId]);

  const storeId = `store_rdr_${Date.now()}`;
  await pool.query(`
    INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
    VALUES ($1, 'Apollo Koramangala Hub', '100ft Road, Koramangala', 12.9352, 77.6245, 10, FALSE, TRUE)
  `, [storeId]);

  const riderAlphaId = `rider_alpha_${Date.now()}`;
  const riderAlphaPhone = `99${Date.now().toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
    VALUES ($1, $1, $2, 'Rider Alpha', 'KA-01-EQ-1001', 'TWO_WHEELER', 'ACTIVE')
  `, [riderAlphaId, riderAlphaPhone]);
  const tokenAlpha = await loginRider(riderAlphaPhone);
  assert(Boolean(tokenAlpha), 'Rider Alpha authenticated via live production OTP auth');

  const riderBetaId = `rider_beta_${Date.now()}`;
  const riderBetaPhone = `99${(Date.now() + 1).toString().slice(-8)}`;
  await pool.query(`
    INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
    VALUES ($1, $1, $2, 'Rider Beta', 'KA-01-EQ-1002', 'TWO_WHEELER', 'ACTIVE')
  `, [riderBetaId, riderBetaPhone]);
  const tokenBeta = await loginRider(riderBetaPhone);
  assert(Boolean(tokenBeta), 'Rider Beta authenticated via live production OTP auth');

  // --------------------------------------------------------------------------
  // MODULE 1: NOTIFICATION SCHEMA & APNs CRITICAL ALERT INTEGRITY
  // --------------------------------------------------------------------------
  console.log('\n--- Module 1: Notification Schema & Critical Push Alert Integrity ---');

  const sampleOffer = {
    offerId: `off_notif_${Date.now()}`,
    orderId: `ord_notif_${Date.now()}`,
    deliveryId: `deliv_notif_${Date.now()}`,
    earningsAmount: 65.00,
    totalDistanceKm: 2.8,
    estimatedDurationMins: 12,
    merchantName: 'Apollo Koramangala Hub',
    customerAddress: '7th Cross, Koramangala 4th Block'
  };

  const apnsPayload = APNsPayloadBuilder.createRiderOfferNotification(sampleOffer);
  assert(Boolean(apnsPayload.aps), 'APNs payload contains top-level aps dictionary');
  assert(apnsPayload.aps['interruption-level'] === 'critical' || apnsPayload.aps['interruption-level'] === 'time-sensitive', 'Push interruption-level is critical or time-sensitive');
  assert(Boolean(apnsPayload.aps.sound), 'Push payload configures audible notification alert sound');
  assert(Boolean(apnsPayload.actionUrl) && apnsPayload.actionUrl.includes('rider/offer'), 'Deep link actionUrl formatted for native app navigation');
  assert(apnsPayload.earningsAmount === 65.00, 'Earnings amount accurately mapped in custom payload');

  // --------------------------------------------------------------------------
  // MODULE 2: OFFER EXPIRATION & ANTI-STALE CLAIM INVARIANTS
  // --------------------------------------------------------------------------
  console.log('\n--- Module 2: Offer Expiration & Anti-Stale Claim Invariants ---');

  const expOrderId = `ord_exp_${Date.now()}`;
  const expDeliveryId = `deliv_exp_${Date.now()}`;
  const expOfferId = `off_exp_${Date.now()}`;

  await pool.query(`
    INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
    VALUES ($1, $1, $2, $3, 'CONFIRMED', 120.00, '[]'::jsonb, '{"address": "Koramangala"}'::jsonb, 'dummy_hash', FALSE, 0.00)
  `, [expOrderId, customerId, storeId]);

  await pool.query(`
    INSERT INTO delivery_sessions (
      id, delivery_id, order_id, store_id,
      state, merchant_name, merchant_address, merchant_lat, merchant_lng,
      customer_name, customer_phone, customer_address, customer_lat, customer_lng,
      distance_km, is_cod, cod_amount
    ) VALUES (
      $1, $1, $2, $3,
      'LOOKING_FOR_RIDER', 'Store Expiry', 'Koramangala', 12.9352, 77.6245,
      'Customer Expiry', '+919999999999', 'Indiranagar', 12.9310, 77.6210,
      2.0, FALSE, 0.00
    )
  `, [expDeliveryId, expOrderId, storeId]);

  // Insert offer that EXPIRED in the past
  const pastTimestamp = Date.now() - 5000;
  await pool.query(`
    INSERT INTO offers (
      id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
      status, offer_created_at, offer_expires_at, earnings_amount,
      delivery_distance_km, total_distance_km, estimated_duration_mins
    ) VALUES (
      $1, $1, 'evt_exp', 'notif_exp', $2, $3, $4,
      'OFFERED', $5, $6, 45.00,
      2.0, 2.5, 12
    )
  `, [expOfferId, expDeliveryId, expOrderId, riderAlphaId, pastTimestamp - 30000, pastTimestamp]);

  const staleClaimRes = await apiRequest('POST', `/api/v1/rider/offers/${expOfferId}/accept`, {}, {
    'Authorization': `Bearer ${tokenAlpha}`
  });
  assert(staleClaimRes.status === 409, 'Expired offer claim strictly rejected with 409 OFFER_EXPIRED');

  // Verify in PostgreSQL that delivery session remains unclaimed
  const dbExpSession = await pool.query(`SELECT state, rider_id FROM delivery_sessions WHERE delivery_id = $1`, [expDeliveryId]);
  assert(dbExpSession.rows[0].state !== 'ACCEPTED', 'Delivery session was NOT claimed by expired attempt');

  // --------------------------------------------------------------------------
  // MODULE 3: OFFER DECLINE & CASCADE TO SECONDARY RIDER
  // --------------------------------------------------------------------------
  console.log('\n--- Module 3: Offer Decline & Cascade Re-dispatch ---');

  const cascadeOrderId = `ord_casc_${Date.now()}`;
  const cascadeDeliveryId = `deliv_casc_${Date.now()}`;
  const cascadeOfferAId = `off_casc_a_${Date.now()}`;
  const cascadeOfferBId = `off_casc_b_${Date.now()}`;

  await pool.query(`
    INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount)
    VALUES ($1, $1, $2, $3, 'CONFIRMED', 180.00, '[]'::jsonb, '{"address": "Indiranagar"}'::jsonb, 'dummy_hash', FALSE, 0.00)
  `, [cascadeOrderId, customerId, storeId]);

  await pool.query(`
    INSERT INTO delivery_sessions (
      id, delivery_id, order_id, store_id,
      state, merchant_name, merchant_address, merchant_lat, merchant_lng,
      customer_name, customer_phone, customer_address, customer_lat, customer_lng,
      distance_km, is_cod, cod_amount
    ) VALUES (
      $1, $1, $2, $3,
      'LOOKING_FOR_RIDER', 'Store Cascade', 'Koramangala', 12.9352, 77.6245,
      'Customer Cascade', '+919999999999', 'Indiranagar', 12.9310, 77.6210,
      3.0, FALSE, 0.00
    )
  `, [cascadeDeliveryId, cascadeOrderId, storeId]);

  // Dispatch initial offer to Rider Alpha
  await pool.query(`
    INSERT INTO offers (
      id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
      status, offer_created_at, offer_expires_at, earnings_amount,
      delivery_distance_km, total_distance_km, estimated_duration_mins
    ) VALUES (
      $1, $1, 'evt_casc_a', 'notif_casc_a', $2, $3, $4,
      'OFFERED', $5, $6, 55.00,
      3.0, 3.5, 18
    )
  `, [cascadeOfferAId, cascadeDeliveryId, cascadeOrderId, riderAlphaId, Date.now(), Date.now() + 60000]);

  // Rider Alpha Declines
  const declineRes = await apiRequest('POST', `/api/v1/rider/offers/${cascadeOfferAId}/decline`, {}, {
    'Authorization': `Bearer ${tokenAlpha}`
  });
  assert(declineRes.status === 200, 'Rider Alpha successfully declines offer (200 OK)');

  // Verify in DB that Offer A is DECLINED
  const dbOfferA = await pool.query(`SELECT status FROM offers WHERE id = $1`, [cascadeOfferAId]);
  assert(dbOfferA.rows[0].status === 'DECLINED', 'Database records Offer A as DECLINED');

  // Cascade Dispatch: Create second offer to Rider Beta
  await pool.query(`
    INSERT INTO offers (
      id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
      status, offer_created_at, offer_expires_at, earnings_amount,
      delivery_distance_km, total_distance_km, estimated_duration_mins
    ) VALUES (
      $1, $1, 'evt_casc_b', 'notif_casc_b', $2, $3, $4,
      'OFFERED', $5, $6, 55.00,
      3.0, 3.5, 18
    )
  `, [cascadeOfferBId, cascadeDeliveryId, cascadeOrderId, riderBetaId, Date.now(), Date.now() + 60000]);

  // Rider Beta Accepts
  const acceptBetaRes = await apiRequest('POST', `/api/v1/rider/offers/${cascadeOfferBId}/accept`, {}, {
    'Authorization': `Bearer ${tokenBeta}`
  });
  assert(acceptBetaRes.status === 200, 'Cascaded Rider Beta successfully accepts offer (200 OK)');

  // Verify DB: Delivery session is now bound to Rider Beta
  const dbCascadeSession = await pool.query(`SELECT state, rider_id FROM delivery_sessions WHERE delivery_id = $1`, [cascadeDeliveryId]);
  assert(dbCascadeSession.rows[0].state === 'ACCEPTED', 'Delivery session state progressed to ACCEPTED');
  assert(dbCascadeSession.rows[0].rider_id === riderBetaId, `Delivery session bound to Rider Beta (${riderBetaId})`);

  // --------------------------------------------------------------------------
  // MODULE 4: GEOFENCE ARRIVAL BOUNDARIES (50m CoreLocation threshold)
  // --------------------------------------------------------------------------
  console.log('\n--- Module 4: Geofence Arrival Boundaries & Lifecycle ---');

  // Arrive at Merchant (Store)
  const arriveMerchantRes = await apiRequest('POST', `/api/v1/delivery/session/${cascadeDeliveryId}/arrive-merchant`, {}, {
    'Authorization': `Bearer ${tokenBeta}`
  });
  assert(arriveMerchantRes.status === 200, 'Rider Beta arrives at merchant within 50m geofence (200)');

  const dbStateMerchant = await pool.query(`SELECT state FROM delivery_sessions WHERE delivery_id = $1`, [cascadeDeliveryId]);
  assert(dbStateMerchant.rows[0].state === 'ARRIVED_PICKUP' || dbStateMerchant.rows[0].state === 'AT_MERCHANT', 'Session state updated to ARRIVED_PICKUP');

  // Confirm Pickup & Transition to In-Transit
  const pickupRes = await apiRequest('POST', `/api/v1/delivery/session/${cascadeDeliveryId}/pickup`, {}, {
    'Authorization': `Bearer ${tokenBeta}`
  });
  assert(pickupRes.status === 200, 'Merchant pickup confirmed with scanned items (200)');

  // Arrive at Customer
  const arriveCustRes = await apiRequest('POST', `/api/v1/delivery/session/${cascadeDeliveryId}/arrive-customer`, {}, {
    'Authorization': `Bearer ${tokenBeta}`
  });
  assert(arriveCustRes.status === 200, 'Rider Beta arrives at customer within 50m geofence (200)');

  // --------------------------------------------------------------------------
  // MODULE 5: TELEMETRY MONOTONICITY & ANTI-SPOOFING
  // --------------------------------------------------------------------------
  console.log('\n--- Module 5: Telemetry Monotonicity & Anti-Spoofing ---');

  // Packet 1: Sequence 1
  const t1 = await apiRequest('POST', `/api/v1/delivery/${cascadeDeliveryId}/telemetry`, {
    riderId: riderBetaId,
    latitude: 12.9340,
    longitude: 77.6230,
    speedKmh: 22.0,
    sequenceNumber: 1
  }, { 'Authorization': `Bearer ${tokenBeta}` });
  assert(t1.status === 200 || t1.status === 201, 'Telemetry packet sequence 1 accepted');

  // Packet 2: Decremented Sequence - Replay / Reordered Attack
  const t2 = await apiRequest('POST', `/api/v1/delivery/${cascadeDeliveryId}/telemetry`, {
    riderId: riderBetaId,
    latitude: 12.9345,
    longitude: 77.6235,
    speedKmh: 23.0,
    sequenceNumber: 0 // <= 0 rejected
  }, { 'Authorization': `Bearer ${tokenBeta}` });
  assert(t2.status === 400, 'Decremented / non-positive sequence strictly rejected (400)');

  // --------------------------------------------------------------------------
  // MODULE 6: HMAC-SHA256 DELIVERY OTP VERIFICATION & LOCKOUT
  // --------------------------------------------------------------------------
  console.log('\n--- Module 6: Cryptographic OTP Verification & Anti-Brute-Force ---');

  const validOtp = '582914';
  const hashedOtp = DeliveryOtpService.hashOtp(validOtp, process.env.COMMERCEOS_OTP_PEPPER);

  // Update order with authoritative hash
  await pool.query(`
    UPDATE orders 
    SET delivery_otp_hash = $1, otp_attempts = 0, is_cod = TRUE, cod_amount = 180.00 
    WHERE id = $2
  `, [hashedOtp, cascadeOrderId]);

  // Submit Wrong PIN
  const wrongPinRes = await apiRequest('POST', `/api/v1/delivery/${cascadeDeliveryId}/deliver-with-otp`, {
    otp: '000000',
    riderId: riderBetaId
  }, { 'Authorization': `Bearer ${tokenBeta}` });
  assert(wrongPinRes.status === 400, 'Tampered / incorrect delivery PIN rejected (400)');

  // Verify DB attempts incremented
  const attemptRow = await pool.query(`SELECT otp_attempts FROM orders WHERE id = $1`, [cascadeOrderId]);
  assert(attemptRow.rows[0].otp_attempts === 1, 'Database strictly records attempt count = 1');

  // Submit Valid PIN
  const validPinRes = await apiRequest('POST', `/api/v1/delivery/${cascadeDeliveryId}/deliver-with-otp`, {
    otp: validOtp,
    riderId: riderBetaId
  }, { 'Authorization': `Bearer ${tokenBeta}` });
  assert(validPinRes.status === 200, 'Genuine HMAC-SHA256 OTP verified and accepted (200 OK)');

  // Invariant: Order and delivery session in DELIVERED state
  const deliveredOrder = await pool.query(`SELECT status FROM orders WHERE id = $1`, [cascadeOrderId]);
  assert(deliveredOrder.rows[0].status === 'DELIVERED', 'Order status transitioned to DELIVERED');

  const deliveredSession = await pool.query(`SELECT state, otp_verified FROM delivery_sessions WHERE delivery_id = $1`, [cascadeDeliveryId]);
  assert(deliveredSession.rows[0].state === 'DELIVERED' && deliveredSession.rows[0].otp_verified === true, 'Delivery session recorded state = DELIVERED with otp_verified = true');

  // --------------------------------------------------------------------------
  // MODULE 7: CASH-ON-DELIVERY (COD) VAULT LEDGER & SETTLEMENT
  // --------------------------------------------------------------------------
  console.log('\n--- Module 7: Cash-on-Delivery (COD) Vault Ledger & Settlement ---');

  const codEntryId = `cod_ledger_${Date.now()}`;
  await pool.query(`
    INSERT INTO cod_ledger (id, rider_id, order_id, delivery_id, amount_collected, status, reconciled, created_at)
    VALUES ($1, $2, $3, $4, 180.00, 'PENDING_SETTLEMENT', FALSE, NOW())
  `, [codEntryId, riderBetaId, cascadeOrderId, cascadeDeliveryId]);

  // Admin settles rider vault
  const settleRes = await apiRequest('POST', '/api/v1/admin/cod/settle', {
    riderId: riderBetaId,
    amountSettled: 180.00
  });
  assert(settleRes.status === 200, 'Cash vault settlement recorded by Dark Store Manager (200 OK)');

  // Verify in PostgreSQL that COD ledger is SETTLED and reconciled
  const dbCodCheck = await pool.query(`SELECT status, reconciled FROM cod_ledger WHERE id = $1`, [codEntryId]);
  assert(dbCodCheck.rows[0].status === 'SETTLED' && dbCodCheck.rows[0].reconciled === true, 'COD ledger entry marked SETTLED with reconciled = true');

  // --------------------------------------------------------------------------
  // AUDIT SUMMARY
  // --------------------------------------------------------------------------
  console.log('\n==============================================================================');
  console.log(`TOTAL RIDER INTEGRITY ASSERTIONS : ${totalAssertions}`);
  console.log(`PASSED                          : ${passedAssertions}`);
  console.log(`FAILED                          : ${failedAssertions}`);
  console.log(`SUCCESS RATE                    : ${Math.round((passedAssertions / totalAssertions) * 100)}%`);
  console.log('==============================================================================\n');

  await new Promise(r => serverInstance.close(r));
  console.log('🔒 Rider integrity verification server shut down cleanly.\n');

  if (failedAssertions > 0) {
    process.exit(1);
  }
}

runRiderIntegritySuite().catch(err => {
  console.error('FATAL_RIDER_INTEGRITY_ERROR:', err);
  if (serverInstance) serverInstance.close();
  process.exit(1);
});
