'use strict';

const crypto = require('crypto');

/**
 * CommerceOS Reversible Order Code Codec
 * 
 * Maps internal order sequences or numbers bijectively to/from friendly 10-digit numeric codes.
 * Example:
 *   orderId: 'ord_abd7affa-62c1-459c-bff7-f5dd46cf06bc'
 *   shortOrderId: '9686509278' (Formatted: '9686-509-278')
 * 
 * Uses a modular multiplicative permutation over modulo 9,000,000,000 + 1,000,000,000 offset:
 *   E(x) = ((x * A + B) mod M) + OFFSET
 *   D(y) = (((y - OFFSET - B) * A_inv) mod M)
 * 
 * Invariants:
 *   1. Zero collisions (Strict mathematical bijection)
 *   2. Exactly 10 decimal digits [1000000000, 9999999999]
 *   3. Non-sequential pseudo-random distribution (prevents order scraping)
 *   4. Zero DB lookups needed to decode back to original sequence number
 */

const MODULUS = 9000000000n;
const OFFSET = 1000000000n;
const MULTIPLIER = 5839217431n;
const ADDITIVE = 2847291847n;

function modInverse(a, m) {
  let [m0, x0, x1] = [m, 0n, 1n];
  if (m === 1n) return 0n;
  while (a > 1n) {
    const q = a / m;
    [a, m] = [m, a % m];
    [x0, x1] = [x1 - q * x0, x0];
  }
  if (x1 < 0n) x1 += m0;
  return x1;
}

const INVERSE = modInverse(MULTIPLIER, MODULUS);

class OrderCodeCodec {
  /**
   * Encodes an integer sequence number into a pseudo-random, non-sequential 10-digit code.
   * @param {number|bigint|string} seq
   * @returns {string} 10-digit numeric string
   */
  static encode(seq) {
    const n = BigInt(seq) % MODULUS;
    const permuted = (n * MULTIPLIER + ADDITIVE) % MODULUS;
    const tenDigit = (permuted + OFFSET).toString();
    return tenDigit.padStart(10, '0');
  }

  /**
   * Decodes a 10-digit code back into the original integer sequence number.
   * @param {string|number|bigint} code
   * @returns {string} Original sequence
   */
  static decode(code) {
    const cleanCode = String(code).replace(/[^0-9]/g, '');
    if (cleanCode.length !== 10) {
      throw new Error(`Invalid order code length: expected 10 digits, got ${cleanCode.length}`);
    }
    const val = BigInt(cleanCode);
    if (val < OFFSET || val >= OFFSET + MODULUS) {
      throw new Error('Invalid 10-digit order code range.');
    }
    const permuted = val - OFFSET;
    let diff = permuted - ADDITIVE;
    while (diff < 0n) diff += MODULUS;
    const original = (diff * INVERSE) % MODULUS;
    return original.toString();
  }

  /**
   * Deterministically derives a 10-digit numeric code from a UUID.
   * @param {string} uuid
   * @returns {string} 10-digit numeric string
   */
  static fromUuid(uuid) {
    const clean = String(uuid).replace(/^ord_/, '').replace(/-/g, '');
    const hash = crypto.createHash('sha256').update(clean).digest();
    const bigVal = hash.readBigUInt64BE(0);
    const n = bigVal % MODULUS;
    return OrderCodeCodec.encode(n);
  }

  /**
   * Formats a 10-digit numeric code with hyphens: e.g. "9686-509-278"
   * @param {string} code
   * @returns {string}
   */
  static formatDisplay(code) {
    const c = String(code).replace(/[^0-9]/g, '');
    if (c.length === 10) {
      return `${c.slice(0, 4)}-${c.slice(4, 7)}-${c.slice(7)}`;
    }
    return String(code);
  }

  /**
   * Strips hyphens and formatting characters to return raw 10 digits.
   * @param {string} displayCode
   * @returns {string}
   */
  static unformat(displayCode) {
    return String(displayCode || '').replace(/[^0-9]/g, '');
  }

  /**
   * Checks whether an input string is a valid 10-digit order code.
   * @param {string} code
   * @returns {boolean}
   */
  static isValid10DigitCode(code) {
    const clean = String(code || '').replace(/[^0-9]/g, '');
    return clean.length === 10 && clean[0] !== '0';
  }
}

module.exports = {
  OrderCodeCodec,
  encodeOrderCode: OrderCodeCodec.encode,
  decodeOrderCode: OrderCodeCodec.decode,
  formatDisplayOrderCode: OrderCodeCodec.formatDisplay
};
