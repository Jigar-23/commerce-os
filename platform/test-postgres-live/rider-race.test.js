/**
 * Live PostgreSQL Test: Rider Double-Assignment Race Condition
 * 
 * Verifies with two concurrent database clients:
 * Two distinct deliveries attempting simultaneous assignment to the same Rider X:
 * Exactly ONE succeeds (200), and the competing assignment is rejected (409 RIDER_ALREADY_ASSIGNED),
 * guaranteeing Rider X is never assigned to two concurrent active deliveries.
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalOfferRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Concurrent Rider Double-Assignment Race...');

  const timestamp = Date.now();
  const storeId = 'store_race_' + timestamp;
  const riderId = 'rider_race_single_' + timestamp;
  const orderAId = 'ord_race_A_' + timestamp;
  const orderBId = 'ord_race_B_' + timestamp;
  const deliveryAId = 'del_race_A_' + timestamp;
  const deliveryBId = 'del_race_B_' + timestamp;
  const offerAId = 'off_race_A_' + timestamp;
  const offerBId = 'off_race_B_' + timestamp;
  const custId = 'cust_race_' + timestamp;

  const offerRepo = new TransactionalOfferRepository(pool);

  try {
    // 1. Seed Store
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Race Store Hub', 'DLF Cyber City', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    // 2. Seed Customer
    const custPhone = '+9198' + String(timestamp).slice(-8);
    const riderPhone = '+9197' + String(timestamp).slice(-8);

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, tier, is_active)
       VALUES ($1, $2, 'Race Customer', 'STANDARD', TRUE)`,
      [custId, custPhone]
    );

    // 3. Seed Single Rider X
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, tier, status)
       VALUES ($1, $1, $2, 'Rider X Single Fleet', 'DL-01-AB-1234', 'TWO_WHEELER', 'STANDARD', 'ACTIVE')`,
      [riderId, riderPhone]
    );

    await pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_seen_at)
       VALUES ($1, 'ONLINE', NOW())`,
      [riderId]
    );

    // 4. Seed Two Distinct Orders
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
       VALUES 
       ($1, $1, $3, $5, 'PLACED', 250.00, '{"addressLine": "Cyber City"}', '[]', 'hashA'),
       ($2, $2, $4, $6, 'PLACED', 300.00, '{"addressLine": "Cyber City"}', '[]', 'hashB')`,
      [orderAId, orderBId, custId, custId, storeId, storeId]
    );

    // 5. Seed Two Distinct Delivery Sessions
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng, is_cod, cod_amount, otp_verified)
       VALUES 
       ($1, $1, $3, $5, 'LOOKING_FOR_RIDER', 'Race Store Hub', 'DLF Cyber City', 28.4595, 77.0266, 'Race Customer', 'Cyber City', 28.4610, 77.0310, FALSE, 0, FALSE),
       ($2, $2, $4, $6, 'LOOKING_FOR_RIDER', 'Race Store Hub', 'DLF Cyber City', 28.4595, 77.0266, 'Race Customer', 'Cyber City', 28.4610, 77.0310, FALSE, 0, FALSE)`,
      [deliveryAId, deliveryBId, orderAId, orderBId, storeId, storeId]
    );

    // 6. Seed Two Active Offers for the SAME Rider X
    const now = Date.now();
    const expiresAt = Date.now() + 45000;
    await pool.query(
      `INSERT INTO offers (id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id, status, offer_created_at, offer_expires_at, earnings_amount, delivery_distance_km, total_distance_km, estimated_duration_mins, pricing_snapshot, history)
       VALUES 
       ($1, $1, 'evt_A', 'notif_A', $3, $5, $7, 'CREATED', $8, $9, 45.00, 2.0, 2.0, 8, '{}', '[]'),
       ($2, $2, 'evt_B', 'notif_B', $4, $6, $7, 'CREATED', $8, $9, 50.00, 2.5, 2.5, 9, '{}', '[]')`,
      [offerAId, offerBId, deliveryAId, deliveryBId, orderAId, orderBId, riderId, now, expiresAt]
    );

    const riderProfile = {
      realName: 'Rider X Single Fleet',
      realPhone: riderPhone,
      realVehicle: 'DL-01-AB-1234'
    };

    // 7. Fire Simultaneous Competing Acceptances for Rider X on both deliveries
    const [resA, resB] = await Promise.all([
      offerRepo.acceptOfferTransactionally(offerAId, riderId, riderProfile),
      offerRepo.acceptOfferTransactionally(offerBId, riderId, riderProfile)
    ]);

    const successes = [resA, resB].filter(r => r.ok && r.httpStatus === 200);
    const rejections = [resA, resB].filter(r => !r.ok && (r.httpStatus === 409 || r.error === 'RIDER_ALREADY_ASSIGNED' || r.error === 'OFFER_CLAIMED'));

    assert.strictEqual(successes.length, 1, 'Exactly ONE acceptance must succeed for the rider');
    assert.strictEqual(rejections.length, 1, 'The competing delivery assignment must be rejected');

    // 8. Verify in Database that Rider X is assigned to exactly ONE active delivery
    const activeAssignedRes = await pool.query(
      `SELECT delivery_id, state FROM delivery_sessions WHERE rider_id = $1 AND state = 'ACCEPTED'`,
      [riderId]
    );
    assert.strictEqual(activeAssignedRes.rows.length, 1, 'Rider X must have exactly 1 active accepted delivery session in PostgreSQL');

    console.log('  ✅ PASS: Concurrent Rider Double-Assignment Race (Exactly 1 delivery assigned, conflicting assignment rejected with 409)\n');
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id IN ($1, $2)`, [deliveryAId, deliveryBId]);
    await pool.query(`DELETE FROM offers WHERE delivery_id IN ($1, $2)`, [deliveryAId, deliveryBId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE id IN ($1, $2)`, [deliveryAId, deliveryBId]);
    await pool.query(`DELETE FROM orders WHERE id IN ($1, $2)`, [orderAId, orderBId]);
    await pool.query(`DELETE FROM rider_presence WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE id = $1`, [riderId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for rider-race.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Rider Race Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
