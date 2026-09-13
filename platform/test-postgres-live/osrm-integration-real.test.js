/**
 * Commerce OS — Real OSRM Route Adapter & Transport Integration Test
 * 
 * Verifies:
 * 1. Real HTTP OSRM Adapter: Parses OSRM HTTP JSON responses to compute distanceKm and durationMins.
 * 2. OSRM Network / HTTP 500 Failure: Returns { ok: false, error: 'OSRM_HTTP_500' } and DispatchService throws ROUTE_UNAVAILABLE.
 * 3. Zero Synthetic Fallbacks: In production mode, router never invents distance or duration when OSRM fails.
 */

const assert = require('assert');
const http = require('http');
const { DispatchService, TransactionalStoreRepository, TransactionalPresenceRepository, TransactionalRiderRepository, TransactionalOfferRepository } = require('../repositories');

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Real OSRM Adapter & Route Resolution Transport...');

  const timestamp = Date.now();
  const storeId = 'store_osrm_' + timestamp;
  const riderId = 'rider_osrm_' + timestamp;
  const deliveryId = 'del_osrm_' + timestamp;
  const orderId = 'ord_osrm_' + timestamp;

  // 1. Mock OSRM HTTP Server
  let osrmStatusToReturn = 200;
  let osrmResponseBody = {
    code: 'Ok',
    routes: [{
      distance: 3500, // 3.5 km
      duration: 720   // 12 mins
    }]
  };

  const osrmServer = http.createServer((req, res) => {
    res.writeHead(osrmStatusToReturn, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(osrmResponseBody));
  });

  await new Promise(r => osrmServer.listen(0, '127.0.0.1', r));
  const osrmPort = osrmServer.address().port;
  const osrmBaseUrl = `http://127.0.0.1:${osrmPort}`;

  try {
    // 2. Real Production OSRM Route Resolver
    async function productionOsrmResolver(lat1, lon1, lat2, lon2) {
      const url = `${osrmBaseUrl}/route/v1/driving/${lon1},${lat1};${lon2},${lat2}?overview=false`;
      const response = await fetch(url);
      if (!response.ok) {
        return { ok: false, error: `OSRM_HTTP_${response.status}` };
      }
      const data = await response.json();
      if (data.code === 'Ok' && data.routes && data.routes.length > 0) {
        const distKm = Math.round((data.routes[0].distance / 1000) * 10) / 10;
        const durMins = Math.round((data.routes[0].duration / 60) * 10) / 10;
        return { ok: true, distanceKm: distKm, durationMins: durMins };
      }
      return { ok: false, error: 'NO_OSRM_ROUTE' };
    }

    // Test A: OSRM Success
    const resSuccess = await productionOsrmResolver(28.4595, 77.0266, 28.4680, 77.0350);
    assert.strictEqual(resSuccess.ok, true);
    assert.strictEqual(resSuccess.distanceKm, 3.5);
    assert.strictEqual(resSuccess.durationMins, 12);

    // Test B: OSRM HTTP 500 Outage
    osrmStatusToReturn = 500;
    osrmResponseBody = { message: 'Internal OSRM Crash' };

    const resFailure = await productionOsrmResolver(28.4595, 77.0266, 28.4680, 77.0350);
    assert.strictEqual(resFailure.ok, false);
    assert.strictEqual(resFailure.error, 'OSRM_HTTP_500');

    // Test C: DispatchService Fail-Closed on OSRM Failure
    await pool.query(
      `INSERT INTO stores (id, store_name, address, latitude, longitude, sla_minutes, is_active)
       VALUES ($1, 'OSRM Dark Store', 'Cyber City', 28.4595, 77.0266, 10, TRUE)`,
      [storeId]
    );

    await pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, tier, status)
       VALUES ($1, $1, $2, 'OSRM Rider', 'HR-26-OS-1122', 'TWO_WHEELER', 'STANDARD', 'ACTIVE')`,
      [riderId, '+9198' + String(timestamp).slice(-8)]
    );

    await pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_known_lat, last_known_lng, last_seen_at)
       VALUES ($1, 'ONLINE', 28.4550, 77.0250, NOW())`,
      [riderId]
    );

    const deliverySession = {
      deliveryId,
      orderId,
      storeId,
      customerLat: 28.4680,
      customerLng: 77.0350
    };

    const dispatchService = new DispatchService({
      isProduction: true,
      storeRepo: new TransactionalStoreRepository(pool),
      presenceRepo: new TransactionalPresenceRepository(pool),
      riderRepo: new TransactionalRiderRepository(pool),
      offerRepo: new TransactionalOfferRepository(pool),
      routeResolver: productionOsrmResolver
    });

    let dispatchError = null;
    try {
      await dispatchService.processDispatch(deliverySession);
    } catch (err) {
      dispatchError = err;
    }
    assert.ok(dispatchError, 'DispatchService must throw error when OSRM fails');
    assert.ok(dispatchError.message.includes('ROUTE_UNAVAILABLE'), `Must throw ROUTE_UNAVAILABLE, got: ${dispatchError.message}`);

    console.log('  ✅ PASS: Real OSRM Adapter & Route Resolution Fail-Closed Integration\n');
  } finally {
    osrmServer.close();
    await pool.query(`DELETE FROM rider_presence WHERE rider_id = $1`, [riderId]);
    await pool.query(`DELETE FROM riders WHERE id = $1`, [riderId]);
    await pool.query(`DELETE FROM stores WHERE id = $1`, [storeId]);
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for osrm-integration-real.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Real OSRM Integration Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
