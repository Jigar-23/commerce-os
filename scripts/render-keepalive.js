#!/usr/bin/env node

/**
 * Commerce OS — Authoritative Cloud API Keep-Alive Daemon
 * 
 * Sends a lightweight HTTP GET heartbeat to the cloud backend on Render
 * every 10 minutes (600 seconds) to prevent free/starter tier instances
 * from spinning down into inactivity sleep.
 * 
 * Bandwidth consumption: ~130 bytes per pulse (~0.5 MB per month).
 * 
 * Usage:
 *   node scripts/render-keepalive.js          # Single pulse check
 *   node scripts/render-keepalive.js --daemon # Continuous 10-minute background pulse loop
 *   pnpm render:keepalive                    # npm script alias
 */

const https = require('https');

const TARGET_URL = process.env.COMMERCEOS_PROD_API_URL || 'https://commerce-os-api.onrender.com/health';
const INTERVAL_MINUTES = Number(process.env.KEEPALIVE_INTERVAL_MINUTES) || 10;
const INTERVAL_MS = INTERVAL_MINUTES * 60 * 1000;

function sendHeartbeat() {
  return new Promise((resolve) => {
    const startTime = Date.now();
    const req = https.get(TARGET_URL, { timeout: 15000 }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        const duration = Date.now() - startTime;
        console.log(`[Render-KeepAlive] [${new Date().toISOString()}] Pulse OK: HTTP ${res.statusCode} (${duration}ms) — Render kept awake.`);
        resolve(true);
      });
    });

    req.on('error', (err) => {
      const duration = Date.now() - startTime;
      console.warn(`[Render-KeepAlive] [${new Date().toISOString()}] Pulse error (${duration}ms): ${err.message}`);
      resolve(false);
    });

    req.on('timeout', () => {
      req.destroy();
      console.warn(`[Render-KeepAlive] [${new Date().toISOString()}] Pulse timed out.`);
      resolve(false);
    });
  });
}

async function main() {
  const isDaemon = process.argv.includes('--daemon') || process.argv.includes('-d');

  console.log(`[Render-KeepAlive] Targeting: ${TARGET_URL}`);
  await sendHeartbeat();

  if (!isDaemon) {
    process.exit(0);
  }

  console.log(`[Render-KeepAlive] Daemon standing active. Pulsing every ${INTERVAL_MINUTES} minutes.`);

  const timer = setInterval(async () => {
    await sendHeartbeat();
  }, INTERVAL_MS);

  const shutdown = () => {
    console.log('\n[Render-KeepAlive] Shutting down keep-alive daemon...');
    clearInterval(timer);
    process.exit(0);
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main();
