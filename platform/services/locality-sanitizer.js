'use strict';

/**
 * Commerce OS — Locality Sanitizer Service
 * Centralized, shared coarse locality extraction for unassigned broadcasts,
 * FCM notifications, and APNs payloads to protect customer privacy and PII.
 */

const UNIT_KEYWORDS = /\b(flat|apt|apartment|house|room|plot|door|bldg|building|tower|floor|lane|street|road|block|no\.?|secret|unit|suite|shop|villa)\s*[-/ #a-z0-9]*/gi;
const UNIT_CODE = /\b#?\d+[a-z]?\b|\b[a-z]?[-/]\d+[a-z\d]*\b|\b\d+[-/][a-z\d]+\b/gi;
const NAME_TITLES = /\b(mr|mrs|ms|dr|prof|shri|smt|c\/o|care\s+of|attn)\b/gi;
const GEOGRAPHIC_INDICATORS = /\b(sector|phase|block|colony|nagar|enclave|vihar|pur|puri|road|marg|bazaar|market|layout|extension|extn|hills|heights|town|city|cantt|west|east|north|south|central|park|place|circle|society|apartments|complex|valley|woods|gardens|estate|drive|avenue|lane|street|gali|mohalla|lines|delhi|mumbai|bangalore|bengaluru|gurgaon|gurugram|noida|rewari|pune|hyderabad|chennai|kolkata|jaipur|chandigarh|faridabad|ghaziabad|connaught|whitefield|indiranagar|koramangala|bandra|andheri)\b/i;

function sanitizeSegment(segment) {
  if (!segment || typeof segment !== 'string') return '';
  let s = segment.trim();
  if (!s) return '';
  const preserved = [];
  s = s.replace(/\b(sector|phase)\s+\d+[a-z]?\b/gi, (m) => {
    preserved.push(m);
    return `__PRESERVED_${preserved.length - 1}__`;
  });

  s = s.replace(UNIT_KEYWORDS, ' ');
  s = s.replace(UNIT_CODE, ' ');
  for (let i = 0; i < preserved.length; i++) {
    s = s.replace(`__PRESERVED_${i}__`, preserved[i]);
  }
  s = s.replace(/^[-,/ ]+|[-,/ ]+$/g, '').replace(/\s+/g, ' ').trim();
  if (!s || s.length < 3 || !/[a-z]{3,}/i.test(s)) {
    return '';
  }

  // Reject personal name segments (e.g. 'Asha Mehta', 'ramesh kumar', 'Dr. Ramesh Gupta') without geographic indicators
  if (NAME_TITLES.test(s) && !GEOGRAPHIC_INDICATORS.test(s)) {
    return '';
  }
  const words = s.split(/\s+/);
  if (words.length >= 2 && words.length <= 4) {
    const allAlphabetic = words.every(w => /^[a-z]+$/i.test(w));
    if (allAlphabetic && !GEOGRAPHIC_INDICATORS.test(s)) {
      return '';
    }
  }

  return s;
}

function extractCoarseLocality(rawAddr, deliveryAddrObj = null) {
  const candidates = [];
  if (deliveryAddrObj && typeof deliveryAddrObj === 'object') {
    if (deliveryAddrObj.sub_locality) candidates.push(deliveryAddrObj.sub_locality);
    if (deliveryAddrObj.subLocality) candidates.push(deliveryAddrObj.subLocality);
    if (deliveryAddrObj.locality) {
      const parts = String(deliveryAddrObj.locality).split(',').map(p => p.trim()).filter(Boolean);
      candidates.push(...parts);
    }
    if (deliveryAddrObj.area) candidates.push(deliveryAddrObj.area);
    if (deliveryAddrObj.city) candidates.push(deliveryAddrObj.city);
  }
  if (candidates.length === 0 && typeof rawAddr === 'string' && rawAddr.trim()) {
    const trimmed = rawAddr.trim();
    if (trimmed.startsWith('{')) {
      try {
        const obj = JSON.parse(trimmed);
        if (obj.sub_locality) candidates.push(obj.sub_locality);
        if (obj.subLocality) candidates.push(obj.subLocality);
        if (obj.locality) {
          const parts = String(obj.locality).split(',').map(p => p.trim()).filter(Boolean);
          candidates.push(...parts);
        }
        if (obj.area) candidates.push(obj.area);
        if (obj.city) candidates.push(obj.city);
      } catch (_) {}
    } else {
      const parts = trimmed.split(',').map(p => p.trim()).filter(Boolean);
      candidates.push(...parts);
    }
  }

  const validTokens = [];
  for (const cand of candidates) {
    if (typeof cand !== 'string') continue;
    const cleaned = sanitizeSegment(cand);
    if (cleaned && !validTokens.includes(cleaned)) {
      validTokens.push(cleaned);
    }
  }

  if (validTokens.length > 0) {
    return validTokens.slice(-2).join(', ');
  }
  return 'Delivery Area';
}

module.exports = {
  extractCoarseLocality,
  sanitizeSegment
};
