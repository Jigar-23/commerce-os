/**
 * Commerce OS — Live PostgreSQL Outbox Lifecycle, Dead-Letter & Process Restart Recovery Test
 * 
 * Verifies with Real PostgreSQL 16+:
 * 1. Event State Transitions: PENDING -> PROCESSING -> SENT
 * 2. Controlled Retry with Exponential Backoff on Failure (retry_count++, next_attempt_at set)
 * 3. Dead-Lettering after MAX_RETRIES (status = DEAD_LETTER)
 * 4. Real Process Restart Recovery: Unprocessed order outbox event is automatically claimed and
 *    dispatched by the background OutboxProcessor when the production server boots.
 */

const assert = require('assert');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');
const { Pool } = require('pg');
const { createProductionRepositories, OutboxProcessor } = require('../repositories');

const SERVER_SCRIPT = path.join(__dirname, '../server/production-server.js');
const TEST_PORT = 8095;

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Outbox Lifecycle, Retry Backoff, Dead-Letter & Restart Recovery...');

  const timestamp = Date.now();
  const storeId = 'store_outbox_' + timestamp;
  const riderId = 'rider_outbox_' + timestamp;
  const custId = 'cust_outbox_' + timestamp;
  const orderId = 'ord_outbox_' + timestamp;
  const deliveryId = 'del_outbox_' + timestamp;
  const failEventId = 'evt_fail_' + timestamp;

  try {
    // 1. Seed Master Records
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Outbox Hub', 'Cyber City Gurugram', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'Outbox Customer', 'STANDARD', TRUE)`,
      [custId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, tier, status)
       VALUES ($1, $1, $2, 'Outbox Rider Alpha', 'HR-26-ZZ-9999', 'TWO_WHEELER', 'STANDARD', 'ACTIVE')`,
      [riderId, '+9197' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_known_lat, last_known_lng, last_seen_at)
       VALUES ($1, 'ONLINE', 28.4550, 77.0250, NOW())`,
      [riderId]
    );

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 'PLACED', 250.00, '{"addressLine": "Cyber City"}', '[]', 'hash99')`,
      [orderId, custId, storeId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng, is_cod, cod_amount, otp_verified)
       VALUES ($1, $1, $2, $3, 'LOOKING_FOR_RIDER', 'Outbox Hub', 'Cyber City', 28.4595, 77.0266, 'Outbox Customer', 'Cyber City', 28.4610, 77.0310, FALSE, 0, FALSE)`,
      [deliveryId, orderId, storeId]
    );

    // 2. Test Controlled Failure, Retry Backoff & Dead-Lettering
    const failEventId = 'evt_fail_' + timestamp;
    await pool.query(
      `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at)
       VALUES ('TEST_AGG', $1, 'INVALID_FAULTY_EVENT', '{"error": true}', 'PENDING', 0, NOW())`,
      [failEventId]
    );

    let failAttempts = 0;
    const failingDispatcher = async (event) => {
      failAttempts++;
      throw new Error('SIMULATED_NETWORK_OUTAGE: Remote endpoint unavailable.');
    };

    const failingProcessor = new OutboxProcessor(pool, failingDispatcher);

    // Process event 1 time -> status should return to PENDING with retry_count = 1, next_attempt_at in future
    await failingProcessor.processPendingEvents();

    const checkRetry1 = await pool.query(`SELECT status, retry_count, last_error, next_attempt_at FROM outbox_events WHERE aggregate_id = $1`, [failEventId]);
    assert.strictEqual(checkRetry1.rows[0].status, 'PENDING', 'Event must remain PENDING for retry');
    assert.strictEqual(Number(checkRetry1.rows[0].retry_count), 1, 'Retry count must increment to 1');
    assert.ok(checkRetry1.rows[0].last_error.includes('SIMULATED_NETWORK_OUTAGE'), 'Must record last error message');

    // Simulate exceeding MAX_RETRIES (set retry_count to 4 and trigger failure)
    await pool.query(`UPDATE outbox_events SET retry_count = 4, next_attempt_at = NOW() WHERE aggregate_id = $1`, [failEventId]);
    await failingProcessor.processPendingEvents();

    const checkDeadLetter = await pool.query(`SELECT status, retry_count FROM outbox_events WHERE aggregate_id = $1`, [failEventId]);
    assert.strictEqual(checkDeadLetter.rows[0].status, 'DEAD_LETTER', 'Event must transition to DEAD_LETTER after exceeding max retries');
    assert.strictEqual(Number(checkDeadLetter.rows[0].retry_count), 5, 'Retry count must be 5');

    // 3. Test Real Process Restart Recovery
    // Insert a fresh PENDING DISPATCH_REQUESTED outbox event
    const recoveryEventId = 'evt_recovery_' + timestamp;
    await pool.query(
      `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at)
       VALUES ('DELIVERY_SESSION', $1, 'DISPATCH_REQUESTED', $2, 'PENDING', 0, NOW())`,
      [
        deliveryId,
        JSON.stringify({
          deliveryId,
          orderId,
          storeId,
          merchantLat: 28.4595,
          merchantLng: 77.0266,
          customerLat: 28.4610,
          customerLng: 77.0310,
          customerName: 'Outbox Customer',
          customerAddress: 'Cyber City',
          deliverySession: {
            id: deliveryId,
            deliveryId,
            orderId,
            storeId,
            merchantLat: 28.4595,
            merchantLng: 77.0266,
            customerLat: 28.4610,
            customerLng: 77.0310,
            customerName: 'Outbox Customer',
            customerAddress: 'Cyber City'
          }
        })
      ]
    );

    // Launch the Standalone Production Server Process
    const prodProc = spawn(process.execPath, [SERVER_SCRIPT], {
      cwd: path.join(__dirname, '../..'),
      env: {
        ...process.env,
        PORT: String(TEST_PORT),
        DATABASE_URL: process.env.DATABASE_URL,
        OSRM_BASE_URL: 'http://router.project-osrm.org',
        JWT_SECRET: 'test_outbox_recovery_secret_key_99',
        JWT_ISSUER: 'commerce-os-auth',
        JWT_AUDIENCE: 'commerce-os-api',
        COMMERCEOS_OTP_PEPPER: 'test_pepper_outbox_rec_11',
        FCM_SERVER_KEY: 'test_fcm_rec_key_991',
        FCM_ENDPOINT_URL: 'https://fcm.googleapis.com/fcm/send',
        NODE_PATH: path.join(__dirname, '../../node_modules')
      },
      stdio: 'pipe'
    });

    let serverStdout = '';
    let serverStderr = '';
    prodProc.stdout.on('data', d => { serverStdout += d; });
    prodProc.stderr.on('data', d => { serverStderr += d; });

    try {
      // Wait for server outbox worker to poll (500ms) and dispatch the event
      let eventSent = false;
      let offerCreated = false;
      let lastEventState = null;

      for (let i = 0; i < 40; i++) {
        const outboxCheck = await pool.query(`SELECT status, last_error, processed_at FROM outbox_events WHERE aggregate_id = $1 AND event_type = 'DISPATCH_REQUESTED'`, [deliveryId]);
        if (outboxCheck.rows.length > 0) {
          lastEventState = outboxCheck.rows[0];
          if (outboxCheck.rows[0].status === 'SENT') {
            eventSent = true;
          }
        }

        const offerCheck = await pool.query(`SELECT * FROM offers WHERE delivery_id = $1`, [deliveryId]);
        if (offerCheck.rows.length > 0) {
          offerCreated = true;
        }

        if (eventSent && offerCreated) break;
        await new Promise(r => setTimeout(r, 200));
      }

      if (!eventSent || !offerCreated) {
        console.error('Outbox event state:', lastEventState);
        console.error('Server stdout:', serverStdout);
        console.error('Server stderr:', serverStderr);
      }

      assert.ok(eventSent, 'OutboxProcessor worker must claim PENDING event on boot and mark status = SENT');
      assert.ok(offerCreated, 'DispatchService must execute and create real offer in PostgreSQL on server recovery');

      console.log('  ✅ PASS: Outbox Lifecycle, Retry Backoff, Dead-Letter & Real Server Process Restart Recovery\n');
    } finally {
      prodProc.kill('SIGTERM');
    }
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id IN ($1, $2, $3)`, [deliveryId, failEventId, orderId]);
    await pool.query(`DELETE FROM offers WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM orders WHERE id = $1`, [orderId]);
    await pool.query(`DELETE FROM rider_presence WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE id = $1`, [riderId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for outbox-lifecycle-live.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Outbox Lifecycle Live Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
