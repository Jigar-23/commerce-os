#!/usr/bin/env node

/**
 * Commerce OS — Authoritative Database Keep-Alive Daemon & Cron Task
 * 
 * Secretly queries the database every 24 hours (or once when invoked via cron)
 * to keep cloud PostgreSQL instances (Supabase, Neon, Render, AWS Aurora) from
 * auto-pausing or hibernating due to inactivity.
 * 
 * In accordance with WORKING_PRINCIPLES.md:
 * - We never change or redirect DATABASE_URL.
 * - In case of failure, fails closed without synthetic fallbacks.
 * 
 * Usage:
 *   node scripts/db-keepalive.js           # Single pulse check (for crontab / CI)
 *   node scripts/db-keepalive.js --daemon  # Continuous 24-hour background pulse loop
 *   pnpm db:keepalive                      # npm script alias
 */

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

const ROOT_DIR = path.resolve(__dirname, '..');

// Read DATABASE_URL from environment or .env.production
function resolveDatabaseUrl() {
  if (process.env.DATABASE_URL && process.env.DATABASE_URL.trim()) {
    return process.env.DATABASE_URL.trim();
  }

  const envPath = path.join(ROOT_DIR, '.env.production');
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf8');
    const match = content.match(/^DATABASE_URL=(.+)$/m);
    if (match && match[1].trim()) {
      return match[1].trim();
    }
  }

  return null;
}

const dbUrl = resolveDatabaseUrl();
if (!dbUrl) {
  console.error('[DB-KeepAlive] FATAL: DATABASE_URL not found in environment or .env.production.');
  process.exit(1);
}

const pool = new Pool({
  connectionString: dbUrl,
  ssl: dbUrl.includes('render.com') || dbUrl.includes('neon.tech') || dbUrl.includes('supabase.com') || dbUrl.includes('aws')
    ? { rejectUnauthorized: false }
    : false,
  connectionTimeoutMillis: 10000,
  idleTimeoutMillis: 30000
});

async function sendKeepAlivePulse() {
  const startTime = Date.now();
  let client = null;
  try {
    client = await pool.connect();
    // Authoritative lightweight keep-alive query
    const res = await client.query('SELECT NOW() AS pulse_time, 1 AS alive;');
    const durationMs = Date.now() - startTime;
    const pulseTime = res.rows[0]?.pulse_time;
    console.log(`[DB-KeepAlive] [${new Date().toISOString()}] Pulse successful in ${durationMs}ms (DB Time: ${pulseTime})`);
    return true;
  } catch (err) {
    console.error(`[DB-KeepAlive] [${new Date().toISOString()}] Pulse failed: ${err.message}`);
    return false;
  } finally {
    if (client) client.release();
  }
}

async function main() {
  const isDaemon = process.argv.includes('--daemon') || process.argv.includes('-d');
  const intervalHours = Number(process.env.KEEPALIVE_INTERVAL_HOURS) || 24;
  const intervalMs = intervalHours * 60 * 60 * 1000;

  // Execute initial pulse
  const success = await sendKeepAlivePulse();

  if (!isDaemon) {
    await pool.end();
    process.exit(success ? 0 : 1);
  }

  console.log(`[DB-KeepAlive] Standing daemon active. Next pulse in ${intervalHours} hours.`);

  const timer = setInterval(async () => {
    await sendKeepAlivePulse();
  }, intervalMs);

  const shutdown = async () => {
    console.log('\n[DB-KeepAlive] Shutting down keep-alive daemon...');
    clearInterval(timer);
    await pool.end();
    process.exit(0);
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main();
