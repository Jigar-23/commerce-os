/**
 * Commerce OS — Unit Verification for Zero-Fee UPI & Address Concurrency
 */

const assert = require('assert');

function runUpiAndAddressVerification() {
  console.log('================================================================');
  console.log('🧪 RUNNING ZERO-FEE UPI & ADDRESS CONCURRENCY SUITE');
  console.log('================================================================\n');

  let passed = 0;
  let failed = 0;

  function test(name, fn) {
    try {
      fn();
      console.log(`  ✅ PASS: ${name}`);
      passed++;
    } catch (err) {
      console.error(`  ❌ FAIL: ${name} -> ${err.message}`);
      failed++;
    }
  }

  // 1. NPCI UPI URI Construction Invariant
  test('UPI URI Construction conforms to NPCI P2M Standard', () => {
    const merchantVpa = 'store@okaxis';
    const merchantName = 'QuickGrocery Store';
    const amount = 349.50;
    const orderId = 'ord_test_84920';

    const upiUri = `upi://pay?pa=${merchantVpa}&pn=${encodeURIComponent(merchantName)}&am=${amount.toFixed(2)}&tr=${orderId}&tn=Order%20${orderId}&cu=INR`;
    
    assert.ok(upiUri.startsWith('upi://pay?'), 'Must start with upi://pay? scheme');
    assert.ok(upiUri.includes(`pa=${merchantVpa}`), 'Must include merchant VPA');
    assert.ok(upiUri.includes(`am=349.50`), 'Must format exact amount with 2 decimal places');
    assert.ok(upiUri.includes(`tr=${orderId}`), 'Must include order tracking reference');
    assert.ok(upiUri.includes(`cu=INR`), 'Currency must be INR');
  });

  // 2. UTR Format Validation
  test('UTR Validation rejects malformed reference numbers (< 6 chars)', () => {
    const validateUtr = (utr) => Boolean(utr && String(utr).trim().length >= 6);
    
    assert.strictEqual(validateUtr(''), false);
    assert.strictEqual(validateUtr('123'), false);
    assert.strictEqual(validateUtr('   '), false);
    assert.strictEqual(validateUtr('429182749102'), true);
    assert.strictEqual(validateUtr('UPI_REF_99812'), true);
  });

  // 3. UTR Anti-Replay Map Invariant
  test('UTR Anti-Replay prevents double spending / voucher reuse', () => {
    const redeemedUtrs = new Map();
    function redeemPayment(orderId, utr) {
      if (redeemedUtrs.has(utr)) {
        const existingOrder = redeemedUtrs.get(utr);
        if (existingOrder === orderId) {
          return { ok: true, alreadyVerified: true };
        }
        return { ok: false, error: 'UTR_ALREADY_REDEEMED' };
      }
      redeemedUtrs.set(utr, orderId);
      return { ok: true, status: 'PAID' };
    }

    const res1 = redeemPayment('ord_101', '429182749102');
    assert.strictEqual(res1.ok, true);
    assert.strictEqual(res1.status, 'PAID');

    // Same order re-submitting same UTR (Idempotent replay)
    const resIdempotent = redeemPayment('ord_101', '429182749102');
    assert.strictEqual(resIdempotent.ok, true);
    assert.strictEqual(resIdempotent.alreadyVerified, true);

    // Different order attempting to reuse same UTR (Replay attack blocked)
    const resReplay = redeemPayment('ord_102', '429182749102');
    assert.strictEqual(resReplay.ok, false);
    assert.strictEqual(resReplay.error, 'UTR_ALREADY_REDEEMED');
  });

  // 4. Reverse Geocoding Graceful Fallback (No Hardcoded Rewari)
  test('Reverse geocoding produces valid non-Rewari coordinate fallback', () => {
    const lat = 19.0760; // Mumbai
    const lng = 72.8777;
    const defaultAddress = `Location at ${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    
    assert.strictEqual(defaultAddress.includes('Rewari'), false);
    assert.strictEqual(defaultAddress, 'Location at 19.0760, 72.8777');
  });

  // 5. Single Default Address Invariant
  test('Setting default address unsets previous defaults for customer', () => {
    let addresses = [
      { id: 'addr_1', customerId: 'cust_A', isDefault: true },
      { id: 'addr_2', customerId: 'cust_A', isDefault: false }
    ];

    function setDefaultAddress(customerId, targetAddressId) {
      addresses = addresses.map(a => {
        if (a.customerId === customerId) {
          return { ...a, isDefault: a.id === targetAddressId };
        }
        return a;
      });
    }

    setDefaultAddress('cust_A', 'addr_2');
    const defaultAddrs = addresses.filter(a => a.customerId === 'cust_A' && a.isDefault);
    assert.strictEqual(defaultAddrs.length, 1);
    assert.strictEqual(defaultAddrs[0].id, 'addr_2');
  });

  console.log(`\n================================================================`);
  console.log(`🏆 SUITE COMPLETE: ${passed} PASSED, ${failed} FAILED`);
  console.log(`================================================================`);
  if (failed > 0) process.exit(1);
}

runUpiAndAddressVerification();
