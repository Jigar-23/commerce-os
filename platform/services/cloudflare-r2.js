/**
 * Commerce OS — Cloudflare R2 S3-Compatible Object Storage Service
 *
 * Provides high-speed, zero-egress, 10 GB permanent free tier storage
 * for prescriptions, product images, and merchant assets.
 * Implements native AWS Signature Version 4 (SigV4) with ZERO external dependencies.
 */

const crypto = require('crypto');
const https = require('https');
const { URL } = require('url');

class CloudflareR2Service {
  constructor(config = {}) {
    this._config = config;
  }

  get accountId() {
    return this._config.accountId || process.env.R2_ACCOUNT_ID || '';
  }

  get accessKeyId() {
    return this._config.accessKeyId || process.env.R2_ACCESS_KEY_ID || process.env.S3_ACCESS_KEY_ID || '';
  }

  get secretAccessKey() {
    return this._config.secretAccessKey || process.env.R2_SECRET_ACCESS_KEY || process.env.S3_SECRET_ACCESS_KEY || '';
  }

  get bucketName() {
    return this._config.bucketName || process.env.R2_BUCKET_NAME || process.env.S3_BUCKET_NAME || 'commerce-os-1';
  }

  get publicDomain() {
    return (this._config.publicDomain || process.env.R2_PUBLIC_DOMAIN || process.env.S3_PUBLIC_BASE_URL || '').replace(/\/+$/, '');
  }

  get region() {
    return this._config.region || process.env.R2_REGION || 'auto';
  }

  /**
   * Returns true if Cloudflare R2 credentials and bucket are configured.
   */
  isConfigured() {
    return Boolean(
      this.accountId &&
      this.accessKeyId &&
      this.secretAccessKey &&
      this.bucketName
    );
  }

  /**
   * Generates AWS SigV4 authorization headers for an HTTP PUT request to Cloudflare R2.
   */
  generateSigV4Headers({ method, pathname, query = '', headers = {}, payloadBuffer }) {
    const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const dateStamp = amzDate.slice(0, 8);
    const host = `${this.accountId}.r2.cloudflarestorage.com`;

    const payloadHash = crypto.createHash('sha256').update(payloadBuffer).digest('hex');

    const canonicalHeadersMap = {
      'host': host,
      'x-amz-content-sha256': payloadHash,
      'x-amz-date': amzDate,
      ...headers
    };

    // Sort header keys lexicographically
    const sortedHeaderKeys = Object.keys(canonicalHeadersMap).sort();
    const canonicalHeadersStr = sortedHeaderKeys
      .map(k => `${k.toLowerCase()}:${String(canonicalHeadersMap[k]).trim()}\n`)
      .join('');
    const signedHeadersStr = sortedHeaderKeys.map(k => k.toLowerCase()).join(';');

    // Canonical Request
    const canonicalRequest = [
      method.toUpperCase(),
      pathname,
      query,
      canonicalHeadersStr,
      signedHeadersStr,
      payloadHash
    ].join('\n');

    const credentialScope = `${dateStamp}/${this.region}/s3/aws4_request`;
    const stringToSign = [
      'AWS4-HMAC-SHA256',
      amzDate,
      credentialScope,
      crypto.createHash('sha256').update(canonicalRequest, 'utf8').digest('hex')
    ].join('\n');

    // Calculate Signature Key
    const kDate = crypto.createHmac('sha256', `AWS4${this.secretAccessKey}`).update(dateStamp, 'utf8').digest();
    const kRegion = crypto.createHmac('sha256', kDate).update(this.region, 'utf8').digest();
    const kService = crypto.createHmac('sha256', kRegion).update('s3', 'utf8').digest();
    const kSigning = crypto.createHmac('sha256', kService).update('aws4_request', 'utf8').digest();

    const signature = crypto.createHmac('sha256', kSigning).update(stringToSign, 'utf8').digest('hex');
    const authHeader = `AWS4-HMAC-SHA256 Credential=${this.accessKeyId}/${credentialScope}, SignedHeaders=${signedHeadersStr}, Signature=${signature}`;

    return {
      ...canonicalHeadersMap,
      'Authorization': authHeader
    };
  }

  /**
   * Uploads a raw Buffer to Cloudflare R2 bucket.
   * @param {Object} params
   * @param {string} params.key - Destination key (e.g. 'prescriptions/cust_123/rx_456.jpg')
   * @param {Buffer} params.buffer - Image binary buffer
   * @param {string} [params.contentType='image/jpeg']
   * @returns {Promise<string>} Public CDN URL of uploaded asset
   */
  async uploadBuffer({ key, buffer, contentType = 'image/jpeg' }) {
    if (!this.isConfigured()) {
      throw new Error('Cloudflare R2 is not configured. Missing R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, or R2_SECRET_ACCESS_KEY.');
    }

    const cleanKey = key.replace(/^\/+/, '');
    const pathname = `/${this.bucketName}/${cleanKey}`;
    const host = `${this.accountId}.r2.cloudflarestorage.com`;

    const customHeaders = {
      'content-type': contentType,
      'content-length': String(buffer.length)
    };

    const signedHeaders = this.generateSigV4Headers({
      method: 'PUT',
      pathname,
      headers: customHeaders,
      payloadBuffer: buffer
    });

    return new Promise((resolve, reject) => {
      const req = https.request(
        {
          hostname: host,
          port: 443,
          path: pathname,
          method: 'PUT',
          headers: signedHeaders,
          timeout: 15000
        },
        (res) => {
          let responseBody = '';
          res.on('data', chunk => { responseBody += chunk; });
          res.on('end', () => {
            if (res.statusCode >= 200 && res.statusCode < 300) {
              const publicUrl = this.publicDomain
                ? `${this.publicDomain}/${cleanKey}`
                : `https://${host}/${this.bucketName}/${cleanKey}`;
              resolve(publicUrl);
            } else {
              reject(new Error(`Cloudflare R2 upload failed [HTTP ${res.statusCode}]: ${responseBody}`));
            }
          });
        }
      );

      req.on('error', err => reject(err));
      req.on('timeout', () => {
        req.destroy();
        reject(new Error('Cloudflare R2 upload timed out after 15000ms'));
      });

      req.write(buffer);
      req.end();
    });
  }

  /**
   * Uploads a data URI (e.g. 'data:image/jpeg;base64,...') to Cloudflare R2.
   * @param {Object} params
   * @param {string} params.key
   * @param {string} params.dataUri
   * @returns {Promise<string>} Public CDN URL
   */
  async uploadDataUri({ key, dataUri }) {
    const match = dataUri.match(/^data:([^;]+);base64,(.+)$/);
    if (!match) {
      throw new Error('Invalid data URI format. Expected data:[mime];base64,[data]');
    }
    const contentType = match[1] || 'image/jpeg';
    const buffer = Buffer.from(match[2], 'base64');
    return this.uploadBuffer({ key, buffer, contentType });
  }
}

const defaultR2Service = new CloudflareR2Service();

module.exports = {
  CloudflareR2Service,
  defaultR2Service
};
