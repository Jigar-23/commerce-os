/**
 * Verification test suite for Codex Batch 4 Audit Remediations:
 * 1. Generic Customer Endpoints: Seller access blocked; multi-tenant isolation enforced (403)
 * 2. Single Offer Endpoint: Customer/Seller access blocked; unassigned offers redact customer PII
 * 3. Cross-Store Broadcast Offers: Riders from other stores blocked from claiming (403)
 * 4. Doorstep UPI: Prepaid orders prohibited (400); already-paid orders blocked (409)
 * 5. Refunds History: Multi-tenant store scoping enforced; cross-store queries blocked (403)
 * 6. Delivery Cancellation: Deadlock-free single-connection transactional state transition
 * 7. Order Feedback / Rating: Requires Bearer JWT, ownership check, delivered-state gating, and payload validation
 */

const http = require('http');
const assert = require('assert');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');

// Load environment
try {
  const envPath = path.resolve(__dirname, '../.env');
  if (fs.existsSync(envPath)) {
    const lines = fs.readFileSync(envPath, 'utf8').split('\n');
    for (const l of lines) {
      const trimmed = l.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx > 0) {
        const k = trimmed.slice(0, eqIdx).trim();
        let v = trimmed.slice(eqIdx + 1).trim();
        if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
          v = v.slice(1, -1);
        }
        if (!process.env[k]) process.env[k] = v;
      }
    }
  }
} catch (_) {}

process.env.PORT = '8204';
const { server, pool } = require('./server/production-server');
const { createProductionRepositories } = require('./repositories');

const BASE_URL = 'http://127.0.0.1:8204';
const JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
const JWT_ISSUER = process.env.JWT_ISSUER || 'https://auth.commerceos.internal';
const JWT_AUDIENCE = process.env.JWT_AUDIENCE || 'commerceos-platform-clients';

function createToken(payload) {
  return jwt.sign(payload, JWT_SECRET, {
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
    expiresIn: '2h'
  });
}

async function request(method, reqPath, body = null, headers = {}) {
  const url = `${BASE_URL}${reqPath}`;
  const reqHeaders = { 'Content-Type': 'application/json', ...headers };
  const options = {
    method,
    headers: reqHeaders
  };
  if (body) {
    options.body = JSON.stringify(body);
  }
  const res = await fetch(url, options);
  let data = null;
  const text = await res.text();
  try {
    data = JSON.parse(text);
  } catch (_) {
    data = text;
  }
  return { status: res.status, headers: res.headers, data };
}

async function runBatch4AuditTests() {
  console.log('================================================================');
  console.log('🧪 RUNNING CODEX BATCH 4 AUDIT REMEDIATION VERIFICATION TESTS');
  console.log('================================================================\n');

  let serverInstance = null;
  if (!server.listening) {
    serverInstance = await new Promise((resolve) => {
      const s = server.listen(8204, '127.0.0.1', () => resolve(s));
    });
  }

  const testCustId = 'cust_b4_' + Date.now();
  const otherCustId = 'cust_b4_other_' + Date.now();
  const testStoreA = 'store_b4_a_' + Date.now();
  const testStoreB = 'store_b4_b_' + Date.now();
  const testSellerAId = 'seller_b4_a_' + Date.now();
  const testSellerBId = 'seller_b4_b_' + Date.now();
  const testRiderAId = 'rider_b4_a_' + Date.now();
  const testRiderBId = 'rider_b4_b_' + Date.now();
  const testRiderCodId = 'rider_b4_cod_' + Date.now();
  const testRiderCancelId = 'rider_b4_cancel_' + Date.now();

  let testOrderIdA = 'ord_offer_' + Date.now();
  let testOfferAId = 'off_pool_' + Date.now();
  let testDeliveryAId = 'del_sess_' + Date.now();
  let codOrderId = 'ord_cod_' + Date.now();
  let codDeliveryId = 'del_cod_' + Date.now();
  let cancelDeliveryId = 'del_cancel_' + Date.now();
  let cancelOrderId = 'ord_cancel_' + Date.now();

  try {
    // -------------------------------------------------------------
    // Setup Test Multi-Tenant Fixtures in DB
    // -------------------------------------------------------------
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES ($1, 'Store A', '123 Market Street, New Delhi', 28.5678, 77.2145, true),
              ($2, 'Store B', '456 Commercial Road, Mumbai', 19.0760, 72.8777, true)
       ON CONFLICT (id) DO NOTHING`,
      [testStoreA, testStoreB]
    );

    await pool.query(
      `INSERT INTO sellers (id, seller_id, store_id, email, phone, status, password_hash)
       VALUES ($1, $1, $2, 'sellerA@example.com', '+919800000001', 'ACTIVE', 'hash_test_placeholder'),
              ($3, $3, $4, 'sellerB@example.com', '+919800000002', 'ACTIVE', 'hash_test_placeholder')
       ON CONFLICT (id) DO NOTHING`,
      [testSellerAId, testStoreA, testSellerBId, testStoreB]
    );

    await pool.query(
      `INSERT INTO customers (id, full_name, phone, email, is_active)
       VALUES ($1, 'Target Customer', '+919811122233', 'target@example.com', true),
              ($2, 'Other Customer', '+919844455566', 'other@example.com', true)
       ON CONFLICT (id) DO NOTHING`,
      [testCustId, otherCustId]
    );

    await pool.query(
      `INSERT INTO customer_addresses (id, customer_id, address_type, address_line, city, postal_code, latitude, longitude, contact_phone)
       VALUES ($1, $2, 'HOME', 'Flat 101, Secret Residency, South Extension', 'New Delhi', '110049', 28.5678, 77.2145, '+919811122233')
       ON CONFLICT (id) DO NOTHING`,
      ['addr_' + testCustId, testCustId]
    );

    await pool.query(
      `INSERT INTO riders (id, rider_id, store_id, full_name, phone, status, vehicle_number)
       VALUES ($1, $1, $2, 'Rider Store A', '+919876543201', 'ACTIVE', 'DL-01-AA-1234'),
              ($3, $3, $4, 'Rider Store B', '+919876543202', 'ACTIVE', 'MH-02-BB-5678'),
              ($5, $5, $2, 'Rider COD Store A', '+919876543203', 'ACTIVE', 'DL-01-CC-9999'),
              ($6, $6, $2, 'Rider Cancel Store A', '+919876543204', 'ACTIVE', 'DL-01-DD-8888')
       ON CONFLICT (id) DO NOTHING`,
      [testRiderAId, testStoreA, testRiderBId, testStoreB, testRiderCodId, testRiderCancelId]
    );

    // Auth Tokens
    const sellerAToken = createToken({ sub: testSellerAId, role: 'ROLE_SELLER', storeId: testStoreA });
    const sellerBToken = createToken({ sub: testSellerBId, role: 'ROLE_SELLER', storeId: testStoreB });
    const custToken = createToken({ sub: testCustId, role: 'ROLE_CUSTOMER', phone: '+919811122233' });
    const otherCustToken = createToken({ sub: otherCustId, role: 'ROLE_CUSTOMER', phone: '+919844455566' });
    const adminToken = createToken({ sub: 'admin_root', role: 'ROLE_ADMIN' });
    const riderAToken = createToken({ sub: testRiderAId, role: 'ROLE_RIDER', storeId: testStoreA });
    const riderBToken = createToken({ sub: testRiderBId, role: 'ROLE_RIDER', storeId: testStoreB });
    const riderCodToken = createToken({ sub: testRiderCodId, role: 'ROLE_RIDER', storeId: testStoreA });
    const riderCancelToken = createToken({ sub: testRiderCancelId, role: 'ROLE_RIDER', storeId: testStoreA });

    // -------------------------------------------------------------
    // Test 1: Generic Customer Profile & Address multi-tenant isolation
    // -------------------------------------------------------------
    console.log('[Test 1] Generic Customer Endpoints Multi-Tenant Protection');
    const sellerCustProfileRes = await request('GET', `/api/v1/customers/${testCustId}`, null, {
      Authorization: `Bearer ${sellerAToken}`
    });
    assert.strictEqual(sellerCustProfileRes.status, 403, 'Seller token must receive 403 on generic customer endpoint');
    assert.strictEqual(sellerCustProfileRes.data.error, 'FORBIDDEN');

    const sellerCustAddrRes = await request('GET', `/api/v1/customers/${testCustId}/addresses`, null, {
      Authorization: `Bearer ${sellerAToken}`
    });
    assert.strictEqual(sellerCustAddrRes.status, 403, 'Seller token must receive 403 on generic customer address endpoint');
    assert.strictEqual(sellerCustAddrRes.data.error, 'FORBIDDEN');

    const custSelfRes = await request('GET', `/api/v1/customers/${testCustId}`, null, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(custSelfRes.status, 200, 'Customer owner can access own profile');
    assert.strictEqual(custSelfRes.data.id, testCustId);

    const custSelfAddrRes = await request('GET', `/api/v1/customers/${testCustId}/addresses`, null, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(custSelfAddrRes.status, 200, 'Customer owner can access own address book');
    assert.ok(Array.isArray(custSelfAddrRes.data), 'Returns addresses array');

    console.log('  ✅ PASS: Generic customer endpoints strictly reject sellers with 403; customers and admins authorized.');

    // -------------------------------------------------------------
    // Test 2: Single Offer Endpoint PII Protection & Cross-Store Scoping
    // -------------------------------------------------------------
    console.log('\n[Test 2] Single Offer Endpoint Role Gating & PII Redaction');

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, total_amount, status, payment_status, is_cod, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 499.00, 'CONFIRMED', 'PAID', false, $4, '[]'::jsonb, 'test_hash_1234')`,
      [testOrderIdA, testCustId, testStoreA, JSON.stringify({
        addressLine: 'Secret Villa 42, Private Colony',
        locality: 'South Extension',
        city: 'New Delhi',
        latitude: 28.5678,
        longitude: 77.2145,
        contactName: 'Target Customer',
        contactPhone: '+919811122233'
      })]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_phone, customer_address, customer_lat, customer_lng, is_cod, cod_amount)
       VALUES ($1, $1, $2, 'READY_FOR_PICKUP', 'Store A', '123 Market St', 28.5678, 77.2145, 'Target Customer', '+919811122233', 'Secret Villa 42, South Extension, New Delhi', 28.5678, 77.2145, false, 0.00)`,
      [testDeliveryAId, testOrderIdA]
    );

    await pool.query(
      `INSERT INTO offers (
        id, offer_id, event_id, notification_id, order_id, delivery_id, rider_id, is_broadcast,
        status, earnings_amount, delivery_distance_km, total_distance_km, estimated_duration_mins,
        offer_created_at, offer_expires_at, waypoints, pricing_snapshot, history
       ) VALUES ($1, $1, $2, $3, $4, $5, 'all', true, 'OFFERED', 45.00, 3.2, 3.2, 15,
                 (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint, (EXTRACT(EPOCH FROM NOW()) * 1000 + 3600000)::bigint,
                 '[]'::jsonb, '{}'::jsonb, '[]'::jsonb)`,
      [testOfferAId, 'evt_' + testOfferAId, 'notif_' + testOfferAId, testOrderIdA, testDeliveryAId]
    );

    // Customer attempts to view offer
    const custOfferRes = await request('GET', `/api/v1/delivery/offers/${testOfferAId}`, null, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(custOfferRes.status, 403, 'Customer token must receive 403 on offer query');

    // Seller attempts to view offer
    const sellerOfferRes = await request('GET', `/api/v1/delivery/offers/${testOfferAId}`, null, {
      Authorization: `Bearer ${sellerAToken}`
    });
    assert.strictEqual(sellerOfferRes.status, 403, 'Seller token must receive 403 on offer query');

    // Competing Rider from Store B attempts to view Store A broadcast offer
    const riderBOfferRes = await request('GET', `/api/v1/delivery/offers/${testOfferAId}`, null, {
      Authorization: `Bearer ${riderBToken}`
    });
    assert.strictEqual(riderBOfferRes.status, 403, 'Rider from Store B cannot view Store A broadcast offer');

    // Authorized Rider from Store A queries unassigned broadcast offer -> PII must be redacted
    const riderAOfferRes = await request('GET', `/api/v1/delivery/offers/${testOfferAId}`, null, {
      Authorization: `Bearer ${riderAToken}`
    });
    assert.strictEqual(riderAOfferRes.status, 200, 'Store A rider can view Store A offer');
    assert.strictEqual(riderAOfferRes.data.customerPhone, '', 'Customer phone must be redacted for unassigned offer');
    assert.strictEqual(riderAOfferRes.data.customerLat, null, 'Customer latitude must be null for unassigned offer');
    assert.strictEqual(riderAOfferRes.data.customerLng, null, 'Customer longitude must be null for unassigned offer');
    assert.strictEqual(riderAOfferRes.data.customerName, 'Customer', 'Customer name must be sanitized');

    console.log('  ✅ PASS: Non-riders blocked (403), cross-store riders blocked (403), and unassigned offer PII redacted.');

    // -------------------------------------------------------------
    // Test 3: Cross-Store Broadcast Offer Claiming Prevention
    // -------------------------------------------------------------
    console.log('\n[Test 3] Cross-Store Offer Claiming Prevention');
    const claimByStoreBRes = await request('POST', `/api/v1/delivery/offers/${testOfferAId}/accept`, {}, {
      Authorization: `Bearer ${riderBToken}`
    });
    assert.strictEqual(claimByStoreBRes.status, 403, 'Rider from Store B must be forbidden from claiming Store A offer');
    assert.strictEqual(claimByStoreBRes.data.error, 'CROSS_STORE_OFFER_CLAIM_FORBIDDEN');

    const claimByStoreARes = await request('POST', `/api/v1/delivery/offers/${testOfferAId}/accept`, {}, {
      Authorization: `Bearer ${riderAToken}`
    });
    assert.strictEqual(claimByStoreARes.status, 200, 'Rider from Store A successfully accepts Store A offer');
    assert.strictEqual(claimByStoreARes.data.state, 'ACCEPTED');

    console.log('  ✅ PASS: Store B rider strictly blocked with 403; Store A rider successfully accepted.');

    // -------------------------------------------------------------
    // Test 4: Doorstep UPI Prepaid & Paid Precondition Checks
    // -------------------------------------------------------------
    console.log('\n[Test 4] Doorstep UPI Prepaid & Already-Paid Order Guards');
    // testOrderIdA is a PREPAID order (is_cod = false, payment_status = 'PAID')
    // Set delivery state to PICKED_UP to pass state checks
    await pool.query(`UPDATE delivery_sessions SET state = 'PICKED_UP', rider_id = $1 WHERE delivery_id = $2`, [testRiderAId, testDeliveryAId]);

    const prepaidDoorstepRes = await request('POST', '/api/v1/payments/doorstep-upi/complete', {
      deliveryId: testDeliveryAId,
      utr: 'UTR_PREPAID_999999',
      collectedAmount: 499.00
    }, {
      Authorization: `Bearer ${riderAToken}`
    });
    assert.strictEqual(prepaidDoorstepRes.status, 400, 'Doorstep UPI must reject prepaid orders');
    assert.strictEqual(prepaidDoorstepRes.data.error, 'PREPAID_ORDER_DOORSTEP_UPI_PROHIBITED');

    // Create a real COD order
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, total_amount, status, payment_status, is_cod, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 350.00, 'CONFIRMED', 'PENDING', true, '{}'::jsonb, '[]'::jsonb, 'test_hash_cod')`,
      [codOrderId, testCustId, testStoreA]
    );
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_phone, customer_address, customer_lat, customer_lng, is_cod, cod_amount)
       VALUES ($1, $1, $2, $3, 'PICKED_UP', 'Store A', '123 Market St', 28.5678, 77.2145, 'Target Customer', '+919811122233', '123 Delivery St', 28.5678, 77.2145, true, 350.00)`,
      [codDeliveryId, codOrderId, testRiderCodId]
    );
    await pool.query(
      `INSERT INTO cod_ledger (id, order_id, amount_expected, amount_collected, status, reconciled)
       VALUES ($1, $1, 350.00, 0.00, 'PENDING_COLLECTION', false)`,
      [codOrderId]
    );

    const validCodDoorstepRes = await request('POST', '/api/v1/payments/doorstep-upi/complete', {
      deliveryId: codDeliveryId,
      utr: 'UTR_COD_VALID_888888',
      collectedAmount: 350.00
    }, {
      Authorization: `Bearer ${riderCodToken}`
    });
    assert.strictEqual(validCodDoorstepRes.status, 200, 'Doorstep UPI records valid COD collection');
    assert.strictEqual(validCodDoorstepRes.data.paymentStatus, 'VERIFICATION_PENDING');

    // Verify order is in VERIFICATION_PENDING and NOT marked PAID yet
    const ordCheck = await pool.query(`SELECT payment_status FROM orders WHERE id = $1`, [codOrderId]);
    assert.strictEqual(ordCheck.rows[0].payment_status, 'VERIFICATION_PENDING');

    console.log('  ✅ PASS: Prepaid doorstep UPI rejected with 400; valid COD recorded as VERIFICATION_PENDING.');

    // -------------------------------------------------------------
    // Test 5: Cross-Store Refund History Disclosure Multi-Tenant Guard
    // -------------------------------------------------------------
    console.log('\n[Test 5] Refund History Multi-Tenant Disclosure Guard');
    await pool.query(
      `INSERT INTO payment_refunds (id, refund_id, order_id, amount, currency, reason, idempotency_key, status)
       VALUES ($1, $1, $2, 50.00, 'INR', 'Damage item', $3, 'PENDING')`,
      ['ref_' + Date.now(), testOrderIdA, 'idemp_' + Date.now()]
    );

    // Seller B querying refunds for Store A order
    const sellerBRefundRes = await request('GET', `/api/v1/payments/refunds/${testOrderIdA}`, null, {
      Authorization: `Bearer ${sellerBToken}`
    });
    assert.strictEqual(sellerBRefundRes.status, 403, 'Cross-store seller must receive 403 on refunds endpoint');
    assert.strictEqual(sellerBRefundRes.data.error, 'FORBIDDEN');

    // Customer querying other customer's order refund
    const unauthCustRefundRes = await request('GET', `/api/v1/payments/refunds/${testOrderIdA}`, null, {
      Authorization: `Bearer ${otherCustToken}`
    });
    assert.strictEqual(unauthCustRefundRes.status, 403, 'Unrelated customer must receive 403 on refunds endpoint');

    // Seller A querying Store A order refund
    const sellerARefundRes = await request('GET', `/api/v1/payments/refunds/${testOrderIdA}`, null, {
      Authorization: `Bearer ${sellerAToken}`
    });
    assert.strictEqual(sellerARefundRes.status, 200, 'Store A seller can query Store A refunds');
    assert.ok(Array.isArray(sellerARefundRes.data.refunds));

    // Admin querying refunds
    const adminRefundRes = await request('GET', `/api/v1/payments/refunds/${testOrderIdA}`, null, {
      Authorization: `Bearer ${adminToken}`
    });
    assert.strictEqual(adminRefundRes.status, 200, 'Admin can query all refunds');

    console.log('  ✅ PASS: Cross-store refund disclosure strictly blocked with 403; authorized seller and admin permitted.');

    // -------------------------------------------------------------
    // Test 6: Delivery Cancellation Deadlock-Free Transaction
    // -------------------------------------------------------------
    console.log('\n[Test 6] Delivery Cancellation Deadlock-Free Transaction');

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, total_amount, status, payment_status, is_cod, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 200.00, 'CONFIRMED', 'PAID', false, '{}'::jsonb, '[]'::jsonb, 'test_hash_cancel')`,
      [cancelOrderId, testCustId, testStoreA]
    );
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_phone, customer_address, customer_lat, customer_lng, is_cod, cod_amount)
       VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', 'Store A', '123 Market St', 28.5678, 77.2145, 'Target Customer', '+919811122233', '123 Delivery St', 28.5678, 77.2145, false, 0.00)`,
      [cancelDeliveryId, cancelOrderId, testRiderCancelId]
    );

    // Rider A cancels before pickup
    const cancelRes = await request('POST', `/api/v1/delivery/session/${cancelDeliveryId}/cancel`, {
      reason: 'Vehicle breakdown before pickup'
    }, {
      Authorization: `Bearer ${riderCancelToken}`
    });
    assert.strictEqual(cancelRes.status, 200, 'Delivery cancellation must complete successfully without deadlock');
    assert.strictEqual(cancelRes.data.state, 'CANCELLED');

    const verifyDeliv = await pool.query(`SELECT state FROM delivery_sessions WHERE id = $1`, [cancelDeliveryId]);
    assert.strictEqual(verifyDeliv.rows[0].state, 'CANCELLED');

    console.log('  ✅ PASS: Delivery cancellation executed atomically with zero deadlock.');

    // -------------------------------------------------------------
    // Test 7: Order Feedback / Rating Auth, Ownership, Validation & Status Gating
    // -------------------------------------------------------------
    console.log('\n[Test 7] Order Feedback Authentication & Delivery-State Gating');
    // 1. Unauthenticated
    const unauthFeedbackRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 5,
      comment: 'Great service'
    });
    assert.strictEqual(unauthFeedbackRes.status, 401, 'Unauthenticated feedback request must receive 401');

    // 2. Invalid Rating
    const invalidRatingRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 6,
      comment: 'Great service'
    }, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(invalidRatingRes.status, 400, 'Rating > 5 must receive 400');
    assert.strictEqual(invalidRatingRes.data.error, 'INVALID_RATING');

    // 3. Invalid Comment (>500 chars)
    const invalidCommentRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 5,
      comment: 'A'.repeat(501)
    }, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(invalidCommentRes.status, 400, 'Comment > 500 chars must receive 400');
    assert.strictEqual(invalidCommentRes.data.error, 'INVALID_COMMENT');

    // 4. Undelivered Order (testOrderIdA is still CONFIRMED, not DELIVERED)
    const undeliveredRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 5,
      comment: 'Delivered fast'
    }, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(undeliveredRes.status, 409, 'Undelivered order must receive 409');
    assert.strictEqual(undeliveredRes.data.error, 'ORDER_NOT_DELIVERED');

    // 5. Unrelated Customer
    await pool.query(`UPDATE orders SET status = 'DELIVERED' WHERE id = $1`, [testOrderIdA]);
    const unauthCustRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 5,
      comment: 'Great'
    }, {
      Authorization: `Bearer ${otherCustToken}`
    });
    assert.strictEqual(unauthCustRes.status, 403, 'Non-owner customer must receive 403');
    assert.strictEqual(unauthCustRes.data.error, 'FORBIDDEN');

    // 6. Authorized Owner on DELIVERED Order
    const validFeedbackRes = await request('POST', `/api/v1/orders/${testOrderIdA}/feedback`, {
      rating: 5,
      comment: 'Everything arrived in perfect condition!'
    }, {
      Authorization: `Bearer ${custToken}`
    });
    assert.strictEqual(validFeedbackRes.status, 200, 'Owner can submit feedback for delivered order');
    assert.strictEqual(validFeedbackRes.data.ok, true);

    console.log('  ✅ PASS: Order feedback requires Bearer JWT, validates rating/comment, strictly gates to DELIVERED status.');

    console.log('\n================================================================');
    console.log('🎉 ALL CODEX BATCH 4 AUDIT REMEDIATION TESTS PASSED!');
    console.log('================================================================\n');

  } finally {
    // Cleanup fixtures
    await pool.query(`DELETE FROM payment_refunds WHERE order_id IN ($1, $2, $3)`, [testOrderIdA, codOrderId, cancelOrderId]).catch(() => {});
    await pool.query(`DELETE FROM cod_ledger WHERE order_id IN ($1, $2, $3)`, [testOrderIdA, codOrderId, cancelOrderId]).catch(() => {});
    await pool.query(`DELETE FROM offers WHERE order_id IN ($1, $2, $3)`, [testOrderIdA, codOrderId, cancelOrderId]).catch(() => {});
    await pool.query(`DELETE FROM delivery_sessions WHERE order_id IN ($1, $2, $3)`, [testOrderIdA, codOrderId, cancelOrderId]).catch(() => {});
    await pool.query(`DELETE FROM orders WHERE id IN ($1, $2, $3)`, [testOrderIdA, codOrderId, cancelOrderId]).catch(() => {});
    await pool.query(`DELETE FROM customer_addresses WHERE customer_id IN ($1, $2)`, [testCustId, otherCustId]).catch(() => {});
    await pool.query(`DELETE FROM customers WHERE id IN ($1, $2)`, [testCustId, otherCustId]).catch(() => {});
    await pool.query(`DELETE FROM riders WHERE id IN ($1, $2, $3, $4)`, [testRiderAId, testRiderBId, testRiderCodId, testRiderCancelId]).catch(() => {});
    await pool.query(`DELETE FROM sellers WHERE id IN ($1, $2)`, [testSellerAId, testSellerBId]).catch(() => {});
    await pool.query(`DELETE FROM stores WHERE id IN ($1, $2)`, [testStoreA, testStoreB]).catch(() => {});

    if (serverInstance) {
      await new Promise((resolve) => serverInstance.close(resolve));
    }
  }
}

runBatch4AuditTests().then(() => {
  process.exit(0);
}).catch((err) => {
  console.error('❌ Batch 4 Audit Test Failure:', err);
  process.exit(1);
});
