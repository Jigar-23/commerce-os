/**
 * Live PostgreSQL Test: Authoritative Dispatch Engine
 * 
 * Verifies DispatchService behavior against real PostgreSQL database:
 * - Case A: Valid store + eligible online fleet rider + OSRM success -> real route, actual distance, offer created
 * - Case B: Valid store + no eligible online rider -> throws NO_RIDERS_AVAILABLE (zero fake riders)
 * - Case C: Valid store + OSRM route failure -> throws ROUTE_UNAVAILABLE (zero synthetic 3.5km/12min ETA)
 */

const assert = require('assert');
const crypto = require('crypto');
const {
  TransactionalStoreRepository,
  TransactionalPresenceRepository,
  TransactionalRiderRepository,
  TransactionalOfferRepository,
  DispatchService
} = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Authoritative Dispatch Engine (Real fleet & route resolution)...');

  const timestamp = Date.now();
  const storeId = 'store_disp_' + crypto.randomUUID();
  const riderId = 'rider_disp_' + crypto.randomUUID();
  const deliveryId = 'del_disp_' + crypto.randomUUID();
  const orderId = 'ord_disp_' + crypto.randomUUID();

  const storeRepo = new TransactionalStoreRepository(pool);
  const presenceRepo = new TransactionalPresenceRepository(pool);
  const riderRepo = new TransactionalRiderRepository(pool);
  const offerRepo = new TransactionalOfferRepository(pool);

  try {
    // 1. Seed Store
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'Cyber Hub Dark Store', 'DLF Cyber City, Gurugram', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    // 2. Seed Rider & Online Presence
    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status)
       VALUES ($1, $1, $2, 'Authorized Fleet Rider', 'HR-26-AB-9988', 'TWO_WHEELER', 'ACTIVE')`,
      [riderId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_known_lat, last_known_lng, last_seen_at)
       VALUES ($1, 'ONLINE', 28.4550, 77.0250, NOW())`,
      [riderId]
    );

    await pool.query(
      `INSERT INTO orders (id, order_id, store_id, status, total_amount, delivery_address, items, delivery_otp_hash, created_at, updated_at)
       VALUES ($1, $1, $2, 'READY_FOR_PICKUP', 250.00, '{"addressLine": "Flat 101", "latitude": 28.4680, "longitude": 77.0350}', '[]', 'hash_test', NOW(), NOW())`,
      [orderId, storeId]
    );

    await pool.query(
      `INSERT INTO delivery_sessions (
        id, delivery_id, order_id, store_id, state, is_cod,
        merchant_name, merchant_address, merchant_lat, merchant_lng,
        customer_name, customer_address, customer_lat, customer_lng,
        created_at, updated_at
      ) VALUES ($1, $1, $2, $3, 'READY_FOR_PICKUP', FALSE, 'Cyber Hub Dark Store', 'DLF Cyber City, Gurugram', 28.4595, 77.0266, 'Verified Customer', 'Flat 101, Palm Springs, Gurugram', 28.4680, 77.0350, NOW(), NOW())`,
      [deliveryId, orderId, storeId]
    );

    const deliverySession = {
      deliveryId,
      orderId,
      storeId,
      customerName: 'Verified Customer',
      customerAddress: 'Flat 101, Palm Springs, Gurugram',
      customerLat: 28.4680,
      customerLng: 77.0350,
      isCod: false,
      codAmount: 0
    };

    // Verify Presence DTO contains mapped lastKnownLat and lastKnownLng
    const eligibleRiders = await presenceRepo.getEligibleOnlineRiders();
    const riderPres = eligibleRiders.find(r => r.riderId === riderId);
    assert.ok(riderPres, 'Target rider must be in eligible online riders');
    assert.strictEqual(riderPres.lastKnownLat, 28.4550, 'Must map last_known_lat to lastKnownLat');
    assert.strictEqual(riderPres.lastKnownLng, 77.0250, 'Must map last_known_lng to lastKnownLng');

    // -------------------------------------------------------------
    // Case A: Valid Store + Eligible Fleet Rider + OSRM Success
    // -------------------------------------------------------------
    const routeCalls = [];
    const mockOsrmSuccess = async (sLat, sLng, dLat, dLng) => {
      routeCalls.push({ sLat, sLng, dLat, dLng });
      return {
        ok: true,
        distanceKm: 2.4,
        durationMins: 8.5
      };
    };

    const dispatchServiceA = new DispatchService({
      isProduction: true,
      storeRepo,
      presenceRepo,
      riderRepo,
      offerRepo,
      routeResolver: mockOsrmSuccess
    });

    const offer = await dispatchServiceA.processDispatch(deliverySession);
    assert.ok(offer, 'Offer must be generated');
    assert.strictEqual(offer.riderId, riderId, 'Must assign to authorized online rider');
    assert.strictEqual(offer.status, 'CREATED');

    // Verify routeResolver was called for rider->store AND store->customer legs
    assert.strictEqual(routeCalls.length, 2, 'Must calculate both rider->store and store->customer legs');
    assert.strictEqual(routeCalls[0].sLat, 28.4595, 'First leg store lat');
    assert.strictEqual(routeCalls[1].sLat, 28.455, 'Second leg rider lat (from rider_presence last_known_lat)');

    // Verify offer in DB
    const offerDb = await pool.query(`SELECT * FROM offers WHERE offer_id = $1`, [offer.offerId]);
    assert.strictEqual(offerDb.rows.length, 1);
    assert.strictEqual(offerDb.rows[0].rider_id, riderId);

    // -------------------------------------------------------------
    // Case B: Valid Store + No Eligible Online Rider
    // -------------------------------------------------------------
    // Set all riders OFFLINE in PostgreSQL for clean isolation
    await pool.query(`UPDATE rider_presence SET status = 'OFFLINE'`);

    const dispatchServiceB = new DispatchService({
      isProduction: true,
      storeRepo,
      presenceRepo,
      riderRepo,
      offerRepo,
      routeResolver: mockOsrmSuccess
    });

    let caseBError = null;
    try {
      await dispatchServiceB.processDispatch(deliverySession);
    } catch (e) {
      caseBError = e;
    }
    assert.ok(caseBError, 'Must fail when no online rider is available');
    assert.ok(caseBError.message.includes('NO_RIDERS_AVAILABLE'), `Must throw NO_RIDERS_AVAILABLE, got: ${caseBError.message}`);

    // -------------------------------------------------------------
    // Case C: Valid Store + OSRM Route Failure (Zero synthetic ETA)
    // -------------------------------------------------------------
    // Restore rider ONLINE
    await pool.query(`UPDATE rider_presence SET status = 'ONLINE' WHERE rider_id = $1`, [riderId]);

    const mockOsrmFailure = async () => ({ ok: false, error: 'OSRM_GATEWAY_TIMEOUT' });

    const dispatchServiceC = new DispatchService({
      isProduction: true,
      storeRepo,
      presenceRepo,
      riderRepo,
      offerRepo,
      routeResolver: mockOsrmFailure
    });

    let caseCError = null;
    try {
      await dispatchServiceC.processDispatch(deliverySession);
    } catch (e) {
      caseCError = e;
    }
    assert.ok(caseCError, 'Must fail when route calculation fails');
    assert.ok(caseCError.message.includes('ROUTE_UNAVAILABLE'), `Must throw ROUTE_UNAVAILABLE, got: ${caseCError.message}`);

    // -------------------------------------------------------------
    // Case D: Production Dispatch Fail-Fast without Route Resolver
    // -------------------------------------------------------------
    let caseDError = null;
    try {
      new DispatchService({
        isProduction: true,
        storeRepo,
        presenceRepo,
        riderRepo,
        offerRepo,
        routeResolver: null
      });
    } catch (e) {
      caseDError = e;
    }
    assert.ok(caseDError, 'Must fail fast when routeResolver is missing in production mode');
    assert.ok(caseDError.message.includes('FATAL_CONFIGURATION_ERROR'), `Must throw FATAL_CONFIGURATION_ERROR, got: ${caseDError?.message}`);

    // -------------------------------------------------------------
    // Case E: Target Rider Validation (Stale/Offline Target Rejected)
    // -------------------------------------------------------------
    await pool.query(`UPDATE rider_presence SET status = 'OFFLINE' WHERE rider_id = $1`, [riderId]);
    const dispatchServiceE = new DispatchService({
      isProduction: true,
      storeRepo,
      presenceRepo,
      riderRepo,
      offerRepo,
      routeResolver: mockOsrmSuccess
    });

    let caseEError = null;
    try {
      await dispatchServiceE.processDispatch(deliverySession, riderId);
    } catch (e) {
      caseEError = e;
    }
    assert.ok(caseEError, 'Must fail when target rider is offline');
    assert.ok(caseEError.message.includes('RIDER_NOT_ELIGIBLE'), `Must throw RIDER_NOT_ELIGIBLE, got: ${caseEError?.message}`);

    console.log('  ✅ PASS: Authoritative Dispatch Engine (Case A Success, Case B No Fleet, Case C Route Unavailable, Case D Missing Router Fail-Closed, Case E Rider Validation)\n');
  } finally {
    await pool.query(`DELETE FROM outbox_events WHERE aggregate_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM offers WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM delivery_sessions WHERE delivery_id = $1`, [deliveryId]);
    await pool.query(`DELETE FROM orders WHERE order_id = $1`, [orderId]);
    await pool.query(`DELETE FROM rider_presence WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for dispatch-real.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Dispatch Real Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
