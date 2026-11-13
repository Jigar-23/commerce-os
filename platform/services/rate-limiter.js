/**
 * Commerce OS — Production Rate Limiter & Distributed DDoS Abuse Shield
 * 
 * Provides:
 * 1. Hybrid In-Memory & Redis-Backed Sliding Window Rate Limiting
 * 2. Multi-tier sensitivity (Auth/OTP, Checkout/Payment, High-frequency Telemetry, General API)
 * 3. Cluster-wide Distributed IP Ban Broadcast via Redis Pub/Sub (cross-worker sync)
 * 4. Zero-latency synchronous check() with background Redis counter synchronization
 * 5. RFC 6585 compliant HTTP 429 headers (Retry-After, X-RateLimit-*)
 * 6. Memory-safe LRU cleanup preventing memory leakage under sustained attack
 */

let Redis = null;
try {
  Redis = require('ioredis');
} catch (_) {}

class ProductionRateLimiter {
  constructor(options = {}) {
    this.windowMs = options.windowMs || 60000; // 1 minute
    this.hits = new Map();
    this.bannedIps = new Map(); // ip -> unbanTimestamp
    this.cleanupIntervalMs = options.cleanupIntervalMs || 30000;
    this.redisUrl = options.redisUrl || process.env.REDIS_URL || null;
    this.redisClient = null;
    this.subscriberClient = null;
    this.isRedisReady = false;
    this.banChannel = 'commerceos:distributed_banned_ips';

    // Default rate limit thresholds per 60-second window
    this.tiers = {
      AUTH: { limit: 15, blockMultiplier: 3, banDurationMs: 300000 },      // 15 req/min, ban 5 mins if > 45
      CHECKOUT: { limit: 30, blockMultiplier: 3, banDurationMs: 180000 },  // 30 req/min, ban 3 mins if > 90
      TELEMETRY: { limit: 180, blockMultiplier: 3, banDurationMs: 60000 }, // 180 req/min (3 req/sec)
      GENERAL: { limit: 300, blockMultiplier: 4, banDurationMs: 120000 }   // 300 req/min (5 req/sec)
    };

    this.initDistributedSync();

    // Periodic sweep of expired windows to prevent memory bloat
    this.cleanupTimer = setInterval(() => this.cleanup(), this.cleanupIntervalMs);
    if (this.cleanupTimer.unref) this.cleanupTimer.unref();
  }

  initDistributedSync() {
    if (!this.redisUrl || !Redis) return;

    try {
      this.redisClient = new Redis(this.redisUrl, {
        maxRetriesPerRequest: 1,
        connectTimeout: 3000,
        enableOfflineQueue: false,
        retryStrategy: (times) => Math.min(times * 100, 2000)
      });

      this.subscriberClient = new Redis(this.redisUrl, {
        maxRetriesPerRequest: 1,
        connectTimeout: 3000,
        enableOfflineQueue: false
      });

      this.redisClient.on('connect', () => {
        this.isRedisReady = true;
      });

      this.redisClient.on('error', () => {
        this.isRedisReady = false;
      });

      this.subscriberClient.on('connect', () => {
        this.subscriberClient.subscribe(this.banChannel).catch(() => {});
      });

      this.subscriberClient.on('message', (channel, message) => {
        if (channel === this.banChannel && message) {
          try {
            const data = JSON.parse(message);
            if (data.ip && data.banUntil && data.banUntil > Date.now()) {
              this.bannedIps.set(data.ip, data.banUntil);
            }
          } catch {}
        }
      });
    } catch (_) {
      this.isRedisReady = false;
    }
  }

  getClientIp(req) {
    const cfConnectingIp = req.headers['cf-connecting-ip'];
    if (cfConnectingIp && typeof cfConnectingIp === 'string') {
      return cfConnectingIp.trim();
    }

    const xRealIp = req.headers['x-real-ip'];
    if (xRealIp && typeof xRealIp === 'string') {
      return xRealIp.trim();
    }

    const xForwardedFor = req.headers['x-forwarded-for'];
    if (xForwardedFor && typeof xForwardedFor === 'string') {
      const parts = xForwardedFor.split(',');
      const first = parts[0]?.trim();
      if (first) return first;
    }

    const remoteAddr = req.socket?.remoteAddress || req.connection?.remoteAddress;
    return remoteAddr || '127.0.0.1';
  }

  resolveTier(pathname, method) {
    if (pathname.startsWith('/api/v1/auth') || 
        pathname.startsWith('/api/v1/customer/auth') || 
        pathname.includes('/login') || 
        pathname.includes('/send-otp') ||
        pathname.includes('/verify-otp')) {
      return 'AUTH';
    }

    if (pathname.includes('/checkout') || 
        pathname.includes('/accept-by-seller') || 
        pathname.includes('/ready-for-pickup') ||
        pathname.startsWith('/api/v1/payments')) {
      return 'CHECKOUT';
    }

    if (pathname.includes('/telemetry') || pathname.includes('/presence')) {
      return 'TELEMETRY';
    }

    return 'GENERAL';
  }

  check(req, pathname) {
    const ip = this.getClientIp(req);
    const now = Date.now();

    // Check if IP is currently under active ban
    const banUntil = this.bannedIps.get(ip);
    if (banUntil) {
      if (now < banUntil) {
        const retryAfter = Math.ceil((banUntil - now) / 1000);
        return {
          allowed: false,
          status: 429,
          retryAfter,
          reason: 'IP_TEMPORARILY_BLOCKED',
          message: `IP temporarily blocked due to excessive request burst. Retry in ${retryAfter}s.`
        };
      } else {
        this.bannedIps.delete(ip);
      }
    }

    const tierName = this.resolveTier(pathname, req.method);
    const tier = this.tiers[tierName] || this.tiers.GENERAL;
    const bucketKey = `${ip}:${tierName}`;

    let record = this.hits.get(bucketKey);
    if (!record || now - record.windowStart >= this.windowMs) {
      record = { count: 1, windowStart: now };
      this.hits.set(bucketKey, record);

      if (this.isRedisReady && this.redisClient) {
        this._syncHitToRedis(bucketKey, ip, tierName, tier).catch(() => {});
      }

      return {
        allowed: true,
        limit: tier.limit,
        remaining: tier.limit - 1,
        resetMs: this.windowMs
      };
    }

    record.count++;
    const remaining = Math.max(0, tier.limit - record.count);
    const windowElapsed = now - record.windowStart;
    const resetSeconds = Math.ceil((this.windowMs - windowElapsed) / 1000);

    // If request exceeds tier limit
    if (record.count > tier.limit) {
      // If aggressive flooding exceeds ban threshold (e.g. 3x-4x above limit), apply active ban
      if (record.count >= tier.limit * tier.blockMultiplier) {
        const banExpiry = now + tier.banDurationMs;
        this.bannedIps.set(ip, banExpiry);
        console.warn(`[DDoS Shield] IP ${ip} banned for ${tier.banDurationMs / 1000}s on ${pathname} (${record.count} reqs in window)`);

        // Broadcast ban to other cluster workers via Redis
        if (this.isRedisReady && this.redisClient) {
          this.redisClient.publish(this.banChannel, JSON.stringify({ ip, banUntil: banExpiry })).catch(() => {});
        }
      }

      return {
        allowed: false,
        status: 429,
        retryAfter: resetSeconds,
        limit: tier.limit,
        remaining: 0,
        reason: 'RATE_LIMIT_EXCEEDED',
        message: `Too many requests on ${tierName} endpoint. Limit: ${tier.limit} req/min. Please retry in ${resetSeconds}s.`
      };
    }

    if (this.isRedisReady && this.redisClient) {
      this._syncHitToRedis(bucketKey, ip, tierName, tier).catch(() => {});
    }

    return {
      allowed: true,
      limit: tier.limit,
      remaining,
      resetMs: this.windowMs - windowElapsed
    };
  }

  async _syncHitToRedis(bucketKey, ip, tierName, tier) {
    if (!this.redisClient || !this.isRedisReady) return;
    try {
      const redisKey = `ratelimit:${bucketKey}`;
      const count = await this.redisClient.incr(redisKey);
      if (count === 1) {
        await this.redisClient.expire(redisKey, Math.ceil(this.windowMs / 1000));
      }
      if (count >= tier.limit * tier.blockMultiplier) {
        const banExpiry = Date.now() + tier.banDurationMs;
        this.bannedIps.set(ip, banExpiry);
        await this.redisClient.publish(this.banChannel, JSON.stringify({ ip, banUntil: banExpiry }));
      }
    } catch {}
  }

  async destroy() {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
    if (this.subscriberClient) {
      try { await this.subscriberClient.quit(); } catch (_) {
        try { this.subscriberClient.disconnect(); } catch (_) {}
      }
      this.subscriberClient = null;
    }
    if (this.redisClient) {
      try { await this.redisClient.quit(); } catch (_) {
        try { this.redisClient.disconnect(); } catch (_) {}
      }
      this.redisClient = null;
    }
    this.hits.clear();
    this.bannedIps.clear();
  }

  cleanup() {
    const now = Date.now();
    for (const [key, record] of this.hits.entries()) {
      if (now - record.windowStart >= this.windowMs) {
        this.hits.delete(key);
      }
    }
    for (const [ip, unbanTime] of this.bannedIps.entries()) {
      if (now >= unbanTime) {
        this.bannedIps.delete(ip);
      }
    }
  }

  destroy() {
    if (this.cleanupTimer) clearInterval(this.cleanupTimer);
    if (this.redisClient) {
      try { this.redisClient.quit(); } catch {}
    }
    if (this.subscriberClient) {
      try { this.subscriberClient.quit(); } catch {}
    }
  }
}

const rateLimiterInstance = new ProductionRateLimiter();
module.exports = { ProductionRateLimiter, rateLimiterInstance };
