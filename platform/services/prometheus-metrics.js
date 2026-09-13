'use strict';

/**
 * Commerce OS — Prometheus Metrics & Operational Observability Engine
 * 
 * Standard Prometheus text exposition format (version 0.0.4)
 * Instruments:
 * 1. apns_dispatches_total (Counter): Total native APNs pushes tagged by platform, status, push_type
 * 2. apns_delivery_latency_ms (Histogram): Push dispatch latency in milliseconds
 * 3. apns_registered_tokens_total (Gauge): Active APNs registered device tokens by platform
 * 4. http_requests_total (Counter): HTTP request throughput
 */

const DEFAULT_LATENCY_BUCKETS = [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000];

function formatLabels(labels = {}) {
  const entries = Object.entries(labels).filter(([_, v]) => v != null);
  if (entries.length === 0) return '';
  const formatted = entries
    .map(([k, v]) => `${k}="${String(v).replace(/"/g, '\\"')}"`)
    .join(',');
  return `{${formatted}}`;
}

class PrometheusMetrics {
  constructor(options = {}) {
    this.latencyBuckets = options.latencyBuckets || DEFAULT_LATENCY_BUCKETS;
    this.reset();
  }

  reset() {
    // 1. apns_dispatches_total: Map(labelKey -> count)
    this.apnsDispatches = new Map();

    // 2. apns_registered_tokens_total: Map(platform -> count)
    this.apnsRegisteredTokens = new Map([
      ['iOS', 0],
      ['iOS-Rider', 0]
    ]);

    // 3. apns_delivery_latency_ms: Map(labelKey -> { buckets: Map(le -> count), sum, count })
    this.apnsLatency = new Map();

    // 4. http_requests_total: Map(labelKey -> count)
    this.httpRequests = new Map();
  }

  /**
   * Helper to serialize labels map key
   */
  _getLabelKey(labels) {
    return Object.keys(labels)
      .sort()
      .map(k => `${k}=${labels[k]}`)
      .join('|');
  }

  /**
   * Record APNs dispatch event
   */
  recordApnsDispatch({ platform = 'iOS', status = 'DELIVERED', push_type = 'alert', durationMs = 0 }) {
    const normPlatform = String(platform).toUpperCase().includes('RIDER') ? 'iOS-Rider' : 'iOS';
    const normStatus = String(status).toUpperCase();
    const normPushType = String(push_type).toLowerCase();

    // Increment counter
    const counterLabels = { platform: normPlatform, status: normStatus, push_type: normPushType };
    const counterKey = this._getLabelKey(counterLabels);
    const currCount = this.apnsDispatches.get(counterKey)?.count || 0;
    this.apnsDispatches.set(counterKey, { labels: counterLabels, count: currCount + 1 });

    // Record histogram latency
    const histLabels = { platform: normPlatform, push_type: normPushType };
    const histKey = this._getLabelKey(histLabels);
    let histEntry = this.apnsLatency.get(histKey);
    if (!histEntry) {
      histEntry = {
        labels: histLabels,
        buckets: new Map(this.latencyBuckets.map(b => [b, 0])),
        sum: 0,
        count: 0
      };
      this.apnsLatency.set(histKey, histEntry);
    }

    const duration = Math.max(0, Number(durationMs) || 0);
    histEntry.count += 1;
    histEntry.sum += duration;

    for (const b of this.latencyBuckets) {
      if (duration <= b) {
        histEntry.buckets.set(b, histEntry.buckets.get(b) + 1);
      }
    }
  }

  /**
   * Gauge: update registered APNs tokens
   */
  setRegisteredTokens(platform, count) {
    const norm = String(platform).toUpperCase().includes('RIDER') ? 'iOS-Rider' : 'iOS';
    this.apnsRegisteredTokens.set(norm, Math.max(0, Number(count) || 0));
  }

  recordTokenRegistered(platform) {
    const norm = String(platform).toUpperCase().includes('RIDER') ? 'iOS-Rider' : 'iOS';
    const curr = this.apnsRegisteredTokens.get(norm) || 0;
    this.apnsRegisteredTokens.set(norm, curr + 1);
  }

  recordTokenUnregistered(platform) {
    const norm = String(platform).toUpperCase().includes('RIDER') ? 'iOS-Rider' : 'iOS';
    const curr = this.apnsRegisteredTokens.get(norm) || 0;
    this.apnsRegisteredTokens.set(norm, Math.max(0, curr - 1));
  }

  /**
   * HTTP request counter instrumentation
   */
  recordHttpRequest(method, path, statusCode) {
    const labels = {
      method: String(method).toUpperCase(),
      path: path.split('?')[0],
      status: String(statusCode)
    };
    const key = this._getLabelKey(labels);
    const curr = this.httpRequests.get(key)?.count || 0;
    this.httpRequests.set(key, { labels, count: curr + 1 });
  }

  /**
   * Sync active token counts from PostgreSQL if pool is available
   */
  async syncDatabaseTokens(pool) {
    if (!pool) return;
    try {
      const res = await pool.query(`
        SELECT 
          CASE 
            WHEN platform ILIKE '%RIDER%' THEN 'iOS-Rider'
            ELSE 'iOS'
          END as norm_platform,
          COUNT(*) as cnt
        FROM rider_device_tokens 
        WHERE platform ILIKE '%IOS%'
        GROUP BY 1
      `);
      for (const row of res.rows) {
        this.setRegisteredTokens(row.norm_platform, parseInt(row.cnt, 10));
      }
    } catch (_) {
      // Non-blocking sync error handling
    }
  }

  /**
   * Formats all metrics into standard Prometheus text exposition syntax
   */
  async renderMetrics(pool = null) {
    if (pool) {
      await this.syncDatabaseTokens(pool);
    }

    const lines = [];

    // 1. apns_dispatches_total
    lines.push('# HELP apns_dispatches_total Total count of Apple Push Notification (APNs) dispatches by platform, status, and push type');
    lines.push('# TYPE apns_dispatches_total counter');
    if (this.apnsDispatches.size === 0) {
      lines.push('apns_dispatches_total{platform="iOS",status="DELIVERED",push_type="alert"} 0');
    } else {
      for (const { labels, count } of this.apnsDispatches.values()) {
        lines.push(`apns_dispatches_total${formatLabels(labels)} ${count}`);
      }
    }
    lines.push('');

    // 2. apns_registered_tokens_total
    lines.push('# HELP apns_registered_tokens_total Current count of active native Apple Push Notification device tokens registered');
    lines.push('# TYPE apns_registered_tokens_total gauge');
    for (const [platform, count] of this.apnsRegisteredTokens.entries()) {
      lines.push(`apns_registered_tokens_total{platform="${platform}"} ${count}`);
    }
    lines.push('');

    // 3. apns_delivery_latency_ms
    lines.push('# HELP apns_delivery_latency_ms Latency of Apple Push Notification (APNs) dispatch in milliseconds');
    lines.push('# TYPE apns_delivery_latency_ms histogram');
    if (this.apnsLatency.size === 0) {
      lines.push('apns_delivery_latency_ms_bucket{platform="iOS",push_type="alert",le="+Inf"} 0');
      lines.push('apns_delivery_latency_ms_sum{platform="iOS",push_type="alert"} 0');
      lines.push('apns_delivery_latency_ms_count{platform="iOS",push_type="alert"} 0');
    } else {
      for (const { labels, buckets, sum, count } of this.apnsLatency.values()) {
        for (const [le, bCount] of buckets.entries()) {
          lines.push(`apns_delivery_latency_ms_bucket${formatLabels({ ...labels, le: String(le) })} ${bCount}`);
        }
        lines.push(`apns_delivery_latency_ms_bucket${formatLabels({ ...labels, le: '+Inf' })} ${count}`);
        lines.push(`apns_delivery_latency_ms_sum${formatLabels(labels)} ${Number(sum.toFixed(3))}`);
        lines.push(`apns_delivery_latency_ms_count${formatLabels(labels)} ${count}`);
      }
    }
    lines.push('');

    // 4. http_requests_total
    if (this.httpRequests.size > 0) {
      lines.push('# HELP http_requests_total Total number of HTTP requests processed');
      lines.push('# TYPE http_requests_total counter');
      for (const { labels, count } of this.httpRequests.values()) {
        lines.push(`http_requests_total${formatLabels(labels)} ${count}`);
      }
      lines.push('');
    }

    return lines.join('\n') + '\n';
  }
}

const prometheusMetricsInstance = new PrometheusMetrics();

module.exports = {
  PrometheusMetrics,
  prometheusMetricsInstance,
  DEFAULT_LATENCY_BUCKETS
};
