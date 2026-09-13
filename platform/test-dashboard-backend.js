'use strict';

const http = require('http');
const { Pool } = require('pg');

process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://jigar@127.0.0.1:5432/commerceos_test';
process.env.OSRM_BASE_URL = process.env.OSRM_BASE_URL || 'https://router.project-osrm.org';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test_jwt_secret_for_production_suite_32chars_min';
process.env.JWT_ISSUER = 'https://auth.commerceos.internal';
process.env.JWT_AUDIENCE = 'commerceos-platform-clients';
process.env.COMMERCEOS_OTP_PEPPER = 'test_otp_pepper_salt_value';
process.env.FCM_SERVER_KEY = 'test_fcm_server_key';
process.env.FCM_ENDPOINT_URL = 'https://fcm.googleapis.com/fcm/send';
process.env.PORT = '8197';

const TEST_PORT = 8197;
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

async function runDashboardBackendTests() {
  console.log('======================================================================');
  console.log('  COMMERCE OS — PRODUCTION DASHBOARD & ADMIN CONTRACT TEST SUITE      ');
  console.log('======================================================================\n');

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

    // -----------------------------------------------------------------------
    // SECTION 1: Pharmacist Prescription Verification Queue & Decision
    // -----------------------------------------------------------------------
    console.log('--- Section 1: Prescription Verification Queue & Pharmacist Decision ---');

    // Seed test customer
    const testCustomerId = 'cust_rx_test_01';
    await pool.query(`
      INSERT INTO customers (id, phone, full_name, email)
      VALUES ($1, '+919811998877', 'Ramesh Sharma', 'ramesh@commerceos.io')
      ON CONFLICT (id) DO NOTHING
    `, [testCustomerId]);

    // Seed test prescription
    const rxId1 = 'rx_test_pending_01';
    const rxId2 = 'rx_test_pending_02';
    await pool.query(`
      INSERT INTO prescriptions (
        id, customer_id, patient_name, doctor_name, doctor_registration_no, attachments, note, status
      ) VALUES (
        $1, $2, 'Ramesh Sharma', 'Dr. Alok Verma, MD', 'MCI-99412',
        '[{"name":"Paracetamol 650mg","sku":"PCM-650","quantity":2},{"name":"Azithromycin 500mg","sku":"AZI-500","quantity":1}]'::jsonb,
        'Patient presents with fever and cough for 3 days', 'PENDING'
      ) ON CONFLICT (id) DO UPDATE SET status = 'PENDING'`,
      [rxId1, testCustomerId]
    );

    await pool.query(`
      INSERT INTO prescriptions (
        id, customer_id, patient_name, doctor_name, doctor_registration_no, attachments, note, status
      ) VALUES (
        $1, $2, 'Kavita Sharma', 'Dr. Sneha Roy', 'DMC-44129',
        '[{"name":"Metformin 500mg","sku":"MET-500","quantity":1}]'::jsonb,
        'Regular diabetes checkup refill', 'PENDING'
      ) ON CONFLICT (id) DO UPDATE SET status = 'PENDING'`,
      [rxId2, testCustomerId]
    );

    // 1. Fetch prescription queue
    const qRes = await apiRequest('GET', '/api/v1/orders/prescription-verification-queue');
    assert(qRes.status === 200, `GET /api/v1/orders/prescription-verification-queue -> 200 (got ${qRes.status})`);
    assert(Array.isArray(qRes.data), 'Prescription queue returns JSON array');
    
    const item1 = qRes.data.find(it => it.id === rxId1);
    assert(Boolean(item1), 'Queue contains seeded prescription rx_test_pending_01');
    assert(item1?.patientName === 'Ramesh Sharma', 'Correct patient name reflected');
    assert(item1?.status === 'PENDING', 'Initial status is PENDING');
    assert(Array.isArray(item1?.rxItems) && item1.rxItems.length === 2, 'Parsed 2 rxItems from prescription payload');
    assert(Boolean(item1?.ocr?.doctorName) && item1.ocr.confidenceScore >= 0.9, 'Contains OCR extraction with high confidence score');

    // 2. Approve Prescription
    const approveRes = await apiRequest('POST', '/api/v1/orders/verify-prescription', {
      verificationId: rxId1,
      status: 'VERIFIED',
      pharmacistLicenseNo: 'PHARM-LIC-KA-8819',
      notes: 'Approved by Registered Pharmacist after tele-consult review'
    });
    assert(approveRes.status === 200, `POST /api/v1/orders/verify-prescription (Approve) -> 200`);
    assert(approveRes.data?.ok === true && approveRes.data?.status === 'VERIFIED', 'Approval returned status: VERIFIED');

    // Verify in PostgreSQL
    const rxDb1 = await pool.query('SELECT status, license_no FROM prescriptions WHERE id = $1', [rxId1]);
    assert(rxDb1.rows[0].status === 'APPROVED', `PostgreSQL prescription status updated to APPROVED (got ${rxDb1.rows[0].status})`);
    assert(rxDb1.rows[0].license_no === 'PHARM-LIC-KA-8819', 'PostgreSQL license_no saved correctly');

    // 3. Reject Prescription
    const rejectRes = await apiRequest('POST', '/api/v1/orders/verify-prescription', {
      verificationId: rxId2,
      status: 'REJECTED',
      pharmacistLicenseNo: 'PHARM-LIC-KA-8819',
      notes: 'Prescription expired: issued > 6 months ago'
    });
    assert(rejectRes.status === 200, `POST /api/v1/orders/verify-prescription (Reject) -> 200`);
    assert(rejectRes.data?.status === 'REJECTED', 'Rejection returned status: REJECTED');

    const rxDb2 = await pool.query('SELECT status, rejection_reason FROM prescriptions WHERE id = $1', [rxId2]);
    assert(rxDb2.rows[0].status === 'REJECTED', 'PostgreSQL prescription status updated to REJECTED');
    assert(rxDb2.rows[0].rejection_reason.includes('expired'), 'PostgreSQL rejection_reason saved');

    // -----------------------------------------------------------------------
    // SECTION 2: Seller KYC Queue & Admin Decision
    // -----------------------------------------------------------------------
    console.log('\n--- Section 2: Seller KYC Queue & Decision ---');

    const testStoreId = 'store_kyc_01';
    await pool.query(`
      INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
      VALUES ($1, 'Apollo Green Pharmacy', 'Indiranagar 100ft Road', 12.9716, 77.5946, TRUE)
      ON CONFLICT (id) DO NOTHING
    `, [testStoreId]);

    const sellerId1 = 'sel_kyc_test_01';
    const sellerId2 = 'sel_kyc_test_02';
    await pool.query(`
      INSERT INTO sellers (id, seller_id, email, phone, password_hash, store_id, store_name, merchant_name, status)
      VALUES 
        ($1, $1, 'apollo_green@commerceos.io', '9876543321', 'hash_01', $3, 'Apollo Green Pharmacy', 'Apollo Health Ltd', 'PENDING_APPROVAL'),
        ($2, $2, 'bad_pharma@commerceos.io', '9876543322', 'hash_02', $3, 'Unlicensed Meds Store', 'Shady Corp', 'PENDING_APPROVAL')
      ON CONFLICT (id) DO UPDATE SET status = 'PENDING_APPROVAL'`,
      [sellerId1, sellerId2, testStoreId]
    );

    // 1. Fetch KYC Queue
    const kycQueueRes = await apiRequest('GET', '/api/v1/admin/sellers/kyc-queue');
    assert(kycQueueRes.status === 200, `GET /api/v1/admin/sellers/kyc-queue -> 200`);
    assert(Array.isArray(kycQueueRes.data?.sellers), 'sellers is an array in response');
    const sItem1 = kycQueueRes.data.sellers.find(s => s.sellerId === sellerId1);
    assert(Boolean(sItem1), 'Found seeded seller in KYC queue');
    assert(Array.isArray(sItem1?.documents) && sItem1.documents.length >= 2, 'Includes drug license and tax documents');

    // 2. Approve Seller KYC
    const kycApproveRes = await apiRequest('POST', '/api/v1/admin/sellers/kyc-decision', {
      sellerId: sellerId1,
      decision: 'APPROVED',
      reason: 'Drug license 20B/21B verified with state pharmacy council'
    });
    assert(kycApproveRes.status === 200, `POST /api/v1/admin/sellers/kyc-decision (Approve) -> 200`);
    assert(kycApproveRes.data?.status === 'ACTIVE', 'Seller status transitioned to ACTIVE');

    const sDb1 = await pool.query('SELECT status FROM sellers WHERE seller_id = $1', [sellerId1]);
    assert(sDb1.rows[0].status === 'ACTIVE', 'PostgreSQL seller status updated to ACTIVE');

    // 3. Reject Seller KYC
    const kycRejectRes = await apiRequest('POST', '/api/v1/admin/sellers/kyc-decision', {
      sellerId: sellerId2,
      decision: 'REJECTED',
      reason: 'Invalid GSTIN and forged pharmacist credentials'
    });
    assert(kycRejectRes.status === 200, `POST /api/v1/admin/sellers/kyc-decision (Reject) -> 200`);
    assert(kycRejectRes.data?.status === 'REJECTED', 'Seller status transitioned to REJECTED');

    const sDb2 = await pool.query('SELECT status FROM sellers WHERE seller_id = $1', [sellerId2]);
    assert(sDb2.rows[0].status === 'REJECTED', 'PostgreSQL seller status updated to REJECTED');

    // -----------------------------------------------------------------------
    // SECTION 3: COD Reconciliation & Driver Settlement
    // -----------------------------------------------------------------------
    console.log('\n--- Section 3: COD Reconciliation & Driver Settlement ---');

    const codRiderPhone = '9811334455';
    const codRiderId = 'rdr_cod_test_01';
    await pool.query(`
      INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
      VALUES ($1, $1, $2, 'Rohan COD Rider', 'DL-01-COD', 'TWO_WHEELER', 'ACTIVE')
      ON CONFLICT (id) DO NOTHING
    `, [codRiderId, `+91${codRiderPhone}`]);

    const orderCod1 = 'ord_cod_test_01';
    const { DeliveryOtpService } = require('./repositories');
    const otpHash = DeliveryOtpService.hashDeliveryOtp('123456', process.env.COMMERCEOS_OTP_PEPPER || 'test_otp_pepper_salt_value');

    await pool.query(`
      INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, items, delivery_address, delivery_otp_hash, is_cod, cod_amount, created_at, updated_at)
      VALUES ($1, $1, $2, $3, 'DELIVERED', 450.00, '[]'::jsonb, '{}'::jsonb, $4, TRUE, 450.00, NOW(), NOW())
      ON CONFLICT (id) DO NOTHING
    `, [orderCod1, testCustomerId, testStoreId, otpHash]);

    const ledgerId1 = 'cod_leg_test_01';
    await pool.query(`
      INSERT INTO cod_ledger (id, order_id, seller_id, collector_id, amount_expected, amount_collected, status, reconciled)
      VALUES ($1, $2, $3, $4, 450.00, 450.00, 'COLLECTED_BY_RIDER', FALSE)
      ON CONFLICT (id) DO UPDATE SET status = 'COLLECTED_BY_RIDER', reconciled = FALSE
    `, [ledgerId1, orderCod1, testStoreId, codRiderId]);

    // 1. Fetch COD Reconciliation
    const codRes = await apiRequest('GET', '/api/v1/admin/cod/reconciliation');
    assert(codRes.status === 200, `GET /api/v1/admin/cod/reconciliation -> 200`);
    assert(Boolean(codRes.data?.summary), 'Returns summary object');
    assert(codRes.data.summary.totalCodExpected >= 450.00, 'Summary totalCodExpected reflects seeded collection');
    assert(Array.isArray(codRes.data.ledger), 'ledger entries returned as array');

    // 2. Settle COD
    const settleRes = await apiRequest('POST', '/api/v1/admin/cod/settle', {
      riderId: codRiderId,
      ledgerId: ledgerId1,
      amount: 450.00,
      settlementReference: 'BANK-TRF-COD-9921',
      notes: 'Cash deposited at Indiranagar hub counter'
    });
    assert(settleRes.status === 200, `POST /api/v1/admin/cod/settle -> 200`);
    assert(settleRes.data?.settled === true && settleRes.data?.settledAmount === 450, 'Settlement confirmed for 450.00');

    const codDb = await pool.query('SELECT status, reconciled FROM cod_ledger WHERE id = $1', [ledgerId1]);
    assert(codDb.rows[0].status === 'SETTLED', 'PostgreSQL cod_ledger status updated to SETTLED');
    assert(codDb.rows[0].reconciled === true, 'PostgreSQL cod_ledger reconciled set to TRUE');

    // -----------------------------------------------------------------------
    // SECTION 4: Distributed Audit Ledger
    // -----------------------------------------------------------------------
    console.log('\n--- Section 4: Distributed Audit Ledger Verification ---');

    const auditRes = await apiRequest('GET', '/api/v1/admin/audit-ledger');
    assert(auditRes.status === 200, `GET /api/v1/admin/audit-ledger -> 200`);
    assert(Array.isArray(auditRes.data?.auditLogs), 'auditLogs is an array');
    assert(auditRes.data.auditLogs.length >= 3, `Audit logs recorded actions (count: ${auditRes.data.auditLogs.length})`);

    const actions = auditRes.data.auditLogs.map(l => l.action);
    assert(actions.includes('PRESCRIPTION_VERIFIED'), 'Audit log contains PRESCRIPTION_VERIFIED action');
    assert(actions.includes('SELLER_KYC_DECISION'), 'Audit log contains SELLER_KYC_DECISION action');
    assert(actions.includes('COD_SETTLED'), 'Audit log contains COD_SETTLED action');

    // -----------------------------------------------------------------------
    // SECTION 5: Realtime Admin SSE Live Stream
    // -----------------------------------------------------------------------
    console.log('\n--- Section 5: Realtime Admin SSE Live Stream ---');

    let sseHandshakeReceived = false;
    let sseEventReceived = false;

    await new Promise((resolve) => {
      const url = new URL('/api/v1/admin/live-stream', TEST_BASE_URL);
      const req = http.request(url, {
        method: 'GET',
        headers: { 'Accept': 'text/event-stream' }
      }, (res) => {
        assert(res.statusCode === 200, `SSE HTTP status is 200 (got ${res.statusCode})`);
        assert(res.headers['content-type'].includes('text/event-stream'), 'Content-Type is text/event-stream');

        res.on('data', (chunk) => {
          const str = chunk.toString();
          if (str.includes('handshake') || str.includes('CONNECTED')) {
            sseHandshakeReceived = true;
          }
          if (str.includes('PRESCRIPTION_VERIFIED') || str.includes('COD_SETTLED') || str.includes('TEST_EVENT')) {
            sseEventReceived = true;
          }
        });

        // Broadcast test event via server broadcaster
        setTimeout(async () => {
          await prodServer.appRepositories?.outboxProcessor?.intervalHandle;
          const { sseBroadcasterInstance } = require('./server/production-server');
          if (sseBroadcasterInstance) {
            sseBroadcasterInstance.broadcast('admin', 'TEST_EVENT', {
              message: 'Live stream verification event',
              timestamp: Date.now()
            });
          }
        }, 50);

        setTimeout(() => {
          req.destroy();
          resolve();
        }, 250);
      });

      req.on('error', () => resolve());
      req.end();
    });

    assert(sseHandshakeReceived, 'SSE stream established and received initial handshake event');
    assert(sseEventReceived, 'SSE stream received live broadcast event over admin channel');

    // Clean up test data
    await pool.query('DELETE FROM prescriptions WHERE id IN ($1, $2)', [rxId1, rxId2]);
    await pool.query('DELETE FROM sellers WHERE id IN ($1, $2)', [sellerId1, sellerId2]);
    await pool.query('DELETE FROM cod_ledger WHERE id = $1', [ledgerId1]);
    await pool.query('DELETE FROM orders WHERE id = $1', [orderCod1]);
    await pool.query('DELETE FROM riders WHERE id = $1', [codRiderId]);
    await pool.query('DELETE FROM stores WHERE id = $1', [testStoreId]);
    await pool.query('DELETE FROM customers WHERE id = $1', [testCustomerId]);

  } finally {
    if (serverInstance && serverInstance.listening) {
      await new Promise(resolve => serverInstance.close(resolve));
    }
    await pool.end();
  }

  // -----------------------------------------------------------------------
  // FINAL REPORT
  // -----------------------------------------------------------------------
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

runDashboardBackendTests().catch((err) => {
  console.error('FATAL_DASHBOARD_TEST_ERROR:', err);
  process.exit(1);
});
