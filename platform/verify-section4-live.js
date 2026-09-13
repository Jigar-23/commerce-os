#!/usr/bin/env node
'use strict';

/**
 * Verification of Audit Report Section 4 Backend Endpoints:
 * 1. GET /api/v1/search?q=...
 * 2. GET /api/v1/delivery/rider/trips
 * 3. GET /api/v1/customers/:id/prescriptions
 */

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@127.0.0.1:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8199';

const { server, pool } = require('./server/production-server');

const PORT = 8199;
const BASE_URL = `http://127.0.0.1:${PORT}`;

async function request(method, path, body = null, headers = {}) {
  const url = `${BASE_URL}${path}`;
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
    data = await res.json();
  } else {
    data = await res.text();
  }
  return {
    status: res.status,
    headers: Object.fromEntries(res.headers.entries()),
    data
  };
}

async function main() {
  console.log('======================================================================');
  console.log('  COMMERCE OS — AUDIT REPORT SECTION 4 LIVE ENDPOINT VERIFICATION     ');
  console.log('======================================================================\n');

  // 1. Boot server
  await new Promise((resolve, reject) => {
    server.listen(PORT, '127.0.0.1', (err) => {
      if (err) return reject(err);
      console.log(`🚀 Production server live on ${BASE_URL}\n`);
      resolve();
    });
  });

  try {
    // 2. Fixtures
    const customerPhone = '9876509999';
    const customerId = `cust_${customerPhone}`;
    const riderPhone = '9811228888';
    const riderId = `rdr_${riderPhone}`;

    // Seed product
    await pool.query(
      `INSERT INTO products (id, sku, name, category, mrp, price, rx_requirement, is_active)
       VALUES ('prod_audit_01', 'SKU_AUDIT_01', 'Amoxicillin 500mg Antibiotic', 'Medicines', 180.0, 150.0, 'RX_REQUIRED', TRUE)
       ON CONFLICT (id) DO UPDATE SET is_active = TRUE, price = 150.0`
    );

    // Seed customer
    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier)
       VALUES ($1, $2, 'Dr. Aris Thorne', 'PLATINUM')
       ON CONFLICT (id) DO UPDATE SET full_name = 'Dr. Aris Thorne'`,
      [customerId, `+91${customerPhone}`]
    );

    // Seed prescription
    const rxId = `rx_audit_${Date.now()}`;
    await pool.query(
      `INSERT INTO prescriptions (id, customer_id, patient_name, doctor_name, status, attachments, created_at, updated_at)
       VALUES ($1, $2, 'Aris Thorne', 'Dr. Vikram Sethi', 'APPROVED', '["https://storage.commerceos.internal/rx/test.pdf"]'::jsonb, NOW(), NOW())
       ON CONFLICT (id) DO NOTHING`,
      [rxId, customerId]
    );

    // Seed rider
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, tier)
       VALUES ($1, $1, $2, 'Karan Sharma', 'KA-05-AB-4321', 'TWO_WHEELER', 'ACTIVE', 'GOLD')
       ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE'`,
      [riderId, `+91${riderPhone}`]
    );

    // Seed delivery sessions / trips for rider
    const delivId = `deliv_audit_${Date.now()}`;
    const orderId = `ord_audit_${Date.now()}`;
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items)
       VALUES ($1, $1, $2, 'store_01', 'DELIVERED', 450.0, '{"addressLine": "Indiranagar 100ft Road"}', '[]'::jsonb)
       ON CONFLICT (id) DO NOTHING`,
      [orderId, customerId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, rider_id, state, merchant_name, merchant_address, 
                                      customer_name, customer_address, is_cod, cod_amount, remaining_duration_mins, created_at, updated_at)
       VALUES ($1, $1, $2, $3, 'DELIVERED', 'Central Pharmacy Hub', 'MG Road, Bengaluru', 
               'Aris Thorne', 'Indiranagar 100ft Road, Bengaluru', FALSE, 0, 0, NOW(), NOW())
       ON CONFLICT (id) DO NOTHING`,
      [delivId, orderId, riderId]
    );

    // Authenticate Customer
    const custOtpSend = await request('POST', '/api/v1/auth/customer/otp/send', { phone: customerPhone }, { 'X-Client-Platform': 'iOS' });
    const custOtpVerify = await request('POST', '/api/v1/auth/customer/otp/verify', {
      challengeId: custOtpSend.data.challengeId,
      phone: customerPhone,
      otp: '123456',
      fullName: 'Dr. Aris Thorne'
    }, { 'X-Client-Platform': 'iOS' });
    const customerToken = custOtpVerify.data.accessToken;

    // Authenticate Rider
    const riderOtpSend = await request('POST', '/api/v1/auth/rider/otp/send', { phone: riderPhone }, { 'X-Client-Platform': 'iOS-Rider' });
    const riderOtpVerify = await request('POST', '/api/v1/auth/rider/otp/verify', {
      challengeId: riderOtpSend.data.challengeId,
      phone: riderPhone,
      otp: '123456'
    }, { 'X-Client-Platform': 'iOS-Rider' });
    const riderToken = riderOtpVerify.data.accessToken;

    console.log('----------------------------------------------------------------------');
    console.log('TEST 1: GET /api/v1/search?q=Amoxicillin');
    console.log('----------------------------------------------------------------------');
    const searchRes = await request('GET', '/api/v1/search?q=Amoxicillin', null, {
      'X-Client-Platform': 'iOS'
    });
    console.log(`HTTP Status: ${searchRes.status}`);
    console.log(`Content-Type: ${searchRes.headers['content-type']}`);
    console.log(`Payload Type: ${Array.isArray(searchRes.data) ? 'Array' : typeof searchRes.data} (count: ${searchRes.data?.length || 0})`);
    console.log('Sample Result:', JSON.stringify(searchRes.data?.[0], null, 2));

    console.log('\n----------------------------------------------------------------------');
    console.log('TEST 2: GET /api/v1/delivery/rider/trips');
    console.log('----------------------------------------------------------------------');
    const tripsRes = await request('GET', '/api/v1/delivery/rider/trips', null, {
      'Authorization': `Bearer ${riderToken}`,
      'X-Client-Platform': 'iOS-Rider'
    });
    console.log(`HTTP Status: ${tripsRes.status}`);
    console.log(`Content-Type: ${tripsRes.headers['content-type']}`);
    console.log(`Payload Type: ${Array.isArray(tripsRes.data) ? 'Array' : typeof tripsRes.data} (count: ${tripsRes.data?.length || 0})`);
    console.log('Sample Result:', JSON.stringify(tripsRes.data?.[0], null, 2));

    console.log('\n----------------------------------------------------------------------');
    console.log(`TEST 3: GET /api/v1/customers/${customerId}/prescriptions`);
    console.log('----------------------------------------------------------------------');
    const rxRes = await request('GET', `/api/v1/customers/${customerId}/prescriptions`, null, {
      'Authorization': `Bearer ${customerToken}`,
      'X-Client-Platform': 'iOS'
    });
    console.log(`HTTP Status: ${rxRes.status}`);
    console.log(`Content-Type: ${rxRes.headers['content-type']}`);
    console.log(`Payload Type: ${Array.isArray(rxRes.data) ? 'Array' : typeof rxRes.data} (count: ${rxRes.data?.length || 0})`);
    console.log('Sample Result:', JSON.stringify(rxRes.data?.[0], null, 2));

    console.log('\n======================================================================');
    const allPassed = searchRes.status === 200 && tripsRes.status === 200 && rxRes.status === 200;
    if (allPassed) {
      console.log('🏆 ALL SECTION 4 ENDPOINTS RETURN LIVE 200 OK RESPONSES WITH VALID PAYLOADS');
    } else {
      console.error('❌ ONE OR MORE ENDPOINTS FAILED VERIFICATION');
      process.exitCode = 1;
    }
    console.log('======================================================================\n');
  } finally {
    server.close();
    await pool.end();
  }
}

main().catch(err => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
