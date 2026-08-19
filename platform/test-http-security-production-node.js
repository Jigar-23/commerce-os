/**
 * Commerce OS — HTTP Security Suite [Profile: Node Production Gateway]
 * 
 * Verifies production gateway HTTP security gates:
 * 1. Unauthenticated requests -> 401
 * 2. Cross-store seller access -> 403 / store isolated
 * 3. Cross-customer order access -> 403 / 404
 * 4. Imposter rider actions -> 403
 * 5. Invalid payment methods -> 400 INVALID_PAYMENT_METHOD
 * 6. Direct customer status mutations -> 403 / 404 / 405
 */

const { runHttpSecurityTests } = require('./test-http-security-gates');

if (require.main === module) {
  runHttpSecurityTests()
    .then(() => {
      process.exit(0);
    })
    .catch(err => {
      console.error('Fatal Production Node HTTP security test error:', err);
      process.exit(1);
    });
}

module.exports = { runHttpSecurityTests };
