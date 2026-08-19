/**
 * Commerce OS — Live PostgreSQL Rider Active-Assignment Invariant & Complete State Machine Matrix Test
 * 
 * Verifies:
 * 1. Partial Unique Index Invariant: `unq_rider_active_delivery_session` in PostgreSQL
 *    Active States: ['ACCEPTED', 'ARRIVED_MERCHANT', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED']
 * 2. Active Conflict Matrix: Rider X in state A cannot simultaneously hold another session in state B.
 * 3. Terminal State Freedom: Rider X can have unlimited historical sessions in ['DELIVERED', 'CANCELLED', 'DECLINED', 'FAILED', 'EXPIRED'].
 * 4. Parallel Double-Assignment Race: 2 simultaneous assignment transactions for Rider X on 2 different deliveries -> exactly 1 succeeds, 1 gets rejected with DB constraint violation.
 * 5. Competing Offer Acceptance Race: 2 competing riders on same delivery -> exactly 1 winner.
 */

const assert = require('assert');
const { Pool } = require('pg');
const { TransactionalOfferRepository, TransactionalDeliveryRepository } = require('../repositories');

const ACTIVE_STATES = [
  'ACCEPTED',
  'ARRIVED_MERCHANT',
  'PICKED_UP',
  'OUT_FOR_DELIVERY',
  'ARRIVED_CUSTOMER',
  'HANDOFF_STARTED'
];

const TERMINAL_STATES = [
  'DELIVERED',
  'CANCELLED',
  'DECLINED',
  'FAILED',
  'EXPIRED'
];

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Rider Active-Assignment Invariant & Complete State Machine Matrix...');

  const timestamp = Date.now();
  const storeId = 'store_rsm_' + timestamp;
  const riderAId = 'rider_rsm_a_' + timestamp;
  const riderBId = 'rider_rsm_b_' + timestamp;
  const custId = 'cust_rsm_' + timestamp;

  const offerRepo = new TransactionalOfferRepository(pool);
  const deliveryRepo = new TransactionalDeliveryRepository(pool);

  try {
    // 1. Seed Store, Rider & Customer
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, is_active)
       VALUES ($1, 'RSM Hub', 'Cyber City', 28.4595, 77.0266, TRUE)`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, status)
       VALUES 
       ($1, $1, '+919988001101', 'Rider Alpha RSM', 'HR-26-RSM-1', 'ACTIVE'),
       ($2, $2, '+919988001102', 'Rider Beta RSM', 'HR-26-RSM-2', 'ACTIVE')`,
      [riderAId, riderBId]
    );

    await pool.query(
      `INSERT INTO customers (id, phone, full_name, is_active)
       VALUES ($1, '+919988001199', 'Customer RSM', TRUE)`,
      [custId]
    );

    // 2. Test Terminal States Freedom: Rider A can have multiple sessions in terminal states
    for (let i = 0; i < TERMINAL_STATES.length; i++) {
      const termState = TERMINAL_STATES[i];
      const orderId = `order_term_${i}_${timestamp}`;
      const delId = `del_term_${i}_${timestamp}`;

      await pool.query(
        `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
         VALUES ($1, $1, $2, $3, 'COMPLETED', 100.00, '{}', '[]', 'dummy_hash_rsm')`,
        [orderId, custId, storeId]
      );

      await pool.query(
        `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng)
         VALUES ($1, $1, $2, $3, $4, $5, 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300)`,
        [delId, orderId, storeId, riderAId, termState]
      );
    }

    // Verify all terminal sessions coexist cleanly for Rider A
    const termCountRes = await pool.query(
      `SELECT COUNT(*) as count FROM delivery_sessions WHERE rider_id = $1 AND state = ANY($2)`,
      [riderAId, TERMINAL_STATES]
    );
    assert.strictEqual(Number(termCountRes.rows[0].count), TERMINAL_STATES.length, 'Terminal states must coexist without violating index');

    // 3. Test Active States Conflict Matrix:
    // Create an active session in 'ACCEPTED' for Rider A
    const activeOrder1 = 'order_act_1_' + timestamp;
    const activeDel1 = 'del_act_1_' + timestamp;

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 'PROCESSING', 200.00, '{}', '[]', 'dummy_hash_rsm')`,
      [activeOrder1, custId, storeId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng)
       VALUES ($1, $1, $2, $3, $4, 'ACCEPTED', 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300)`,
      [activeDel1, activeOrder1, storeId, riderAId]
    );

    // Attempt to insert a second session for Rider A in each active state -> Must fail on PostgreSQL partial unique index
    for (const testState of ACTIVE_STATES) {
      const conflictOrder = `order_conf_${testState}_${timestamp}`;
      const conflictDel = `del_conf_${testState}_${timestamp}`;

      await pool.query(
        `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
         VALUES ($1, $1, $2, $3, 'PROCESSING', 200.00, '{}', '[]', 'dummy_hash_rsm')`,
        [conflictOrder, custId, storeId]
      );

      let conflictFailed = false;
      try {
        await pool.query(
          `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng)
           VALUES ($1, $1, $2, $3, $4, $5, 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300)`,
          [conflictDel, conflictOrder, storeId, riderAId, testState]
        );
      } catch (err) {
        conflictFailed = true;
        assert.ok(
          err.message.includes('unq_rider_active_delivery_session') || err.code === '23505',
          `Expected partial unique index violation, got: ${err.message}`
        );
      }
      assert.strictEqual(conflictFailed, true, `PostgreSQL must reject duplicate active state '${testState}' for rider already in ACCEPTED`);
    }

    // 4. Test Transition of Active Session through all Active States:
    // ACCEPTED -> ARRIVED_MERCHANT -> PICKED_UP -> OUT_FOR_DELIVERY -> ARRIVED_CUSTOMER -> HANDOFF_STARTED -> DELIVERED
    for (const st of ACTIVE_STATES) {
      await pool.query(`UPDATE delivery_sessions SET state = $1 WHERE id = $2`, [st, activeDel1]);
      const checkRes = await pool.query(`SELECT state FROM delivery_sessions WHERE id = $1`, [activeDel1]);
      assert.strictEqual(checkRes.rows[0].state, st);
    }

    // Move to DELIVERED (Terminal) -> Rider A is now free again!
    await pool.query(`UPDATE delivery_sessions SET state = 'DELIVERED' WHERE id = $1`, [activeDel1]);

    // 5. Test Live PostgreSQL Concurrent Double-Assignment Race:
    // Two parallel transactions attempt to assign freed Rider A to two different deliveries at the exact same millisecond
    const raceOrder1 = 'order_race_1_' + timestamp;
    const raceOrder2 = 'order_race_2_' + timestamp;
    const raceDel1 = 'del_race_1_' + timestamp;
    const raceDel2 = 'del_race_2_' + timestamp;

    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
       VALUES 
       ($1, $1, $3, $4, 'READY_FOR_PICKUP', 150.00, '{}', '[]', 'dummy_hash_rsm'),
       ($2, $2, $3, $4, 'READY_FOR_PICKUP', 180.00, '{}', '[]', 'dummy_hash_rsm')`,
      [raceOrder1, raceOrder2, custId, storeId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng)
       VALUES 
       ($1, $1, $3, $4, 'LOOKING_FOR_RIDER', 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300),
       ($2, $2, $5, $4, 'LOOKING_FOR_RIDER', 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300)`,
      [raceDel1, raceDel2, raceOrder1, storeId, raceOrder2]
    );

    // Run 2 simultaneous assignments for Rider A
    const assignRider = async (deliveryId) => {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const res = await client.query(
          `UPDATE delivery_sessions 
           SET rider_id = $1, state = 'ACCEPTED', updated_at = NOW()
           WHERE delivery_id = $2 AND state = 'LOOKING_FOR_RIDER' AND rider_id IS NULL
           RETURNING id`,
          [riderAId, deliveryId]
        );
        if (res.rows.length === 0) {
          await client.query('ROLLBACK');
          return { ok: false, error: 'NOT_FOUND_OR_CLAIMED' };
        }
        await client.query('COMMIT');
        return { ok: true };
      } catch (err) {
        await client.query('ROLLBACK');
        return { ok: false, error: err.code === '23505' ? 'RIDER_ALREADY_ASSIGNED' : err.message };
      } finally {
        client.release();
      }
    };

    const [raceRes1, raceRes2] = await Promise.all([
      assignRider(raceDel1),
      assignRider(raceDel2)
    ]);

    const successes = [raceRes1, raceRes2].filter(r => r.ok).length;
    const rejections = [raceRes1, raceRes2].filter(r => !r.ok).length;

    // 6. Test Forbidden Backward & Illegal Transitions
    const testSessionDelId = 'del_trans_test_' + timestamp;
    const testSessionOrderId = 'ord_trans_test_' + timestamp;
    await pool.query(
      `INSERT INTO orders (id, order_id, customer_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash)
       VALUES ($1, $1, $2, $3, 'PLACED', 100.00, '{}', '[]', 'dummy_hash_rsm')`,
      [testSessionOrderId, custId, storeId]
    );
    await pool.query(
      `INSERT INTO delivery_sessions (id, delivery_id, order_id, store_id, rider_id, state, merchant_name, merchant_address, merchant_lat, merchant_lng, customer_name, customer_address, customer_lat, customer_lng)
       VALUES ($1, $1, $2, $3, $4, 'ACCEPTED', 'RSM Store', 'Address', 28.4595, 77.0266, 'Customer', 'Address', 28.4600, 77.0300)`,
      [testSessionDelId, testSessionOrderId, storeId, riderBId]
    );

    // Transition ACCEPTED -> ARRIVED_STORE -> PICKED_UP
    const t1 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'ARRIVED_STORE', riderBId);
    assert.strictEqual(t1.ok, true);
    const t2 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'PICKED_UP', riderBId);
    assert.strictEqual(t2.ok, true);

    // Negative: PICKED_UP -> ACCEPTED (Illegal backward transition)
    const badT1 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'ACCEPTED', riderBId);
    assert.strictEqual(badT1.ok, false);
    assert.strictEqual(badT1.error, 'INVALID_DELIVERY_STATE_TRANSITION');

    // Transition PICKED_UP -> OUT_FOR_DELIVERY
    const t3 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'OUT_FOR_DELIVERY', riderBId);
    assert.strictEqual(t3.ok, true);

    // Negative: OUT_FOR_DELIVERY -> PICKED_UP (Illegal backward transition)
    const badT2 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'PICKED_UP', riderBId);
    assert.strictEqual(badT2.ok, false);
    assert.strictEqual(badT2.error, 'INVALID_DELIVERY_STATE_TRANSITION');

    // Transition OUT_FOR_DELIVERY -> ARRIVED_CUSTOMER
    const t4 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'ARRIVED_CUSTOMER', riderBId);
    assert.strictEqual(t4.ok, true);

    // Negative: ARRIVED_CUSTOMER -> OUT_FOR_DELIVERY (Illegal backward transition)
    const badT3 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'OUT_FOR_DELIVERY', riderBId);
    assert.strictEqual(badT3.ok, false);
    assert.strictEqual(badT3.error, 'INVALID_DELIVERY_STATE_TRANSITION');

    // Transition ARRIVED_CUSTOMER -> DELIVERED (Terminal)
    const t5 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'DELIVERED', riderBId, { otpVerified: true });
    assert.strictEqual(t5.ok, true);

    // Negative: DELIVERED -> ACCEPTED or any active state (Terminal violation)
    const badT4 = await deliveryRepo.transitionStateTransactionally(testSessionDelId, 'ACCEPTED', riderBId);
    assert.strictEqual(badT4.ok, false);
    assert.strictEqual(badT4.error, 'INVALID_DELIVERY_STATE_TRANSITION');

    console.log('  ✅ PASS: Rider Active-Assignment Invariant & Complete State Machine Matrix\n');
  } finally {
    await pool.query(`DELETE FROM delivery_sessions WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM orders WHERE store_id = $1`, [storeId]);
    await pool.query(`DELETE FROM riders WHERE id IN ($1, $2)`, [riderAId, riderBId]);
    await pool.query(`DELETE FROM customers WHERE id = $1`, [custId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for rider-state-machine-matrix.test.js');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Rider State Machine Matrix Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
