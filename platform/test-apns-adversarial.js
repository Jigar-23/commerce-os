'use strict';

/**
 * ==============================================================================
 * COMMERCE OS — ADVERSARIAL APNs STRESS & SECURITY TEST SUITE
 * ==============================================================================
 * Comprehensive penetration, stress, and security tests for the native APNs
 * pipeline:
 *  1. Malformed / SQLi / Buffer-overflow Token Rejections
 *  2. Payload Size Boundaries (>4096 bytes hard limit)
 *  3. Incomplete & Tampered Push Schema Rejections
 *  4. High-Concurrency Push Burst (100 concurrent dispatches)
 *  5. Graceful Transport Error & SSE Fallback Resiliency
 * ==============================================================================
 */

const crypto = require('crypto');
const {
  ApnsPayloadBuilder,
  ApnsDispatcher,
  ApnsDeliveryResult,
  normalizeApnsToken,
  APNS_MAX_PAYLOAD_BYTES
} = require('./services/apns-dispatcher');

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

function assertThrows(fn, expectedSnippet, message) {
  totalTests++;
  try {
    fn();
    failedTests++;
    console.error(` ✘ FAIL: ${message} (did not throw)`);
  } catch (err) {
    if (!expectedSnippet || err.message.includes(expectedSnippet)) {
      passedTests++;
      console.log(` ✔ PASS: ${message} (threw '${err.message}')`);
    } else {
      failedTests++;
      console.error(` ✘ FAIL: ${message} (threw '${err.message}', expected '${expectedSnippet}')`);
    }
  }
}

async function runAdversarialSuite() {
  console.log('======================================================================');
  console.log('  COMMERCE OS — ADVERSARIAL APNs STRESS & SECURITY PENETRATION SUITE  ');
  console.log('======================================================================\n');

  // --------------------------------------------------------------------------
  // SECTION 1: Malicious & Adversarial Token Injections
  // --------------------------------------------------------------------------
  console.log('--- Section 1: Malicious & Adversarial Token Injections ---');

  assertThrows(
    () => normalizeApnsToken("'; DROP TABLE rider_device_tokens; --"),
    'APNS_INVALID_TOKEN',
    'Rejects SQL injection payload in device token'
  );

  assertThrows(
    () => normalizeApnsToken('<script>alert("XSS")</script>'),
    'APNS_INVALID_TOKEN',
    'Rejects XSS script payload in device token'
  );

  assertThrows(
    () => normalizeApnsToken('A'.repeat(5000)),
    'APNS_INVALID_TOKEN',
    'Rejects 5,000-character buffer overflow token'
  );

  assertThrows(
    () => normalizeApnsToken('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdefg'),
    'APNS_INVALID_TOKEN',
    'Rejects 65-character off-by-one hex string'
  );

  assertThrows(
    () => normalizeApnsToken('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde'),
    'APNS_INVALID_TOKEN',
    'Rejects 63-character short hex string'
  );

  assertThrows(
    () => normalizeApnsToken(null),
    'APNS_INVALID_TOKEN',
    'Rejects null device token'
  );

  assertThrows(
    () => normalizeApnsToken(undefined),
    'APNS_INVALID_TOKEN',
    'Rejects undefined device token'
  );

  assertThrows(
    () => normalizeApnsToken({}),
    'APNS_INVALID_TOKEN',
    'Rejects object device token injection'
  );

  // --------------------------------------------------------------------------
  // SECTION 2: Payload Boundary & Ceiling Attack Resistance
  // --------------------------------------------------------------------------
  console.log('\n--- Section 2: Payload Boundary & Ceiling Attack Resistance ---');

  // Hard 4096-byte ceiling attack
  const builder = new ApnsPayloadBuilder();
  builder.setAlert('Normal Title', 'Normal Body');
  builder.setCustomData('huge_attack_payload', 'X'.repeat(5000));

  assertThrows(
    () => builder.build(),
    'APNS_PAYLOAD_TOO_LARGE',
    'Strictly rejects JSON payload exceeding 4,096 bytes'
  );

  // Exact 4096-byte boundary verification
  const exactBuilder = new ApnsPayloadBuilder();
  const baseJsonStr = exactBuilder.setAlert('T', 'B').build().jsonString;
  const remainingBytes = APNS_MAX_PAYLOAD_BYTES - Buffer.byteLength(baseJsonStr, 'utf8') - 40; // padding
  exactBuilder.setCustomData('filler', 'A'.repeat(Math.max(10, remainingBytes)));
  const exactPayload = exactBuilder.build();
  assert(
    exactPayload.byteLength <= APNS_MAX_PAYLOAD_BYTES,
    `Payload right at or below boundary accepted (${exactPayload.byteLength} bytes)`
  );

  // Reserved keyword hijacking protection
  const reservedBuilder = new ApnsPayloadBuilder();
  assertThrows(
    () => reservedBuilder.setCustomData('aps', { hijacked: true }),
    'APNS_RESERVED_KEY',
    'Forbids overwriting reserved "aps" Apple dictionary'
  );

  // Badge validation attacks
  assertThrows(
    () => new ApnsPayloadBuilder().setBadge(-1),
    'APNS_INVALID_BADGE',
    'Rejects negative badge count'
  );

  assertThrows(
    () => new ApnsPayloadBuilder().setBadge(NaN),
    'APNS_INVALID_BADGE',
    'Rejects NaN badge count'
  );

  // --------------------------------------------------------------------------
  // SECTION 3: Tampered Push Schema & Security Attributes
  // --------------------------------------------------------------------------
  console.log('\n--- Section 3: Push Schema & Interruption Level Security ---');

  assertThrows(
    () => new ApnsPayloadBuilder().setInterruptionLevel('ultra-critical'),
    'APNS_INVALID_INTERRUPTION_LEVEL',
    'Rejects invalid interruption-level string'
  );

  // Valid critical alert building
  const criticalPayload = new ApnsPayloadBuilder()
    .setAlert('URGENT DISPATCH OFFER', 'You have a new high-payout delivery offer!')
    .setSound({ critical: 1, name: 'dispatch_siren.wav', volume: 1.0 })
    .setInterruptionLevel('critical')
    .setRelevanceScore(1.0)
    .setThreadId('orders.dispatch')
    .build();

  assert(criticalPayload.payload.aps['interruption-level'] === 'critical', 'Sets aps.interruption-level to critical');
  assert(criticalPayload.payload.aps['relevance-score'] === 1.0, 'Sets aps.relevance-score to 1.0');
  assert(criticalPayload.payload.aps.sound.critical === 1, 'Sets aps.sound.critical flag to 1');
  assert(criticalPayload.payload.aps.sound.volume === 1.0, 'Sets aps.sound.volume to 1.0');
  assert(criticalPayload.payload.aps['thread-id'] === 'orders.dispatch', 'Sets aps.thread-id');

  // --------------------------------------------------------------------------
  // SECTION 4: High-Concurrency Push Burst (100 Concurrent Dispatches)
  // --------------------------------------------------------------------------
  console.log('\n--- Section 4: High-Concurrency Push Burst (100 Concurrent Dispatches) ---');

  const dispatcher = new ApnsDispatcher({
    transport: async (token, payload, headers) => {
      // Simulate 5ms Apple HTTP/2 network roundtrip
      await new Promise(r => setTimeout(r, 5));
      return new ApnsDeliveryResult({
        status: 'DELIVERED',
        httpStatus: 200,
        deviceToken: token,
        apnsId: crypto.randomUUID()
      });
    }
  });

  const validToken = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
  const burstPayload = dispatcher.createRiderOfferPayload({
    offerId: 'off_burst_001',
    earningsAmount: 65.00,
    totalDistanceKm: 2.5,
    estimatedDurationMins: 12,
    merchantName: 'Apollo 24/7 Dark Store',
    customerAddress: 'Indiranagar 100ft Road'
  });

  const startTime = Date.now();
  const dispatchPromises = [];
  const BURST_COUNT = 100;

  for (let i = 0; i < BURST_COUNT; i++) {
    dispatchPromises.push(dispatcher.send(validToken, burstPayload));
  }

  const results = await Promise.all(dispatchPromises);
  const totalDuration = Date.now() - startTime;

  const deliveredCount = results.filter(r => r.status === 'DELIVERED').length;
  assert(deliveredCount === BURST_COUNT, `All ${BURST_COUNT} concurrent pushes delivered successfully`);
  assert(totalDuration < 3000, `Completed 100-push burst in ${totalDuration}ms (well under 3.0s SLA)`);
  assert(dispatcher.stats.totalDispatched === BURST_COUNT, `Dispatcher stats accurately record ${BURST_COUNT} dispatches`);

  // --------------------------------------------------------------------------
  // SECTION 5: Graceful Transport Error & Failure Recovery
  // --------------------------------------------------------------------------
  console.log('\n--- Section 5: Graceful Transport Error & Failure Recovery ---');

  const failingDispatcher = new ApnsDispatcher({
    transport: async (token) => {
      throw new Error('ETIMEDOUT: Connection to api.push.apple.com timed out after 5000ms');
    }
  });

  const failResult = await failingDispatcher.send(validToken, burstPayload);
  assert(failResult.status === 'FAILED', 'Handles network timeout gracefully without crashing process');
  assert(failResult.httpStatus === 500, 'Returns 500 status on network failure');
  assert(failingDispatcher.stats.totalFailed === 1, 'Increments failure counter on transport exception');

  // Handle 410 BadDeviceToken / Unregistered
  const unregisteredDispatcher = new ApnsDispatcher({
    transport: async (token) => {
      return new ApnsDeliveryResult({
        status: 'REJECTED',
        httpStatus: 410,
        deviceToken: token,
        reason: 'Unregistered'
      });
    }
  });

  const unregResult = await unregisteredDispatcher.send(validToken, burstPayload);
  assert(unregResult.status === 'REJECTED', 'Properly parses 410 Unregistered response');
  assert(unregResult.httpStatus === 410, 'HTTP status is 410');
  assert(unregisteredDispatcher.stats.totalRejected === 1, 'Increments totalRejected on device token invalidation');

  // --------------------------------------------------------------------------
  // Summary
  // --------------------------------------------------------------------------
  console.log('\n======================================================================');
  console.log(`TOTAL TESTS RUN : ${totalTests}`);
  console.log(`PASSED          : ${passedTests}`);
  console.log(`FAILED          : ${failedTests}`);
  console.log(`SUCCESS RATE    : ${Math.round((passedTests / totalTests) * 100)}%`);
  console.log('======================================================================\n');

  if (failedTests > 0) {
    process.exit(1);
  }
}

runAdversarialSuite().catch(err => {
  console.error('FATAL_ADVERSARIAL_SUITE_ERROR:', err);
  process.exit(1);
});
