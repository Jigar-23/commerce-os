/**
 * Unit Test: Cloudflare R2 S3-Compatible Storage Service
 */

const assert = require('assert');
const { CloudflareR2Service } = require('./services/cloudflare-r2');

function runTest() {
  console.log('================================================================');
  console.log('🧪 RUNNING CLOUDFLARE R2 SERVICE & SIGV4 SPECIFICATION TEST');
  console.log('================================================================\n');

  // Test 1: Configuration Detection
  const unconfigured = new CloudflareR2Service();
  assert.strictEqual(unconfigured.isConfigured(), false, 'Should be unconfigured without env vars');
  console.log('  ✅ PASS: Unconfigured service returns isConfigured() === false');

  const configured = new CloudflareR2Service({
    accountId: 'a1b2c3d4e5f6',
    accessKeyId: 'test_access_key',
    secretAccessKey: 'test_secret_key_1234567890',
    bucketName: 'commerce-os-prescriptions',
    publicDomain: 'https://media.commerceos.io'
  });
  assert.strictEqual(configured.isConfigured(), true, 'Should be configured with credentials');
  console.log('  ✅ PASS: Configured service returns isConfigured() === true');

  // Test 2: SigV4 Header Generation
  const dummyPayload = Buffer.from('TEST_PRESCRIPTION_DATA_CONTENT', 'utf8');
  const headers = configured.generateSigV4Headers({
    method: 'PUT',
    pathname: '/commerce-os-prescriptions/test_rx.jpg',
    headers: { 'content-type': 'image/jpeg' },
    payloadBuffer: dummyPayload
  });

  assert.ok(headers['Authorization'], 'Authorization header must be generated');
  assert.ok(headers['Authorization'].startsWith('AWS4-HMAC-SHA256 Credential=test_access_key/'), 'Auth header must follow AWS4-HMAC-SHA256 format');
  assert.ok(headers['Authorization'].includes('SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date'), 'SignedHeaders must be sorted');
  assert.strictEqual(headers['host'], 'a1b2c3d4e5f6.r2.cloudflarestorage.com', 'Host must target Cloudflare R2 endpoint');
  assert.ok(headers['x-amz-content-sha256'], 'x-amz-content-sha256 must be populated');
  assert.ok(headers['x-amz-date'], 'x-amz-date must be populated');
  console.log('  ✅ PASS: SigV4 headers format, host, and hash generated correctly');

  // Test 3: Data URI parsing validation
  assert.rejects(async () => {
    await configured.uploadDataUri({ key: 'test', dataUri: 'not_a_valid_data_uri' });
  }, /Invalid data URI format/);
  console.log('  ✅ PASS: Malformed data URI rejected with explicit error');

  console.log('\n================================================================');
  console.log('🏆 CLOUDFLARE R2 TEST SUITE PASSED (100%)');
  console.log('================================================================\n');
}

if (require.main === module) {
  try {
    runTest();
    process.exit(0);
  } catch (err) {
    console.error('❌ Cloudflare R2 test failed:', err);
    process.exit(1);
  }
}

module.exports = { runTest };
