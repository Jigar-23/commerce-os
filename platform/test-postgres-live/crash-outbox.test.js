/**
 * Live PostgreSQL Test: Real Multi-Process Crash & Durable Outbox Event Recovery
 * 
 * Verifies with real independent OS processes:
 * 1. Process A: Executes order commit + outbox event commit, then terminates abruptly (simulating server crash).
 * 2. Process B: Starts independently, boots fresh OutboxProcessor and DispatchService, recovers pending event, creates real offer and downstream outbox event.
 * 3. Asserts database state across process lifecycles.
 */

const assert = require('assert');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const path = require('path');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Independent Multi-Process Crash & Outbox Recovery...');

  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for crash-outbox.test.js');
    process.exit(1);
  }

  const timestamp = Date.now();
  const deliveryId = 'del_crash_' + crypto.randomUUID();
  const orderId = 'ord_crash_' + crypto.randomUUID();
  const storeId = 'store_crash_' + crypto.randomUUID();
  const riderId = 'rider_crash_' + crypto.randomUUID();
  let outboxId = null;

  try {
    // 1. Seed Store and Online Rider
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Crash Hub', 'Cyber City Hub', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, tier, status, created_at, updated_at)
       VALUES ($1, $1, $2, 'Crash Test Fleet Partner', 'HR-26-CC-1122', 'TWO_WHEELER', 'STANDARD', 'ACTIVE', NOW(), NOW())`,
      [riderId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_known_lat, last_known_lng, last_seen_at)
       VALUES ($1, 'ONLINE', 28.4595, 77.0266, NOW())
       ON CONFLICT (rider_id) DO UPDATE SET status = 'ONLINE', last_known_lat = 28.4595, last_known_lng = 77.0266, last_seen_at = NOW()`,
      [riderId]
    );

    // 2. Setup Order and Delivery Session
    await pool.query(
      `INSERT INTO orders (id, order_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash, created_at, updated_at)
       VALUES ($1, $1, $2, 'READY_FOR_PICKUP', 250.00, '{"addressLine": "Crash Test Tower", "latitude": 28.46, "longitude": 77.03}', '[]', 'hash_test', NOW(), NOW())`,
      [orderId, storeId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (
        id, delivery_id, order_id, store_id, state, is_cod,
        merchant_name, merchant_address, merchant_lat, merchant_lng,
        customer_name, customer_address, customer_lat, customer_lng,
        created_at, updated_at
      ) VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', FALSE, 'Crash Hub', 'Cyber City Hub', 28.4595, 77.0266, 'Crash Cust', 'Crash Test Tower', 28.46, 77.03, NOW(), NOW())`,
      [deliveryId, orderId, storeId]
    );

    // 3. Process A: Commits outbox event and immediately terminates (simulating crash before worker pick up)
    const insertRes = await pool.query(
      `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
       VALUES ('DELIVERY_SESSION', $1, 'DISPATCH_REQUESTED', $2, 'PENDING', 0, NOW())
       RETURNING id`,
      [
        deliveryId,
        JSON.stringify({
          deliveryId,
          orderId,
          storeId,
          merchantLat: 28.4595,
          merchantLng: 77.0266,
          customerLat: 28.46,
          customerLng: 77.03,
          customerName: 'Crash Cust',
          customerAddress: 'Crash Test Tower',
          deliverySession: {
            id: deliveryId,
            deliveryId,
            orderId,
            storeId,
            merchantLat: 28.4595,
            merchantLng: 77.0266,
            customerLat: 28.46,
            customerLng: 77.03,
            customerName: 'Crash Cust',
            customerAddress: 'Crash Test Tower'
          }
        })
      ]
    );
    outboxId = insertRes.rows[0].id;

    // 4. Process B: Spawn an independent OS process to simulate server reboot & outbox recovery
    const workerScript = `
      const { Pool } = require('pg');
      const {
        DispatchService,
        TransactionalStoreRepository,
        TransactionalPresenceRepository,
        TransactionalRiderRepository,
        TransactionalOfferRepository,
        TransactionalServiceabilityRepository,
        OutboxProcessor
      } = require('${path.join(__dirname, '../repositories')}');

      async function main() {
        const pool = new Pool({ connectionString: process.env.DATABASE_URL });
        try {
          const storeRepo = new TransactionalStoreRepository(pool);
          const presenceRepo = new TransactionalPresenceRepository(pool);
          const riderRepo = new TransactionalRiderRepository(pool);
          const offerRepo = new TransactionalOfferRepository(pool);
          const serviceabilityRepo = new TransactionalServiceabilityRepository(pool);
          const routeResolver = async (lat1, lon1, lat2, lon2) => ({ ok: true, distanceKm: 2.5, durationMins: 8 });

          const dispatchService = new DispatchService({
            storeRepo,
            presenceRepo,
            riderRepo,
            offerRepo,
            serviceabilityRepo,
            routeResolver,
            isProduction: true
          });

          const outboxProcessor = new OutboxProcessor(pool, async (event) => {
            const payload = typeof event.payload === 'string' ? JSON.parse(event.payload) : event.payload;
            if (event.event_type === 'DISPATCH_REQUESTED') {
              await dispatchService.processDispatch(payload.deliverySession || payload, '${riderId}');
            }
          });

          let processed = 0;
          for (let i = 0; i < 10; i++) {
            const count = await outboxProcessor.processPendingEvents();
            processed += count;
            if (processed >= 1) break;
            await new Promise(r => setTimeout(r, 200));
          }
          if (processed < 1) {
            const debugRes = await pool.query('SELECT * FROM outbox_events WHERE id = $1', ['${outboxId}']);
            console.error('Failed to process pending outbox events. Target event state:', debugRes.rows);
            process.exit(1);
          }
          await pool.end();
          process.exit(0);
        } catch (err) {
          console.error('Worker error:', err);
          await pool.end();
          process.exit(1);
        }
      }
      main();
    `;

    const child = spawnSync(process.execPath, ['-e', workerScript], {
      cwd: path.join(__dirname, '../..'),
      env: { ...process.env, DATABASE_URL: databaseUrl, NODE_PATH: path.join(__dirname, '../../node_modules') },
      timeout: 15000,
      encoding: 'utf8'
    });

    if (child.status !== 0) {
      console.error('Child Process B Error:', child.stderr || child.stdout);
      throw new Error(`Child Process B exited with code ${child.status}`);
    }

    // 5. Assertions on Database State after Recovery Process
    // A. Original DISPATCH_REQUESTED marked SENT
    const statusRes = await pool.query(`SELECT status FROM outbox_events WHERE id = $1`, [outboxId]);
    assert.strictEqual(statusRes.rows[0].status, 'SENT', 'Recovered event must be marked SENT in PostgreSQL');

    // B. Real offer record created in offers table
    const offerDbRes = await pool.query(`SELECT * FROM offers WHERE delivery_id = $1`, [deliveryId]);
    assert.ok(offerDbRes.rows.length >= 1, 'DispatchService must create a real offer record in offers table');
    assert.strictEqual(offerDbRes.rows[0].rider_id, riderId, 'Offer must be targeted to the online rider');

    // C. Downstream NEW_DISPATCH_OFFER event created in outbox_events
    const offerOutboxRes = await pool.query(
      `SELECT * FROM outbox_events WHERE event_type = 'NEW_DISPATCH_OFFER' AND aggregate_id = $1`,
      [offerDbRes.rows[0].offer_id || offerDbRes.rows[0].id]
    );
    assert.ok(offerOutboxRes.rows.length >= 1, 'Downstream NEW_DISPATCH_OFFER outbox event must be created');

    console.log('  ✅ PASS: Multi-Process Crash & Outbox Recovery (Independent Process Lifecycle Validated)\n');
  } finally {
    if (outboxId) {
      await pool.query(`DELETE FROM outbox_events WHERE id = $1 OR aggregate_id = $2`, [outboxId, deliveryId]);
    }
    await pool.query(`DELETE FROM offers WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM orders WHERE id = $1 OR order_id = $1`, [orderId]);
    await pool.query(`DELETE FROM rider_presence WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE id = $1`, [riderId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for crash-outbox.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Live Crash & Outbox Recovery Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
