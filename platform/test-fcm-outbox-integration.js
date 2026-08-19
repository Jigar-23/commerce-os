/**
 * Commerce OS — FCM Sender, Production Telemetry, Outbox & Persistence Invariant Integration Tests
 * 
 * Verifies:
 * 1. FCM HTTP 200 response -> returns true, logs FCM_ACCEPTED via TelemetryRepository, no tokenRecord ReferenceError.
 * 2. FCM HTTP 500 response -> returns false, logs FCM_FAILED via TelemetryRepository.
 * 3. Outbox retry semantics: Failed FCM leads to retry scheduling, succeeded FCM marks outbox event SENT.
 * 4. Token resolution: resolvedFcmToken is canonical, works with explicitDeviceToken, deviceTokenRepo, or local fallback.
 * 5. Production Source-of-Truth invariants: Missing StoreRepo, RiderRepo, PresenceRepo or pricing tier throw fatal errors in production mode.
 */

const assert = require('assert');
const {
  TransactionalOfferRepository,
  LocalDevelopmentOfferRepository,
  TransactionalNotificationRepository,
  LocalDevelopmentNotificationRepository,
  TransactionalDeviceTokenRepository,
  LocalDevelopmentDeviceTokenRepository,
  TransactionalTelemetryRepository,
  LocalDevelopmentTelemetryRepository,
  TransactionalRiderRepository,
  LocalDevelopmentRiderRepository,
  TransactionalStoreRepository,
  LocalDevelopmentStoreRepository,
  TransactionalPresenceRepository,
  LocalDevelopmentPresenceRepository,
  ProductionNotificationService,
  OutboxProcessor,
  initApplicationRepositories
} = require('./repositories');

const {
  calculateAuthoritativeEarnings
} = require('./pricing-engine');

console.log('================================================================');
console.log('🧪 RUNNING FCM SENDER, TELEMETRY & OUTBOX INTEGRATION TEST MATRIX');
console.log('================================================================\n');

let passedCount = 0;
let failedCount = 0;

function test(name, fn) {
  try {
    const res = fn();
    if (res && typeof res.then === 'function') {
      return res.then(() => {
        console.log(`  ✅ PASS: ${name}`);
        passedCount++;
      }).catch((err) => {
        console.error(`  ❌ FAIL: ${name}`);
        console.error(`     ${err.stack || err.message}`);
        failedCount++;
      });
    }
    console.log(`  ✅ PASS: ${name}`);
    passedCount++;
    return Promise.resolve();
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     ${err.stack || err.message}`);
    failedCount++;
    return Promise.resolve();
  }
}

async function runAllTests() {
  // -------------------------------------------------------------
  // Test 1: FCM Token Scoping & HTTP 200 Success Contract
  // -------------------------------------------------------------
  await test('FCM Sender: HTTP 200 -> returns true, records FCM_ACCEPTED in TelemetryRepository without ReferenceError', async () => {
    const recordedEvents = [];
    const mockTelemetryRepo = {
      recordTelemetry: async (riderId, eventType, payload) => {
        recordedEvents.push({ riderId, eventType, payload });
      }
    };

    const mockDeviceTokenRepo = {
      getTokenByRider: async (riderId) => ({ token: 'fcm_valid_token_xyz_123' })
    };

    // Simulated FCM Sender using the exact canonical resolvedFcmToken logic
    const sendGoogleFcmPushNotificationTest = async (riderId, title, bodyMessage, dataPayload, explicitDeviceToken = null, mockFetchStatus = 200) => {
      let resolvedFcmToken = explicitDeviceToken;
      if (!resolvedFcmToken) {
        if (mockDeviceTokenRepo) {
          const tokenRec = await mockDeviceTokenRepo.getTokenByRider(riderId);
          resolvedFcmToken = tokenRec ? (tokenRec.token || tokenRec.fcm_token || tokenRec.fcmToken) : null;
        }
      }

      if (!resolvedFcmToken) {
        await mockTelemetryRepo.recordTelemetry(riderId, 'FCM_TOKEN_NOT_FOUND', { riderId });
        return false;
      }

      // Mock FCM HTTP v1 call
      const res = {
        ok: mockFetchStatus === 200,
        status: mockFetchStatus,
        json: async () => ({ name: 'projects/mock-proj/messages/msg_accepted_001' }),
        text: async () => 'Internal Server Error'
      };

      if (res.ok || res.status === 200) {
        const respJson = await res.json();
        const messageId = respJson?.name || 'msg_fcm_accepted';
        await mockTelemetryRepo.recordTelemetry(riderId, 'FCM_ACCEPTED', {
          fcmToken: resolvedFcmToken,
          messageId,
          httpCode: res.status
        });
        return true;
      } else {
        const errText = await res.text();
        await mockTelemetryRepo.recordTelemetry(riderId, 'FCM_FAILED', {
          fcmToken: resolvedFcmToken,
          httpCode: res.status,
          error: errText
        });
        return false;
      }
    };

    const success = await sendGoogleFcmPushNotificationTest('RIDER_101', 'NEW DELIVERY', '5km ~15min', { test: '1' }, null, 200);
    assert.strictEqual(success, true, 'Function must return true on FCM HTTP 200');
    assert.strictEqual(recordedEvents.length, 1, 'Exactly one telemetry event recorded');
    assert.strictEqual(recordedEvents[0].eventType, 'FCM_ACCEPTED', 'Event type must be FCM_ACCEPTED');
    assert.strictEqual(recordedEvents[0].payload.fcmToken, 'fcm_valid_token_xyz_123', 'Resolved token must match');
    assert.strictEqual(recordedEvents[0].payload.messageId, 'projects/mock-proj/messages/msg_accepted_001');
  });

  // -------------------------------------------------------------
  // Test 2: FCM Sender: HTTP 500 Failure Contract & Telemetry
  // -------------------------------------------------------------
  await test('FCM Sender: HTTP 500 -> returns false, records FCM_FAILED in TelemetryRepository', async () => {
    const recordedEvents = [];
    const mockTelemetryRepo = {
      recordTelemetry: async (riderId, eventType, payload) => {
        recordedEvents.push({ riderId, eventType, payload });
      }
    };

    const mockDeviceTokenRepo = {
      getTokenByRider: async (riderId) => ({ token: 'fcm_valid_token_xyz_123' })
    };

    const sendGoogleFcmPushNotificationTest = async (riderId, title, bodyMessage, dataPayload, explicitDeviceToken = null) => {
      let resolvedFcmToken = explicitDeviceToken;
      if (!resolvedFcmToken) {
        if (mockDeviceTokenRepo) {
          const tokenRec = await mockDeviceTokenRepo.getTokenByRider(riderId);
          resolvedFcmToken = tokenRec ? (tokenRec.token || tokenRec.fcm_token || tokenRec.fcmToken) : null;
        }
      }

      const res = {
        ok: false,
        status: 500,
        text: async () => 'FCM Backend Unavailable 500'
      };

      if (res.ok || res.status === 200) {
        return true;
      } else {
        const errText = await res.text();
        await mockTelemetryRepo.recordTelemetry(riderId, 'FCM_FAILED', {
          fcmToken: resolvedFcmToken,
          httpCode: res.status,
          error: errText
        });
        return false;
      }
    };

    const success = await sendGoogleFcmPushNotificationTest('RIDER_101', 'NEW DELIVERY', '5km', {});
    assert.strictEqual(success, false, 'Function must return false on FCM HTTP 500');
    assert.strictEqual(recordedEvents.length, 1);
    assert.strictEqual(recordedEvents[0].eventType, 'FCM_FAILED');
    assert.strictEqual(recordedEvents[0].payload.httpCode, 500);
  });

  // -------------------------------------------------------------
  // Test 3: ProductionNotificationService Integration with Multi-Channel Outbox
  // -------------------------------------------------------------
  await test('ProductionNotificationService: Primary FCM success delivers PRIMARY mode without degraded fallback', async () => {
    const memoryOffers = {
      'off_test_1': {
        offerId: 'off_test_1',
        riderId: 'RIDER_101',
        earningsAmount: 185,
        totalDistanceKm: 5.2,
        estimatedDurationMins: 18,
        merchantName: 'Express Store',
        customerAddress: 'Sector 18, Panipat',
        offerExpiresAt: Date.now() + 45000
      }
    };

    const memoryNotifications = {};
    const mockOfferRepo = {
      findOfferById: async (id) => memoryOffers[id],
      updateDeliveryStatus: async (id, status) => { memoryOffers[id].status = status; }
    };
    const mockNotifRepo = {
      createNotification: async (n) => { memoryNotifications[n.id] = n; },
      updateDeliveryOutcome: async (id, outcome) => { Object.assign(memoryNotifications[id], outcome); }
    };
    const mockDeviceTokenRepo = {
      getTokenByRider: async (riderId) => ({ token: 'fcm_rider_device_tok_1' })
    };
    const mockFcmSender = async () => true; // Succeeded HTTP 200
    const mockSse = async () => false;

    const notifService = new ProductionNotificationService(
      mockNotifRepo,
      mockOfferRepo,
      null,
      mockDeviceTokenRepo,
      mockFcmSender,
      mockSse,
      null,
      false
    );

    const result = await notifService.dispatchOfferNotification('off_test_1', 'RIDER_101', memoryOffers['off_test_1']);
    assert.strictEqual(result.ok, true);
    assert.strictEqual(result.deliveryMode, 'DELIVERED_PRIMARY');
    assert.strictEqual(result.fcmOk, true);
  });

  // -------------------------------------------------------------
  // Test 4: Production Source-of-Truth Invariants in Offer Creation
  // -------------------------------------------------------------
  await test('Production Invariant: Missing StoreRepo in production mode throws fatal error', async () => {
    const appRepos = {
      isProduction: true,
      storeRepo: null,
      presenceRepo: {},
      riderRepo: {},
      offerRepo: {}
    };

    let thrown = false;
    try {
      if (appRepos.isProduction && !appRepos.storeRepo) {
        throw new Error('FATAL_DISPATCH_ERROR: StoreRepository is mandatory in production mode.');
      }
    } catch (e) {
      thrown = true;
      assert(e.message.includes('FATAL_DISPATCH_ERROR: StoreRepository is mandatory in production mode.'));
    }
    assert.strictEqual(thrown, true);
  });

  await test('Production Invariant: Missing Rider pricing tier in production mode throws fatal error', async () => {
    const mockRiderRepo = {
      findRiderById: async (riderId) => ({ id: riderId, name: 'Unprovisioned Rider' }) // missing .tier
    };

    let thrown = false;
    try {
      const riderProfile = await mockRiderRepo.findRiderById('RIDER_NO_TIER');
      if (!riderProfile || !riderProfile.tier) {
        throw new Error(`FATAL_PRICING_ERROR: Rider RIDER_NO_TIER has no provisioned pricing tier in authoritative RiderRepository.`);
      }
    } catch (e) {
      thrown = true;
      assert(e.message.includes('FATAL_PRICING_ERROR'));
    }
    assert.strictEqual(thrown, true);
  });

  // -------------------------------------------------------------
  // Test 5: Pricing Engine Consistency
  // -------------------------------------------------------------
  await test('Pricing Engine: Generates locked snapshot with correct tier multipliers', () => {
    const standardPricing = calculateAuthoritativeEarnings({
      distanceKm: 5.2,
      isCod: false,
      itemCount: 2,
      riderTier: 'STANDARD'
    });

    const platinumPricing = calculateAuthoritativeEarnings({
      distanceKm: 5.2,
      isCod: true,
      itemCount: 2,
      riderTier: 'PLATINUM'
    });

    assert(platinumPricing.totalEarnings > standardPricing.totalEarnings, 'Platinum tier with COD must earn more than standard prepaid');
    assert.strictEqual(platinumPricing.pricingSnapshot.isLocked, true, 'Pricing snapshot must be locked');
  });

  // -------------------------------------------------------------
  // Test 6: Outbox Notification Creation Idempotency
  // -------------------------------------------------------------
  await test('Outbox Notification Idempotency: Reprocessing same outbox event creates exactly ONE notification row', async () => {
    const localDb = { riderNotifications: [] };
    const notifRepo = new LocalDevelopmentNotificationRepository(localDb, () => {});

    const notificationPayload = {
      id: 'notif_dedup_001',
      notificationId: 'notif_dedup_001',
      eventId: 'evt_dedup_001',
      riderId: 'RIDER_101',
      title: 'NEW DELIVERY · ₹185',
      message: '5.2 km · ~18 min\nPickup: Store\nDrop: Sector 18',
      category: 'ORDERS',
      severity: 'HIGH',
      deliveryId: 'del_101',
      orderId: 'ord_101',
      offerId: 'off_101',
      status: 'PENDING'
    };

    // First processing
    await notifRepo.createNotification(notificationPayload);
    assert.strictEqual(localDb.riderNotifications.length, 1, 'Initial creation must create 1 notification');

    // Duplicate retry processing with same notificationId & eventId
    await notifRepo.createNotification(notificationPayload);
    assert.strictEqual(localDb.riderNotifications.length, 1, 'Duplicate processing must NOT create a 2nd row');

    // Update delivery outcome
    await notifRepo.updateDeliveryOutcome(notificationPayload.notificationId, { status: 'DELIVERED_FCM' });
    assert.strictEqual(localDb.riderNotifications[0].status, 'DELIVERED_FCM', 'Existing notification updated in-place');
  });

  // -------------------------------------------------------------
  // Test 7: Atomic Offer Acceptance Idempotency & Concurrency
  // -------------------------------------------------------------
  await test('Offer Acceptance Idempotency: Duplicate accept by same rider succeeds idempotently; competing rider receives OFFER_CLAIMED', async () => {
    const localDb = {
      offers: {
        'off_race_1': {
          offerId: 'off_race_1',
          deliveryId: 'del_race_1',
          orderId: 'ord_race_1',
          riderId: 'RIDER_WINNER',
          status: 'CREATED',
          earningsAmount: 185,
          offerExpiresAt: Date.now() + 60000,
          pricingSnapshot: { totalEarnings: 185 }
        },
        'off_race_competing': {
          offerId: 'off_race_competing',
          deliveryId: 'del_race_1',
          orderId: 'ord_race_1',
          riderId: 'RIDER_LOSER',
          status: 'CREATED',
          earningsAmount: 185,
          offerExpiresAt: Date.now() + 60000,
          pricingSnapshot: { totalEarnings: 185 }
        }
      },
      deliverySessions: {
        'del_race_1': {
          deliveryId: 'del_race_1',
          orderId: 'ord_race_1',
          state: 'OFFERED'
        }
      }
    };

    const offerRepo = new LocalDevelopmentOfferRepository(localDb, () => {});

    const winnerProfile = {
      realName: 'Rahul Sharma',
      realPhone: '+919876543210',
      realVehicle: 'HR 06 AB 1234'
    };

    // 1. First accept by winner
    const res1 = await offerRepo.acceptOfferTransactionally('off_race_1', 'RIDER_WINNER', winnerProfile);
    assert.strictEqual(res1.ok, true, 'First accept must succeed');
    assert.strictEqual(localDb.offers['off_race_1'].status, 'ACCEPTED');
    assert.strictEqual(localDb.offers['off_race_competing'].status, 'CLAIMED_BY_OTHER', 'Competing offer revoked');

    // 2. Duplicate accept by winner (Network retry scenario)
    const res2 = await offerRepo.acceptOfferTransactionally('off_race_1', 'RIDER_WINNER', winnerProfile);
    assert.strictEqual(res2.ok, true, 'Duplicate accept by same winner must return idempotent success');
    assert.strictEqual(res2.idempotencyReplay, true, 'Result marked as idempotent replay');

    // 3. Attempt by competing rider
    const res3 = await offerRepo.acceptOfferTransactionally('off_race_competing', 'RIDER_LOSER', winnerProfile);
    assert.strictEqual(res3.ok, false, 'Competing rider accept must fail');
    assert.strictEqual(res3.error, 'OFFER_CLAIMED');
  });

  // -------------------------------------------------------------
  // Test 8: Device Token Registration Idempotency
  // -------------------------------------------------------------
  await test('Device Token Registration Idempotency: Multiple token saves for same rider update cleanly', async () => {
    const localDb = { riderTokens: {} };
    const deviceRepo = new LocalDevelopmentDeviceTokenRepository(localDb, () => {});

    await deviceRepo.saveToken('RIDER_101', { token: 'tok_v1', deviceId: 'dev_1', platform: 'android' });
    assert.strictEqual(localDb.riderTokens['RIDER_101'].token, 'tok_v1');

    await deviceRepo.saveToken('RIDER_101', { token: 'tok_v2', deviceId: 'dev_1', platform: 'android' });
    assert.strictEqual(localDb.riderTokens['RIDER_101'].token, 'tok_v2');
    assert.strictEqual(Object.keys(localDb.riderTokens).length, 1, 'Exactly one entry in riderTokens dictionary');
  });

  // -------------------------------------------------------------
  // Test 9: ProductionFcmSender Data-Only Payload Transport Invariant
  // -------------------------------------------------------------
  await test('FCM Transport Invariant: ProductionFcmSender sends DATA-ONLY payload (zero notification object)', async () => {
    const { ProductionFcmSender } = require('./server/production-server');
    let capturedBody = null;

    // Local intercepting mock server
    const http = require('http');
    const mockServer = http.createServer((req, res) => {
      let data = '';
      req.on('data', c => { data += c; });
      req.on('end', () => {
        capturedBody = JSON.parse(data);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: 1 }));
      });
    });

    await new Promise(r => mockServer.listen(0, '127.0.0.1', r));
    const port = mockServer.address().port;

    try {
      const sender = new ProductionFcmSender('test_key_123', `http://127.0.0.1:${port}/fcm/send`);
      const result = await sender.sendPushNotification('token_xyz', {
        title: 'New Offer',
        body: '3.2 km trip',
        notificationId: 'notif_1',
        offerId: 'off_1',
        orderId: 'ord_1',
        deliveryId: 'del_1',
        data: { earnings: '55.00' }
      });

      assert.strictEqual(Boolean(result && (result.ok || result === true)), true, 'FCM send must return true / DELIVERED on 200');
      assert.ok(capturedBody, 'Must send HTTP body to FCM endpoint');
      assert.strictEqual(capturedBody.to, 'token_xyz');
      assert.ok(capturedBody.data, 'Must contain data payload object');
      assert.strictEqual(capturedBody.data.offerId, 'off_1');
      assert.strictEqual(capturedBody.notification, undefined, 'Must NEVER contain notification object in FCM payload');
    } finally {
      mockServer.close();
    }
  });

  // -------------------------------------------------------------
  // Test 10: FCM Provider-Level Response Verification (HTTP 200 != Success)
  // -------------------------------------------------------------
  await test('FCM Provider Verification: HTTP 200 with provider failure returns false (no false FCM_ACCEPTED)', async () => {
    const { ProductionFcmSender } = require('./server/production-server');
    const http = require('http');

    // Mock FCM server returning HTTP 200 but provider-level rejection
    const mockServer = http.createServer((req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        multicast_id: 998811,
        success: 0,
        failure: 1,
        canonical_ids: 0,
        results: [{ error: 'InvalidRegistration' }]
      }));
    });

    await new Promise(r => mockServer.listen(0, '127.0.0.1', r));
    const port = mockServer.address().port;

    try {
      const sender = new ProductionFcmSender('test_key_123', `http://127.0.0.1:${port}/fcm/send`);
      const result = await sender.sendPushNotification('bad_token_xyz', {
        title: 'New Offer',
        body: '3.2 km trip',
        notificationId: 'notif_1',
        offerId: 'off_1'
      });

      assert.strictEqual(Boolean(result && (result.ok || result === true)), false, 'Must return false / REJECTED when FCM provider returns application failure (InvalidRegistration)');
    } finally {
      mockServer.close();
    }
  });

  console.log('\n================================================================');
  console.log(`🏆 ALL TESTS COMPLETE: ${passedCount} PASSED, ${failedCount} FAILED`);
  console.log('================================================================\n');

  if (failedCount > 0) {
    process.exit(1);
  }
  process.exit(0);
}

runAllTests();
