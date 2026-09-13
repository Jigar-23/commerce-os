#!/usr/bin/env node

/**
 * Commerce OS — Live Native iOS Backend & Header Parity Verification Suite
 * 
 * Validates:
 * 1. Active backend routes matching 100% of APIEndpoint.swift and RiderEndpoint.swift
 * 2. Native iOS Client Header Support:
 *    - X-Client-Platform: iOS
 *    - X-Client-Platform: iOS-Rider
 *    - X-Client-Version
 *    - CORS preflight OPTIONS handling
 * 3. Real PostgreSQL 16+ execution with Zero Mocks
 * 4. End-to-End HTTP requests with validated payloads and responses
 */

const assert = require('assert');
const crypto = require('crypto');
const path = require('path');

// Ensure production environment variables
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@localhost:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8195';

const { server, pool, getAppRepositories } = require('./server/production-server');
const { DeliveryOtpService, TransactionalSellerRepository } = require('./repositories');

const PORT = 8195;
const BASE_URL = `http://127.0.0.1:${PORT}`;

let serverInstance = null;
let passedCount = 0;
let totalCount = 0;
const testResults = [];

function recordResult(name, method, path, status, ok, details = '') {
  totalCount++;
  if (ok) passedCount++;
  testResults.push({ name, method, path, status, ok, details });
  const statusStr = ok ? '\x1b[32m✔ PASS\x1b[0m' : '\x1b[31m✘ FAIL\x1b[0m';
  console.log(` ${statusStr} [${method}] ${path} -> ${status} | ${name} ${details ? `(${details})` : ''}`);
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
  console.log('\x1b[1m\x1b[36m======================================================================\x1b[0m');
  console.log('\x1b[1m\x1b[36m  COMMERCE OS — NATIVE iOS BACKEND LIVE INTEGRATION TEST SUITE        \x1b[0m');
  console.log('\x1b[1m\x1b[36m======================================================================\x1b[0m\n');

  // 1. Boot Server
  await new Promise((resolve, reject) => {
    serverInstance = server.listen(PORT, '127.0.0.1', (err) => {
      if (err) return reject(err);
      console.log(`🚀 Live backend listening on ${BASE_URL} (PostgreSQL active)\n`);
      resolve();
    });
  });

  // 2. Database Fixtures Setup
  await pool.query('DELETE FROM delivery_sessions');
  await pool.query('DELETE FROM orders');
  await pool.query('DELETE FROM carts');
  await pool.query('DELETE FROM customer_addresses');
  await pool.query('DELETE FROM auth_challenges');
  await pool.query('DELETE FROM rider_presence');
  await pool.query('DELETE FROM rider_device_tokens');

  const storeId = 'store_ios_test_01';
  await pool.query(
    `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, seller_approval_required, is_active)
     VALUES ($1, 'iOS Test Flagship Store', 'Connaught Place, New Delhi', 28.6315, 77.2167, 10, FALSE, TRUE)
     ON CONFLICT (id) DO UPDATE SET is_active = TRUE, seller_approval_required = FALSE`,
    [storeId]
  );

  const sku = 'SKU_IOS_LIVE_01';
  const prodId = 'prod_ios_live_01';
  await pool.query(
    `INSERT INTO products (id, sku, name, category, mrp, price, rx_requirement, is_active)
     VALUES ($1, $2, 'Vital Health Tonic 200ml', 'Medicines', 150.0, 120.0, 'OTC', TRUE)
     ON CONFLICT (id) DO UPDATE SET is_active = TRUE, price = 120.0`,
    [prodId, sku]
  );

  await pool.query(
    `INSERT INTO inventory (id, store_id, product_id, sku, stock_count, reserved_count)
     VALUES ('inv_ios_01', $1, $2, $3, 50, 0)
     ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = 50, reserved_count = 0`,
    [storeId, prodId, sku]
  );

  const sellerPasswordHash = TransactionalSellerRepository.hashPassword('test_sec_881');
  await pool.query(
    `INSERT INTO sellers (id, seller_id, email, phone, password_hash, store_id, store_name, merchant_name, status, is_primary, roles)
     VALUES ('sel_ios_01', 'sel_ios_01', 'seller_ios@commerceos.io', '9876543219', $1, $2, 'iOS Test Flagship Store', 'Commerce OS Flagship Ltd', 'ACTIVE', TRUE, '["ROLE_SELLER"]'::jsonb)
     ON CONFLICT (id) DO UPDATE SET password_hash = $1, status = 'ACTIVE', store_id = $2, is_primary = TRUE`,
    [sellerPasswordHash, storeId]
  );

  const riderPhone = '9811223344';
  const riderId = 'rdr_9811223344';
  await pool.query(
    `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, tier)
     VALUES ($1, $1, $2, 'Vikram iOS Rider', 'DL-01-XY-9999', 'TWO_WHEELER', 'ACTIVE', 'GOLD')
     ON CONFLICT (id) DO UPDATE SET phone = $2, status = 'ACTIVE'`,
    [riderId, `+91${riderPhone}`]
  );

  try {
    // =========================================================================
    // SECTION 1: CORS & Native Header Verification
    // =========================================================================
    console.log('\x1b[1m\x1b[33m--- Section 1: iOS Header & Preflight Verification ---\x1b[0m');

    // Test OPTIONS Preflight
    const optRes = await apiRequest('OPTIONS', '/api/v1/catalog/home-feed', null, {
      'Origin': 'http://localhost:3000',
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'X-Client-Platform, X-Client-Version, Content-Type, Authorization'
    });
    const allowHeaders = optRes.headers.get('access-control-allow-headers') || '';
    const corsPass = optRes.status === 204 && allowHeaders.includes('X-Client-Platform');
    recordResult('CORS Preflight with X-Client-Platform header allowlist', 'OPTIONS', '/api/v1/catalog/home-feed', optRes.status, corsPass);

    // Test X-Client-Platform Echo for iOS
    const echoIosRes = await apiRequest('GET', '/api/v1/catalog/home-feed', null, {
      'X-Client-Platform': 'iOS',
      'X-Client-Version': '2.1.0'
    });
    const echoIosPass = echoIosRes.status === 200 && echoIosRes.headers.get('x-client-platform') === 'iOS';
    recordResult('X-Client-Platform: iOS response header reflection', 'GET', '/api/v1/catalog/home-feed', echoIosRes.status, echoIosPass);

    // Test X-Client-Platform Echo for iOS-Rider
    const echoRiderRes = await apiRequest('GET', '/health', null, {
      'X-Client-Platform': 'iOS-Rider',
      'X-Client-Version': '1.0.0'
    });
    const echoRiderPass = echoRiderRes.status === 200 && echoRiderRes.headers.get('x-client-platform') === 'iOS-Rider';
    recordResult('X-Client-Platform: iOS-Rider response header reflection', 'GET', '/health', echoRiderRes.status, echoRiderPass);

    // =========================================================================
    // SECTION 2: Customer Endpoints (APIEndpoint.swift)
    // =========================================================================
    console.log('\n\x1b[1m\x1b[33m--- Section 2: Customer API (APIEndpoint.swift) ---\x1b[0m');

    const customerPhone = '9876501234';
    let customerChallengeId = '';
    let customerToken = '';
    let customerId = `cust_${customerPhone}`;
    let placedOrderOtp = '';

    // 1. sendCustomerOtp
    const custOtpSend = await apiRequest('POST', '/api/v1/auth/customer/otp/send', { phone: customerPhone }, {
      'X-Client-Platform': 'iOS'
    });
    const custOtpSendOk = custOtpSend.status === 200 && Boolean(custOtpSend.data?.challengeId);
    customerChallengeId = custOtpSend.data?.challengeId || '';
    recordResult('POST api/v1/auth/customer/otp/send', 'POST', '/api/v1/auth/customer/otp/send', custOtpSend.status, custOtpSendOk);

    // 2. verifyCustomerOtp (using master OTP 123456)
    const custOtpVerify = await apiRequest('POST', '/api/v1/auth/customer/otp/verify', {
      challengeId: customerChallengeId,
      phone: customerPhone,
      otp: '123456',
      fullName: 'Ananya Sharma'
    }, { 'X-Client-Platform': 'iOS' });
    const custOtpVerifyOk = custOtpVerify.status === 200 && Boolean(custOtpVerify.data?.accessToken);
    customerToken = custOtpVerify.data?.accessToken || '';
    recordResult('POST api/v1/auth/customer/otp/verify', 'POST', '/api/v1/auth/customer/otp/verify', custOtpVerify.status, custOtpVerifyOk);

    // 3. verifyCustomerOtp Alias: /api/v1/auth/customer/verify-otp
    const custSend2 = await apiRequest('POST', '/api/v1/auth/customer/otp/send', { phone: '9876509999' }, { 'X-Client-Platform': 'iOS' });
    const aliasRes = await apiRequest('POST', '/api/v1/auth/customer/verify-otp', {
      challengeId: custSend2.data?.challengeId,
      phone: '9876509999',
      otp: '123456'
    }, { 'X-Client-Platform': 'iOS' });
    recordResult('POST api/v1/auth/customer/verify-otp (APIClient alias)', 'POST', '/api/v1/auth/customer/verify-otp', aliasRes.status, aliasRes.status === 200);

    const authHeaders = {
      'Authorization': `Bearer ${customerToken}`,
      'X-Client-Platform': 'iOS',
      'X-Client-Version': '2.1.0'
    };

    // 4. homeFeed
    const homeRes = await apiRequest('GET', '/api/v1/catalog/home-feed', null, authHeaders);
    recordResult('GET api/v1/catalog/home-feed', 'GET', '/api/v1/catalog/home-feed', homeRes.status, homeRes.status === 200 && Boolean(homeRes.data?.hero || homeRes.data?.verticals || homeRes.data?.products));

    // 5. categories
    const catRes = await apiRequest('GET', '/api/v1/catalog/categories', null, authHeaders);
    recordResult('GET api/v1/catalog/categories', 'GET', '/api/v1/catalog/categories', catRes.status, catRes.status === 200 && Array.isArray(catRes.data));

    // 6. destinations
    const destRes = await apiRequest('GET', '/api/v1/catalog/destinations', null, authHeaders);
    recordResult('GET api/v1/catalog/destinations', 'GET', '/api/v1/catalog/destinations', destRes.status, destRes.status === 200 && Array.isArray(destRes.data));

    // 7. searchMedicines
    const medRes = await apiRequest('GET', '/api/v1/catalog/medicines/search?q=Vital', null, authHeaders);
    recordResult('GET api/v1/catalog/medicines/search', 'GET', '/api/v1/catalog/medicines/search?q=Vital', medRes.status, medRes.status === 200);

    // 8. search
    const searchRes = await apiRequest('GET', '/api/v1/search?q=Tonic', null, authHeaders);
    recordResult('GET api/v1/search', 'GET', '/api/v1/search?q=Tonic', searchRes.status, searchRes.status === 200);

    // 9. autocomplete
    const autoRes = await apiRequest('GET', '/api/v1/search/autocomplete?q=Vit', null, authHeaders);
    recordResult('GET api/v1/search/autocomplete', 'GET', '/api/v1/search/autocomplete?q=Vit', autoRes.status, autoRes.status === 200);

    // 10. createAddress
    const addAddrRes = await apiRequest('POST', `/api/v1/customers/${customerId}/addresses`, {
      addressType: 'HOME',
      addressLine: 'Flat 402, Block B, Connaught Residency',
      city: 'New Delhi',
      postalCode: '110001',
      latitude: 28.6320,
      longitude: 77.2180,
      contactPhone: '9876501234',
      isDefault: true
    }, authHeaders);
    const addAddrOk = (addAddrRes.status === 200 || addAddrRes.status === 201) && Boolean(addAddrRes.data?.id || addAddrRes.data?.addressId);
    const createdAddressId = addAddrRes.data?.id || addAddrRes.data?.addressId || '';
    recordResult('POST api/v1/customers/:customerId/addresses', 'POST', `/api/v1/customers/${customerId}/addresses`, addAddrRes.status, addAddrOk);

    // 11. getAddresses
    const getAddrRes = await apiRequest('GET', `/api/v1/customers/${customerId}/addresses`, null, authHeaders);
    const getAddrOk = getAddrRes.status === 200 && Array.isArray(getAddrRes.data) && getAddrRes.data.length > 0;
    recordResult('GET api/v1/customers/:customerId/addresses', 'GET', `/api/v1/customers/${customerId}/addresses`, getAddrRes.status, getAddrOk);

    // 12. addToCart
    const addCartRes = await apiRequest('POST', `/api/v1/cart/${customerId}/items`, {
      sku: sku,
      quantity: 2,
      price: 120.0
    }, authHeaders);
    recordResult('POST api/v1/cart/:customerId/items', 'POST', `/api/v1/cart/${customerId}/items`, addCartRes.status, addCartRes.status === 200);

    // 13. getCart
    const getCartRes = await apiRequest('GET', `/api/v1/cart/${customerId}`, null, authHeaders);
    const getCartOk = getCartRes.status === 200 && Array.isArray(getCartRes.data?.items || getCartRes.data);
    recordResult('GET api/v1/cart/:customerId', 'GET', `/api/v1/cart/${customerId}`, getCartRes.status, getCartOk);

    // 14. updateCartItem
    const updateCartRes = await apiRequest('PATCH', `/api/v1/cart/${customerId}/items/${sku}`, {
      quantity: 3
    }, authHeaders);
    recordResult('PATCH api/v1/cart/:customerId/items/:sku', 'PATCH', `/api/v1/cart/${customerId}/items/${sku}`, updateCartRes.status, updateCartRes.status === 200);

    // 15. checkServiceability
    const servRes = await apiRequest('POST', '/api/v1/orders/serviceability', {
      latitude: 28.6320,
      longitude: 77.2180
    }, authHeaders);
    recordResult('POST api/v1/orders/serviceability', 'POST', '/api/v1/orders/serviceability', servRes.status, servRes.status === 200 && servRes.data?.serviceable === true);

    // 16. pricingQuote
    const quoteRes = await apiRequest('POST', '/api/v1/pricing/quote', {
      storeId: storeId,
      itemsSubtotal: 240.0,
      items: [{ sku, quantity: 2, price: 120.0 }],
      deliveryLatitude: 28.6320,
      deliveryLongitude: 77.2180,
      isCod: false
    }, authHeaders);
    recordResult('POST api/v1/pricing/quote', 'POST', '/api/v1/pricing/quote', quoteRes.status, quoteRes.status === 200 && (quoteRes.data?.totalAmount != null || quoteRes.data?.total != null));

    // 17. checkoutFromCart
    const checkoutRes = await apiRequest('POST', `/api/v1/orders/checkout-from-cart/${customerId}`, {
      addressId: createdAddressId,
      paymentMethod: 'COD',
      isCod: true
    }, authHeaders);
    const checkoutOk = (checkoutRes.status === 200 || checkoutRes.status === 201) && Boolean(checkoutRes.data?.orderId);
    const placedOrderId = checkoutRes.data?.orderId || '';
    placedOrderOtp = checkoutRes.data?.deliveryOtp || '';
    if (!checkoutOk) console.log('CHECKOUT_ERROR_DATA:', checkoutRes.status, checkoutRes.data);
    recordResult('POST api/v1/orders/checkout-from-cart/:customerId', 'POST', `/api/v1/orders/checkout-from-cart/${customerId}`, checkoutRes.status, checkoutOk);

    // 18. getCustomerOrders
    const custOrdersRes = await apiRequest('GET', `/api/v1/orders/customer/${customerId}`, null, authHeaders);
    const custOrdersOk = custOrdersRes.status === 200 && Array.isArray(custOrdersRes.data) && custOrdersRes.data.length > 0;
    recordResult('GET api/v1/orders/customer/:customerId', 'GET', `/api/v1/orders/customer/${customerId}`, custOrdersRes.status, custOrdersOk);

    // 19. getOrderDetail
    const orderDetailRes = await apiRequest('GET', `/api/v1/orders/${placedOrderId}`, null, authHeaders);
    const orderDetailOk = orderDetailRes.status === 200 && orderDetailRes.data?.id === placedOrderId;
    recordResult('GET api/v1/orders/:orderId', 'GET', `/api/v1/orders/${placedOrderId}`, orderDetailRes.status, orderDetailOk);

    // 20. getActiveDelivery
    const activeDelivRes = await apiRequest('GET', '/api/v1/orders/active-delivery', null, authHeaders);
    // 200 or 404 with well-formed json is valid contract
    const activeDelivOk = (activeDelivRes.status === 200 || activeDelivRes.status === 404);
    recordResult('GET api/v1/orders/active-delivery', 'GET', '/api/v1/orders/active-delivery', activeDelivRes.status, activeDelivOk);

    // 21. realtime ticket
    const ticketRes = await apiRequest('POST', '/api/v1/realtime/ticket', null, authHeaders);
    const ticketOk = ticketRes.status === 200 && Boolean(ticketRes.data?.ticket);
    recordResult('POST api/v1/realtime/ticket', 'POST', '/api/v1/realtime/ticket', ticketRes.status, ticketOk);

    // 22. direct placeOrder
    const directOrderRes = await apiRequest('POST', '/api/v1/orders', {
      customerId: customerId,
      storeId: storeId,
      addressId: createdAddressId,
      items: [{ sku, name: 'Vital Health Tonic 200ml', quantity: 1, price: 120.0 }],
      paymentMethod: 'COD',
      isCod: true
    }, authHeaders);
    const directOrderOk = (directOrderRes.status === 200 || directOrderRes.status === 201) && Boolean(directOrderRes.data?.orderId);
    const directOrderId = directOrderRes.data?.orderId || '';
    recordResult('POST api/v1/orders', 'POST', '/api/v1/orders', directOrderRes.status, directOrderOk);

    // 23. cancelOrder
    const cancelRes = await apiRequest('POST', `/api/v1/orders/${directOrderId}/cancel`, {
      reason: 'Ordered by mistake'
    }, authHeaders);
    const cancelOk = cancelRes.status === 200 && (cancelRes.data?.ok === true || cancelRes.data?.order?.status === 'CANCELLED' || cancelRes.data?.cancelled === true);
    recordResult('POST api/v1/orders/:orderId/cancel', 'POST', `/api/v1/orders/${directOrderId}/cancel`, cancelRes.status, cancelOk);

    // 24. deleteCartItem
    await apiRequest('POST', `/api/v1/cart/${customerId}/items`, { sku, quantity: 1 }, authHeaders);
    const deleteCartRes = await apiRequest('DELETE', `/api/v1/cart/${customerId}/items/${sku}`, null, authHeaders);
    recordResult('DELETE api/v1/cart/:customerId/items/:sku', 'DELETE', `/api/v1/cart/${customerId}/items/${sku}`, deleteCartRes.status, deleteCartRes.status === 200);


    // =========================================================================
    // SECTION 3: Rider Endpoints (RiderEndpoint.swift)
    // =========================================================================
    console.log('\n\x1b[1m\x1b[33m--- Section 3: Rider API (RiderEndpoint.swift) ---\x1b[0m');

    let riderChallengeId = '';
    let riderToken = '';

    // 1. sendRiderOtp (Registered Active Rider)
    const riderOtpSend = await apiRequest('POST', '/api/v1/auth/rider/otp/send', { phone: riderPhone }, {
      'X-Client-Platform': 'iOS-Rider'
    });
    const riderOtpSendOk = riderOtpSend.status === 200 && Boolean(riderOtpSend.data?.challengeId);
    riderChallengeId = riderOtpSend.data?.challengeId || '';
    recordResult('POST api/v1/auth/rider/otp/send', 'POST', '/api/v1/auth/rider/otp/send', riderOtpSend.status, riderOtpSendOk);

    // 2. verifyRiderOtp
    const riderOtpVerify = await apiRequest('POST', '/api/v1/auth/rider/otp/verify', {
      challengeId: riderChallengeId,
      phone: riderPhone,
      otp: '123456'
    }, { 'X-Client-Platform': 'iOS-Rider' });
    const riderOtpVerifyOk = riderOtpVerify.status === 200 && Boolean(riderOtpVerify.data?.accessToken);
    riderToken = riderOtpVerify.data?.accessToken || '';
    recordResult('POST api/v1/auth/rider/otp/verify', 'POST', '/api/v1/auth/rider/otp/verify', riderOtpVerify.status, riderOtpVerifyOk);

    const riderHeaders = {
      'Authorization': `Bearer ${riderToken}`,
      'X-Client-Platform': 'iOS-Rider',
      'X-Client-Version': '1.0.0'
    };

    // 3. getRiderProfile
    const profileRes = await apiRequest('GET', '/api/v1/delivery/rider/profile', null, riderHeaders);
    recordResult('GET api/v1/delivery/rider/profile', 'GET', '/api/v1/delivery/rider/profile', profileRes.status, profileRes.status === 200 && Boolean(profileRes.data?.riderId || profileRes.data?.rider));

    // 4. updateShiftStatus
    const shiftRes = await apiRequest('POST', '/api/v1/delivery/rider/shift-status', {
      shiftStatus: 'ONLINE_AVAILABLE',
      latitude: 28.6315,
      longitude: 77.2167
    }, riderHeaders);
    recordResult('POST api/v1/delivery/rider/shift-status', 'POST', '/api/v1/delivery/rider/shift-status', shiftRes.status, shiftRes.status === 200);

    // 5. registerDeviceToken
    const regDevRes = await apiRequest('POST', '/api/v1/delivery/rider/device-token', {
      deviceToken: 'apns_token_ios_rider_test_abc123'
    }, riderHeaders);
    recordResult('POST api/v1/delivery/rider/device-token', 'POST', '/api/v1/delivery/rider/device-token', regDevRes.status, regDevRes.status === 200);

    // 6. getNotifications
    const notifsRes = await apiRequest('GET', '/api/v1/delivery/rider/notifications', null, riderHeaders);
    recordResult('GET api/v1/delivery/rider/notifications', 'GET', '/api/v1/delivery/rider/notifications', notifsRes.status, notifsRes.status === 200 && Array.isArray(notifsRes.data?.notifications || notifsRes.data));

    // 7. markAllNotificationsRead
    const markAllRes = await apiRequest('POST', '/api/v1/delivery/rider/notifications/read-all', {}, riderHeaders);
    recordResult('POST api/v1/delivery/rider/notifications/read-all', 'POST', '/api/v1/delivery/rider/notifications/read-all', markAllRes.status, markAllRes.status === 200);

    // 8. getActiveSession
    const sessRes = await apiRequest('GET', '/api/v1/delivery/rider/active-session', null, riderHeaders);
    recordResult('GET api/v1/delivery/rider/active-session', 'GET', '/api/v1/delivery/rider/active-session', sessRes.status, sessRes.status === 200);

    // 9. getActiveOffers
    const offersRes = await apiRequest('GET', '/api/v1/delivery/offers/active', null, riderHeaders);
    recordResult('GET api/v1/delivery/offers/active', 'GET', '/api/v1/delivery/offers/active', offersRes.status, offersRes.status === 200 && Array.isArray(offersRes.data?.offers || offersRes.data));

    // 10. Seed Delivery Session for Lifecycle Testing
    let targetOrderId = placedOrderId;
    if (!targetOrderId) {
      const rawOtp = '123456';
      const pepper = process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value';
      const otpHash = DeliveryOtpService.hashDeliveryOtp(rawOtp, pepper);
      const fallbackOrder = await pool.query(
        `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
         VALUES ('ord_ios_seed', 'ord_ios_seed', $1, $2, 'READY_FOR_PICKUP', 240.00, '[]'::jsonb, '{}'::jsonb, $3, TRUE, 240.00, NOW(), NOW())
         ON CONFLICT (id) DO UPDATE SET status = 'READY_FOR_PICKUP', delivery_otp_hash = $3 RETURNING order_id`,
        [customerId, storeId, otpHash]
      );
      targetOrderId = fallbackOrder.rows[0].order_id;
    }

    const deliveryId = 'deliv_ios_test_01';
    const offerId = 'off_ios_test_01';
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
      [deliveryId, targetOrderId, storeId, riderId]
    );

    const expiresAt = Date.now() + 600000;
    await pool.query(
      `INSERT INTO offers (
         id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
         status, offer_created_at, offer_expires_at, earnings_amount,
         delivery_distance_km, total_distance_km, estimated_duration_mins
       ) VALUES (
         $1, $1, 'evt_ios_test_01', 'notif_ios_test_01', $2, $3, $4,
         'OFFERED', $5, $6, 45.00,
         1.5, 2.0, 10
       ) ON CONFLICT (id) DO UPDATE SET status = 'OFFERED', offer_expires_at = $6, rider_id = $4, delivery_id = $2, order_id = $3`,
      [offerId, deliveryId, targetOrderId, riderId, Date.now(), expiresAt]
    );

    // 11. acknowledgeOffer
    const ackRes = await apiRequest('POST', `/api/v1/delivery/offers/${offerId}/ack`, {}, riderHeaders);
    recordResult('POST api/v1/delivery/offers/:id/ack', 'POST', `/api/v1/delivery/offers/${offerId}/ack`, ackRes.status, ackRes.status === 200);

    // 12. acceptOffer
    const acceptRes = await apiRequest('POST', `/api/v1/rider/offers/${offerId}/accept`, {}, riderHeaders);
    recordResult('POST api/v1/rider/offers/:id/accept', 'POST', `/api/v1/rider/offers/${offerId}/accept`, acceptRes.status, acceptRes.status === 200 && (acceptRes.data?.accepted === true || acceptRes.data?.ok === true));

    // 13. arriveMerchant
    const arrMerchRes = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/arrive-merchant`, {}, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/arrive-merchant', 'POST', `/api/v1/delivery/session/${deliveryId}/arrive-merchant`, arrMerchRes.status, arrMerchRes.status === 200);

    // 14. confirmPickup
    const pickupRes = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/pickup`, {}, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/pickup', 'POST', `/api/v1/delivery/session/${deliveryId}/pickup`, pickupRes.status, pickupRes.status === 200);

    // 15. submitTelemetry (via both route aliases)
    const telemRes1 = await apiRequest('POST', `/api/v1/delivery/${deliveryId}/telemetry`, {
      latitude: 28.6318,
      longitude: 77.2170,
      sequenceNumber: 1,
      heading: 45,
      accuracyMeters: 5,
      speedMps: 7.5
    }, riderHeaders);
    recordResult('POST api/v1/delivery/:id/telemetry', 'POST', `/api/v1/delivery/${deliveryId}/telemetry`, telemRes1.status, telemRes1.status === 200);

    const telemRes2 = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/telemetry`, {
      latitude: 28.6319,
      longitude: 77.2175,
      sequenceNumber: 2,
      heading: 45,
      accuracyMeters: 5,
      speedMps: 6.0
    }, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/telemetry (session alias)', 'POST', `/api/v1/delivery/session/${deliveryId}/telemetry`, telemRes2.status, telemRes2.status === 200);

    // 16. arriveCustomer
    const arrCustRes = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/arrive-customer`, {}, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/arrive-customer', 'POST', `/api/v1/delivery/session/${deliveryId}/arrive-customer`, arrCustRes.status, arrCustRes.status === 200);

    // 17. resendCustomerOtp
    const resendOtpRes = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/resend-otp`, {}, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/resend-otp', 'POST', `/api/v1/delivery/session/${deliveryId}/resend-otp`, resendOtpRes.status, resendOtpRes.status === 200);

    // 18. reportIssue
    const reportRes = await apiRequest('POST', `/api/v1/delivery/session/${deliveryId}/report-issue`, {
      reason: 'CUSTOMER_UNREACHABLE',
      note: 'Calling doorbell, waiting at reception'
    }, riderHeaders);
    recordResult('POST api/v1/delivery/session/:id/report-issue', 'POST', `/api/v1/delivery/session/${deliveryId}/report-issue`, reportRes.status, reportRes.status === 200);

    // 19. deliverWithOtp
    const actualOtp = placedOrderOtp || '123456';
    const deliverRes = await apiRequest('POST', `/api/v1/orders/${deliveryId}/deliver-with-otp`, {
      otp: actualOtp,
      codCollected: true,
      codAmount: 240.00
    }, riderHeaders);
    const deliverOk = deliverRes.status === 200 && deliverRes.data?.verified === true;
    recordResult('POST api/v1/orders/:id/deliver-with-otp', 'POST', `/api/v1/orders/${deliveryId}/deliver-with-otp`, deliverRes.status, deliverOk);

    // 20. unregisterDeviceToken (Logout)
    const logoutRes = await apiRequest('POST', '/api/v1/delivery/rider/device-token/logout', {
      deviceToken: 'apns_token_ios_rider_test_abc123'
    }, riderHeaders);
    recordResult('POST api/v1/delivery/rider/device-token/logout', 'POST', '/api/v1/delivery/rider/device-token/logout', logoutRes.status, logoutRes.status === 200);

    // =========================================================================
    // SECTION 4: Database Authoritative Verification
    // =========================================================================
    console.log('\n\x1b[1m\x1b[33m--- Section 4: PostgreSQL Authoritative Audit ---\x1b[0m');

    const dbDelivCheck = await pool.query('SELECT state, otp_verified, cod_collected_amount FROM delivery_sessions WHERE delivery_id = $1', [deliveryId]);
    const isDeliveredInDb = dbDelivCheck.rows[0]?.state === 'DELIVERED' && dbDelivCheck.rows[0]?.otp_verified === true;
    recordResult('PostgreSQL delivery_sessions record in DELIVERED state', 'DB_AUDIT', 'delivery_sessions', 200, isDeliveredInDb, `state=${dbDelivCheck.rows[0]?.state}`);

    const dbOrderCheck = await pool.query('SELECT status FROM orders WHERE order_id = $1', [placedOrderId]);
    const isOrderDelivered = dbOrderCheck.rows[0]?.status === 'DELIVERED';
    recordResult('PostgreSQL orders record in DELIVERED status', 'DB_AUDIT', 'orders', 200, isOrderDelivered, `status=${dbOrderCheck.rows[0]?.status}`);

  } finally {
    if (serverInstance) {
      serverInstance.close();
    }
  }

  // Summary
  console.log('\n\x1b[1m\x1b[36m======================================================================\x1b[0m');
  console.log(`\x1b[1mTOTAL TESTS RUN: ${totalCount}\x1b[0m`);
  console.log(`\x1b[1m\x1b[32mPASSED: ${passedCount}\x1b[0m`);
  console.log(`\x1b[1m\x1b[31mFAILED: ${totalCount - passedCount}\x1b[0m`);
  console.log(`\x1b[1mSUCCESS RATE: ${Math.round((passedCount / totalCount) * 100)}%\x1b[0m`);
  console.log('\x1b[1m\x1b[36m======================================================================\x1b[0m\n');

  if (passedCount !== totalCount) {
    process.exit(1);
  } else {
    process.exit(0);
  }
}

run().catch(err => {
  console.error('FATAL TEST ERROR:', err);
  if (serverInstance) serverInstance.close();
  process.exit(1);
});
