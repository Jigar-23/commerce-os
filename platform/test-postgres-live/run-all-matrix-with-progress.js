/**
 * Commerce OS — Strict Master Acceptance Test Orchestrator & Live Progress Runner
 * 
 * Strict Orchestrator Invariants:
 * 1. Zero Synthetic Secret Injections: Passes actual process.env without manufacturing hardcoded fallback secrets.
 * 2. Fail-Closed Dependency Preflight: Verifies jsonwebtoken, pg, and runtime environment.
 * 3. Independent Execution & Failure Isolation: Every suite runs to completion or records strict preflight failure.
 * 4. Strict Accounting: Distinguishes PASSED, FAILED, and NOT EXECUTED across all 133 required tests.
 * 5. Zero False-Positive Tolerance: Exit code 0 is ONLY emitted when all 133 required tests execute and pass with 0 failures.
 */

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const REQUIRED_TOTAL_TESTS = 137;

const SUITES = [
  {
    id: 1,
    name: 'Production Static Guards',
    file: 'test-production-static-guards.test.js',
    expected: 70,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: false
  },
  {
    id: 2,
    name: 'Concurrency & Dedup Matrix',
    file: 'test-concurrency-and-dedup.js',
    expected: 10,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: false
  },
  {
    id: 3,
    name: 'FCM & Outbox Integration',
    file: 'test-fcm-outbox-integration.js',
    expected: 11,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: true
  },
  {
    id: 4,
    name: 'HTTP Security & Multi-Tenant Gates',
    file: 'test-http-security-gates.js',
    expected: 14,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: true
  },
  {
    id: 5,
    name: 'Local Development Contracts',
    file: 'test-postgres-live/run-local-contracts.js',
    expected: 4,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: false
  },
  {
    id: 6,
    name: 'Production Package & Deployment Contract',
    file: 'test-production-package-install.test.js',
    expected: 4,
    requiresDb: false,
    requiresPg: false,
    requiresJwt: false
  },
  {
    id: 7,
    name: 'Dedicated Live PostgreSQL & HTTP',
    file: 'test-postgres-live/run-all-live.js',
    expected: 24,
    requiresDb: true,
    requiresPg: true,
    requiresJwt: true
  }
];

let totalExecuted = 0;
let totalPassed = 0;
let totalFailed = 0;
const overallStartTime = Date.now();

function renderProgressBar(currentTest = '') {
  const width = 35;
  const ratio = Math.min(1, totalExecuted / REQUIRED_TOTAL_TESTS);
  const filled = Math.round(width * ratio);
  const empty = width - filled;
  const bar = '█'.repeat(filled) + '░'.repeat(empty);
  const percent = Math.round(ratio * 100);
  const elapsed = ((Date.now() - overallStartTime) / 1000).toFixed(1);
  const testDisplay = currentTest ? ` | ${currentTest.slice(0, 48)}` : '';
  process.stdout.write(`\r[${bar}] ${percent}% (${totalExecuted}/${REQUIRED_TOTAL_TESTS}) [${elapsed}s]${testDisplay}\x1b[K`);
}

function runDependencyPreflight() {
  console.log('===============================================================');
  console.log('🔍 COMMERCE OS ACCEPTANCE PREFLIGHT & RUNTIME ENVIRONMENT');
  console.log('===============================================================');

  const preflight = {
    jwt: false,
    pg: false,
    dbUrl: Boolean(process.env.DATABASE_URL)
  };

  try {
    require.resolve('jsonwebtoken', { paths: [process.cwd(), path.resolve(__dirname, '../../'), path.resolve(__dirname, '../../node_modules')] });
    preflight.jwt = true;
  } catch (_) {
    preflight.jwt = false;
  }

  try {
    require.resolve('pg', { paths: [process.cwd(), path.resolve(__dirname, '../../'), path.resolve(__dirname, '../../node_modules')] });
    preflight.pg = true;
  } catch (_) {
    preflight.pg = false;
  }

  console.log(`  • Node.js Version:      ${process.version}`);
  console.log(`  • Platform:             ${process.platform}`);
  console.log(`  • jsonwebtoken:         ${preflight.jwt ? 'AVAILABLE ✅' : 'MISSING ❌'}`);
  console.log(`  • pg (PostgreSQL):      ${preflight.pg ? 'AVAILABLE ✅' : 'MISSING ❌'}`);
  console.log(`  • DATABASE_URL:         ${preflight.dbUrl ? 'CONFIGURED ✅' : 'NOT CONFIGURED ⚠️'}`);
  console.log(`  • OSRM_BASE_URL:        ${process.env.OSRM_BASE_URL ? 'CONFIGURED ✅' : 'NOT CONFIGURED ⚠️'}`);
  console.log(`  • PREFLIGHT STATUS:     ${preflight.jwt && preflight.pg ? 'PASS ✅' : 'FAIL ❌'}`);
  console.log('===============================================================\n');

  return preflight;
}

async function executeSuite(suite, preflight) {
  const suiteStartTime = Date.now();
  const suitePath = path.resolve(__dirname, '../', suite.file);

  // Check preconditions strictly without synthetic defaults
  if (suite.requiresJwt && !preflight.jwt) {
    return {
      id: suite.id,
      name: suite.name,
      expected: suite.expected,
      passed: 0,
      failed: 0,
      notExecuted: suite.expected,
      exitCode: 1,
      duration: '0.00',
      status: 'FAIL (DEPENDENCY_MISSING: jsonwebtoken)',
      stdout: '',
      stderr: 'Missing jsonwebtoken module'
    };
  }

  if (suite.requiresPg && !preflight.pg) {
    return {
      id: suite.id,
      name: suite.name,
      expected: suite.expected,
      passed: 0,
      failed: 0,
      notExecuted: suite.expected,
      exitCode: 1,
      duration: '0.00',
      status: 'FAIL (DEPENDENCY_MISSING: pg)',
      stdout: '',
      stderr: 'Missing pg module'
    };
  }

  if (suite.requiresDb && !preflight.dbUrl) {
    return {
      id: suite.id,
      name: suite.name,
      expected: suite.expected,
      passed: 0,
      failed: 0,
      notExecuted: suite.expected,
      exitCode: 1,
      duration: '0.00',
      status: 'FAIL (CONFIGURATION_MISSING: DATABASE_URL)',
      stdout: '',
      stderr: 'DATABASE_URL environment variable is required for live PostgreSQL tests'
    };
  }

  return new Promise((resolve) => {
    let suitePassed = 0;
    let suiteFailed = 0;
    let stdoutBuffer = '';
    let stderrBuffer = '';

    renderProgressBar(`Starting: ${suite.name}...`);

    // Pass process.env directly without manufactured secrets
    const child = spawn(process.execPath, [suitePath], {
      cwd: path.resolve(__dirname, '../../'),
      env: {
        ...process.env
      },
      stdio: ['ignore', 'pipe', 'pipe']
    });

    let lineBuffer = '';
    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      stdoutBuffer += text;
      lineBuffer += text;
      const lines = lineBuffer.split('\n');
      lineBuffer = lines.pop();

      for (const line of lines) {
        if (line.includes('✅ PASS:')) {
          suitePassed++;
          totalExecuted++;
          totalPassed++;
          const testDesc = line.replace(/.*✅ PASS:\s*/, '').trim();
          renderProgressBar(testDesc);
        } else if (line.includes('❌ FAIL:')) {
          suiteFailed++;
          totalExecuted++;
          totalFailed++;
          const testDesc = line.replace(/.*❌ FAIL:\s*/, '').trim();
          renderProgressBar(`FAIL: ${testDesc}`);
        }
      }
    });

    child.stderr.on('data', (chunk) => {
      stderrBuffer += chunk.toString();
    });

    child.on('close', (code) => {
      const suiteDuration = ((Date.now() - suiteStartTime) / 1000).toFixed(2);
      const executedInSuite = suitePassed + suiteFailed;
      const notExecutedInSuite = Math.max(0, suite.expected - executedInSuite);

      const status = (code === 0 && suitePassed === suite.expected && suiteFailed === 0) ? 'PASS' : 'FAIL';

      resolve({
        id: suite.id,
        name: suite.name,
        expected: suite.expected,
        passed: suitePassed,
        failed: suiteFailed,
        notExecuted: notExecutedInSuite,
        exitCode: code,
        duration: suiteDuration,
        status,
        stdout: stdoutBuffer,
        stderr: stderrBuffer
      });
    });
  });
}

async function runAll() {
  const preflight = runDependencyPreflight();

  console.log(`🚀 EXECUTING MASTER ACCEPTANCE MATRIX (${REQUIRED_TOTAL_TESTS} REQUIRED TESTS)...\n`);

  const suiteResults = [];

  for (const suite of SUITES) {
    const result = await executeSuite(suite, preflight);
    suiteResults.push(result);
  }

  renderProgressBar('Acceptance run complete!');
  const totalDuration = ((Date.now() - overallStartTime) / 1000).toFixed(2);

  const totalNotExecuted = suiteResults.reduce((acc, s) => acc + s.notExecuted, 0);
  const allSuitesPassed = suiteResults.every(s => s.status === 'PASS');
  const overallSuccess = allSuitesPassed && totalPassed === REQUIRED_TOTAL_TESTS && totalFailed === 0 && totalNotExecuted === 0;

  console.log('\n\n===============================================================');
  console.log('FINAL VERIFICATION SUMMARY');
  console.log('===============================================================');
  console.log('');

  for (const r of suiteResults) {
    const label = `${r.id}. ${r.name}:`.padEnd(42, ' ');
    const countStr = `${r.passed} / ${r.expected}`.padStart(7, ' ');
    const statusStr = r.status === 'PASS' ? 'PASS' : `FAIL (${r.status})`;
    console.log(`${label} ${countStr}  ${statusStr}`);
  }

  console.log('');
  console.log('---------------------------------------------------------------');
  console.log(`REQUIRED TESTS:                               ${REQUIRED_TOTAL_TESTS}`);
  console.log(`EXECUTED:                                     ${totalExecuted}`);
  console.log(`PASSED:                                       ${totalPassed}`);
  console.log(`FAILED:                                       ${totalFailed}`);
  console.log(`NOT EXECUTED:                                 ${totalNotExecuted}`);
  console.log(`TOTAL DURATION:                               ${totalDuration}s`);
  console.log('---------------------------------------------------------------');
  console.log('');
  console.log(`OVERALL ACCEPTANCE: ${overallSuccess ? 'PASS' : 'FAIL'}`);
  console.log(`EXIT CODE:          ${overallSuccess ? '0' : '1'}`);
  console.log('===============================================================\n');

  if (!overallSuccess) {
    for (const r of suiteResults) {
      if (r.status !== 'PASS') {
        console.error(`\n❌ [SUITE FAILURE] ${r.name} (Exit code ${r.exitCode}):`);
        if (r.stderr) console.error(`Stderr:\n${r.stderr.slice(0, 500)}`);
      }
    }
    process.exit(1);
  }

  process.exit(0);
}

runAll().catch(err => {
  console.error('Fatal test runner orchestrator error:', err);
  process.exit(1);
});
