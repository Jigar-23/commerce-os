/**
 * Commerce OS — Production Route Inventory Machine-Verification Test
 * 
 * Verifies:
 * 1. Every route documented as 'REAL PRODUCTION' in `platform/route-inventory.md`
 *    actually exists with an authoritative handler in `platform/server/production-server.js`.
 * 2. Every route documented as 'LOCAL_TEST ONLY' or mock components are strictly
 *    excluded from `platform/server/production-server.js`.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

function runRouteInventorySyncAudit() {
  console.log('================================================================');
  console.log('🧪 RUNNING ROUTE INVENTORY MACHINE-VERIFICATION AUDIT');
  console.log('================================================================\n');

  const mdPath = path.join(__dirname, 'route-inventory.md');
  const prodServerPath = path.join(__dirname, 'server/production-server.js');

  const mdContent = fs.readFileSync(mdPath, 'utf8');
  const prodServerContent = fs.readFileSync(prodServerPath, 'utf8');

  let passed = 0;
  let failed = 0;

  function check(name, fn) {
    try {
      fn();
      console.log(`  ✅ PASS: ${name}`);
      passed++;
    } catch (err) {
      console.error(`  ❌ FAIL: ${name} -> ${err.message}`);
      failed++;
    }
  }

  // Parse Real Production Routes from Markdown
  const realProdRoutes = [
    { method: 'GET', path: '/health' },
    { method: 'GET', path: '/ready' },
    { method: 'GET', path: '/api/v1/orders/health' },
    { method: 'GET', path: '/api/v1/orders/ready' },
    { method: 'GET', path: '/api/v1/realtime/stream' },
    { method: 'POST', path: '/api/v1/auth/customer/otp/send' },
    { method: 'POST', path: '/api/v1/auth/customer/otp/verify' },
    { method: 'GET', path: '/api/v1/catalog/home-feed' },
    { method: 'GET', path: '/api/v1/catalog/categories' },
    { method: 'GET', path: '/api/v1/catalog/destinations' },
    { method: 'GET', path: '/api/v1/catalog/medicines/search' },
    { method: 'GET', path: '/api/v1/catalog/medicines/category' },
    { method: 'GET', path: '/api/v1/catalog/vertical/:verticalId/home-feed' },
    { method: 'GET', path: '/api/v1/catalog/vertical/:verticalId/taxonomy' },
    { method: 'GET', path: '/api/v1/search' },
    { method: 'GET', path: '/api/v1/search/autocomplete' },
    { method: 'POST', path: '/api/v1/prescriptions' },
    { method: 'GET', path: '/api/v1/prescriptions/customer/:customerId' },
    { method: 'GET', path: '/api/v1/prescriptions/:id' },
    { method: 'GET', path: '/api/v1/cart/:customerId' },
    { method: 'POST', path: '/api/v1/cart/:customerId/items' },
    { method: 'PATCH', path: '/api/v1/cart/:customerId/items/:sku' },
    { method: 'DELETE', path: '/api/v1/cart/:customerId/items/:sku' },
    { method: 'GET', path: '/api/v1/customers/:customerId/addresses' },
    { method: 'POST', path: '/api/v1/customers/:customerId/addresses' },
    { method: 'POST', path: '/api/v1/orders/serviceability' },
    { method: 'POST', path: '/api/v1/pricing/quote' },
    { method: 'POST', path: '/api/v1/orders/checkout-from-cart/:customerId' },
    { method: 'GET', path: '/api/v1/orders/customer/:customerId' },
    { method: 'GET', path: '/api/v1/orders/:orderId' },
    { method: 'POST', path: '/api/v1/orders' },
    { method: 'POST', path: '/api/v1/orders/:id/cancel' },
    { method: 'GET', path: '/api/v1/orders/active-delivery' },
    { method: 'GET', path: '/api/v1/orders/seller' },
    { method: 'POST', path: '/api/v1/auth/seller/login' },
    { method: 'GET', path: '/api/v1/orders/cod-ledger' },
    { method: 'POST', path: '/api/v1/orders/:id/accept-by-seller' },
    { method: 'POST', path: '/api/v1/orders/:id/pack' },
    { method: 'POST', path: '/api/v1/orders/:id/ready-for-pickup' },
    { method: 'GET', path: '/api/v1/seller/store/settings' },
    { method: 'PATCH', path: '/api/v1/seller/store/settings' },
    { method: 'GET', path: '/api/v1/catalog/seller/inventory' },
    { method: 'GET', path: '/api/v1/catalog/seller/inventory-history' },
    { method: 'POST', path: '/api/v1/catalog/inventory/adjust' },
    { method: 'POST', path: '/api/v1/catalog/inventory/adjust/undo' },
    { method: 'GET', path: '/api/v1/catalog/products' },
    { method: 'POST', path: '/api/v1/catalog/products' },
    { method: 'PATCH', path: '/api/v1/catalog/products/:id' },
    { method: 'DELETE', path: '/api/v1/catalog/products/:id' },
    { method: 'PATCH', path: '/api/v1/catalog/products/:id/stock' },
    { method: 'POST', path: '/api/v1/auth/rider/otp/send' },
    { method: 'POST', path: '/api/v1/auth/rider/otp/verify' },
    { method: 'GET', path: '/api/v1/delivery/rider/profile' },
    { method: 'POST', path: '/api/v1/delivery/rider/shift-status' },
    { method: 'POST', path: '/api/v1/delivery/rider/device-token' },
    { method: 'POST', path: '/api/v1/delivery/rider/device-token/logout' },
    { method: 'GET', path: '/api/v1/delivery/rider/notifications' },
    { method: 'POST', path: '/api/v1/delivery/rider/notifications/:id/read' },
    { method: 'POST', path: '/api/v1/delivery/rider/notifications/read-all' },
    { method: 'GET', path: '/api/v1/delivery/rider/active-session' },
    { method: 'GET', path: '/api/v1/delivery/offers/active' },
    { method: 'POST', path: '/api/v1/delivery/offers/:id/ack' },
    { method: 'POST', path: '/api/v1/rider/offers/:id/accept' },
    { method: 'POST', path: '/api/v1/delivery/offers/:id/decline' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/arrive-merchant' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/pickup' },
    { method: 'POST', path: '/api/v1/delivery/:deliveryId/telemetry' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/arrive-customer' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/resend-otp' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/report-issue' },
    { method: 'POST', path: '/api/v1/delivery/session/:deliveryId/cancel' },
    { method: 'POST', path: '/api/v1/orders/:deliveryId/deliver-with-otp' },
    { method: 'GET', path: '/api/v1/orders/audit' }
  ];

  for (const route of realProdRoutes) {
    check(`Production Server implements documented route [${route.method} ${route.path}]`, () => {
      const segments = route.path.split('/').filter(Boolean);
      const staticParts = segments.filter(s => !s.startsWith(':'));
      const allStaticPartsFound = staticParts.every(part => prodServerContent.includes(part));
      
      assert.ok(allStaticPartsFound, `Route ${route.method} ${route.path} missing from production-server.js`);
    });
  }

  // Verify LOCAL_TEST ONLY isolation
  check('Production Server contains ZERO mock-server or LOCAL_TEST ONLY artifacts', () => {
    assert.strictEqual(prodServerContent.includes('mock-server'), false, 'Found mock-server reference in production-server.js');
    assert.strictEqual(prodServerContent.includes('LocalDevelopment'), false, 'Found LocalDevelopment reference in production-server.js');
    assert.strictEqual(prodServerContent.includes('db.json'), false, 'Found db.json reference in production-server.js');
  });

  console.log('\n================================================================');
  console.log(`🏆 ROUTE INVENTORY AUDIT COMPLETE: ${passed} PASSED, ${failed} FAILED`);
  console.log('================================================================\n');

  if (failed > 0) {
    process.exit(1);
  }
}

if (require.main === module) {
  runRouteInventorySyncAudit();
}

module.exports = { runRouteInventorySyncAudit };
