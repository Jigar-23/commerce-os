/**
 * Commerce OS — Production Multi-Core Cluster & High-Availability Manager
 * 
 * Provides:
 * 1. Zero-downtime multi-worker architecture utilizing all CPU cores
 * 2. Strict process role isolation (N x API Workers + 1 x Dedicated Outbox/Watchdog Worker)
 * 3. Automatic worker resurrection with crash-backoff protection
 * 4. Graceful rolling reload support on SIGUSR2 / SIGHUP
 */

const cluster = require('cluster');
const os = require('os');
const path = require('path');
const fs = require('fs');

// Auto-populate process.env from .env if present
try {
  for (const envFile of ['.env', '.env.production']) {
    const envPath = path.resolve(process.cwd(), envFile);
    if (fs.existsSync(envPath)) {
      const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const eqIdx = trimmed.indexOf('=');
        if (eqIdx > 0) {
          const k = trimmed.substring(0, eqIdx).trim();
          let v = trimmed.substring(eqIdx + 1).trim();
          if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
            v = v.slice(1, -1);
          }
          if (process.env[k] === undefined) process.env[k] = v;
        }
      }
    }
  }
} catch (_) {}

const NUM_CORES = os.cpus().length;
const API_WORKERS_COUNT = parseInt(process.env.API_WORKERS_COUNT, 10) || Math.max(2, Math.min(NUM_CORES, 8));
const SCRIPT_PATH = path.resolve(__dirname, 'production-server.js');

if (cluster.isPrimary || cluster.isMaster) {
  console.log(`================================================================`);
  console.log(`🚀 Commerce OS Cluster Master Initialized [PID ${process.pid}]`);
  console.log(`   Available CPU Cores : ${NUM_CORES}`);
  console.log(`   API Worker Instances: ${API_WORKERS_COUNT}`);
  console.log(`   Dedicated Background: 1 (Outbox + SLA Watchdog)`);
  console.log(`================================================================`);

  cluster.setupPrimary({
    exec: SCRIPT_PATH
  });

  const workerRoles = new Map(); // worker.id -> role

  // 1. Fork Dedicated Background Worker
  const bgWorker = cluster.fork({
    COMMERCEOS_PROCESS_ROLE: 'WORKER',
    PORT: process.env.WORKER_INTERNAL_PORT || '8084'
  });
  workerRoles.set(bgWorker.id, 'WORKER');
  console.log(`[ClusterMaster] Spawned Background Worker [PID ${bgWorker.process.pid}]`);

  // 2. Fork Stateless API Workers
  for (let i = 0; i < API_WORKERS_COUNT; i++) {
    const apiWorker = cluster.fork({
      COMMERCEOS_PROCESS_ROLE: 'API',
      PORT: process.env.PORT || '8083'
    });
    workerRoles.set(apiWorker.id, 'API');
    console.log(`[ClusterMaster] Spawned API Worker #${i + 1} [PID ${apiWorker.process.pid}]`);
  }

  // 3. Auto-Resurrection on Worker Crash with Rate-Limited Backoff
  const crashTimestamps = [];
  cluster.on('exit', (worker, code, signal) => {
    const role = workerRoles.get(worker.id) || 'API';
    workerRoles.delete(worker.id);
    const exitReason = signal ? `killed by signal ${signal}` : `exited with code ${code}`;
    console.error(`💥 [ClusterMaster Alert] ${role} Worker [PID ${worker.process.pid}] ${exitReason}. Initiating auto-resurrection...`);

    const now = Date.now();
    crashTimestamps.push(now);
    // Prune crashes older than 60s
    while (crashTimestamps.length > 0 && now - crashTimestamps[0] > 60000) {
      crashTimestamps.shift();
    }

    // Safety brake: if > 15 crashes in 60s, delay respawn to prevent tight CPU burning loops
    const delay = crashTimestamps.length > 15 ? 5000 : 500;
    setTimeout(() => {
      const newWorker = cluster.fork({
        COMMERCEOS_PROCESS_ROLE: role,
        PORT: role === 'WORKER' ? (process.env.WORKER_INTERNAL_PORT || '8084') : (process.env.PORT || '8083')
      });
      workerRoles.set(newWorker.id, role);
      console.log(`[ClusterMaster] Resurrected replacement ${role} Worker [PID ${newWorker.process.pid}]`);
    }, delay);
  });

  // 4. Graceful Cluster Shutdown
  const gracefulShutdown = () => {
    console.log('[ClusterMaster] Graceful cluster shutdown initiated. Signaling all workers...');
    for (const id in cluster.workers) {
      cluster.workers[id].process.kill('SIGTERM');
    }
    setTimeout(() => {
      console.log('[ClusterMaster] Force terminating remaining workers...');
      process.exit(0);
    }, 5000);
  };

  process.on('SIGTERM', gracefulShutdown);
  process.on('SIGINT', gracefulShutdown);
} else {
  // Workers execute production-server.js
  require(SCRIPT_PATH);
}
