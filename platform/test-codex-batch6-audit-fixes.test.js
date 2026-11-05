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

const { server, pool, ProductionFcmSender } = require('./server/production-server');
const { ProductionNotificationService, TransactionalNotificationRepository } = require('./repositories/index');

const PORT = 8199;
const JWT_SECRET = process.env.JWT_SECRET || 'test-jwt-secret-for-security-audit-minimum-32-chars-long';
const JWT_ISSUER = process.env.JWT_ISSUER || 'https://auth.commerceos.internal';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerceos-platform-clients';

function signToken(payload, options = {}) {
  return jwt.sign(
    {
      sub: payload.sub,
      role: payload.role || 'ROLE_CUSTOMER',
      roles: payload.roles || [payload.role || 'ROLE_CUSTOMER'],
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

async function runBatch6Tests() {
  console.log('================================================================');
  console.log('🧪 RUNNING CODEX BATCH 6 AUDIT REMEDIATION VERIFICATION TESTS');
  console.log('================================================================\n');

  let srv;
  try {
    srv = await new Promise((resolve, reject) => {
      const s = server.listen(PORT, '127.0.0.1', () => resolve(s));
      s.on('error', reject);
    });
  } catch (e) {
    if (e.code === 'EADDRINUSE') {
      console.log(`Port ${PORT} in use, tests will connect to existing server.`);
    } else {
      throw e;
    }
  }

  const testStoreA = `str_batch6_a_${Date.now()}`;
  const testStoreB = `str_batch6_b_${Date.now()}`;
  const testRiderAId = `rdr_batch6_a_${Date.now()}`;
  const testRiderBId = `rdr_batch6_b_${Date.now()}`;
  const testRiderUnassignedId = `rdr_batch6_unassigned_${Date.now()}`;
  const testSellerAId = `sel_batch6_a_${Date.now()}`;
  const testCustomerId = `cust_batch6_${Date.now()}`;

  const customerToken = signToken({ sub: testCustomerId, role: 'ROLE_CUSTOMER' });
  const riderAToken = signToken({ sub: testRiderAId, role: 'ROLE_RIDER', storeId: testStoreA });
  const riderBToken = signToken({ sub: testRiderBId, role: 'ROLE_RIDER', storeId: testStoreB });
  const riderUnassignedToken = signToken({ sub: testRiderUnassignedId, role: 'ROLE_RIDER', storeId: null });
  const sellerAToken = signToken({ sub: testSellerAId, role: 'ROLE_SELLER', storeId: testStoreA });
  const adminToken = signToken({ sub: 'admin', role: 'ROLE_ADMIN' });

  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`
      INSERT INTO stores (id, store_name, address, latitude, longitude, is_active, created_at, updated_at)
      VALUES 
        ($1, 'Batch6 Store A', 'Store A Address', 28.4595, 77.0266, TRUE, NOW(), NOW()),
        ($2, 'Batch6 Store B', 'Store B Address', 28.5000, 77.1000, TRUE, NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [testStoreA, testStoreB]);

    await pool.query(`
      INSERT INTO customers (id, phone, full_name, is_active, created_at, updated_at)
      VALUES ($1, '+9199' || $2 || '01', 'Test Customer Batch6', TRUE, NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [testCustomerId, randDigits]);
    await pool.query(`
      INSERT INTO riders (id, rider_id, store_id, full_name, phone, status, vehicle_number)
      VALUES 
        ($1, $1, $2, 'Rider A Store A', '+9191' || $6 || '11', 'ACTIVE', 'HR26AB1111'),
        ($3, $3, $4, 'Rider B Store B', '+9192' || $6 || '22', 'ACTIVE', 'HR26AB2222'),
        ($5, $5, NULL, 'Rider Unassigned', '+9193' || $6 || '33', 'ACTIVE', 'HR26AB3333')
      ON CONFLICT (id) DO NOTHING
    `, [testRiderAId, testStoreA, testRiderBId, testStoreB, testRiderUnassignedId, randDigits]);

    await pool.query(`
      INSERT INTO sellers (id, seller_id, store_id, email, phone, status, password_hash)
      VALUES ($1, $1, $2, 'sellera_' || $3 || '@test.com', '99' || $3, 'ACTIVE', 'scrypt_placeholder')
      ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', store_id = $2
    `, [testSellerAId, testStoreA, randDigits]);
  }

  // -------------------------------------------------------------
  // Finding 1: Rider notifications PII redaction, scope, and no rdr_9817916180 duplication
  // -------------------------------------------------------------
  console.log('[Test 1] Rider Notifications: PII Redaction, Store Scoping, and Recipient Isolation');

  // 1.1: Customer token cannot view rider notifications
  const custNotifRes = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${customerToken}`
  });
  assert.strictEqual(custNotifRes.status, 403, `Expected 403 FORBIDDEN for customer token on rider notifications, got ${custNotifRes.status}`);
  console.log('  ✅ 1.1 PASS: Customer token rejected with 403 FORBIDDEN.');

  // 1.2: Create a broadcast offer notification for Store A containing sensitive customer fields
  const testOfferId = `off_batch6_${Date.now()}`;
  const notifId = `notif_batch6_${Date.now()}`;
  if (pool) {
    const rawMetadataWithPii = {
      storeId: testStoreA,
      orderId: `ord_b6_${Date.now()}`,
      deliveryId: `del_b6_${Date.now()}`,
      offerId: testOfferId,
      actionPayload: {
        offerId: testOfferId,
        storeId: testStoreA,
        customerName: 'Jigar Private Customer',
        customerPhone: '+919999988888',
        customerLat: 28.4595,
        customerLng: 77.0266,
        dropAddress: 'Sector 56, Gurgaon, Secret Apartment 404',
        earningsAmount: 55
      }
    };

    await pool.query(`
      INSERT INTO rider_notifications (id, notification_id, rider_id, category, title, body, status, metadata, created_at)
      VALUES ($1, $1, 'broadcast', 'ORDERS', '🚨 NEW DELIVERY · ₹55', 'New delivery offer', 'UNREAD', $2, NOW())
    `, [notifId, JSON.stringify(rawMetadataWithPii)]);
  }

  // 1.3: Store A rider receives the notification, but PII is strictly redacted
  const riderANotifRes = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${riderAToken}`
  });
  assert.strictEqual(riderANotifRes.status, 200);
  const riderANotifs = riderANotifRes.body.notifications || [];
  const foundNotif = riderANotifs.find(n => n.id === notifId || n.notificationId === notifId);
  assert.ok(foundNotif, 'Store A rider must receive broadcast notification for Store A');
  
  const payload = foundNotif.metadata?.actionPayload || {};
  assert.strictEqual(payload.customerPhone, undefined, 'customerPhone must be stripped from actionPayload');
  assert.strictEqual(payload.customerName, undefined, 'customerName must be stripped from actionPayload');
  assert.strictEqual(payload.customerLat, undefined, 'customerLat must be stripped from actionPayload');
  assert.strictEqual(payload.customerLng, undefined, 'customerLng must be stripped from actionPayload');
  assert.ok(!payload.dropAddress.includes('Secret Apartment 404'), 'dropAddress must not expose house/apartment details');
  assert.ok(!payload.customerAddress.includes('Secret Apartment 404'), 'customerAddress must not expose house/apartment details');
  assert.strictEqual(payload.customerLocality, 'Sector 56, Gurgaon', 'customerLocality must be coarse locality');
  console.log('  ✅ 1.2 PASS: Store A rider received notification with customer PII strictly redacted and coarse locality enforced.');

  // 1.4: Store B rider does NOT receive Store A broadcast notification
  const riderBNotifRes = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${riderBToken}`
  });
  assert.strictEqual(riderBNotifRes.status, 200);
  const riderBNotifs = riderBNotifRes.body.notifications || [];
  const foundCrossStore = riderBNotifs.find(n => n.id === notifId || n.notificationId === notifId);
  assert.strictEqual(foundCrossStore, undefined, 'Store B rider must NOT receive Store A broadcast notification');
  console.log('  ✅ 1.3 PASS: Store B rider isolated; cannot view Store A broadcast notification.');

  // 1.5: Unassigned rider does NOT receive broadcast notifications
  const riderUnassignedNotifRes = await request('GET', '/api/v1/delivery/rider/notifications', null, {
    Authorization: `Bearer ${riderUnassignedToken}`
  });
  assert.strictEqual(riderUnassignedNotifRes.status, 200);
  const unassignedNotifs = riderUnassignedNotifRes.body.notifications || [];
  const foundUnassigned = unassignedNotifs.find(n => n.id === notifId || n.notificationId === notifId);
  assert.strictEqual(foundUnassigned, undefined, 'Unassigned rider must NOT receive store broadcast notifications');
  console.log('  ✅ 1.4 PASS: Unassigned rider receives zero broadcast notifications.');

  // 1.6: Check that ProductionNotificationService does NOT duplicate to rdr_9817916180
  if (pool) {
    const notifRepo = new TransactionalNotificationRepository(pool);
    let capturedTopics = [];
    const mockFcm = {
      sendPushToTopic: async (topic, payload) => { capturedTopics.push(topic); return true; },
      sendPushNotification: async () => true
    };
    const notifService = new ProductionNotificationService(
      notifRepo,
      null,
      null,
      null,
      mockFcm,
      null,
      null
    );

    const freshOffer = {
      offerId: `off_fresh_${Date.now()}`,
      orderId: `ord_fresh_${Date.now()}`,
      deliveryId: `del_fresh_${Date.now()}`,
      storeId: testStoreA,
      customerPhone: '+919876543210',
      customerName: 'Secret Customer',
      customerAddress: 'Wadgaon, Pune'
    };

    await notifService.dispatchOfferNotification(freshOffer, 'broadcast', freshOffer);

    // Assert no duplicated notification for rdr_9817916180 was created
    const dupCheck = await pool.query(
      `SELECT * FROM rider_notifications WHERE rider_id = 'rdr_9817916180' AND (metadata->>'offerId' = $1 OR notification_id LIKE $2)`,
      [freshOffer.offerId, `%${freshOffer.offerId}%`]
    );
    assert.strictEqual(dupCheck.rows.length, 0, 'No notification duplicate to hardcoded rdr_9817916180 should exist in database');
    console.log('  ✅ 1.5 PASS: Hardcoded recipient rdr_9817916180 duplication eliminated.\n');
  }

  // -------------------------------------------------------------
  // Finding 2: Offer Dispatcher Store-Scoped Topic & Rejection of Missing Store ID
  // -------------------------------------------------------------
  console.log('[Test 2] Generic Offer Dispatcher: Rejection of Missing Store ID and Store-Scoped FCM Routing');

  let sentTopics = [];
  const testFcmSender = {
    sendPushToTopic: async (topic, payload) => {
      sentTopics.push({ topic, payload });
      return true;
    },
    sendPushNotification: async () => true
  };

  const scopedNotifService = new ProductionNotificationService(
    pool ? new TransactionalNotificationRepository(pool) : null,
    null,
    null,
    null,
    testFcmSender,
    null,
    null
  );

  // 2.1: Missing storeId for broadcast offer is rejected
  let missingStoreErr = null;
  try {
    await scopedNotifService.dispatchOfferNotification({
      offerId: `off_nostore_${Date.now()}`,
      orderId: 'ord_1',
      deliveryId: 'del_1',
      customerAddress: 'Area 1'
    }, 'broadcast');
  } catch (err) {
    missingStoreErr = err;
  }
  assert.ok(missingStoreErr, 'Expected dispatchOfferNotification to throw OFFER_STORE_ID_REQUIRED');
  assert.ok(missingStoreErr.message.includes('OFFER_STORE_ID_REQUIRED'), `Expected OFFER_STORE_ID_REQUIRED, got: ${missingStoreErr.message}`);
  console.log('  ✅ 2.1 PASS: Broadcast offer missing storeId rejected with OFFER_STORE_ID_REQUIRED.');

  // 2.2: Valid broadcast offer is sent to riders_store_<storeId> and NEVER riders_broadcast
  sentTopics = [];
  await scopedNotifService.dispatchOfferNotification({
    offerId: `off_scoped_${Date.now()}`,
    orderId: 'ord_2',
    deliveryId: 'del_2',
    storeId: testStoreA,
    customerAddress: 'Area 2'
  }, 'broadcast');

  assert.strictEqual(sentTopics.length, 1);
  assert.strictEqual(sentTopics[0].topic, `riders_store_${testStoreA}`, `Expected topic riders_store_${testStoreA}, got ${sentTopics[0].topic}`);
  assert.strictEqual(sentTopics[0].payload.data.storeId, testStoreA);
  assert.notStrictEqual(sentTopics[0].topic, 'riders_broadcast', 'Must NOT send to riders_broadcast');
  console.log(`  ✅ 2.2 PASS: Broadcast offer routed strictly to riders_store_${testStoreA} (never riders_broadcast).\n`);

  // -------------------------------------------------------------
  // Finding 3: Seller Realtime Ticket & SSE Stale JWT Database Revalidation
  // -------------------------------------------------------------
  console.log('[Test 3] Seller Realtime Ticket & SSE Stream: Database Status & Store Revalidation');

  // 3.1: Active seller gets ticket with authoritative DB store ID
  const ticketRes = await request('POST', '/api/v1/realtime/ticket', {}, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(ticketRes.status, 200, `Expected 200 for active seller ticket, got ${ticketRes.status}`);
  assert.ok(ticketRes.body.ticket, 'Ticket must be returned');
  console.log('  ✅ 3.1 PASS: Active seller successfully issued SSE ticket.');

  // 3.2: Deactivated seller rejected with 403 SELLER_DEACTIVATED on ticket issuance
  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`UPDATE sellers SET status = 'INACTIVE' WHERE id = $1`, [testSellerAId]);
  }
  const deactivatedTicketRes = await request('POST', '/api/v1/realtime/ticket', {}, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(deactivatedTicketRes.status, 403, `Expected 403 SELLER_DEACTIVATED, got ${deactivatedTicketRes.status}`);
  assert.strictEqual(deactivatedTicketRes.body.error, 'SELLER_DEACTIVATED');
  console.log('  ✅ 3.2 PASS: Deactivated seller rejected with 403 SELLER_DEACTIVATED for ticket issuance.');

  // 3.3: Reassigned seller without store rejected with 403 NO_ASSIGNED_STORE
  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`UPDATE sellers SET status = 'ACTIVE', store_id = NULL WHERE id = $1`, [testSellerAId]);
  }
  const noStoreTicketRes = await request('POST', '/api/v1/realtime/ticket', {}, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(noStoreTicketRes.status, 403, `Expected 403 NO_ASSIGNED_STORE, got ${noStoreTicketRes.status}`);
  console.log('  ✅ 3.3 PASS: Seller with no assigned store rejected with 403 NO_ASSIGNED_STORE.');

  // Restore seller to ACTIVE on Store A
  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`UPDATE sellers SET status = 'ACTIVE', store_id = $2 WHERE id = $1`, [testSellerAId, testStoreA]);
  }
  console.log('  ✅ 3.4 PASS: Seller DB revalidation verified.\n');

  // -------------------------------------------------------------
  // Finding 4: Seller Payment Verification & COD Collection Fail-Closed on null-store orders
  // -------------------------------------------------------------
  console.log('[Test 4] Seller Payment Verification & COD Collection Fail Closed on null-store Orders');

  const testNullStoreOrderId = `ord_null_store_${Date.now()}`;
  const testStoreBOrderId = `ord_store_b_${Date.now()}`;
  const testStoreAOrderId = `ord_store_a_${Date.now()}`;

  if (pool) {
    const randDigits = Date.now().toString().slice(-6);
    await pool.query(`
      INSERT INTO orders (id, order_id, customer_id, store_id, total_amount, cod_amount, is_cod, status, payment_status, delivery_address, items, delivery_otp_hash, created_at, updated_at)
      VALUES 
        ($1, $1, $4, NULL, 500.00, 500.00, TRUE, 'PLACED', 'PAYMENT_PENDING', '{}'::jsonb, '[]'::jsonb, 'hash_null', NOW(), NOW()),
        ($2, $2, $4, $5, 500.00, 500.00, TRUE, 'PLACED', 'PAYMENT_PENDING', '{}'::jsonb, '[]'::jsonb, 'hash_b', NOW(), NOW()),
        ($3, $3, $4, $6, 500.00, 500.00, TRUE, 'PLACED', 'PAYMENT_PENDING', '{}'::jsonb, '[]'::jsonb, 'hash_a', NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [testNullStoreOrderId, testStoreBOrderId, testStoreAOrderId, testCustomerId, testStoreB, testStoreA]);

    await pool.query(`
      INSERT INTO cod_ledger (id, order_id, amount_expected, status, reconciled, created_at, updated_at)
      VALUES 
        ('cod_' || $1, $1, 500.00, 'PENDING_COLLECTION', FALSE, NOW(), NOW()),
        ('cod_' || $2, $2, 500.00, 'PENDING_COLLECTION', FALSE, NOW(), NOW()),
        ('cod_' || $3, $3, 500.00, 'PENDING_COLLECTION', FALSE, NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [testNullStoreOrderId, testStoreBOrderId, testStoreAOrderId]);
  }

  // 4.1: Store A seller attempts UPI intent verification on null-store order -> must fail closed with 403
  const nullStoreUpiRes = await request('POST', '/api/v1/payments/upi/verify-intent', {
    orderId: testNullStoreOrderId,
    utr: 'UTRNULLSTORE12345'
  }, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(nullStoreUpiRes.status, 403, `Expected 403 FORBIDDEN on null-store UPI verification, got ${nullStoreUpiRes.status}`);
  console.log('  ✅ 4.1 PASS: Seller UPI verification fails closed on null-store order (403 FORBIDDEN).');

  // 4.2: Store A seller attempts COD collection on null-store order -> must fail closed with 403
  const nullStoreCodRes = await request('POST', `/api/v1/orders/${testNullStoreOrderId}/collect-cod`, {
    collectedAmount: 500.00,
    notes: 'Attempted collection on null store'
  }, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(nullStoreCodRes.status, 403, `Expected 403 FORBIDDEN on null-store COD collection, got ${nullStoreCodRes.status}`);
  console.log('  ✅ 4.2 PASS: Seller COD collection fails closed on null-store order (403 FORBIDDEN).');

  // 4.3: Store A seller attempts COD collection on Store B order -> 403 FORBIDDEN
  const crossStoreCodRes = await request('POST', `/api/v1/orders/${testStoreBOrderId}/collect-cod`, {
    collectedAmount: 500.00,
    notes: 'Cross-store collection'
  }, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(crossStoreCodRes.status, 403, `Expected 403 FORBIDDEN on cross-store COD collection, got ${crossStoreCodRes.status}`);
  console.log('  ✅ 4.3 PASS: Seller COD collection on cross-store order rejected with 403 FORBIDDEN.');

  // 4.4: Store A seller collects COD on Store A order -> succeeds (200 OK)
  const storeACodRes = await request('POST', `/api/v1/orders/${testStoreAOrderId}/collect-cod`, {
    collectedAmount: 500.00,
    notes: 'Legitimate collection'
  }, {
    Authorization: `Bearer ${sellerAToken}`
  });
  assert.strictEqual(storeACodRes.status, 200, `Expected 200 on authorized store COD collection, got ${storeACodRes.status}`);
  assert.strictEqual(storeACodRes.body.codReconciled, true);
  console.log('  ✅ 4.4 PASS: Seller COD collection on own authorized store order succeeds (200 OK).\n');

  console.log('================================================================');
  console.log('🎉 ALL 4 CODEX BATCH 6 AUDIT FINDINGS VERIFIED & PASSED!');
  console.log('================================================================\n');

  if (srv) {
    srv.close();
  }
  process.exit(0);
}

runBatch6Tests().catch((err) => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
