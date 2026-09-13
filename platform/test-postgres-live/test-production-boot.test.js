/**
 * Commerce OS — Production Server Boot & Fail-Fast Integrity Test
 * 
 * Verifies:
 * 1. Fail-Fast on Missing DATABASE_URL: Server exits with status code 1.
 * 2. Fail-Fast on Missing OSRM_BASE_URL: Server exits with status code 1 (no silent fallback).
 * 3. Fail-Fast on Missing JWT_SECRET: Server exits with status code 1 (no source code fallback).
 * 4. Fail-Fast on Missing COMMERCEOS_OTP_PEPPER: Server exits with status code 1.
 * 5. Clean Boot & Readiness: Production server boots successfully with valid environment and /api/v1/orders/ready responds 200 READY.
 */

const assert = require('assert');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');

const SERVER_SCRIPT = path.join(__dirname, '../server/production-server.js');
const TEST_PORT = 8099;

function runProcess(env) {
  return new Promise((resolve) => {
    const proc = spawn(process.execPath, [SERVER_SCRIPT], {
      cwd: path.join(__dirname, '../..'),
      env: { ...process.env, ...env, NODE_PATH: path.join(__dirname, '../../node_modules') },
      stdio: 'pipe'
    });

    let stdout = '';
    let stderr = '';
    proc.stdout.on('data', d => { stdout += d; });
    proc.stderr.on('data', d => { stderr += d; });

    proc.on('exit', (code) => {
      resolve({ code, stdout, stderr, proc });
    });
  });
}

async function runTest(pool) {
  console.log('🧪 [Live Postgres] Testing Production Server Boot & Fail-Fast Guards...');

  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error('DATABASE_URL is strictly required to test production boot');
  }

  const baseValidEnv = {
    ...process.env,
    DATABASE_URL: databaseUrl,
    PORT: String(TEST_PORT),
    OSRM_BASE_URL: 'http://router.project-osrm.org',
    JWT_SECRET: 'test_secret_for_boot',
    JWT_ISSUER: 'commerce-os-auth',
    JWT_AUDIENCE: 'commerce-os-api',
    COMMERCEOS_OTP_PEPPER: 'test_pepper',
    FCM_SERVER_KEY: 'test_fcm_key_11',
    FCM_ENDPOINT_URL: 'https://fcm.googleapis.com/fcm/send'
  };

  // 1. Test Fail-Fast when DATABASE_URL is missing
  const noDbRes = await runProcess({ ...baseValidEnv, DATABASE_URL: '' });
  assert.strictEqual(noDbRes.code, 1, 'Server must exit with code 1 when DATABASE_URL is missing');
  assert.ok((noDbRes.stderr + noDbRes.stdout).includes('DATABASE_URL is strictly required'), 'Must output fatal error message for missing DATABASE_URL');

  // 2. Test Fail-Fast when OSRM_BASE_URL is missing (No default fallback)
  const noOsrmRes = await runProcess({ ...baseValidEnv, OSRM_BASE_URL: '' });
  assert.strictEqual(noOsrmRes.code, 1, 'Server must exit with code 1 when OSRM_BASE_URL is missing');
  assert.ok((noOsrmRes.stderr + noOsrmRes.stdout).includes('OSRM_BASE_URL is strictly required'), 'Must output fatal error message for missing OSRM_BASE_URL');

  // 3. Test Fail-Fast when JWT_SECRET is missing (No source code fallback)
  const noJwtRes = await runProcess({ ...baseValidEnv, JWT_SECRET: '' });
  assert.strictEqual(noJwtRes.code, 1, 'Server must exit with code 1 when JWT_SECRET is missing');
  assert.ok((noJwtRes.stderr + noJwtRes.stdout).includes('JWT_SECRET is strictly required'), 'Must output fatal error message for missing JWT_SECRET');

  // 4. Test Fail-Fast when JWT_ISSUER is missing
  const noIssRes = await runProcess({ ...baseValidEnv, JWT_ISSUER: '' });
  assert.strictEqual(noIssRes.code, 1, 'Server must exit with code 1 when JWT_ISSUER is missing');
  assert.ok((noIssRes.stderr + noIssRes.stdout).includes('JWT_ISSUER is strictly required'), 'Must output fatal error message for missing JWT_ISSUER');

  // 5. Test Fail-Fast when JWT_AUDIENCE is missing
  const noAudRes = await runProcess({ ...baseValidEnv, JWT_AUDIENCE: '' });
  assert.strictEqual(noAudRes.code, 1, 'Server must exit with code 1 when JWT_AUDIENCE is missing');
  assert.ok((noAudRes.stderr + noAudRes.stdout).includes('JWT_AUDIENCE is strictly required'), 'Must output fatal error message for missing JWT_AUDIENCE');

  // 6. Test Fail-Fast when COMMERCEOS_OTP_PEPPER is missing
  const noPepperRes = await runProcess({ ...baseValidEnv, COMMERCEOS_OTP_PEPPER: '', OTP_PEPPER: '' });
  assert.strictEqual(noPepperRes.code, 1, 'Server must exit with code 1 when OTP pepper is missing');
  assert.ok((noPepperRes.stderr + noPepperRes.stdout).includes('COMMERCEOS_OTP_PEPPER is strictly required'), 'Must output fatal error message for missing OTP pepper');

  // 7. Test Fail-Fast when FCM_SERVER_KEY is missing
  const noFcmRes = await runProcess({ ...baseValidEnv, FCM_SERVER_KEY: '' });
  assert.strictEqual(noFcmRes.code, 1, 'Server must exit with code 1 when FCM_SERVER_KEY is missing');
  assert.ok((noFcmRes.stderr + noFcmRes.stdout).includes('FCM_SERVER_KEY is strictly required'), 'Must output fatal error message for missing FCM_SERVER_KEY');

  // 8. Test Fail-Fast when FCM_ENDPOINT_URL is missing
  const noFcmUrlRes = await runProcess({ ...baseValidEnv, FCM_ENDPOINT_URL: '' });
  assert.strictEqual(noFcmUrlRes.code, 1, 'Server must exit with code 1 when FCM_ENDPOINT_URL is missing');
  assert.ok((noFcmUrlRes.stderr + noFcmUrlRes.stdout).includes('FCM_ENDPOINT_URL is strictly required'), 'Must output fatal error message for missing FCM_ENDPOINT_URL');

  // 9. Test Fail-Closed Dependency Behavior (Missing jsonwebtoken or pg fails fast with FATAL_DEPENDENCY_ERROR)
  const nodeDepTest = (depName) => {
    return new Promise((resolve) => {
      const code = `
        delete require.cache[require.resolve('${SERVER_SCRIPT}')];
        const origResolve = require('module')._resolveFilename;
        require('module')._resolveFilename = function(request, parent, isMain, options) {
          if (request === '${depName}' || (typeof request === 'string' && request.includes('${depName}'))) {
            const err = new Error("Cannot find module '${depName}'");
            err.code = 'MODULE_NOT_FOUND';
            throw err;
          }
          return origResolve.apply(this, arguments);
        };
        try {
          require('${SERVER_SCRIPT}');
        } catch (e) {
          console.error(e.message);
        }
      `;
      const proc = spawn(process.execPath, ['-e', code], {
        cwd: path.join(__dirname, '../..'),
        env: { ...baseValidEnv },
        stdio: 'pipe'
      });
      let stderr = '';
      let stdout = '';
      proc.stderr.on('data', d => { stderr += d; });
      proc.stdout.on('data', d => { stdout += d; });
      proc.on('exit', (code) => resolve({ code, stdout, stderr }));
    });
  };

  const noJwtDepRes = await nodeDepTest('jsonwebtoken');
  assert.strictEqual(noJwtDepRes.code, 1, 'Server must exit code 1 when jsonwebtoken is unavailable');
  assert.ok((noJwtDepRes.stderr + noJwtDepRes.stdout).includes('FATAL_DEPENDENCY_ERROR: jsonwebtoken is strictly required'), 'Must output FATAL_DEPENDENCY_ERROR for jsonwebtoken');

  const noPgDepRes = await nodeDepTest('pg');
  assert.strictEqual(noPgDepRes.code, 1, 'Server must exit code 1 when pg is unavailable');
  assert.ok((noPgDepRes.stderr + noPgDepRes.stdout).includes('FATAL_DEPENDENCY_ERROR: pg (PostgreSQL) is strictly required'), 'Must output FATAL_DEPENDENCY_ERROR for pg');

  // 10. Test Successful Boot with Valid Configuration
  const validEnv = {
    ...process.env,
    DATABASE_URL: databaseUrl,
    PORT: String(TEST_PORT),
    JWT_SECRET: 'test_production_boot_secret_key_55991',
    JWT_ISSUER: 'commerce-os-auth',
    JWT_AUDIENCE: 'commerce-os-api',
    COMMERCEOS_OTP_PEPPER: 'test_boot_pepper_sec_key_112',
    OSRM_BASE_URL: 'http://router.project-osrm.org',
    FCM_SERVER_KEY: 'test_fcm_prod_key_771',
    FCM_ENDPOINT_URL: 'https://fcm.googleapis.com/fcm/send'
  };

  const proc = spawn(process.execPath, [SERVER_SCRIPT], {
    cwd: path.join(__dirname, '../..'),
    env: { ...validEnv, NODE_PATH: path.join(__dirname, '../../node_modules') },
    stdio: 'pipe'
  });

  let serverStdout = '';
  let serverStderr = '';
  proc.stdout.on('data', d => { serverStdout += d; });
  proc.stderr.on('data', d => { serverStderr += d; });

  try {
    let isReady = false;
    for (let i = 0; i < 40; i++) {
      try {
        const res = await new Promise((resolve, reject) => {
          const req = http.request({ hostname: '127.0.0.1', port: TEST_PORT, path: '/api/v1/orders/ready', method: 'GET' }, (r) => {
            let body = '';
            r.on('data', c => { body += c; });
            r.on('end', () => resolve({ status: r.statusCode, data: JSON.parse(body) }));
          });
          req.on('error', reject);
          req.end();
        });
        if (res.status === 200 && res.data.status === 'READY' && res.data.checks && ['UP', 'READY'].includes(res.data.checks.database)) {
          isReady = true;
          break;
        }
      } catch {
        await new Promise(r => setTimeout(r, 150));
      }
    }
    if (!isReady) {
      console.error('Server stdout:', serverStdout);
      console.error('Server stderr:', serverStderr);
    }
    assert.ok(isReady, 'Production server failed to respond with 200 READY on readiness endpoint');
    console.log('  ✅ PASS: Production Server Boot & Fail-Fast Integrity (4 fail-fast guards + live database readiness check)\n');
  } finally {
    proc.kill('SIGTERM');
  }
}

if (require.main === module) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('❌ FATAL: DATABASE_URL is strictly required for test-production-boot.test.js');
    process.exit(1);
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: databaseUrl });
  runTest(pool)
    .then(() => pool.end())
    .catch(err => {
      console.error('❌ FAIL: Boot Test Error:', err);
      pool.end();
      process.exit(1);
    });
}

module.exports = { runTest };
