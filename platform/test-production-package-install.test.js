/**
 * Commerce OS — Production Package Installation & Deployment Contract Verification
 *
 * Verifies:
 * 1. Root package.json strictly classifies mandatory runtime libraries (pg, jsonwebtoken) under dependencies.
 * 2. Root package.json does NOT classify pg or jsonwebtoken under devDependencies.
 * 3. A genuine clean isolated production installation (pnpm install --prod / npm install --omit=dev in an isolated temp directory) resolves and loads jsonwebtoken and pg cleanly.
 * 4. Production server entrypoint requires zero devDependencies (typescript, prettier, turbo).
 */

const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

function runTest() {
  console.log('================================================================');
  console.log('🧪 RUNNING PRODUCTION PACKAGE INSTALLATION & DEPLOYMENT CONTRACT');
  console.log('================================================================\n');

  const rootPkgPath = path.join(__dirname, '../package.json');
  assert.ok(fs.existsSync(rootPkgPath), 'Root package.json must exist');

  const pkg = JSON.parse(fs.readFileSync(rootPkgPath, 'utf8'));

  // 1. Assert jsonwebtoken is in dependencies
  assert.ok(pkg.dependencies, 'package.json must contain a dependencies section');
  assert.ok(pkg.dependencies.jsonwebtoken, 'jsonwebtoken must be in dependencies');
  assert.ok(!pkg.devDependencies || !pkg.devDependencies.jsonwebtoken, 'jsonwebtoken must NOT be in devDependencies');
  console.log('  ✅ PASS: jsonwebtoken strictly declared in production dependencies');

  // 2. Assert pg is in dependencies
  assert.ok(pkg.dependencies.pg, 'pg must be in dependencies');
  assert.ok(!pkg.devDependencies || !pkg.devDependencies.pg, 'pg must NOT be in devDependencies');
  console.log('  ✅ PASS: pg strictly declared in production dependencies');

  // 3. Perform a REAL isolated clean production installation in a temporary directory
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commerceos-prod-pkg-test-'));
  try {
    const isolatedPkg = {
      name: 'commerce-os-production-verification',
      version: '1.0.0',
      private: true,
      dependencies: {
        jsonwebtoken: pkg.dependencies.jsonwebtoken,
        pg: pkg.dependencies.pg
      }
    };
    fs.writeFileSync(path.join(tempDir, 'package.json'), JSON.stringify(isolatedPkg, null, 2));

    // Execute package installation in clean temp directory
    let installResult = spawnSync('pnpm', ['install', '--prod', '--ignore-scripts'], {
      cwd: tempDir,
      encoding: 'utf8',
      timeout: 30000
    });

    if (installResult.status !== 0) {
      // Fallback to npm if pnpm is not in current subprocess PATH
      installResult = spawnSync('npm', ['install', '--omit=dev', '--ignore-scripts'], {
        cwd: tempDir,
        encoding: 'utf8',
        timeout: 30000
      });
    }

    assert.strictEqual(installResult.status, 0, `Isolated production installation failed: ${installResult.stderr || installResult.stdout}`);

    // Verify modules exist in isolated tempDir/node_modules
    const nodeModulesPath = path.join(tempDir, 'node_modules');
    assert.ok(fs.existsSync(nodeModulesPath), 'Isolated node_modules must exist');
    assert.ok(fs.existsSync(path.join(nodeModulesPath, 'jsonwebtoken')), 'jsonwebtoken must be installed in isolated production directory');
    assert.ok(fs.existsSync(path.join(nodeModulesPath, 'pg')), 'pg must be installed in isolated production directory');

    // Execute node in isolated temp directory with NODE_PATH pointing strictly to tempDir
    const child = spawnSync(process.execPath, ['-e', `
      const jwt = require('jsonwebtoken');
      const { Pool } = require('pg');
      if (typeof jwt.verify !== 'function') throw new Error('jsonwebtoken verify is not a function');
      if (typeof Pool !== 'function') throw new Error('pg Pool is not a constructor');
      console.log('CLEAN_ISOLATED_PROD_INSTALL_LOAD_SUCCESS');
    `], {
      cwd: tempDir,
      env: {
        PATH: process.env.PATH,
        NODE_PATH: nodeModulesPath
      },
      encoding: 'utf8',
      timeout: 10000
    });

    assert.strictEqual(child.status, 0, `Isolated dependency execution failed: ${child.stderr || child.stdout}`);
    assert.ok(child.stdout.includes('CLEAN_ISOLATED_PROD_INSTALL_LOAD_SUCCESS'), 'Must confirm isolated production load success');
    console.log('  ✅ PASS: Real isolated production installation (pnpm/npm --prod) verified in clean temp directory');
  } finally {
    try {
      fs.rmSync(tempDir, { recursive: true, force: true });
    } catch (_) {}
  }

  // 4. Verify production server entrypoint does not import devDependencies
  const serverPath = path.join(__dirname, 'server/production-server.js');
  const serverSrc = fs.readFileSync(serverPath, 'utf8');
  const devDeps = ['typescript', 'prettier', 'turbo', '@types'];
  for (const dep of devDeps) {
    assert.ok(!serverSrc.includes(`require('${dep}')`) && !serverSrc.includes(`require("${dep}")`), `Production server must not require dev dependency: ${dep}`);
  }
  console.log('  ✅ PASS: Production server entrypoint requires ZERO devDependencies (typescript, prettier, turbo)');

  console.log('\n================================================================');
  console.log('🏆 ALL PRODUCTION DEPLOYMENT & PACKAGE CONTRACTS VERIFIED');
  console.log('================================================================\n');
}

if (require.main === module) {
  try {
    runTest();
    process.exit(0);
  } catch (err) {
    console.error('❌ FATAL: Production package verification failed:', err);
    process.exit(1);
  }
}

module.exports = { runTest };
