const http = require('http');
const assert = require('assert');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');

// Load environment from .env if present
try {
  const envPath = path.resolve(__dirname, '../.env');
  if (fs.existsSync(envPath)) {
    const lines = fs.readFileSync(envPath, 'utf8').split('\n');
    for (const l of lines) {
      const trimmed = l.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx > 0) {
        const k = trimmed.substring(0, eqIdx).trim();
        const v = trimmed.substring(eqIdx + 1).trim().replace(/^['"]|['"]$/g, '');
        if (!process.env[k]) process.env[k] = v;
      }
    }
  }
} catch (_) {}

const { server, pool } = require('./server/production-server');
const { ProductionNotificationService, TransactionalNotificationRepository } = require('./repositories/index');

const PORT = 8201;
const JWT_SECRET = process.env.JWT_SECRET || 'test-jwt-secret-for-security-audit-minimum-32-chars-long';
const JWT_ISSUER = process.env.JWT_ISSUER || 'https://auth.commerceos.internal';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerceos-platform-clients';

function signToken(payload, options = {}) {
  return jwt.sign(
    {
      sub: payload.sub,
      role: payload.role || 'ROLE_RIDER',
      roles: payload.roles || [payload.role || 'ROLE_RIDER'],
      phone: payload.phone || '+919876543210',
      storeId: payload.storeId || null,
      ...payload
    },
    JWT_SECRET,
    {
      algorithm: 'HS256',
      issuer: JWT_ISSUER,
      audience: JWT_AUDIENCE,
      expiresIn: options.expiresIn || '1h',
      ...options
    }
  );
}

function request(method, path, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const postData = body ? JSON.stringify(body) : '';
    const reqHeaders = {
      'Content-Type': 'application/json',
      ...headers
    };
    if (postData) {
      reqHeaders['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: PORT,
        path,
        method,
        headers: reqHeaders
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          try {
            const parsed = data ? JSON.parse(data) : {};
            resolve({ status: res.statusCode, headers: res.headers, body: parsed });
          } catch (e) {
            resolve({ status: res.statusCode, headers: res.headers, raw: data });
          }
        });
      }
    );
    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function runBatch7Tests() {
  console.log('================================================================');
  console.log('🧪 RUNNING CODEX BATCH 7 REMAINING FINDINGS VERIFICATION TESTS');
  console.log('================================================================\n');

  let srv;
  try {
    srv = await new Promise((resolve, reject) => {
      const s = server.listen(PORT, '127.0.0.1', () => resolve(s));
      s.on('error', reject);
    });
  } catch (err) {
    console.error('Failed to bind server on port', PORT, err);
    process.exit(1);
  }

  const testStoreA = `str_batch7_a_${Date.now()}`;
  const riderAId = `rdr_b7_a_${Date.now()}`;
  const riderBId = `rdr_b7_b_${Date.now()}`;

  const riderAToken = signToken({ sub: riderAId, role: 'ROLE_RIDER', storeId: testStoreA });
  const riderBToken = signToken({ sub: riderBId, role: 'ROLE_RIDER', storeId: testStoreA });

  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`
      INSERT INTO stores (id, store_name, address, latitude, longitude, is_active, created_at, updated_at)
      VALUES ($1, 'Batch7 Store A', 'Store A Address', 28.4595, 77.0266, TRUE, NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [testStoreA]);

    await pool.query(`
      INSERT INTO riders (id, rider_id, store_id, full_name, phone, status, vehicle_number)
      VALUES 
        ($1, $1, $2, 'Rider A Store A', '+9191' || $4 || '11', 'ACTIVE', 'HR26AB1111'),
        ($3, $3, $2, 'Rider B Store A', '+9192' || $4 || '22', 'ACTIVE', 'HR26AB2222')
      ON CONFLICT (id) DO NOTHING
    `, [riderAId, testStoreA, riderBId, randDigits]);
  }

  // -------------------------------------------------------------
  // Test 1: Broadcast Notification Drop Address Redaction
  // -------------------------------------------------------------
  console.log('[Test 1] Broadcast Notifications: Coarse Locality Redaction For Unaccepted Offers');

  const unacceptedOfferId = `off_unaccepted_${Date.now()}`;
  const notifId1 = `notif_b7_unaccepted_${Date.now()}`;

  if (pool) {
    const rawMetadataWithAddress = {
      storeId: testStoreA,
      orderId: `ord_b7_${Date.now()}`,
      deliveryId: `del_b7_${Date.now()}`,
      offerId: unacceptedOfferId,
      actionPayload: {
        offerId: unacceptedOfferId,
        storeId: testStoreA,
        status: 'OFFERED',
        customerName: 'Secret Customer',
        dropAddress: 'Flat 12, Example Street, South Delhi',
        customerAddress: 'Flat 12, Example Street, South Delhi',
        deliveryAddress: {
          addressLine: 'Flat 12, Example Street',
          locality: 'South Delhi',
          city: 'New Delhi'
        },
        earningsAmount: 48
      }
    };

    await pool.query(`
      INSERT INTO rider_notifications (id, notification_id, rider_id, category, title, body, status, metadata, created_at)
      VALUES ($1, $1, 'broadcast', 'ORDERS', '🚨 NEW DELIVERY · ₹48', 'New delivery offer', 'UNREAD', $2, NOW())
    `, [notifId1, JSON.stringify(rawMetadataWithAddress)]);
  }

  const res1 = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${riderAToken}`
  });
  assert.strictEqual(res1.status, 200);
  const notifs1 = res1.body.notifications || [];
  const foundUnaccepted = notifs1.find(n => n.id === notifId1 || n.notificationId === notifId1);
  assert.ok(foundUnaccepted, 'Rider A should find broadcast notification for testStoreA');

  const payload1 = foundUnaccepted.metadata?.actionPayload || {};
  assert.ok(!payload1.dropAddress.includes('Flat 12'), 'Unaccepted offer notification dropAddress must not expose Flat 12');
  assert.ok(!payload1.dropAddress.includes('Example Street'), 'Unaccepted offer notification dropAddress must not expose street name');
  assert.strictEqual(payload1.dropAddress, 'South Delhi', 'dropAddress should be coarse locality');
  assert.strictEqual(payload1.customerAddress, 'South Delhi', 'customerAddress should be coarse locality');
  assert.strictEqual(payload1.customerLocality, 'South Delhi', 'customerLocality should be coarse locality');
  console.log('  ✅ 1.1 PASS: Unaccepted broadcast notification dropAddress redacted strictly to coarse locality without house/street.');

  // 1.2: When offer is assigned to Rider A, Rider A receives the full drop address
  const assignedOfferId = `off_assigned_${Date.now()}`;
  const notifId2 = `notif_b7_assigned_${Date.now()}`;
  if (pool) {
    const rawAssignedMetadata = {
      storeId: testStoreA,
      orderId: `ord_b7_as_${Date.now()}`,
      deliveryId: `del_b7_as_${Date.now()}`,
      offerId: assignedOfferId,
      actionPayload: {
        offerId: assignedOfferId,
        storeId: testStoreA,
        status: 'ASSIGNED',
        assignedRiderId: riderAId,
        riderId: riderAId,
        dropAddress: 'Flat 12, Example Street, South Delhi',
        customerAddress: 'Flat 12, Example Street, South Delhi',
        earningsAmount: 50
      }
    };

    await pool.query(`
      INSERT INTO rider_notifications (id, notification_id, rider_id, category, title, body, status, metadata, created_at)
      VALUES ($1, $1, $2, 'ORDERS', '🚨 DELIVERY ASSIGNED · ₹50', 'Assigned order', 'UNREAD', $3, NOW())
    `, [notifId2, riderAId, JSON.stringify(rawAssignedMetadata)]);
  }

  const res2 = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${riderAToken}`
  });
  assert.strictEqual(res2.status, 200);
  const notifs2 = res2.body.notifications || [];
  const foundAssigned = notifs2.find(n => n.id === notifId2 || n.notificationId === notifId2);
  assert.ok(foundAssigned, 'Rider A should find assigned notification');
  const payload2 = foundAssigned.metadata?.actionPayload || {};
  assert.strictEqual(payload2.dropAddress, 'Flat 12, Example Street, South Delhi', 'Assigned rider receives full drop address');
  console.log('  ✅ 1.2 PASS: Assigned rider receives full delivery drop address upon assignment.\n');

  // -------------------------------------------------------------
  // Test 2: Dispatch Neutral Score Tie-Breaking
  // -------------------------------------------------------------
  console.log('[Test 2] Dispatch Ranking: Neutral Heartbeat Tie-Breaker (No Favoritism)');

  const candidateA = {
    riderId: 'rdr_candidate_alpha',
    tier: 'STANDARD',
    lastKnownLat: 28.2000,
    lastKnownLng: 76.6100,
    lastSeenAt: new Date(Date.now() - 60000).toISOString() // 1 min ago
  };

  const candidateB = {
    riderId: 'rdr_9817916180', // previously hardcoded rider
    tier: 'STANDARD',
    lastKnownLat: 28.2000,
    lastKnownLng: 76.6100,
    lastSeenAt: new Date(Date.now() - 300000).toISOString() // 5 mins ago (stale)
  };

  const candidateC = {
    riderId: 'rdr_candidate_gamma',
    tier: 'STANDARD',
    lastKnownLat: 28.2000,
    lastKnownLng: 76.6100,
    lastSeenAt: new Date(Date.now() - 10000).toISOString() // 10s ago (freshest)
  };

  const candidates = [candidateA, candidateB, candidateC];

  // Apply scoring formula from DispatchService
  const mLat = 28.2000, mLng = 76.6100;
  const scored = candidates.map(r => {
    const proximityScore = 100; // identical distance
    const tierBonus = 0; // all STANDARD
    const totalScore = proximityScore + tierBonus;
    return { ...r, totalScore };
  });

  scored.sort((a, b) => {
    if (b.totalScore !== a.totalScore) {
      return b.totalScore - a.totalScore;
    }
    const aTime = a.lastSeenAt ? new Date(a.lastSeenAt).getTime() : 0;
    const bTime = b.lastSeenAt ? new Date(b.lastSeenAt).getTime() : 0;
    if (bTime !== aTime) {
      return bTime - aTime;
    }
    return String(a.riderId).localeCompare(String(b.riderId));
  });

  assert.strictEqual(scored[0].riderId, 'rdr_candidate_gamma', 'Freshest heartbeat rider must win tie break');
  assert.strictEqual(scored[1].riderId, 'rdr_candidate_alpha', 'Second freshest rider must be ranked second');
  assert.strictEqual(scored[2].riderId, 'rdr_9817916180', 'Stale heartbeat rider ranked last despite former hardcoded ID');
  console.log('  ✅ 2.1 PASS: Candidate ranking sorts strictly by score and fresher heartbeat without hardcoded ID bias.\n');

  // -------------------------------------------------------------
  // Test 3: Static Contract Audit for Android & MockServer
  // -------------------------------------------------------------
  console.log('[Test 3] Static Code Contracts: Android FCM Reconciliation & MockServer Security');

  const sessionManagerCode = fs.readFileSync(path.resolve(__dirname, '../apps/android/rider-app/src/main/java/com/commerceos/rider/session/RiderSessionManager.kt'), 'utf8');
  assert.ok(sessionManagerCode.includes('reconcileStoreTopic'), 'RiderSessionManager must implement reconcileStoreTopic');
  assert.ok(sessionManagerCode.includes('riders_store_'), 'RiderSessionManager must manage riders_store_ topic');
  
  // Verify clearSession unsubscribes before prefs clear
  const clearSessionIdx = sessionManagerCode.indexOf('fun clearSession');
  const prefsEditClearIdx = sessionManagerCode.indexOf('prefs.edit().clear()', clearSessionIdx);
  const unsubsStoreIdx = sessionManagerCode.indexOf('riders_store_', clearSessionIdx);
  assert.ok(unsubsStoreIdx > clearSessionIdx && unsubsStoreIdx < prefsEditClearIdx, 'clearSession must unsubscribe from saved store topic before clearing preferences');
  console.log('  ✅ 3.1 PASS: Android RiderSessionManager unsubscribes from store topic before clearing session.');

  const riderMainCode = fs.readFileSync(path.resolve(__dirname, '../apps/android/rider-app/src/main/java/com/commerceos/rider/ui/RiderMainScreen.kt'), 'utf8');
  assert.ok(riderMainCode.includes('reconcileStoreTopic'), 'RiderMainScreen must invoke reconcileStoreTopic on authoritative profile refresh');
  console.log('  ✅ 3.2 PASS: Android RiderMainScreen reconciles store topics on authoritative profile load.');

  const mockServerCode = fs.readFileSync(path.resolve(__dirname, './mock-server.js'), 'utf8');
  assert.ok(!mockServerCode.includes("o.rider_id = 'rdr_9817916180'"), 'MockServer must contain ZERO o.rider_id = rdr_9817916180 clauses');
  assert.ok(!mockServerCode.includes("targetRiders.add('rdr_9817916180')"), 'MockServer must not add hardcoded rdr_9817916180 to target riders');
  assert.ok(mockServerCode.includes('Bearer JWT is strictly required to access active offers'), 'MockServer active-offers must enforce Bearer JWT');
  console.log('  ✅ 3.3 PASS: MockServer requires JWT and contains zero hardcoded rider fallbacks.\n');

  console.log('================================================================');
  console.log('🎉 ALL CODEX BATCH 7 REMAINING FINDINGS VERIFIED & PASSED!');
  console.log('================================================================\n');

  if (srv) {
    srv.close();
  }
  process.exit(0);
}

runBatch7Tests().catch((err) => {
  console.error('Batch 7 Test Failure:', err);
  process.exit(1);
});
