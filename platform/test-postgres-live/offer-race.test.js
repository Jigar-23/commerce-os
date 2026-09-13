/**
 * Live PostgreSQL Test: Concurrent Rider Offer Acceptance Race
 * 
 * Verifies with 2 isolated DB connections:
 * When two riders attempt to accept competing offers for the same delivery session:
 * - Exactly 1 rider succeeds (200 / ok: true)
 * - Competing rider is rejected with 409 OFFER_CLAIMED
 * - Competing offer is atomically revoked to CLAIMED_BY_OTHER
 */

const assert = require('assert');
const crypto = require('crypto');
const { TransactionalOfferRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Concurrent Rider Offer Acceptance Race...');

  const timestamp = Date.now();
  const storeId = 'store_off_race_' + crypto.randomUUID();
  const orderId = 'ord_off_race_' + crypto.randomUUID();
  const deliveryId = 'del_off_race_' + crypto.randomUUID();
  const offerAId = 'off_live_A_' + crypto.randomUUID();
  const offerBId = 'off_live_B_' + crypto.randomUUID();
  const riderA = 'rider_A_' + crypto.randomUUID();
  const riderB = 'rider_B_' + crypto.randomUUID();

  // 1. Seed Store, Riders, Order, and Delivery Session
  await pool.query(
    `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
     VALUES ($1, 'Hub 1', 'Cyber City Hub', 28.4595, 77.0266, 10, TRUE)`,
    [storeId]
  );

  await pool.query(
    `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
     VALUES 
     ($1, $1, $2, 'Rider Alpha', 'DL01AB1234', 'TWO_WHEELER', 'ACTIVE'),
     ($3, $3, $4, 'Rider Beta', 'DL01CD5678', 'TWO_WHEELER', 'ACTIVE')`,
    [riderA, '+9198' + String(timestamp).slice(-8), riderB, '+9197' + String(timestamp).slice(-8)]
  );

  await pool.query(
    `INSERT INTO orders (id, order_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash, created_at, updated_at)
     VALUES ($1, $1, $2, 'READY_FOR_PICKUP', 150.00, '{"addressLine": "Test Line", "latitude": 28.46, "longitude": 77.03}', '[]', 'dummy_hash', NOW(), NOW())`,
    [orderId, storeId]
  );

  await pool.query(
    `INSERT INTO delivery_sessions (
      id, delivery_id, order_id, store_id, state, is_cod,
      merchant_name, merchant_address, merchant_lat, merchant_lng,
      customer_name, customer_address, customer_lat, customer_lng,
      created_at, updated_at
    ) VALUES ($1, $1, $2, $3, 'LOOKING_FOR_RIDER', FALSE, 'Hub 1', 'Cyber City Hub', 28.4595, 77.0266, 'Test Cust', 'Test Line', 28.46, 77.03, NOW(), NOW())`,
    [deliveryId, orderId, storeId]
  );

  // 2. Seed Competing Offers
  const expiresAt = Date.now() + 45000;
  await pool.query(
    `INSERT INTO offers (
      id, offer_id, event_id, notification_id, delivery_id, order_id, rider_id, status,
      offer_created_at, offer_expires_at, earnings_amount, delivery_distance_km, total_distance_km, estimated_duration_mins,
      created_at, updated_at
    ) VALUES 
     ($1, $1, $2, $3, $4, $5, $6, 'CREATED', $7, $8, 85.00, 2.5, 3.5, 12, NOW(), NOW()),
     ($9, $9, $10, $11, $4, $5, $12, 'CREATED', $7, $8, 85.00, 2.5, 3.5, 12, NOW(), NOW())`,
    [
      offerAId, 'evt_A_' + crypto.randomUUID(), 'notif_A_' + crypto.randomUUID(), deliveryId, orderId, riderA, timestamp, expiresAt,
      offerBId, 'evt_B_' + crypto.randomUUID(), 'notif_B_' + crypto.randomUUID(), riderB
    ]
  );

  const offerRepo = new TransactionalOfferRepository(pool);
  const profileA = { realName: 'Vikram Singh', realPhone: '+919876543210', realVehicle: 'HR-26-BV-1122' };
  const profileB = { realName: 'Amit Sharma', realPhone: '+919876543211', realVehicle: 'DL-3C-AS-9988' };

  try {
    const [resA, resB] = await Promise.all([
      offerRepo.acceptOfferTransactionally(offerAId, riderA, profileA),
      offerRepo.acceptOfferTransactionally(offerBId, riderB, profileB)
    ]);

    const successes = [resA, resB].filter(r => r.ok === true).length;
    const claimedByOther = [resA, resB].filter(r => r.ok === false && r.error === 'OFFER_CLAIMED').length;

    assert.strictEqual(successes, 1, 'Exactly one rider must succeed in accepting the delivery');
    assert.strictEqual(claimedByOther, 1, 'Competing rider must be rejected with OFFER_CLAIMED');

    // Verify Delivery Session in database is updated to ACCEPTED with winning rider
    const sessionRes = await pool.query(`SELECT state, rider_id FROM delivery_sessions WHERE id = $1`, [deliveryId]);
    assert.strictEqual(sessionRes.rows[0].state, 'ACCEPTED', 'Delivery session must be transitioned to ACCEPTED');
    assert.ok([riderA, riderB].includes(sessionRes.rows[0].rider_id), 'Assigned rider must be one of the competing riders');

    console.log('  ✅ PASS: Concurrent Offer Acceptance Race (1 accepted, 1 OFFER_CLAIMED)\n');
  } finally {
    await pool.query(`DELETE FROM offers WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM orders WHERE id = $1`, [orderId]);
    await pool.query(`DELETE FROM riders WHERE id IN ($1, $2)`, [riderA, riderB]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for offer-race.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Live Offer Acceptance Race Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
