'use strict';

const crypto = require('crypto');

/**
 * Commerce OS — Native Apple Push Notification (APNs) Dispatcher & Payload Engine
 * 
 * Complies with Apple Push Notification service (APNs) HTTP/2 Specification:
 * - Full 'aps' dictionary schema support:
 *     alert (title, subtitle, body, localized keys)
 *     sound (string or critical alert dictionary: critical, name, volume)
 *     badge (numeric badge counter)
 *     content-available (1 for silent background sync)
 *     mutable-content (1 for Notification Service Extension)
 *     category (actionable notification category)
 *     thread-id (conversation / thread grouping)
 *     interruption-level (passive, active, time-sensitive, critical)
 *     relevance-score (0.0 to 1.0)
 * - Strict payload size limit enforcement (max 4096 bytes)
 * - Custom domain routing dictionary (deepLink, actionUrl, offerId, deliveryId, orderId)
 * - APNs HTTP/2 request header formatting (apns-topic, apns-push-type, apns-priority, apns-expiration, apns-id, apns-collapse-id)
 * - Token normalization (strips '<>', spaces, validates 64-hex string)
 * - Pluggable live / simulated HTTP/2 transport
 */

const APNS_MAX_PAYLOAD_BYTES = 4096;
const APNS_PUSH_TYPES = ['alert', 'background', 'location', 'voip', 'complication', 'fileprovider', 'mdm', 'liveactivity'];
const INTERRUPTION_LEVELS = ['passive', 'active', 'time-sensitive', 'critical'];

/**
 * Validates and normalizes 64-character hexadecimal Apple device token
 */
function normalizeApnsToken(rawToken) {
  if (!rawToken || typeof rawToken !== 'string') {
    throw new Error('APNS_INVALID_TOKEN: Token must be a non-empty string.');
  }
  const clean = rawToken.replace(/[<>\s-]/g, '').trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/i.test(clean)) {
    throw new Error(`APNS_INVALID_TOKEN: Token '${rawToken}' is not a valid 64-character hexadecimal device token.`);
  }
  return clean;
}

/**
 * Fluent builder for native Apple Push Notification (APNs) JSON payloads
 */
class ApnsPayloadBuilder {
  constructor() {
    this.aps = {};
    this.customData = {};
  }

  /**
   * Set alert content
   * @param {string|object} alert - Simple string body or object { title, subtitle, body }
   */
  setAlert(alert) {
    if (typeof alert === 'string') {
      this.aps.alert = alert;
    } else if (typeof alert === 'object' && alert !== null) {
      const alertObj = {};
      if (alert.title) alertObj.title = String(alert.title);
      if (alert.subtitle) alertObj.subtitle = String(alert.subtitle);
      if (alert.body) alertObj.body = String(alert.body);
      if (alert['title-loc-key']) alertObj['title-loc-key'] = String(alert['title-loc-key']);
      if (alert['title-loc-args']) alertObj['title-loc-args'] = alert['title-loc-args'];
      if (alert['loc-key']) alertObj['loc-key'] = String(alert['loc-key']);
      if (alert['loc-args']) alertObj['loc-args'] = alert['loc-args'];
      if (alert['action-loc-key']) alertObj['action-loc-key'] = String(alert['action-loc-key']);
      if (alert['launch-image']) alertObj['launch-image'] = String(alert['launch-image']);
      this.aps.alert = alertObj;
    }
    return this;
  }

  setTitle(title) {
    if (!this.aps.alert || typeof this.aps.alert !== 'object') {
      this.aps.alert = { title: String(title), body: '' };
    } else {
      this.aps.alert.title = String(title);
    }
    return this;
  }

  setSubtitle(subtitle) {
    if (!this.aps.alert || typeof this.aps.alert !== 'object') {
      this.aps.alert = { subtitle: String(subtitle), body: '' };
    } else {
      this.aps.alert.subtitle = String(subtitle);
    }
    return this;
  }

  setBody(body) {
    if (!this.aps.alert || typeof this.aps.alert !== 'object') {
      this.aps.alert = { body: String(body) };
    } else {
      this.aps.alert.body = String(body);
    }
    return this;
  }

  /**
   * Set sound.
   * Can be string (e.g. 'default', 'ping.aiff') or critical sound object:
   * { critical: 1, name: 'critical_alert.aiff', volume: 1.0 }
   */
  setSound(sound) {
    if (typeof sound === 'string') {
      this.aps.sound = sound;
    } else if (typeof sound === 'object' && sound !== null) {
      this.aps.sound = {
        critical: sound.critical ? 1 : 0,
        name: sound.name || 'default',
        volume: typeof sound.volume === 'number' ? Math.max(0.0, Math.min(1.0, sound.volume)) : 1.0
      };
    }
    return this;
  }

  /**
   * Set numeric application badge counter
   */
  setBadge(count) {
    if (count == null) {
      delete this.aps.badge;
    } else {
      const num = Number(count);
      if (isNaN(num) || num < 0) {
        throw new Error('APNS_INVALID_BADGE: Badge count must be a non-negative integer.');
      }
      this.aps.badge = Math.floor(num);
    }
    return this;
  }

  /**
   * Set content-available (1 for silent background push)
   */
  setContentAvailable(enabled = true) {
    if (enabled) {
      this.aps['content-available'] = 1;
    } else {
      delete this.aps['content-available'];
    }
    return this;
  }

  /**
   * Set mutable-content (1 for Notification Service Extension)
   */
  setMutableContent(enabled = true) {
    if (enabled) {
      this.aps['mutable-content'] = 1;
    } else {
      delete this.aps['mutable-content'];
    }
    return this;
  }

  /**
   * Set notification category for actionable buttons
   */
  setCategory(category) {
    if (category) {
      this.aps.category = String(category);
    } else {
      delete this.aps.category;
    }
    return this;
  }

  /**
   * Set thread-id for grouping in Notification Center
   */
  setThreadId(threadId) {
    if (threadId) {
      this.aps['thread-id'] = String(threadId);
    } else {
      delete this.aps['thread-id'];
    }
    return this;
  }

  /**
   * Set iOS 15+ interruption level: 'passive' | 'active' | 'time-sensitive' | 'critical'
   */
  setInterruptionLevel(level) {
    if (level) {
      const lvl = String(level).toLowerCase();
      if (!INTERRUPTION_LEVELS.includes(lvl)) {
        throw new Error(`APNS_INVALID_INTERRUPTION_LEVEL: Must be one of ${INTERRUPTION_LEVELS.join(', ')}`);
      }
      this.aps['interruption-level'] = lvl;
    } else {
      delete this.aps['interruption-level'];
    }
    return this;
  }

  /**
   * Set relevance score (0.0 to 1.0)
   */
  setRelevanceScore(score) {
    if (score != null) {
      const num = Number(score);
      this.aps['relevance-score'] = Math.max(0.0, Math.min(1.0, num));
    } else {
      delete this.aps['relevance-score'];
    }
    return this;
  }

  /**
   * Set custom domain data outside of 'aps' dictionary
   */
  setCustomData(key, value) {
    if (key === 'aps') {
      throw new Error("APNS_RESERVED_KEY: Cannot set custom key 'aps'. Use dedicated builder methods.");
    }
    if (value !== undefined) {
      this.customData[key] = value;
    }
    return this;
  }

  setCustomFields(dict) {
    if (dict && typeof dict === 'object') {
      for (const [k, v] of Object.entries(dict)) {
        this.setCustomData(k, v);
      }
    }
    return this;
  }

  setDeepLink(url) {
    if (url) {
      this.customData.actionUrl = String(url);
      this.customData.deepLink = String(url);
    }
    return this;
  }

  /**
   * Assemble and validate the final JSON payload
   */
  build() {
    const payload = {
      aps: { ...this.aps },
      ...this.customData
    };

    const jsonString = JSON.stringify(payload);
    const byteLength = Buffer.byteLength(jsonString, 'utf8');

    if (byteLength > APNS_MAX_PAYLOAD_BYTES) {
      throw new Error(`APNS_PAYLOAD_TOO_LARGE: Payload exceeds ${APNS_MAX_PAYLOAD_BYTES} bytes limit (actual: ${byteLength} bytes).`);
    }

    return {
      payload,
      jsonString,
      byteLength
    };
  }
}

/**
 * Result of an APNs push dispatch attempt
 */
class ApnsDeliveryResult {
  constructor({
    status,
    apnsId = null,
    httpStatus = 200,
    deviceToken = null,
    reason = null,
    timestamp = new Date().toISOString()
  }) {
    this.status = status; // 'DELIVERED', 'REJECTED', 'FAILED'
    this.apnsId = apnsId || crypto.randomUUID();
    this.httpStatus = httpStatus;
    this.deviceToken = deviceToken;
    this.reason = reason;
    this.timestamp = timestamp;
    this.delivered = status === 'DELIVERED';
  }
}

/**
 * High-performance APNs Dispatcher for iOS Customer & Rider Apps
 */
class ApnsDispatcher {
  constructor(options = {}) {
    this.defaultTopic = options.defaultTopic || 'io.commerceos.ios';
    this.riderTopic = options.riderTopic || 'io.commerceos.rider';
    this.customerTopic = options.customerTopic || 'io.commerceos.customer';
    this.environment = options.environment || 'production'; // 'production' | 'sandbox'
    this.deviceTokenRepo = options.deviceTokenRepo || null;
    this.transport = options.transport || this._createDefaultTransport();
    this.metrics = options.metrics || null;
    
    this.stats = {
      totalDispatched: 0,
      totalDelivered: 0,
      totalFailed: 0,
      totalRejected: 0
    };
  }

  _createDefaultTransport() {
    // Built-in HTTP/2 APNs simulation & testing transport
    return async (deviceToken, payloadJson, headers) => {
      // Validate device token format
      if (!/^[0-9a-f]{64}$/i.test(deviceToken)) {
        return new ApnsDeliveryResult({
          status: 'REJECTED',
          httpStatus: 400,
          deviceToken,
          reason: 'BadDeviceToken'
        });
      }

      // Simulate token unregistration for special test tokens
      if (deviceToken.startsWith('dead') || deviceToken.endsWith('dead')) {
        return new ApnsDeliveryResult({
          status: 'REJECTED',
          httpStatus: 410,
          deviceToken,
          reason: 'Unregistered'
        });
      }

      // Successful simulated delivery
      const apnsId = headers['apns-id'] || crypto.randomUUID();
      return new ApnsDeliveryResult({
        status: 'DELIVERED',
        httpStatus: 200,
        apnsId,
        deviceToken
      });
    };
  }

  /**
   * Builds standardized APNs HTTP/2 request headers
   */
  buildRequestHeaders(options = {}) {
    const isBackground = options.pushType === 'background' || options.isSilent === true;
    const pushType = options.pushType || (isBackground ? 'background' : 'alert');
    const priority = options.priority != null ? options.priority : (pushType === 'alert' ? 10 : 5);
    const expiration = options.expiration != null ? options.expiration : Math.floor(Date.now() / 1000) + (options.ttlSeconds || 3600);
    const apnsId = options.apnsId || crypto.randomUUID();

    const headers = {
      ':method': 'POST',
      'apns-topic': options.topic || this.defaultTopic,
      'apns-push-type': pushType,
      'apns-priority': String(priority),
      'apns-expiration': String(expiration),
      'apns-id': apnsId
    };

    if (options.collapseId) {
      headers['apns-collapse-id'] = String(options.collapseId);
    }

    return headers;
  }

  /**
   * Dispatch an APNs notification to a single device token
   */
  async send(rawToken, payloadBuilderOrObj, options = {}) {
    this.stats.totalDispatched++;
    let token;
    try {
      token = normalizeApnsToken(rawToken);
    } catch (err) {
      this.stats.totalFailed++;
      return new ApnsDeliveryResult({
        status: 'FAILED',
        httpStatus: 400,
        deviceToken: rawToken,
        reason: err.message
      });
    }

    let built;
    if (payloadBuilderOrObj instanceof ApnsPayloadBuilder) {
      built = payloadBuilderOrObj.build();
    } else if (payloadBuilderOrObj && payloadBuilderOrObj.payload && payloadBuilderOrObj.jsonString) {
      built = payloadBuilderOrObj;
    } else if (typeof payloadBuilderOrObj === 'object' && payloadBuilderOrObj !== null && payloadBuilderOrObj.aps) {
      const json = JSON.stringify(payloadBuilderOrObj);
      built = { payload: payloadBuilderOrObj, jsonString: json, byteLength: Buffer.byteLength(json, 'utf8') };
    } else {
      throw new Error('APNS_INVALID_PAYLOAD: Expected ApnsPayloadBuilder or object with aps property.');
    }

    const headers = this.buildRequestHeaders({
      topic: options.topic || this.defaultTopic,
      pushType: options.pushType || (built.payload.aps['content-available'] === 1 && !built.payload.aps.alert ? 'background' : 'alert'),
      priority: options.priority,
      expiration: options.expiration,
      collapseId: options.collapseId
    });

    const startTime = Date.now();
    try {
      const result = await this.transport(token, built.jsonString, headers);
      const durationMs = Date.now() - startTime;
      if (result.status === 'DELIVERED') {
        this.stats.totalDelivered++;
      } else if (result.status === 'REJECTED') {
        this.stats.totalRejected++;
      } else {
        this.stats.totalFailed++;
      }

      if (this.metrics && typeof this.metrics.recordApnsDispatch === 'function') {
        const platform = (options.topic && options.topic.includes('rider')) ? 'iOS-Rider' : (options.platform || 'iOS');
        this.metrics.recordApnsDispatch({
          platform,
          status: result.status,
          push_type: headers['apns-push-type'] || 'alert',
          durationMs
        });
      }

      return result;
    } catch (netErr) {
      const durationMs = Date.now() - startTime;
      this.stats.totalFailed++;
      if (this.metrics && typeof this.metrics.recordApnsDispatch === 'function') {
        const platform = (options.topic && options.topic.includes('rider')) ? 'iOS-Rider' : (options.platform || 'iOS');
        this.metrics.recordApnsDispatch({
          platform,
          status: 'FAILED',
          push_type: headers['apns-push-type'] || 'alert',
          durationMs
        });
      }
      return new ApnsDeliveryResult({
        status: 'FAILED',
        httpStatus: 500,
        deviceToken: token,
        reason: netErr.message || 'TRANSPORT_NETWORK_ERROR'
      });
    }
  }

  // =========================================================================
  // Standard Commerce OS Notification Templates
  // =========================================================================

  /**
   * 1. High-Priority Critical Dispatch Offer for Rider iOS App
   */
  createRiderOfferPayload(offer) {
    const offerId = offer.offerId || offer.offer_id || offer.id;
    const earnings = Math.floor(offer.earningsAmount || offer.earnings_amount || 45);
    const distanceKm = offer.totalDistanceKm || offer.total_distance_km || 1.5;
    const duration = offer.estimatedDurationMins || offer.estimated_duration_mins || 10;
    const mName = offer.merchantName || offer.merchant_name || 'Commerce OS Store';
    const cAddr = offer.customerAddress || offer.customer_address || '';
    const dropShort = cAddr ? cAddr.split(',')[0].trim() : 'Customer location';

    return new ApnsPayloadBuilder()
      .setTitle(`🚨 NEW DELIVERY · ₹${earnings}`)
      .setSubtitle(`${distanceKm} km (~${duration} min)`)
      .setBody(`Pickup: ${mName} • Drop: ${dropShort}`)
      .setSound({ critical: 1, name: 'critical_alert.aiff', volume: 1.0 })
      .setBadge(1)
      .setContentAvailable(true)
      .setMutableContent(true)
      .setCategory('RIDER_DISPATCH_OFFER')
      .setInterruptionLevel('time-sensitive')
      .setThreadId(`delivery_${offer.deliveryId || offer.delivery_id}`)
      .setCustomFields({
        offerId,
        deliveryId: offer.deliveryId || offer.delivery_id,
        orderId: offer.orderId || offer.order_id,
        earningsAmount: earnings,
        expiresAt: offer.offerExpiresAt || offer.offer_expires_at,
        actionUrl: `commerceos://rider/offer/${offerId}`,
        targetScreen: 'OFFER_DETAIL'
      });
  }

  /**
   * 2. Customer Order Status Update (e.g. Delivered, Out for Delivery)
   */
  createOrderStatusPayload(order, status) {
    const orderId = order.orderId || order.order_id || order.id;
    const st = String(status || order.status || 'UPDATED').toUpperCase();

    const builder = new ApnsPayloadBuilder()
      .setSound('default')
      .setBadge(1)
      .setMutableContent(true)
      .setCategory('CUSTOMER_ORDER_STATUS')
      .setThreadId(`order_${orderId}`)
      .setCustomFields({
        orderId,
        status: st,
        actionUrl: `commerceos://orders/${orderId}`,
        targetScreen: 'ORDER_TRACKING'
      });

    if (st === 'DELIVERED') {
      builder
        .setTitle('Order Delivered! 🎉')
        .setBody(`Your order #${orderId.slice(-6)} has been safely delivered. Thank you!`)
        .setBadge(0);
    } else if (st === 'OUT_FOR_DELIVERY') {
      builder
        .setTitle('Order Out for Delivery 🛵')
        .setBody(`Your delivery partner is on the way with order #${orderId.slice(-6)}.`)
        .setInterruptionLevel('active');
    } else {
      builder
        .setTitle(`Order Update: ${st}`)
        .setBody(`Your order #${orderId.slice(-6)} is now ${st}.`);
    }

    return builder;
  }

  /**
   * 3. Live Telemetry & Turn-by-Turn Silent Sync (content-available: 1, zero alert)
   */
  createSilentSyncPayload(syncType, data = {}) {
    return new ApnsPayloadBuilder()
      .setContentAvailable(true)
      .setCustomFields({
        syncType,
        ...data,
        timestamp: Date.now()
      });
  }

  /**
   * Send notification to a registered rider using deviceTokenRepo
   */
  async sendToRider(riderId, payloadBuilder, options = {}) {
    if (!this.deviceTokenRepo) {
      throw new Error('APNS_REPO_REQUIRED: deviceTokenRepo is required for sendToRider.');
    }
    const tokenRecord = await this.deviceTokenRepo.getTokenByRider(riderId);
    if (!tokenRecord || !tokenRecord.token) {
      return new ApnsDeliveryResult({
        status: 'FAILED',
        httpStatus: 404,
        reason: 'RIDER_DEVICE_TOKEN_NOT_FOUND'
      });
    }

    return this.send(tokenRecord.token, payloadBuilder, {
      topic: options.topic || this.riderTopic,
      ...options
    });
  }

  getStats() {
    return { ...this.stats };
  }
}

module.exports = {
  ApnsPayloadBuilder,
  ApnsDispatcher,
  ApnsDeliveryResult,
  normalizeApnsToken,
  APNS_MAX_PAYLOAD_BYTES
};
