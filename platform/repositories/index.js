/**
 * Commerce OS Production Persistence Architecture & Repository Layer
 *
 * Implements strict ACID transactional boundaries, row-level locking (SELECT ... FOR UPDATE),
 * conditional multi-instance atomic offer acceptance, decline transactions, atomic stock reservation/order placement,
 * catalog resolution, COD ledger, prescription validation, and Transactional Outbox event dispatching.
 */

const crypto = require('crypto');


function getOtpPepper() {
  const isProd = process.env.COMMERCEOS_ENV === 'production' || process.env.COMMERCEOS_PERSISTENCE_MODE === 'postgres';
  if (isProd && !process.env.COMMERCEOS_OTP_PEPPER) {
    throw new Error('FATAL_CONFIGURATION_ERROR: COMMERCEOS_OTP_PEPPER is strictly required in production mode. Secret cannot be empty.');
  }
  return process.env.COMMERCEOS_OTP_PEPPER || 'local_test_otp_pepper_non_prod';
}

class DeliveryOtpService {
  static OTP_EXPIRY_MINUTES = 45;

  static generateSecureOtp() {
    return String(crypto.randomInt(100000, 1000000));
  }

  static getOtpExpiryDate() {
    return new Date(Date.now() + this.OTP_EXPIRY_MINUTES * 60 * 1000);
  }

  static hashOtp(otp, salt = null) {
    const pepper = salt || getOtpPepper();
    return crypto.createHmac('sha256', pepper).update(String(otp).trim()).digest('hex');
  }

  static verifyOtp(enteredPin, authoritativePinOrHash, currentAttempts = 0, maxAttempts = 5, isExpired = false) {
    if (isExpired) {
      return { ok: false, error: 'OTP_EXPIRED', message: 'Delivery PIN has expired. Please request customer to refresh PIN.' };
    }
    if (currentAttempts >= maxAttempts) {
      return { ok: false, error: 'MAX_ATTEMPTS_EXCEEDED', message: 'Too many incorrect attempts. Please request a new delivery PIN.' };
    }
    if (!enteredPin || !authoritativePinOrHash) {
      return { ok: false, error: 'INVALID_PIN', message: 'PIN cannot be empty.' };
    }
    const cleanEntered = String(enteredPin).trim();
    const cleanAuth = String(authoritativePinOrHash).trim();
    const enteredHash = this.hashOtp(cleanEntered);

    const isProd = process.env.COMMERCEOS_ENV === 'production' || process.env.COMMERCEOS_PERSISTENCE_MODE === 'postgres';

    // In production, strictly enforce cryptographic hash comparison
    if (cleanAuth.length === 64) {
      try {
        const hashA = Buffer.from(enteredHash, 'hex');
        const hashB = Buffer.from(cleanAuth, 'hex');
        if (hashA.length === hashB.length && crypto.timingSafeEqual(hashA, hashB)) {
          return { ok: true };
        }
      } catch {
        return { ok: false, error: 'HASH_VERIFICATION_FAILED' };
      }
    } else if (!isProd) {
      // Local development fallback only
      if (cleanEntered === cleanAuth || enteredHash === this.hashOtp(cleanAuth)) {
        return { ok: true };
      }
    }

    return { ok: false, error: 'INVALID_OTP', message: 'Incorrect OTP. Please check with customer and try again.' };
  }
}

class TransactionalCatalogRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async getSellableProductBySku(sku, storeId = null) {
    if (!this.pool) return null;
    // Finalized catalog model: products is the GLOBAL catalog identity (store_id is advisory/nullable).
    // Store-scoped sellable availability is represented by inventory, never by products.store_id.
    const res = await this.pool.query(
      `SELECT id, sku, name, brand_name, pack_size, mrp, price, discounted_price,
              rx_requirement, cold_chain_required, category, image_url, is_active,
              created_at, updated_at
       FROM products
       WHERE (sku = $1 OR id = $1) AND is_active = TRUE
       LIMIT 1`,
      [sku]
    );
    const product = res.rows[0] || null;
    if (!product || !storeId) return product;
    // Verify exact store-scoped availability through the canonical triple (store_id, product_id, sku).
    const invRes = await this.pool.query(
      `SELECT 1 FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3 LIMIT 1`,
      [storeId, product.id, product.sku]
    );
    return invRes.rows.length > 0 ? product : null;
  }

  async getActiveProducts(storeId = null) {
    if (!this.pool) return [];
    if (storeId) {
      // Store-scoped product list is derived from store inventory joined to the global catalog,
      // with no dependence on products.store_id being populated and no per-store product duplication.
      const res = await this.pool.query(
        `SELECT DISTINCT
           p.id, p.sku, p.name, p.brand_name, p.pack_size, p.mrp, p.price,
           p.discounted_price, p.rx_requirement, p.cold_chain_required,
           p.category, p.image_url, p.is_active,
           i.store_id AS inventory_store_id,
           i.stock_count,
           i.reserved_count,
           (i.stock_count - i.reserved_count) AS available_count
         FROM inventory i
         JOIN products p ON i.product_id = p.id
         WHERE i.store_id = $1 AND p.is_active = TRUE
         ORDER BY p.name ASC`,
        [storeId]
      );
      return res.rows;
    }
    const res = await this.pool.query(
      `SELECT * FROM products WHERE is_active = TRUE ORDER BY name ASC`
    );
    return res.rows;
  }

  async hasCatalogWriteAuth(sub) {
    if (!this.pool || !sub) return false;
    // DB-backed GLOBAL_CATALOG_WRITE: JWT.sub must map to an ACTIVE catalog_admins member
    // holding the explicit GLOBAL_CATALOG_WRITE permission. A JWT role claim alone is never
    // sufficient — membership is resolved from PostgreSQL.
    const res = await this.pool.query(
      `SELECT permissions FROM catalog_admins
       WHERE (operator_id = $1 OR id = $1) AND status = 'ACTIVE'
       LIMIT 1`,
      [sub]
    );
    if (res.rows.length === 0) return false;
    const perms = res.rows[0].permissions || [];
    return Array.isArray(perms) && perms.includes('GLOBAL_CATALOG_WRITE');
  }

  async saveProductTransactionally(product, storeId = null) {
    if (!this.pool) return null;
    // Finalized model (Option A): products rows are GLOBAL catalog identity.
    // products.store_id is legacy/advisory and always NULL for global products.
    // products.sku UNIQUE enforces "no duplicate global SKU" at the database boundary.
    // NOTE: saveProductTransactionally is a GLOBAL CATALOG authority mutation. Sellers must route
    // store-level changes through inventory, never through this method.
    const res = await this.pool.query(
      `INSERT INTO products (
        id, sku, name, brand_name, pack_size, mrp, price, discounted_price,
        rx_requirement, category, is_active, store_id, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, TRUE, NULL, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        brand_name = EXCLUDED.brand_name,
        pack_size = EXCLUDED.pack_size,
        mrp = EXCLUDED.mrp,
        price = EXCLUDED.price,
        discounted_price = EXCLUDED.discounted_price,
        rx_requirement = EXCLUDED.rx_requirement,
        category = EXCLUDED.category,
        store_id = NULL,
        updated_at = NOW()
      RETURNING *`,
      [
        product.id || ('prod_' + crypto.randomUUID()),
        product.sku || ('SKU_' + crypto.randomUUID()),
        product.name,
        product.brandName || product.brand || product.brand_name || null,
        product.packSize || product.pack_size || null,
        Number(product.mrp || product.price || 0),
        Number(product.price || 0),
        Number(product.discountedPrice || product.discounted_price || product.price || 0),
        (product.rxRequirement || product.rx_requirement || 'OTC').toUpperCase(),
        product.category ? String(product.category) : null
      ]
    );
    return res.rows[0] || null;
  }

  async deleteProductTransactionally(productId, storeId = null) {
    if (!this.pool) return false;
    // Global Catalog Authority Requirement: a pure global-product model has no store-scoped
    // deactivation. This mutation belongs exclusively to GLOBAL_CATALOG_WRITE operators and is
    // therefore not reachable by seller store operations (sellers disable their own inventory).
    if (!storeId) {
      const res = await this.pool.query(
        `UPDATE products SET is_active = FALSE, updated_at = NOW()
         WHERE (id = $1 OR sku = $1)
         RETURNING *`,
        [productId]
      );
      return res.rowCount > 0;
    }
    // Legacy store-scoped path is intentionally opaque: it must NEVER be reached from a seller
    // route. If invoked it refuses rather than risk cross-merchant global deactivation.
    return false;
  }
}

class LocalDevelopmentCatalogRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async hasCatalogWriteAuth(sub) {
    const dbAdmins = (this.db.catalog_admins || this.db.catalogAdmins || []);
    const ctx = dbAdmins.find(a => (a.operator_id === sub || a.id === sub) && a.status === 'ACTIVE');
    if (!ctx) return false;
    const perms = ctx.permissions || [];
    return Array.isArray(perms) && perms.includes('GLOBAL_CATALOG_WRITE');
  }

  async getSellableProductBySku(sku, storeId = null) {
    return (this.db.products || []).find((p) => (p.sku === sku || p.id === sku) && (!storeId || p.storeId === storeId || p.store_id === storeId) && p.inStock !== false && p.isActive !== false) || null;
  }

  async getActiveProducts(storeId = null) {
    return (this.db.products || []).filter(p => p.inStock !== false && (!storeId || p.storeId === storeId || p.store_id === storeId));
  }

  async saveProductTransactionally(product, storeId = null) {
    this.db.products = this.db.products || [];
    const idx = this.db.products.findIndex(p => p.id === product.id || p.sku === product.sku);
    if (idx >= 0) {
      this.db.products[idx] = { ...this.db.products[idx], ...product };
    } else {
      this.db.products.unshift(product);
    }
    this.saveDb();
    return product;
  }

  async deleteProductTransactionally(productId, storeId = null) {
    this.db.products = (this.db.products || []).filter(p => !(p.id === productId || p.sku === productId));
    this.saveDb();
    return true;
  }
}

class TransactionalCustomerRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async findCustomerById(customerId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM customers WHERE id = $1 LIMIT 1`, [customerId]);
    return res.rows[0] || null;
  }

  async findById(customerId) {
    return this.findCustomerById(customerId);
  }

  async findCustomerByPhone(phone) {
    if (!this.pool) return null;
    const digitsOnly = String(phone).replace(/\D/g, '');
    const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : digitsOnly;
    const res = await this.pool.query(
      `SELECT * FROM customers WHERE phone = $1 OR phone LIKE '%' || $2 LIMIT 1`,
      [phone, cleanPhone]
    );
    return res.rows[0] || null;
  }

  async createCustomer(customerData) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `INSERT INTO customers (id, phone, full_name, email, tier, is_active, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, TRUE, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, updated_at = NOW()
       RETURNING *`,
      [
        customerData.id || ('cust_' + crypto.randomUUID()),
        customerData.phone,
        customerData.fullName || customerData.name || null,
        customerData.email || null,
        customerData.tier || 'STANDARD'
      ]
    );
    return res.rows[0];
  }

  async getAllCustomers() {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT id, full_name, phone, email, tier, is_active, created_at FROM customers ORDER BY created_at DESC`);
    return res.rows;
  }

  async createOtpChallenge(phone, challengeId, otpHash, expiresAt) {
    if (!this.pool) return null;
    await this.pool.query(
      `INSERT INTO auth_challenges (id, phone, otp_hash, expires_at, attempts, created_at)
       VALUES ($1, $2, $3, $4, 0, NOW())`,
      [challengeId, phone, otpHash, new Date(expiresAt)]
    );
    return { challengeId, phone, expiresAt };
  }

  async verifyOtpChallenge(phone, challengeId, submittedOtp, isLocalTest = false) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const res = await this.pool.query(
      `SELECT * FROM auth_challenges WHERE id = $1 AND phone = $2 LIMIT 1`,
      [challengeId, phone]
    );
    const challenge = res.rows[0];
    if (!challenge) {
      return { ok: false, error: 'CHALLENGE_NOT_FOUND', message: 'Invalid or expired OTP challenge.' };
    }
    if (challenge.verified_at != null) {
      return { ok: false, error: 'CHALLENGE_ALREADY_VERIFIED', message: 'OTP challenge has already been verified.' };
    }
    if (new Date(challenge.expires_at) < new Date()) {
      return { ok: false, error: 'OTP_EXPIRED', message: 'OTP challenge has expired. Request a new OTP.' };
    }
    if (Number(challenge.attempts || 0) >= 5) {
      return { ok: false, error: 'MAX_ATTEMPTS_EXCEEDED', message: 'Maximum verification attempts exceeded. Request a new OTP.' };
    }

    const submittedHash = crypto.createHash('sha256').update(String(submittedOtp).trim()).digest('hex');
    if (challenge.otp_hash !== submittedHash) {
      await this.pool.query(
        `UPDATE auth_challenges SET attempts = COALESCE(attempts, 0) + 1 WHERE id = $1`,
        [challengeId]
      );
      return { ok: false, error: 'INVALID_OTP', message: 'Incorrect OTP entered.', attemptsLeft: 5 - ((challenge.attempts || 0) + 1) };
    }

    await this.pool.query(
      `UPDATE auth_challenges SET verified_at = NOW(), attempts = COALESCE(attempts, 0) + 1 WHERE id = $1`,
      [challengeId]
    );
    return { ok: true, phone };
  }
}

class LocalDevelopmentCustomerRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async findCustomerById(customerId) {
    return (this.db.users || []).find((u) => u.id === customerId) || null;
  }

  async findById(customerId) {
    return this.findCustomerById(customerId);
  }

  async findCustomerByPhone(phone) {
    const digitsOnly = String(phone).replace(/\D/g, '');
    const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : digitsOnly;
    return (this.db.users || []).find((u) => u.phone === phone || u.phone.includes(cleanPhone)) || null;
  }

  async createCustomer(customerData) {
    this.db.users = this.db.users || [];
    const record = {
      id: customerData.id || ('cust_' + Date.now()),
      name: customerData.name || 'Commerce Customer',
      phone: customerData.phone,
      email: customerData.email || null,
      role: customerData.role || 'ROLE_CUSTOMER',
      createdAt: new Date().toISOString()
    };
    this.db.users.push(record);
    this.saveDb();
    return record;
  }

  async getAllCustomers() {
    return this.db.users || [];
  }

  async createOtpChallenge(phone, challengeId, otpHash, expiresAt) {
    this.db.authChallenges = this.db.authChallenges || {};
    this.db.authChallenges[challengeId] = { phone, otpHash, expiresAt, attempts: 0, verifiedAt: null };
    this.saveDb();
    return { challengeId, phone, expiresAt };
  }

  async verifyOtpChallenge(phone, challengeId, submittedOtp, isLocalTest = true) {
    this.db.authChallenges = this.db.authChallenges || {};
    const challenge = this.db.authChallenges[challengeId];
    if (!challenge) {
      return { ok: false, error: 'CHALLENGE_NOT_FOUND', message: 'Invalid or expired OTP challenge.' };
    }
    if (challenge.verifiedAt) {
      return { ok: false, error: 'CHALLENGE_ALREADY_VERIFIED', message: 'OTP challenge has already been verified.' };
    }
    const submittedHash = crypto.createHash('sha256').update(String(submittedOtp).trim()).digest('hex');
    const isMasterCode = isLocalTest && String(submittedOtp).trim() === '123456';
    if (challenge.otpHash !== submittedHash && !isMasterCode) {
      challenge.attempts = (challenge.attempts || 0) + 1;
      this.saveDb();
      return { ok: false, error: 'INVALID_OTP', message: 'Incorrect OTP entered.' };
    }
    challenge.verifiedAt = new Date().toISOString();
    this.saveDb();
    return { ok: true, phone };
  }
}

class TransactionalSellerRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  static hashPassword(password, salt = null) {
    const s = salt || crypto.randomBytes(16).toString('hex');
    const derived = crypto.scryptSync(password, s, 64).toString('hex');
    return `scrypt:${s}:${derived}`;
  }

  static verifyPassword(password, storedHash) {
    if (!storedHash || !password) return false;
    if (!storedHash.startsWith('scrypt:')) return false;
    const parts = storedHash.split(':');
    if (parts.length !== 3) return false;
    const salt = parts[1];
    const expected = parts[2];
    const derived = crypto.scryptSync(password, salt, 64).toString('hex');
    const bufA = Buffer.from(derived, 'hex');
    const bufB = Buffer.from(expected, 'hex');
    return bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB);
  }

  async findSellerByIdentifier(identifier) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT s.*, st.store_name, st.address as store_address, st.is_active as store_active 
       FROM sellers s
       LEFT JOIN stores st ON s.store_id = st.id
       WHERE s.id = $1 OR s.email = $1 OR s.seller_id = $1 LIMIT 1`,
      [identifier]
    );
    return res.rows[0] || null;
  }

  async verifySellerCredentials(identifier, password) {
    const seller = await this.findSellerByIdentifier(identifier);
    if (!seller) return { ok: false, error: 'INVALID_CREDENTIALS', message: 'Seller not registered.' };
    if (seller.status && seller.status !== 'ACTIVE') {
      return { ok: false, error: 'ACCOUNT_SUSPENDED', message: 'Seller account is inactive.' };
    }
    const isPasswordValid = TransactionalSellerRepository.verifyPassword(password, seller.password_hash);
    if (!isPasswordValid) {
      return { ok: false, error: 'INVALID_CREDENTIALS', message: 'Incorrect merchant credentials.' };
    }
    if (!seller.store_id || !seller.store_active) {
      return { ok: false, error: 'UNASSIGNED_STORE', message: 'Seller is not mapped to an active store.' };
    }
    return {
      ok: true,
      seller: {
        sellerId: seller.id || seller.seller_id,
        storeId: seller.store_id,
        storeName: seller.store_name,
        roles: seller.roles || ['ROLE_SELLER']
      }
    };
  }
}

class LocalDevelopmentSellerRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
    this.registry = {
      'seller_rewari_01': {
        passwordHash: TransactionalSellerRepository.hashPassword('rewari_hub_sec_881'),
        storeId: 'STORE_REWARI_01',
        storeName: 'Rewari Central Hub',
        merchantName: 'Commerce OS Retail Ltd',
        phone: '9876543210',
        roles: ['ROLE_SELLER']
      },
      'seller_gurugram_01': {
        passwordHash: TransactionalSellerRepository.hashPassword('gurugram_hub_sec_881'),
        storeId: 'STORE_REWARI_01',
        storeName: 'Rewari Central Hub',
        merchantName: 'Commerce OS Retail Ltd',
        phone: '9876543210',
        roles: ['ROLE_SELLER']
      },
      'seller_demo_001': {
        passwordHash: TransactionalSellerRepository.hashPassword('seller_pass_123'),
        storeId: 'STORE_REWARI_01',
        storeName: 'Rewari Central Hub',
        merchantName: 'Commerce OS Retail Ltd',
        phone: '9876543210',
        roles: ['ROLE_SELLER']
      }
    };
  }

  async verifySellerCredentials(identifier, password) {
    const target = this.registry[identifier] || Object.values(this.registry).find(s => s.phone === identifier);
    if (!target) return { ok: false, error: 'INVALID_CREDENTIALS', message: 'Seller not registered.' };
    const isPasswordValid = TransactionalSellerRepository.verifyPassword(password, target.passwordHash);
    if (!isPasswordValid) {
      return { ok: false, error: 'INVALID_CREDENTIALS', message: 'Incorrect merchant credentials.' };
    }
    return {
      ok: true,
      seller: {
        sellerId: identifier,
        storeId: target.storeId,
        storeName: target.storeName,
        merchantName: target.merchantName,
        roles: target.roles
      }
    };
  }
}

class TransactionalAuditRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async recordLog(actorId, action, details, storeId = null) {
    if (!this.pool) return;
    await this.pool.query(
      `INSERT INTO audit_logs (id, actor_id, action, details, store_id, created_at)
       VALUES ($1, $2, $3, $4, $5, NOW())`,
      ['aud_' + crypto.randomUUID(), actorId, action, details, storeId]
    );
  }

  async getRecentAuditLogs(limit = 100) {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT $1`, [limit]);
    return res.rows;
  }

  async getLogsByStore(storeId, limit = 100) {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT * FROM audit_logs WHERE store_id = $1 ORDER BY created_at DESC LIMIT $2`, [storeId, limit]);
    return res.rows;
  }

  async getLogs(claims = {}) {
    if (!this.pool) return [];
    const role = (claims.role || '').toUpperCase();
    if (role === 'ROLE_ADMIN' || role === 'ADMIN' || role === 'AUDITOR') {
      const res = await this.pool.query(`SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 100`);
      return res.rows;
    }
    if (role === 'ROLE_SELLER' || role === 'SELLER') {
      const storeId = claims.storeId;
      const res = await this.pool.query(
        `SELECT * FROM audit_logs WHERE store_id = $1 OR actor_id = $2 ORDER BY created_at DESC LIMIT 100`,
        [storeId, claims.sub]
      );
      return res.rows;
    }
    return [];
  }
}

class LocalDevelopmentAuditRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async recordLog(actorId, action, details, storeId = null) {
    this.db.auditLogs = this.db.auditLogs || [];
    this.db.auditLogs.unshift({
      id: 'aud_' + Date.now(),
      actorId,
      action,
      details,
      storeId,
      createdAt: new Date().toISOString()
    });
    this.saveDb();
  }

  async getRecentAuditLogs(limit = 100) {
    this.db.auditLogs = this.db.auditLogs || [];
    return this.db.auditLogs.slice(0, limit);
  }

  async getLogsByStore(storeId, limit = 100) {
    this.db.auditLogs = this.db.auditLogs || [];
    return this.db.auditLogs.filter(l => l.storeId === storeId).slice(0, limit);
  }

  async getLogs(claims = {}) {
    const all = this.db.auditLogs || [];
    const role = (claims.role || '').toUpperCase();
    if (role === 'ROLE_ADMIN' || role === 'ADMIN' || role === 'AUDITOR') {
      return all;
    }
    if (role === 'ROLE_SELLER' || role === 'SELLER') {
      return all.filter(l => l.storeId === claims.storeId || l.actorId === claims.sub);
    }
    return [];
  }
}

class TransactionalAddressRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async findAddressById(customerId, addressId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT * FROM customer_addresses WHERE customer_id = $1 AND id = $2 LIMIT 1`,
      [customerId, addressId]
    );
    return res.rows[0] || null;
  }

  async getDefaultAddress(customerId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT * FROM customer_addresses WHERE customer_id = $1 ORDER BY is_default DESC, created_at DESC LIMIT 1`,
      [customerId]
    );
    return res.rows[0] || null;
  }

  async getAddresses(customerId) {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT * FROM customer_addresses WHERE customer_id = $1 ORDER BY is_default DESC, created_at DESC`,
      [customerId]
    );
    return res.rows;
  }
}

class LocalDevelopmentAddressRepository {
  constructor(db) {
    this.db = db;
  }

  async findAddressById(customerId, addressId) {
    const list = (this.db.addresses && this.db.addresses[customerId]) || [];
    return list.find((a) => a.id === addressId) || null;
  }

  async getDefaultAddress(customerId) {
    const list = (this.db.addresses && this.db.addresses[customerId]) || [];
    return list.find((a) => a.isDefault) || list[0] || null;
  }
}

class TransactionalPrescriptionRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async findPrescriptionById(rxId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM prescriptions WHERE id = $1 LIMIT 1`, [rxId]);
    return res.rows[0] || null;
  }

  async findByCustomer(customerId) {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT * FROM prescriptions WHERE customer_id = $1 ORDER BY created_at DESC`, [customerId]);
    return res.rows;
  }

  async createPrescription(rx) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `INSERT INTO prescriptions (
        id, customer_id, patient_name, age, gender, doctor_name, doctor_registration_no,
        attachments, note, status, pharmacist_id, license_no, rejection_reason, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, NOW(), NOW())
      RETURNING *`,
      [
        rx.id,
        rx.customerId,
        rx.patientName,
        rx.age || null,
        rx.gender || null,
        rx.doctorName || null,
        rx.doctorRegistrationNo || null,
        JSON.stringify(rx.attachments || []),
        rx.note || '',
        rx.status || 'PENDING',
        rx.pharmacistId || null,
        rx.licenseNo || null,
        rx.rejectionReason || null
      ]
    );
    return res.rows[0] || null;
  }

  async getPendingPrescriptions() {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT p.*, o.items as order_items FROM prescriptions p
       LEFT JOIN orders o ON o.order_id = p.id OR o.id = p.id
       WHERE p.status = 'PENDING'
       ORDER BY p.created_at DESC`
    );
    return res.rows.map(r => ({
      id: r.id,
      orderId: r.id,
      customerId: r.customer_id,
      patientName: r.patient_name || ('Customer #' + String(r.customer_id).slice(0, 6)),
      rxItems: (r.order_items ? (typeof r.order_items === 'string' ? JSON.parse(r.order_items) : r.order_items) : []).filter(i => i.rxRequired),
      uploadedAt: r.created_at,
      status: r.status,
      attachments: typeof r.attachments === 'string' ? JSON.parse(r.attachments) : (r.attachments || [])
    }));
  }

  async verifyPrescription(rxId, verificationData) {
    if (!this.pool) return null;
    if (!verificationData.pharmacistId) {
      throw new Error('VERIFICATION_DATA_INCOMPLETE: Authoritative pharmacist identifier is strictly required.');
    }
    const res = await this.pool.query(
      `UPDATE prescriptions 
       SET status = $1, pharmacist_id = $2, license_no = $3, reviewed_at = NOW(), rejection_reason = $4, updated_at = NOW()
       WHERE id = $5
       RETURNING *`,
      [
        verificationData.status,
        verificationData.pharmacistId,
        verificationData.licenseNo || null,
        verificationData.rejectionReason || null,
        rxId
      ]
    );
    return res.rows[0] || null;
  }
}

class LocalDevelopmentPrescriptionRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async getPendingPrescriptions() {
    return (this.db.prescriptions || []).filter(p => p.status === 'PENDING');
  }

  async findPrescriptionById(rxId) {
    return (this.db.prescriptions || []).find((p) => p.id === rxId) || null;
  }

  async findByCustomer(customerId) {
    return (this.db.prescriptions || []).filter((p) => p.customerId === customerId);
  }

  async createPrescription(rx) {
    this.db.prescriptions = this.db.prescriptions || [];
    this.db.prescriptions.unshift(rx);
    this.saveDb();
    return rx;
  }

  async verifyPrescription(rxId, verificationData) {
    if (!verificationData.pharmacistId) {
      throw new Error('VERIFICATION_DATA_INCOMPLETE: Authoritative pharmacist identifier is strictly required.');
    }
    const rx = (this.db.prescriptions || []).find((p) => p.id === rxId);
    if (rx) {
      rx.status = verificationData.status;
      rx.pharmacistId = verificationData.pharmacistId;
      rx.licenseNo = verificationData.licenseNo || null;
      rx.reviewedAt = new Date().toISOString();
      rx.rejectionReason = verificationData.rejectionReason || null;
      rx.updatedAt = new Date().toISOString();
      this.saveDb();
      return rx;
    }
    return null;
  }
}

class TransactionalCodLedgerRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async recordEntry(entry) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `INSERT INTO cod_ledger (
        id, order_id, seller_id, amount_expected, amount_collected, shortage_amount,
        status, collector_id, notes, reconciled, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW())
      RETURNING *`,
      [
        entry.id || 'cod_tx_' + crypto.randomUUID(),
        entry.orderId,
        entry.sellerId || null,
        entry.amountExpected || 0,
        entry.amountCollected || 0,
        entry.shortageAmount || 0,
        entry.status || 'PENDING_COLLECTION',
        entry.collectorId || null,
        entry.notes || null,
        Boolean(entry.reconciled)
      ]
    );
    return res.rows[0] || null;
  }

  async findByOrderId(orderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM cod_ledger WHERE order_id = $1 ORDER BY created_at DESC LIMIT 1`, [orderId]);
    return res.rows[0] || null;
  }

  async updateHandoff(orderId, updateData) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `UPDATE cod_ledger 
       SET amount_collected = $1, shortage_amount = $2, status = $3, collector_id = $4, notes = $5, reconciled = $6, updated_at = NOW()
       WHERE order_id = $7
       RETURNING *`,
      [
        updateData.amountCollected || 0,
        updateData.shortageAmount || 0,
        updateData.status || 'COLLECTED',
        updateData.collectorId || null,
        updateData.notes || null,
        Boolean(updateData.reconciled),
        orderId
      ]
    );
    return res.rows[0] || null;
  }

  async getAll() {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT * FROM cod_ledger ORDER BY created_at DESC LIMIT 200`);
    return res.rows;
  }
}

class LocalDevelopmentCodLedgerRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async recordEntry(entry) {
    this.db.codLedger = this.db.codLedger || [];
    this.db.codLedger.unshift(entry);
    this.saveDb();
    return entry;
  }

  async findByOrderId(orderId) {
    return (this.db.codLedger || []).find((c) => c.orderId === orderId) || null;
  }

  async updateHandoff(orderId, updateData) {
    this.db.codLedger = this.db.codLedger || [];
    let entry = this.db.codLedger.find((c) => c.orderId === orderId);
    if (entry) {
      entry.amountCollected = updateData.amountCollected ?? entry.amountCollected;
      entry.shortageAmount = updateData.shortageAmount ?? entry.shortageAmount;
      entry.status = updateData.status ?? entry.status;
      entry.collectorId = updateData.collectorId ?? entry.collectorId;
      entry.notes = updateData.notes ?? entry.notes;
      entry.reconciled = updateData.reconciled !== undefined ? Boolean(updateData.reconciled) : entry.reconciled;
      entry.updatedAt = new Date().toISOString();
    }
    this.saveDb();
    return entry || null;
  }

  async getAll() {
    return this.db.codLedger || [];
  }
}

class TransactionalServiceabilityRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async getAllActiveStores() {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT id, store_name, address, latitude, longitude, sla_minutes, is_active 
       FROM stores 
       WHERE is_active = TRUE AND latitude IS NOT NULL AND longitude IS NOT NULL`
    );
    return res.rows;
  }

  async getFulfillmentHub(coordinates) {
    if (!this.pool || !coordinates || coordinates.latitude == null || coordinates.longitude == null) return null;
    const stores = await this.getAllActiveStores();
    if (!stores || stores.length === 0) return null;
    const lat = Number(coordinates.latitude);
    const lng = Number(coordinates.longitude);
    if (isNaN(lat) || isNaN(lng)) return null;

    let nearest = null;
    let minDistance = Infinity;
    for (const store of stores) {
      const dist = ServiceabilityService.calculateDistanceKm(lat, lng, store.latitude, store.longitude);
      if (dist != null && dist < minDistance) {
        minDistance = dist;
        nearest = { ...store, distanceKm: dist };
      }
    }
    return nearest;
  }
}

const AUTHORITATIVE_STORE_MASTER = {
  id: process.env.STORE_MASTER_ID || 'STORE_MASTER_001',
  store_id: process.env.STORE_MASTER_ID || 'STORE_MASTER_001',
  storeName: process.env.STORE_MASTER_NAME || 'Commerce OS Central Fulfillment Hub',
  store_name: process.env.STORE_MASTER_NAME || 'Commerce OS Central Fulfillment Hub',
  name: process.env.STORE_MASTER_NAME || 'Commerce OS Central Fulfillment Hub',
  address: process.env.STORE_MASTER_ADDRESS || 'Central Dark Store Hub, Sector 18',
  latitude: Number(process.env.STORE_MASTER_LAT) || 28.2021899,
  longitude: Number(process.env.STORE_MASTER_LNG) || 76.6153954,
  sla_minutes: 10,
  slaMinutes: 10,
  is_active: true,
  isActive: true
};

class LocalDevelopmentServiceabilityRepository {
  constructor(db) {
    this.db = db;
    this.isProduction = false;
  }

  async getAllActiveStores() {
    return [AUTHORITATIVE_STORE_MASTER];
  }

  async getFulfillmentHub(coordinates) {
    const lat = coordinates && coordinates.latitude != null ? Number(coordinates.latitude) : null;
    const lng = coordinates && coordinates.longitude != null ? Number(coordinates.longitude) : null;
    if (lat == null || lng == null || isNaN(lat) || isNaN(lng)) {
      return { ...AUTHORITATIVE_STORE_MASTER, distanceKm: 1.5 };
    }

    const dist = ServiceabilityService.calculateDistanceKm(lat, lng, AUTHORITATIVE_STORE_MASTER.latitude, AUTHORITATIVE_STORE_MASTER.longitude);
    return {
      ...AUTHORITATIVE_STORE_MASTER,
      distanceKm: dist != null ? dist : 1.5
    };
  }
}

class FulfillmentDecision {
  constructor({
    storeId,
    storeName = null,
    storeAddress = null,
    storeLatitude = null,
    storeLongitude = null,
    distanceKm = 0,
    slaMinutes = 10,
    decisionSource = 'SERVICEABILITY_ENGINE',
    deterministicRank = 1,
    resolvedItems = []
  }) {
    if (!storeId) throw new Error('FULFILLMENT_DECISION_INVALID: storeId is strictly required.');
    this.storeId = storeId;
    this.storeName = storeName;
    this.storeAddress = storeAddress;
    this.storeLatitude = storeLatitude != null ? Number(storeLatitude) : null;
    this.storeLongitude = storeLongitude != null ? Number(storeLongitude) : null;
    this.distanceKm = Number(distanceKm);
    this.slaMinutes = Number(slaMinutes);
    this.decisionSource = decisionSource;
    this.deterministicRank = deterministicRank;
    this.resolvedItems = resolvedItems;
    this.createdAt = new Date().toISOString();
  }
}

class NotificationDeliveryResult {
  constructor({
    status = 'DELIVERED',
    providerMessageId = null,
    errorCode = null,
    retryable = false,
    timestamp = new Date().toISOString()
  }) {
    this.status = status;
    this.ok = status === 'DELIVERED';
    this.providerMessageId = providerMessageId;
    this.errorCode = errorCode;
    this.retryable = retryable;
    this.timestamp = timestamp;
  }

  valueOf() {
    return this.ok;
  }
}

class ServiceabilityService {
  static MAX_SERVICEABLE_RADIUS_KM = 20.0;
  static PREFERRED_STORE_DISTANCE_TOLERANCE_KM = 2.0; // Policy tolerance: preferred store may win if within 2.0km road delta of nearest hub

  static calculateDistanceKm(lat1, lon1, lat2, lon2) {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null;
    const l1 = Number(lat1);
    const n1 = Number(lon1);
    const l2 = Number(lat2);
    const n2 = Number(lon2);
    if (isNaN(l1) || isNaN(n1) || isNaN(l2) || isNaN(n2)) return null;

    const R = 6371;
    const dLat = (l2 - l1) * Math.PI / 180;
    const dLon = (n2 - n1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(l1 * Math.PI / 180) * Math.cos(l2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round(R * c * 1.25 * 10) / 10;
  }

  static isServiceable(distanceKm, maxRadius = ServiceabilityService.MAX_SERVICEABLE_RADIUS_KM) {
    if (distanceKm == null || isNaN(Number(distanceKm))) return false;
    return Number(distanceKm) <= maxRadius;
  }

  static async resolveAuthoritativeFulfillmentStore({
    address,
    items = [],
    preferredStoreId = null,
    pool = null,
    storeRepo = null
  }) {
    if (!address || address.latitude == null || address.longitude == null || isNaN(Number(address.latitude)) || isNaN(Number(address.longitude))) {
      return { ok: false, error: 'INVALID_COORDINATES', message: 'Authoritative customer coordinates are required.' };
    }

    const cLat = Number(address.latitude);
    const cLng = Number(address.longitude);

    // 1. Fetch active stores
    let allActiveStores = [];
    if (pool) {
      const res = await pool.query(`SELECT id, store_name, address, latitude, longitude, sla_minutes, is_active FROM stores WHERE is_active = TRUE AND latitude IS NOT NULL AND longitude IS NOT NULL`);
      allActiveStores = res.rows;
    } else if (storeRepo && typeof storeRepo.getAllActiveStores === 'function') {
      allActiveStores = await storeRepo.getAllActiveStores();
    }

    if (!allActiveStores || allActiveStores.length === 0) {
      return { ok: false, error: 'NO_ACTIVE_STORES', message: 'No active fulfillment nodes in network.' };
    }

    // 2. Filter to candidate stores within MAX_SERVICEABLE_RADIUS_KM
    const isProd = !!pool;
    const maxRadius = isProd ? 20.0 : 25000.0;
    const candidateStores = [];
    for (const store of allActiveStores) {
      const dist = ServiceabilityService.calculateDistanceKm(cLat, cLng, store.latitude, store.longitude);
      if (dist != null && ServiceabilityService.isServiceable(dist, maxRadius)) {
        candidateStores.push({
          ...store,
          distanceKm: isProd ? dist : Math.min(dist, 2.5)
        });
      }
    }

    if (candidateStores.length === 0) {
      if (!isProd && allActiveStores.length > 0) {
        candidateStores.push({
          ...allActiveStores[0],
          distanceKm: 1.8
        });
      } else {
        return { ok: false, error: 'STORE_NOT_SERVICEABLE', message: 'Delivery location is outside the maximum serviceable radius of any active hub.' };
      }
    }

    // 3. For each candidate store, verify product catalog eligibility and inventory availability
    const normalizedItems = (items || []).map(i => ({
      sku: i.sku || i.productId || i.id,
      quantity: Math.max(1, Number(i.quantity || 1))
    }));

    const fulfillableStores = [];

    for (const store of candidateStores) {
      let isStoreFulfillable = true;

      if (normalizedItems.length > 0 && pool) {
        for (const item of normalizedItems) {
          if (!item.sku) continue;
          // 3a. Resolve GLOBAL product identity first (products is the global catalog; store_id is advisory only)
          const globalProductRes = await pool.query(
            `SELECT id, sku, is_active FROM products 
             WHERE (sku = $1 OR id = $1) AND is_active = TRUE 
             LIMIT 1`,
            [item.sku]
          );
          if (globalProductRes.rows.length === 0) {
            isStoreFulfillable = false;
            break;
          }
          const product = globalProductRes.rows[0];

          // 3b. Verify EXACT store-scoped inventory row using the canonical triple (store_id, product_id, sku)
          const invCheck = await pool.query(
            `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count 
             FROM inventory 
             WHERE store_id = $1 AND product_id = $2 AND sku = $3`,
            [store.id, product.id, product.sku]
          );
          if (invCheck.rows.length === 0) {
            isStoreFulfillable = false;
            break;
          }
          const available = Number(invCheck.rows[0].available_count);
          if (available < item.quantity) {
            isStoreFulfillable = false;
            break;
          }
        }
      }

      if (isStoreFulfillable) {
        fulfillableStores.push(store);
      }
    }

    if (fulfillableStores.length === 0) {
      return {
        ok: false,
        error: 'OUT_OF_STOCK_ACROSS_NETWORK',
        message: 'None of the nearby serviceable fulfillment nodes have available inventory for the requested items.'
      };
    }

    // 4. Decision Policy: Deterministic store ranking
    fulfillableStores.sort((a, b) => {
      // Primary: Distance ascending (shortest road travel)
      if (a.distanceKm !== b.distanceKm) {
        return a.distanceKm - b.distanceKm;
      }
      // Secondary: SLA minutes ascending (fastest delivery promise)
      if ((a.sla_minutes || 10) !== (b.sla_minutes || 10)) {
        return (a.sla_minutes || 10) - (b.sla_minutes || 10);
      }
      // Stable tie-breaker: store ID lexicographical
      return String(a.id).localeCompare(String(b.id));
    });

    let selectedStore = fulfillableStores[0];
    let decisionSource = 'OPTIMAL_SLA_PROXIMITY_RANK';

    if (preferredStoreId) {
      const preferred = fulfillableStores.find(s => s.id === preferredStoreId);
      if (preferred && preferred.distanceKm <= selectedStore.distanceKm + ServiceabilityService.PREFERRED_STORE_DISTANCE_TOLERANCE_KM) {
        selectedStore = preferred;
        decisionSource = 'CLIENT_PREFERENCE_SERVICEABLE_APPROVED';
      }
    }

    const decision = new FulfillmentDecision({
      storeId: selectedStore.id,
      storeName: selectedStore.store_name,
      storeAddress: selectedStore.address,
      storeLatitude: selectedStore.latitude,
      storeLongitude: selectedStore.longitude,
      distanceKm: selectedStore.distanceKm,
      slaMinutes: selectedStore.sla_minutes || 10,
      decisionSource,
      deterministicRank: 1,
      resolvedItems: normalizedItems
    });

    return {
      ok: true,
      storeId: decision.storeId,
      decision,
      store: selectedStore,
      distanceKm: decision.distanceKm,
      slaMinutes: decision.slaMinutes
    };
  }

  constructor(serviceabilityRepo, storeRepo = null) {
    this.serviceabilityRepo = serviceabilityRepo;
    this.storeRepo = storeRepo;
  }

  async evaluateServiceability(address, items = []) {
    if (!address || address.latitude == null || address.longitude == null || isNaN(Number(address.latitude)) || isNaN(Number(address.longitude))) {
      return {
        eligible: false,
        reason: 'INVALID_COORDINATES',
        message: 'Authoritative customer latitude and longitude are required for serviceability evaluation.'
      };
    }

    const lat = Number(address.latitude);
    const lng = Number(address.longitude);

    let hub = null;
    if (this.serviceabilityRepo) {
      hub = await this.serviceabilityRepo.getFulfillmentHub({ latitude: lat, longitude: lng });
    }

    if (!hub) {
      return {
        eligible: false,
        reason: 'NO_SERVICEABLE_STORE',
        message: 'No active fulfillment hub found in the network.'
      };
    }

    const hubLat = Number(hub.latitude);
    const hubLng = Number(hub.longitude);
    if (isNaN(hubLat) || isNaN(hubLng)) {
      return {
        eligible: false,
        reason: 'STORE_LOCATION_INVALID',
        message: 'Fulfillment node is missing valid coordinates.'
      };
    }

    const distanceKm = ServiceabilityService.calculateDistanceKm(lat, lng, hubLat, hubLng);
    const isCold = (items || []).some((i) => i.coldChain || i.coldChainRequired);

    const isProd = this.serviceabilityRepo && this.serviceabilityRepo.isProduction === true;
    const maxRadius = isProd ? 20.0 : 25000.0;

    if (!ServiceabilityService.isServiceable(distanceKm, maxRadius)) {
      return {
        eligible: false,
        reason: 'OUT_OF_SERVICE_ZONE',
        message: `Delivery location is ${distanceKm} km from nearest fulfillment node (Max serviceable radius: 20 km).`,
        fulfillmentNode: hub,
        distanceKm
      };
    }

    const effectiveDistKm = (!isProd && distanceKm > 20.0) ? 1.8 : distanceKm;

    let minEta = 8;
    let maxEta = 15;
    let slaLabel = '10-Min Hyperlocal Express SLA Guaranteed';

    if (effectiveDistKm <= 2.5) {
      minEta = 8;
      maxEta = 12;
      slaLabel = '10-Min Hyperlocal Express SLA Guaranteed';
    } else if (effectiveDistKm <= 6.0) {
      minEta = 12;
      maxEta = 20;
      slaLabel = '15-Min Quick Commerce SLA';
    } else if (effectiveDistKm <= 10.0) {
      minEta = 20;
      maxEta = 30;
      slaLabel = '25-Min Express Delivery SLA';
    } else {
      minEta = 30;
      maxEta = 45;
      slaLabel = 'Standard Fulfillment Hub SLA';
    }

    const baseDeliveryFee = 29.0;
    const distanceSurcharge = distanceKm > 4.0 ? Math.round((distanceKm - 4.0) * 5) : 0;
    const totalDeliveryFee = baseDeliveryFee + distanceSurcharge;
    const coldChainFee = isCold ? 35.0 : 0.0;

    return {
      eligible: true,
      etaMinutes: { min: minEta, max: maxEta },
      etaLabel: slaLabel,
      distanceKm,
      fulfillmentNode: {
        id: hub.id || hub.store_id || hub.storeId,
        name: hub.store_name || hub.storeName || hub.name,
        latitude: hubLat,
        longitude: hubLng,
        slaMinutes: hub.sla_minutes || hub.slaMinutes || maxEta
      },
      deliveryFee: totalDeliveryFee,
      coldChainFee: coldChainFee,
      estimatedDeliveryWindow: { min: minEta, max: maxEta }
    };
  }
}

class TransactionalInventoryRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async reserveStockTransactionally(client, storeIdOrItems, maybeItems = null) {
    const qClient = client || this.pool;
    let storeId = typeof storeIdOrItems === 'string' ? storeIdOrItems : null;
    let items = Array.isArray(storeIdOrItems) ? storeIdOrItems : (maybeItems || []);

    if (!storeId && items.length > 0) {
      storeId = items[0].storeId || items[0].store_id || null;
    }

    if (!storeId) {
      return { ok: false, error: 'STORE_ID_REQUIRED', message: 'storeId is strictly required for inventory reservation.' };
    }

    for (const item of items) {
      const sku = item.sku;
      const productId = item.productId || item.product_id || null;
      const itemStoreId = item.storeId || item.store_id || storeId;
      const qty = Math.max(1, Number(item.quantity) || 1);

      if (!productId) {
        return {
          ok: false,
          error: 'CANONICAL_PRODUCT_ID_REQUIRED',
          sku,
          message: `Canonical productId is strictly required for inventory reservation of SKU ${sku}.`
        };
      }

      const lockRes = await qClient.query(
        `SELECT stock_count, reserved_count, (stock_count - reserved_count) as available_count, product_id, sku
         FROM inventory 
         WHERE store_id = $1 AND product_id = $2 AND sku = $3
         FOR UPDATE`,
        [itemStoreId, productId, sku]
      );
      if (lockRes.rows.length === 0) {
        return { ok: false, error: 'OUT_OF_STOCK', sku, productId, requestedQty: qty, message: `SKU ${sku} (product ${productId}) not found in inventory for store ${itemStoreId}.` };
      }

      const row = lockRes.rows[0];
      const available = Number(row.available_count);

      if (available < qty) {
        return { ok: false, error: 'OUT_OF_STOCK', sku, productId, requestedQty: qty, availableQty: available };
      }

      const newReserved = Number(row.reserved_count) + qty;
      await qClient.query(
        `UPDATE inventory
         SET reserved_count = $1, updated_at = NOW()
         WHERE store_id = $2 AND product_id = $3 AND sku = $4`,
        [newReserved, itemStoreId, productId, sku]
      );

      const ledgerId = 'led_res_' + crypto.randomUUID();
      await qClient.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at)
         VALUES ($1, $2, $3, $4, 0, $5, 'RESERVATION_CREATED', 'SYSTEM_ORDER', NOW())`,
        [ledgerId, itemStoreId, productId, sku, Number(row.stock_count)]
      );
    }
    return { ok: true };
  }

  async debitStockTransactionally(client, storeIdOrItems, maybeItems = null) {
    return this.reserveStockTransactionally(client, storeIdOrItems, maybeItems);
  }

  async fulfillStockTransactionally(client, storeIdOrItems, maybeItems = null) {
    const qClient = client || this.pool;
    let storeId = typeof storeIdOrItems === 'string' ? storeIdOrItems : null;
    let items = Array.isArray(storeIdOrItems) ? storeIdOrItems : (maybeItems || []);

    if (!storeId && items.length > 0) {
      storeId = items[0].storeId || items[0].store_id || null;
    }

    if (!storeId) {
      return { ok: false, error: 'STORE_ID_REQUIRED', message: 'storeId is strictly required for inventory fulfillment.' };
    }

    for (const item of items) {
      const sku = item.sku || item.medicineId || item.id;
      const productId = item.productId || item.product_id || null;
      const itemStoreId = item.storeId || item.store_id || storeId;
      const qty = Math.max(1, Number(item.quantity) || 1);

      if (!productId) {
        return {
          ok: false,
          error: 'CANONICAL_PRODUCT_ID_REQUIRED',
          sku,
          message: `Canonical productId is strictly required for inventory fulfillment of SKU ${sku}.`
        };
      }

      const lockRes = await qClient.query(
        `SELECT stock_count, reserved_count 
         FROM inventory 
         WHERE store_id = $1 AND product_id = $2 AND sku = $3
         FOR UPDATE`,
        [itemStoreId, productId, sku]
      );
      if (lockRes.rows.length === 0) {
        return { ok: false, error: 'INVENTORY_NOT_FOUND', message: `SKU ${sku} (product ${productId}) not found in inventory during fulfillment for store ${itemStoreId}.` };
      }

      const row = lockRes.rows[0];
      const currentStock = Number(row.stock_count);
      const currentReserved = Number(row.reserved_count);

      if (currentReserved < qty || currentStock < qty) {
        return { ok: false, error: 'CORRUPT_INVENTORY_STATE', message: `Cannot fulfill SKU ${sku}. Requested qty ${qty} exceeds on-hand (${currentStock}) or reserved (${currentReserved}) stock in store ${itemStoreId}.` };
      }

      const newStock = currentStock - qty;
      const newReserved = currentReserved - qty;

      await qClient.query(
        `UPDATE inventory
         SET stock_count = $1, reserved_count = $2, updated_at = NOW()
         WHERE store_id = $3 AND product_id = $4 AND sku = $5`,
        [newStock, newReserved, itemStoreId, productId, sku]
      );

      const ledgerId = 'led_ful_' + crypto.randomUUID();
      await qClient.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, 'STOCK_CONSUMED', 'SYSTEM_ORDER', NOW())`,
        [ledgerId, itemStoreId, productId, sku, -qty, newStock]
      );
    }
    return { ok: true };
  }

  async releaseStockTransactionally(client, storeIdOrItems, maybeItems = null) {
    const qClient = client || this.pool;
    let storeId = typeof storeIdOrItems === 'string' ? storeIdOrItems : null;
    let items = Array.isArray(storeIdOrItems) ? storeIdOrItems : (maybeItems || []);

    if (!storeId && items.length > 0) {
      storeId = items[0].storeId || items[0].store_id || null;
    }

    if (!storeId) {
      return { ok: false, error: 'STORE_ID_REQUIRED', message: 'storeId is strictly required for inventory release.' };
    }

    for (const item of items) {
      const sku = item.sku || item.medicineId || item.id;
      const productId = item.productId || item.product_id || null;
      const itemStoreId = item.storeId || item.store_id || storeId;
      const qty = Math.max(1, Number(item.quantity) || 1);

      if (!productId) {
        return {
          ok: false,
          error: 'CANONICAL_PRODUCT_ID_REQUIRED',
          sku,
          message: `Canonical productId is strictly required for inventory release of SKU ${sku}.`
        };
      }

      const lockRes = await qClient.query(
        `SELECT stock_count, reserved_count 
         FROM inventory 
         WHERE store_id = $1 AND product_id = $2 AND sku = $3
         FOR UPDATE`,
        [itemStoreId, productId, sku]
      );
      if (lockRes.rows.length === 0) {
        return { ok: false, error: 'INVENTORY_NOT_FOUND', message: `SKU ${sku} (product ${productId}) not found in inventory during release for store ${itemStoreId}.` };
      }

      const row = lockRes.rows[0];
      const currentReserved = Number(row.reserved_count);

      if (currentReserved < qty) {
        return { ok: false, error: 'CORRUPT_INVENTORY_STATE', message: `Cannot release SKU ${sku}. Requested qty ${qty} exceeds reserved count (${currentReserved}) in store ${itemStoreId}.` };
      }

      const newReserved = currentReserved - qty;
      await qClient.query(
        `UPDATE inventory
         SET reserved_count = $1, updated_at = NOW()
         WHERE store_id = $2 AND product_id = $3 AND sku = $4`,
        [newReserved, itemStoreId, productId, sku]
      );

      const ledgerId = 'led_rel_' + crypto.randomUUID();
      await qClient.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at)
         VALUES ($1, $2, $3, $4, 0, $5, 'RESERVATION_RELEASED', 'SYSTEM_ORDER', NOW())`,
        [ledgerId, itemStoreId, productId, sku, Number(row.stock_count)]
      );
    }
    return { ok: true };
  }

  async getStoreInventory(storeId) {
    if (!this.pool) return [];
    if (!storeId) {
      throw new Error('FATAL: storeId is strictly required to query store inventory.');
    }
    const res = await this.pool.query(
      `SELECT 
         i.product_id,
         i.sku,
         COALESCE(p.name, i.sku) as name,
         COALESCE(p.brand_name, '') as brand_name,
         COALESCE(p.category, 'General') as category,
         COALESCE(p.pack_size, '1 Unit') as pack_size,
         COALESCE(p.mrp, p.price, 0) as mrp,
         COALESCE(p.price, 0) as price,
         COALESCE(p.discounted_price, p.price, 0) as discounted_price,
         COALESCE(i.stock_count, 0) as on_hand,
         COALESCE(i.reserved_count, 0) as reserved,
         (COALESCE(i.stock_count, 0) - COALESCE(i.reserved_count, 0)) as available,
         COALESCE(i.stock_count, 0) as stock_count
       FROM inventory i
       LEFT JOIN products p ON i.product_id = p.id
       WHERE i.store_id = $1
       ORDER BY p.name ASC`,
      [storeId]
    );
    return res.rows.map(r => ({
      id: r.sku,
      productId: r.product_id,
      sku: r.sku,
      name: r.name,
      brandName: r.brand_name || null,
      category: r.category,
      packSize: r.pack_size,
      mrp: Number(r.mrp),
      price: Number(r.price),
      discountedPrice: Number(r.discounted_price),
      onHand: Number(r.on_hand),
      reserved: Number(r.reserved),
      available: Number(r.available),
      stockCount: Number(r.stock_count)
    }));
  }

  async adjustStockForStore(storeId, productId, sku, delta, reason, actor = { type: 'SELLER' }) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };

    if (!storeId) {
      return { ok: false, httpStatus: 400, error: 'STORE_REQUIRED', message: 'Authoritative store context required.' };
    }
    if (!productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_REQUIRED', message: 'Canonical productId is strictly required for store inventory adjustment.' };
    }
    if (!sku) {
      return { ok: false, httpStatus: 400, error: 'SKU_REQUIRED', message: 'Canonical sku is strictly required for store inventory adjustment.' };
    }
    const numDelta = Number(delta);
    if (!Number.isFinite(numDelta)) {
      return { ok: false, httpStatus: 400, error: 'DELTA_REQUIRED', message: 'Numeric delta is strictly required for store inventory adjustment.' };
    }
    if (!reason || typeof reason !== 'string' || !reason.trim()) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: 'An authorized inventory audit reason is strictly required.' };
    }
    const trimmedReason = reason.trim();
    const ALLOWED_SELLER_REASONS = ['INITIAL_STOCK_LINK', 'SELLER_RESTOCK', 'SELLER_ADJUSTMENT', 'STOCK_ADJUSTMENT'];
    const ALLOWED_ADMIN_REASONS = [...ALLOWED_SELLER_REASONS, 'ADMIN_ADJUSTMENT'];
    const isAdmin = actor && (actor.type === 'ADMIN' || actor.isAdmin === true);

    if (trimmedReason === 'ADMIN_ADJUSTMENT' && !isAdmin) {
      return { ok: false, httpStatus: 403, error: 'INVALID_INVENTORY_REASON', message: 'Reason ADMIN_ADJUSTMENT requires administrator authority.' };
    }
    if (!ALLOWED_ADMIN_REASONS.includes(trimmedReason)) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: `Reason '${trimmedReason}' is invalid or unapproved.` };
    }

    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

      const pidRes = await client.query(
        `SELECT id, sku, is_active FROM products WHERE id = $1`,
        [productId]
      );
      if (pidRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'PRODUCT_NOT_FOUND', message: `Product ${productId} not found in the global catalog.` };
      }
      if (!pidRes.rows[0].is_active) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'PRODUCT_INACTIVE', message: `Product ${productId} is deactivated in the global catalog and cannot receive inventory.` };
      }
      if (String(pidRes.rows[0].sku) !== String(sku)) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_SKU_MISMATCH', message: `productId ${productId} maps to SKU ${pidRes.rows[0].sku}, not ${sku}.` };
      }

      const lockRes = await client.query(
        `SELECT stock_count, reserved_count FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3 FOR UPDATE`,
        [storeId, productId, String(sku)]
      );
      if (lockRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'SKU_NOT_FOUND_IN_STORE', message: `Product ${sku} has no inventory row in store ${storeId}. Link product first via setStockForStore.` };
      }

      const currentStock = Number(lockRes.rows[0].stock_count || 0);
      const currentReserved = Number(lockRes.rows[0].reserved_count || 0);
      const nextStock = currentStock + numDelta;

      if (nextStock < 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'INSUFFICIENT_STOCK', message: `Cannot adjust stock below zero (current: ${currentStock}, delta: ${numDelta})` };
      }
      if (nextStock < currentReserved) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: 'INSUFFICIENT_STOCK', message: `Cannot adjust stock below active reservations (current: ${currentStock}, reserved: ${currentReserved}, requested: ${nextStock})` };
      }

      if (numDelta === 0) {
        await client.query('COMMIT');
        return { ok: true, noChange: true, sku, delta: 0, newStock: currentStock, reason: 'NO_CHANGE' };
      }

      await client.query(
        `UPDATE inventory SET stock_count = $1, updated_at = NOW() WHERE store_id = $2 AND product_id = $3 AND sku = $4`,
        [nextStock, storeId, productId, String(sku)]
      );

      const adjustmentId = 'adj_' + crypto.randomUUID();
      const actorId = (actor && (actor.id || actor.actorId)) || (isAdmin ? 'ADMIN' : 'SELLER');
      await client.query(
        `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())`,
        [adjustmentId, storeId, productId, String(sku), numDelta, nextStock, trimmedReason, actorId]
      );

      await client.query('COMMIT');
      return { ok: true, adjustmentId, storeId, productId, sku, delta: numDelta, newStock: nextStock, reason: trimmedReason };
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      throw err;
    } finally {
      client.release();
    }
  }

  async getStoreInventoryHistory(storeId) {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at
       FROM inventory_ledger
       WHERE store_id = $1
       ORDER BY created_at DESC
       LIMIT 100`,
      [storeId]
    );
    return res.rows.map(r => ({
      id: r.id,
      storeId: r.store_id,
      productId: r.product_id,
      sku: r.sku,
      previousStock: Number(r.new_stock - r.delta),
      newStock: Number(r.new_stock),
      delta: Number(r.delta),
      reason: r.reason,
      actorId: r.actor_id,
      timestamp: r.created_at
    }));
  }

  async setStockForStore(storeId, productId, sku, requestedStock, reason, actor = { type: 'SELLER' }) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };

    if (!storeId) {
      return { ok: false, httpStatus: 400, error: 'STORE_REQUIRED', message: 'Authoritative store context required.' };
    }
    if (!productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_REQUIRED', message: 'Canonical productId is strictly required for store inventory mutation.' };
    }
    if (!sku) {
      return { ok: false, httpStatus: 400, error: 'SKU_REQUIRED', message: 'Canonical sku is strictly required for store inventory mutation.' };
    }
    const target = Number(requestedStock);
    if (!Number.isFinite(target) || target < 0) {
      return { ok: false, httpStatus: 400, error: 'INVALID_STOCK_COUNT', message: 'A non-negative numeric stock count is required.' };
    }
    if (!reason || typeof reason !== 'string' || !reason.trim()) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: 'An authorized inventory audit reason is strictly required.' };
    }
    const trimmedReason = reason.trim();
    const ALLOWED_SELLER_REASONS = ['INITIAL_STOCK_LINK', 'SELLER_RESTOCK', 'SELLER_ADJUSTMENT', 'STOCK_ADJUSTMENT'];
    const ALLOWED_ADMIN_REASONS = [...ALLOWED_SELLER_REASONS, 'ADMIN_ADJUSTMENT'];
    const isAdmin = actor && (actor.type === 'ADMIN' || actor.isAdmin === true);

    if (trimmedReason === 'ADMIN_ADJUSTMENT' && !isAdmin) {
      return { ok: false, httpStatus: 403, error: 'INVALID_INVENTORY_REASON', message: 'Reason ADMIN_ADJUSTMENT requires administrator authority.' };
    }
    if (!ALLOWED_ADMIN_REASONS.includes(trimmedReason)) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: `Reason '${trimmedReason}' is invalid or unapproved.` };
    }

    const client = await this.pool.connect();
    const MAX_ATTEMPTS = 3;
    try {
      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        try {
          await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

          const pidRes = await client.query(
            `SELECT id, sku, is_active FROM products WHERE id = $1`,
            [productId]
          );
          if (pidRes.rows.length === 0) {
            await client.query('ROLLBACK');
            return { ok: false, httpStatus: 404, error: 'PRODUCT_NOT_FOUND', message: `Product ${productId} not found in the global catalog.` };
          }
          if (!pidRes.rows[0].is_active) {
            await client.query('ROLLBACK');
            return { ok: false, httpStatus: 400, error: 'PRODUCT_INACTIVE', message: `Product ${productId} is deactivated in the global catalog and cannot receive inventory.` };
          }
          if (String(pidRes.rows[0].sku) !== String(sku)) {
            await client.query('ROLLBACK');
            return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_SKU_MISMATCH', message: `productId ${productId} maps to SKU ${pidRes.rows[0].sku}, not ${sku}.` };
          }

          const lockRes = await client.query(
            `SELECT stock_count, reserved_count, product_name FROM inventory WHERE store_id = $1 AND product_id = $2 AND sku = $3 FOR UPDATE`,
            [storeId, productId, String(sku)]
          );

          let delta;
          let currentReserved = 0;
          const isFirstTime = lockRes.rows.length === 0;
          if (isFirstTime) {
            delta = target;
            await client.query(
              `INSERT INTO inventory (store_id, product_id, sku, product_name, stock_count, reserved_count, updated_at)
               VALUES ($1, $2, $3, NULL, $4, 0, NOW())`,
              [storeId, productId, String(sku), target]
            );
          } else {
            currentReserved = Number(lockRes.rows[0].reserved_count || 0);
            const currentStock = Number(lockRes.rows[0].stock_count || 0);
            if (target < currentReserved) {
              await client.query('ROLLBACK');
              return { ok: false, httpStatus: 409, error: 'INSUFFICIENT_STOCK', message: `Cannot set stock below active reservations (current: ${currentStock}, reserved: ${currentReserved}, requested: ${target})` };
            }
            delta = target - currentStock;
            if (delta === 0) {
              await client.query('COMMIT');
              return { ok: true, noChange: true, sku, delta: 0, newStock: target, reason: 'NO_CHANGE' };
            }
            await client.query(
              `UPDATE inventory SET stock_count = $1, updated_at = NOW() WHERE store_id = $2 AND product_id = $3 AND sku = $4`,
              [target, storeId, productId, String(sku)]
            );
          }

          const finalReason = trimmedReason;
          const adjustmentId = 'adj_' + crypto.randomUUID();
          const actorId = (actor && (actor.id || actor.actorId)) || (isAdmin ? 'ADMIN' : 'SELLER');
          await client.query(
            `INSERT INTO inventory_ledger (id, store_id, product_id, sku, delta, new_stock, reason, actor_id, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())`,
            [adjustmentId, storeId, productId, String(sku), delta, target, finalReason, actorId]
          );

          await client.query('COMMIT');
          return { ok: true, adjustmentId, storeId, productId, sku, delta, newStock: target, reason: finalReason };
        } catch (err) {
          const retryable = (err.code === '40001') || (err.code === '23505');
          await client.query('ROLLBACK').catch(() => {});
          if (retryable && attempt < MAX_ATTEMPTS - 1) {
            continue;
          }
          if (err.code === '23505') {
            const inner = new Error(`Set stock conflict for ${sku}: a concurrent link owns the (store_id, sku) row.`);
            inner.code = 'CONCURRENT_LINK_CONFLICT';
            throw inner;
          }
          throw err;
        }
      }
    } finally {
      client.release();
    }
    throw new Error(`setStockForStore exhausted ${MAX_ATTEMPTS} serializable attempts for ${sku}`);
  }
}

class LocalDevelopmentInventoryRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async getStoreInventoryHistory(storeId) {
    return (this.db.inventoryHistory || []).filter(h => !storeId || h.storeId === storeId);
  }

  async getStoreInventory(storeId) {
    return (this.db.products || []).filter(p => !storeId || p.storeId === storeId || p.store_id === storeId).map(p => ({
      id: p.id || p.sku,
      productId: p.id || p.sku,
      sku: p.sku || p.id,
      name: p.name || 'Product',
      category: p.category || 'General',
      packSize: p.packSize || '1 Unit',
      price: Number(p.discountedPrice ?? p.price ?? 0),
      discountedPrice: Number(p.discountedPrice ?? p.price ?? 0),
      onHand: Number(p.stockCount || 0),
      reserved: 0,
      available: Number(p.stockCount || 0),
      stockCount: Number(p.stockCount || 0)
    }));
  }

  async adjustStockForStore(storeId, productId, sku, delta, reason, actor = { type: 'SELLER' }) {
    if (!storeId) {
      return { ok: false, httpStatus: 400, error: 'STORE_REQUIRED', message: 'Authoritative store context required.' };
    }
    if (!productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_REQUIRED', message: 'Canonical productId is strictly required.' };
    }
    if (!sku) {
      return { ok: false, httpStatus: 400, error: 'SKU_REQUIRED', message: 'Canonical sku is strictly required.' };
    }
    const numDelta = Number(delta);
    if (!Number.isFinite(numDelta)) {
      return { ok: false, httpStatus: 400, error: 'DELTA_REQUIRED', message: 'Numeric delta is strictly required.' };
    }
    if (!reason || typeof reason !== 'string' || !reason.trim()) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: 'An authorized inventory audit reason is strictly required.' };
    }
    const trimmedReason = reason.trim();
    const ALLOWED_SELLER_REASONS = ['INITIAL_STOCK_LINK', 'SELLER_RESTOCK', 'SELLER_ADJUSTMENT', 'STOCK_ADJUSTMENT'];
    const ALLOWED_ADMIN_REASONS = [...ALLOWED_SELLER_REASONS, 'ADMIN_ADJUSTMENT'];
    const isAdmin = actor && (actor.type === 'ADMIN' || actor.isAdmin === true);

    if (trimmedReason === 'ADMIN_ADJUSTMENT' && !isAdmin) {
      return { ok: false, httpStatus: 403, error: 'INVALID_INVENTORY_REASON', message: 'Reason ADMIN_ADJUSTMENT requires administrator authority.' };
    }
    if (!ALLOWED_ADMIN_REASONS.includes(trimmedReason)) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: `Reason '${trimmedReason}' is invalid or unapproved.` };
    }

    const prod = (this.db.products || []).find(p => (p.sku === sku || p.id === sku || (productId && p.id === productId)) && (!storeId || p.storeId === storeId || p.store_id === storeId));
    if (!prod) return { ok: false, httpStatus: 404, error: 'SKU_NOT_FOUND_IN_STORE' };
    if (productId && prod.id && prod.id !== productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_SKU_MISMATCH' };
    }
    if (prod.isActive === false || prod.is_active === false) {
      return { ok: false, httpStatus: 400, error: 'PRODUCT_INACTIVE' };
    }

    const prev = prod.stockCount || 0;
    prod.stockCount = Math.max(0, (prod.stockCount || 0) + numDelta);
    prod.inStock = prod.stockCount > 0;
    this.db.inventoryHistory = this.db.inventoryHistory || [];
    this.db.inventoryHistory.unshift({
      id: 'adj_' + Date.now(),
      storeId,
      productId: prod.id || productId || sku,
      sku: prod.sku || sku,
      productName: prod.name || sku,
      previousStock: prev,
      newStock: prod.stockCount,
      delta: numDelta,
      reason: trimmedReason,
      actor: isAdmin ? 'admin' : 'seller',
      timestamp: new Date().toISOString()
    });
    this.saveDb();
    return { ok: true, adjustmentId: 'adj_' + Date.now(), storeId, productId: prod.id || productId || sku, sku: prod.sku || sku, delta: numDelta, balanceAfter: prod.stockCount, newStock: prod.stockCount, reason: trimmedReason };
  }

  async setStockForStore(storeId, productId, sku, requestedStock, reason, actor = { type: 'SELLER' }) {
    if (!storeId) {
      return { ok: false, httpStatus: 400, error: 'STORE_REQUIRED', message: 'Authoritative store context required.' };
    }
    if (!productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_REQUIRED', message: 'Canonical productId is strictly required.' };
    }
    if (!sku) {
      return { ok: false, httpStatus: 400, error: 'SKU_REQUIRED', message: 'Canonical sku is strictly required.' };
    }
    const target = Number(requestedStock);
    if (!Number.isFinite(target) || target < 0) {
      return { ok: false, httpStatus: 400, error: 'INVALID_STOCK_COUNT', message: 'A non-negative numeric stock count is required.' };
    }
    if (!reason || typeof reason !== 'string' || !reason.trim()) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: 'An authorized inventory audit reason is strictly required.' };
    }
    const trimmedReason = reason.trim();
    const ALLOWED_SELLER_REASONS = ['INITIAL_STOCK_LINK', 'SELLER_RESTOCK', 'SELLER_ADJUSTMENT', 'STOCK_ADJUSTMENT'];
    const ALLOWED_ADMIN_REASONS = [...ALLOWED_SELLER_REASONS, 'ADMIN_ADJUSTMENT'];
    const isAdmin = actor && (actor.type === 'ADMIN' || actor.isAdmin === true);

    if (trimmedReason === 'ADMIN_ADJUSTMENT' && !isAdmin) {
      return { ok: false, httpStatus: 403, error: 'INVALID_INVENTORY_REASON', message: 'Reason ADMIN_ADJUSTMENT requires administrator authority.' };
    }
    if (!ALLOWED_ADMIN_REASONS.includes(trimmedReason)) {
      return { ok: false, httpStatus: 400, error: 'INVALID_INVENTORY_REASON', message: `Reason '${trimmedReason}' is invalid or unapproved.` };
    }

    const catalogKey = productId || sku;
    const prod = (this.db.products || []).find(p => (p.id === catalogKey || p.sku === catalogKey));
    if (!prod) return { ok: false, httpStatus: 404, error: 'PRODUCT_NOT_FOUND' };
    if (productId && prod.id && prod.id !== productId) {
      return { ok: false, httpStatus: 400, error: 'CANONICAL_PRODUCT_ID_SKU_MISMATCH' };
    }
    if (prod.isActive === false || prod.is_active === false) {
      return { ok: false, httpStatus: 400, error: 'PRODUCT_INACTIVE' };
    }

    const storeRow = (this.db.storeInventory || {})[storeId] || (this.db.inventory || []).find(i => (i.store_id === storeId || i.storeId === storeId) && i.sku === prod.sku);
    const reserved = storeRow ? Number(storeRow.reserved_count || storeRow.reservedCount || 0) : 0;
    const prev = storeRow ? Number(storeRow.stock_count || storeRow.stockCount || 0) : (prod.stockCount || 0);
    if (target < reserved) {
      return { ok: false, httpStatus: 409, error: 'INSUFFICIENT_STOCK', message: `Cannot set stock below active reservations (current: ${prev}, reserved: ${reserved}, requested: ${target})` };
    }
    const delta = target - prev;
    if (storeRow) {
      storeRow.stock_count = target;
      storeRow.stockCount = target;
      storeRow.reserved_count = reserved;
    } else {
      this.db.storeInventory = this.db.storeInventory || {};
      this.db.storeInventory[storeId] = { store_id: storeId, product_id: prod.id || productId, sku: prod.sku, product_name: prod.name, stock_count: target, reserved_count: 0 };
    }
    prod.stockCount = target;
    prod.inStock = target > 0;

    this.db.inventoryHistory = this.db.inventoryHistory || [];
    this.db.inventoryHistory.unshift({
      id: 'adj_' + Date.now(),
      storeId,
      productId: prod.id || productId || sku,
      sku: prod.sku,
      productName: prod.name || sku,
      previousStock: prev,
      newStock: target,
      delta,
      reason: trimmedReason,
      actor: isAdmin ? 'admin' : 'seller',
      timestamp: new Date().toISOString()
    });
    this.saveDb();
    return { ok: true, adjustmentId: 'adj_' + Date.now(), storeId, productId: prod.id || productId || sku, sku: prod.sku, delta, newStock: target, reason: trimmedReason };
  }

  async debitStockTransactionally(storeIdOrItems, maybeItems = null) {
    const items = Array.isArray(storeIdOrItems) ? storeIdOrItems : (maybeItems || []);
    this.db.products = this.db.products || [];
    for (const item of items) {
      const sku = item.sku || item.medicineId || item.id || item.productId || 'SKU-ITEM';
      const qty = Number(item.quantity) || 1;
      let prod = this.db.products.find((p) => p.sku === sku || p.id === sku || (item.productId && p.id === item.productId));
      if (!prod) {
        prod = {
          id: item.productId || item.id || ('prod_' + sku),
          sku: sku,
          name: item.name || item.productName || 'Commerce Item',
          stockCount: 500,
          inStock: true,
          price: item.unitPrice || 10.0
        };
        this.db.products.push(prod);
      }
      if ((prod.stockCount || 0) < qty) {
        prod.stockCount = 500;
        prod.inStock = true;
      }
      prod.stockCount -= qty;
      prod.inStock = prod.stockCount > 0;
    }
    this.saveDb();
    return { ok: true };
  }

  async releaseStockTransactionally(storeIdOrItems, maybeItems = null) {
    const items = Array.isArray(storeIdOrItems) ? storeIdOrItems : (maybeItems || []);
    this.db.products = this.db.products || [];
    for (const item of items) {
      const sku = item.sku || item.medicineId || item.id;
      const qty = Number(item.quantity) || 1;
      const prod = this.db.products.find((p) => p.sku === sku || p.id === sku);
      if (prod) {
        prod.stockCount += qty;
        prod.inStock = prod.stockCount > 0;
      }
    }
    this.saveDb();
    return { ok: true };
  }
}

class TransactionalOfferRepository {
  constructor(dbPool) {
    if (!dbPool) {
      throw new Error('FATAL: PostgreSQL DB pool is required for TransactionalOfferRepository');
    }
    this.pool = dbPool;
  }

  async findOfferById(offerId) {
    const res = await this.pool.query(`SELECT * FROM offers WHERE offer_id = $1`, [offerId]);
    return res.rows[0] || null;
  }

  async updateDeliveryStatus(offerId, status) {
    await this.pool.query(
      `UPDATE offers SET fcm_delivery_status = $1, updated_at = NOW() WHERE offer_id = $2`,
      [status, offerId]
    );
  }

  async createOfferTransactionally(offerData) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

      // 1. Insert Offer record
      const offerInsertQuery = `
        INSERT INTO offers (
          offer_id, event_id, notification_id, delivery_id, order_id, rider_id,
          status, offer_created_at, offer_expires_at, earnings_amount,
          delivery_distance_km, total_distance_km, estimated_duration_mins,
          pricing_snapshot, history, created_at
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, NOW())
        RETURNING *;
      `;
      const offerValues = [
        offerData.offerId,
        offerData.eventId,
        offerData.notificationId,
        offerData.deliveryId,
        offerData.orderId,
        offerData.riderId,
        offerData.status || 'CREATED',
        offerData.offerCreatedAt,
        offerData.offerExpiresAt,
        offerData.earningsAmount,
        offerData.deliveryDistanceKm,
        offerData.totalDistanceKm,
        Math.round(Number(offerData.estimatedDurationMins) || 1),
        JSON.stringify(offerData.pricingSnapshot || {}),
        JSON.stringify(offerData.history || [{ status: 'CREATED', timestamp: new Date().toISOString() }])
      ];
      const offerRes = await client.query(offerInsertQuery, offerValues);

      // 2. Insert Transactional Outbox Event carrying authoritative identifiers
      const outboxInsertQuery = `
        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at)
        VALUES ($1, $2, $3, $4, 'PENDING', 0, NOW(), NOW())
        RETURNING *;
      `;
      await client.query(outboxInsertQuery, [
        'OFFER',
        offerData.offerId,
        'NEW_DISPATCH_OFFER',
        JSON.stringify({
          offerId: offerData.offerId,
          deliveryId: offerData.deliveryId,
          orderId: offerData.orderId,
          targetRiderId: offerData.riderId,
          eventId: offerData.eventId,
          notificationId: offerData.notificationId,
          offer: offerData
        })
      ]);

      await client.query('COMMIT');
      return { ok: true, offer: offerRes.rows[0] };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async acceptOfferTransactionally(offerId, riderId, riderProfile) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

      // 1. Acquire row-level lock on the target offer
      const offerRes = await client.query(
        `SELECT * FROM offers WHERE offer_id = $1 FOR UPDATE`,
        [offerId]
      );

      if (offerRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'OFFER_NOT_FOUND', message: `Offer ${offerId} does not exist.` };
      }

      const offer = offerRes.rows[0];

      // 2. Strict Rider Ownership Verification
      if (offer.rider_id !== riderId) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'You are not the assigned rider for this offer.' };
      }

      // 3. Expiry & State Precondition Checks
      const now = Date.now();
      if (offer.status === 'ACCEPTED' && (offer.rider_id === riderId || offer.riderId === riderId)) {
        const sessionRes = await client.query(
          `SELECT * FROM delivery_sessions WHERE delivery_id = $1`,
          [offer.delivery_id]
        );
        await client.query('COMMIT');
        return { ok: true, httpStatus: 200, offer, session: sessionRes.rows[0] || null, idempotencyReplay: true };
      }

      if (now > Number(offer.offer_expires_at) || offer.status === 'EXPIRED') {
        await client.query(`UPDATE offers SET status = 'EXPIRED' WHERE offer_id = $1`, [offerId]);
        await client.query('COMMIT');
        return { ok: false, httpStatus: 409, error: 'OFFER_EXPIRED', message: 'This offer has expired on the server.' };
      }

      if (['ACCEPTED', 'DECLINED', 'CANCELLED', 'CLAIMED_BY_OTHER'].includes(offer.status)) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: 'OFFER_CLAIMED', message: 'This delivery job has already been claimed.' };
      }

      // 4. Lock delivery session and verify it is still available
      const sessionRes = await client.query(
        `SELECT * FROM delivery_sessions WHERE delivery_id = $1 FOR UPDATE`,
        [offer.delivery_id]
      );

      if (sessionRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'SESSION_NOT_FOUND', message: 'Associated delivery session not found.' };
      }

      const session = sessionRes.rows[0];
      if (session.state === 'ACCEPTED' && session.rider_id !== riderId) {
        await client.query(`UPDATE offers SET status = 'CLAIMED_BY_OTHER' WHERE offer_id = $1`, [offerId]);
        await client.query('COMMIT');
        return { ok: false, httpStatus: 409, error: 'OFFER_CLAIMED', message: 'This delivery job was claimed by another rider.' };
      }

      // 5. Authoritative Profile Precondition Validation (Zero fake values)
      if (!riderProfile || !riderProfile.realName || !riderProfile.realPhone) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'INCOMPLETE_RIDER_PROFILE', message: 'Authoritative rider name and phone must be present in profile.' };
      }
      if (!riderProfile.realVehicle) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'RIDER_VEHICLE_REQUIRED', message: 'Authoritative registered vehicle number must be present in rider profile to accept delivery.' };
      }

      // 5b. Transactional Invariant: Verify rider does not have a conflicting active delivery session
      const conflictingSessionRes = await client.query(
        `SELECT delivery_id FROM delivery_sessions 
         WHERE rider_id = $1 
           AND delivery_id != $2
           AND state IN ('ACCEPTED', 'ARRIVED_MERCHANT', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED')
         FOR UPDATE`,
        [riderId, offer.delivery_id]
      );
      if (conflictingSessionRes.rows.length > 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: 'RIDER_ALREADY_ASSIGNED', message: 'Rider already has an active delivery in progress.' };
      }

      const nowIso = new Date().toISOString();

      // 6. Mutate Offer State atomically
      await client.query(
        `UPDATE offers 
         SET status = 'ACCEPTED', accepted_at = $1, history = history || $2::jsonb
         WHERE offer_id = $3`,
        [nowIso, JSON.stringify([{ status: 'ACCEPTED', timestamp: nowIso, riderId }]), offerId]
      );

      // 7. Atomically Revoke all competing pending offers for this delivery
      await client.query(
        `UPDATE offers 
         SET status = 'CLAIMED_BY_OTHER', history = history || $1::jsonb
         WHERE delivery_id = $2 AND offer_id != $3 AND status IN ('CREATED', 'DISPATCHED', 'NOTIFIED', 'DISPLAYED')`,
        [JSON.stringify([{ status: 'CLAIMED_BY_OTHER', timestamp: nowIso }]), offer.delivery_id, offerId]
      );

      // 8. Mutate Delivery Session & Lock immutable pricing snapshot
      const updatedSessionRes = await client.query(
        `UPDATE delivery_sessions 
         SET rider_id = $1, rider_name = $2, rider_phone = $3, rider_vehicle = $4,
             state = 'ACCEPTED', pricing_snapshot = COALESCE($5, pricing_snapshot),
             history = history || $6::jsonb, updated_at = NOW()
         WHERE delivery_id = $7
         RETURNING *`,
        [
          riderId,
          riderProfile.realName,
          riderProfile.realPhone,
          riderProfile.realVehicle,
          JSON.stringify(offer.pricing_snapshot),
          JSON.stringify([{ state: 'ACCEPTED', timestamp: nowIso, riderId }]),
          offer.delivery_id
        ]
      );

      // 9. Transactional Outbox Event insertion (Guarantees notification durability)
      await client.query(
        `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at)
         VALUES ($1, $2, $3, $4, 'PENDING', 0, NOW(), NOW())`,
        [
          'DELIVERY_SESSION',
          offer.delivery_id,
          'RIDER_ACCEPTED',
          JSON.stringify({ offerId, deliveryId: offer.delivery_id, riderId, acceptedAt: nowIso })
        ]
      );

      await client.query('COMMIT');
      return { ok: true, httpStatus: 200, offer, session: updatedSessionRes.rows[0] };
    } catch (err) {
      await client.query('ROLLBACK');
      if (err.message && (err.message.includes('could not obtain lock') || err.message.includes('could not serialize access') || err.message.includes('deadlock') || err.code === '40001' || err.code === '55P03')) {
        return { ok: false, httpStatus: 409, error: 'OFFER_CLAIMED', message: 'Delivery job claimed by competing transaction.' };
      }
      throw err;
    } finally {
      client.release();
    }
  }

  async declineOfferTransactionally(offerId, riderId) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
      const offerRes = await client.query(`SELECT * FROM offers WHERE offer_id = $1 FOR UPDATE`, [offerId]);
      if (offerRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'OFFER_NOT_FOUND', message: `Offer ${offerId} not found.` };
      }
      const offer = offerRes.rows[0];
      if (offer.rider_id && offer.rider_id !== riderId) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'You are not the assigned rider for this offer.' };
      }
      if (offer.status === 'DECLINED') {
        await client.query('COMMIT');
        return { ok: true, httpStatus: 200, status: 'DECLINED' };
      }
      const nowIso = new Date().toISOString();
      await client.query(
        `UPDATE offers 
         SET status = 'DECLINED', declined_at = $1, history = history || $2::jsonb
         WHERE offer_id = $3`,
        [Date.now(), JSON.stringify([{ status: 'DECLINED', timestamp: nowIso, riderId }]), offerId]
      );
      await client.query('COMMIT');
      return { ok: true, httpStatus: 200, status: 'DECLINED' };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }
}

class LocalDevelopmentOfferRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async findOfferById(offerId) {
    return (this.db.offers || {})[offerId] || null;
  }

  async updateDeliveryStatus(offerId, status) {
    const offer = (this.db.offers || {})[offerId];
    if (offer) {
      offer.fcmDeliveryStatus = status;
      this.saveDb();
    }
  }

  async createOfferTransactionally(offerData) {
    this.db.offers = this.db.offers || {};
    this.db.offers[offerData.offerId] = offerData;

    this.db.outboxEvents = this.db.outboxEvents || [];
    this.db.outboxEvents.push({
      id: 'evt_outbox_' + crypto.randomUUID(),
      aggregateType: 'OFFER',
      aggregateId: offerData.offerId,
      eventType: 'NEW_DISPATCH_OFFER',
      payload: {
        offerId: offerData.offerId,
        deliveryId: offerData.deliveryId,
        orderId: offerData.orderId,
        targetRiderId: offerData.riderId,
        eventId: offerData.eventId,
        notificationId: offerData.notificationId,
        offer: offerData
      },
      status: 'PENDING',
      createdAt: new Date().toISOString()
    });

    this.saveDb();
    return { ok: true, offer: offerData };
  }

  async acceptOfferTransactionally(offerId, riderId, riderProfile) {
    const offer = (this.db.offers || {})[offerId];
    const session = Object.values(this.db.deliverySessions || {}).find(
      (s) => s.deliveryId === (offer && offer.deliveryId)
    );

    if (!offer) {
      return { ok: false, httpStatus: 404, error: 'OFFER_NOT_FOUND', message: `Offer ${offerId} does not exist.` };
    }

    if (offer.riderId && offer.riderId !== riderId) {
      return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'You are not the assigned rider for this offer.' };
    }

    const now = Date.now();
    if (offer.status === 'ACCEPTED' && offer.riderId === riderId) {
      return { ok: true, httpStatus: 200, offer, session, idempotencyReplay: true };
    }

    if (offer.offerExpiresAt && (now > offer.offerExpiresAt + 300000) && offer.status === 'EXPIRED') {
      return { ok: false, httpStatus: 409, error: 'OFFER_EXPIRED', message: 'This offer has expired on the server.' };
    }

    if (['ACCEPTED', 'DECLINED', 'CANCELLED', 'CLAIMED_BY_OTHER'].includes(offer.status)) {
      return { ok: false, httpStatus: 409, error: 'OFFER_CLAIMED', message: 'This delivery job has already been claimed or declined.' };
    }

    if (session && session.state === 'ACCEPTED' && session.riderId !== riderId) {
      offer.status = 'CLAIMED_BY_OTHER';
      this.saveDb();
      return { ok: false, httpStatus: 409, error: 'OFFER_CLAIMED', message: 'This delivery job was claimed by another rider.' };
    }

    if (!riderProfile || !riderProfile.realName || !riderProfile.realPhone) {
      return {
        ok: false,
        httpStatus: 400,
        error: 'INCOMPLETE_RIDER_PROFILE',
        message: 'Authoritative rider name and phone must be present in profile.'
      };
    }
    if (!riderProfile.realVehicle) {
      return {
        ok: false,
        httpStatus: 400,
        error: 'RIDER_VEHICLE_REQUIRED',
        message: 'Authoritative registered vehicle number must be present in rider profile to accept delivery.'
      };
    }

    const nowIso = new Date().toISOString();

    // 1. Mutate Offer State
    offer.status = 'ACCEPTED';
    offer.riderId = riderId;
    offer.acceptedAt = now;
    offer.history = offer.history || [];
    offer.history.push({ status: 'ACCEPTED', timestamp: nowIso, riderId });

    // 2. Atomically Revoke Competing Offers
    Object.values(this.db.offers || {}).forEach((otherOffer) => {
      if (otherOffer.deliveryId === offer.deliveryId && otherOffer.offerId !== offer.offerId) {
        if (['CREATED', 'DISPATCHED', 'NOTIFIED', 'DISPLAYED'].includes(otherOffer.status)) {
          otherOffer.status = 'CLAIMED_BY_OTHER';
          otherOffer.history = otherOffer.history || [];
          otherOffer.history.push({ status: 'CLAIMED_BY_OTHER', timestamp: nowIso });
        }
      }
    });

    // 3. Mutate Session & Lock Pricing Snapshot
    if (session) {
      session.riderId = riderId;
      session.riderName = riderProfile.realName;
      session.riderPhone = riderProfile.realPhone;
      session.riderVehicle = riderProfile.realVehicle;
      session.pricingSnapshot = offer.pricingSnapshot || session.pricingSnapshot;
      session.state = 'ACCEPTED';
      session.history = session.history || [];
      session.history.push({ state: 'ACCEPTED', timestamp: nowIso, riderId });
    }

    // 4. Update linked Customer Order
    const order = (this.db.orders || []).find(o => o.id === offer.orderId || o.orderId === offer.orderId);
    if (order) {
      order.rider = {
        id: riderId,
        name: riderProfile.realName,
        phone: riderProfile.realPhone,
        vehicle: riderProfile.realVehicle,
        vehicleNumber: riderProfile.realVehicle
      };
      order.updatedAt = nowIso;
      order.trackingCheckpoints = order.trackingCheckpoints || [];
      order.trackingCheckpoints.push({
        status: 'RIDER_ASSIGNED',
        label: `Delivery partner ${riderProfile.realName} has been assigned and is heading to the store`,
        actor: 'RIDER',
        location: 'En Route to Store',
        createdAt: nowIso
      });
    }

    // 5. Record Transactional Outbox Event
    this.db.outboxEvents = this.db.outboxEvents || [];
    this.db.outboxEvents.push({
      id: 'evt_outbox_' + crypto.randomUUID(),
      aggregateType: 'DELIVERY_SESSION',
      aggregateId: offer.deliveryId,
      eventType: 'RIDER_ACCEPTED',
      payload: { offerId, deliveryId: offer.deliveryId, riderId, acceptedAt: nowIso },
      status: 'PENDING',
      createdAt: nowIso
    });

    this.saveDb();
    return { ok: true, httpStatus: 200, offer, session, order };
  }

  async declineOfferTransactionally(offerId, riderId) {
    const offer = (this.db.offers || {})[offerId];
    if (!offer) {
      return { ok: false, httpStatus: 404, error: 'OFFER_NOT_FOUND', message: `Offer ${offerId} does not exist.` };
    }
    if (offer.riderId && offer.riderId !== riderId) {
      return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'You are not the assigned rider for this offer.' };
    }
    if (offer.status === 'DECLINED') {
      return { ok: true, httpStatus: 200, status: 'DECLINED' };
    }
    const nowIso = new Date().toISOString();
    offer.status = 'DECLINED';
    offer.declinedAt = Date.now();
    offer.history = offer.history || [];
    offer.history.push({ status: 'DECLINED', timestamp: nowIso, riderId });
    this.saveDb();
    return { ok: true, httpStatus: 200, status: 'DECLINED' };
  }
}

class TransactionalDeliveryRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async createSessionTransactionally(sessionData) {
    if (!this.pool) return null;
    
    // Strict Authoritative Validation (Zero Fake Placeholders)
    if (!sessionData.orderId) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative orderId is strictly required.');
    }
    if (!sessionData.storeId && !sessionData.merchantId) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative storeId is strictly required.');
    }
    if (!sessionData.merchantName || !sessionData.merchantAddress) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative merchant name and address are required from store record.');
    }
    if (sessionData.merchantLat == null || sessionData.merchantLng == null || isNaN(Number(sessionData.merchantLat)) || isNaN(Number(sessionData.merchantLng))) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative merchant coordinates are strictly required.');
    }
    if (!sessionData.customerName || !sessionData.customerAddress) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative customer name and delivery address are required.');
    }
    if (sessionData.customerLat == null || sessionData.customerLng == null || isNaN(Number(sessionData.customerLat)) || isNaN(Number(sessionData.customerLng))) {
      throw new Error('INCOMPLETE_DELIVERY_DATA: Authoritative customer coordinates are strictly required.');
    }

    const deliveryId = sessionData.deliveryId || sessionData.id || ('del_' + crypto.randomUUID());
    const primaryKeyId = sessionData.id || deliveryId;
    const res = await this.pool.query(
      `INSERT INTO delivery_sessions (
        id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
        state, merchant_name, merchant_address, merchant_lat, merchant_lng,
        customer_name, customer_phone, customer_address, customer_lat, customer_lng,
        distance_km, is_cod, cod_amount, otp_verified, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, FALSE, NOW(), NOW())
      RETURNING *`,
      [
        primaryKeyId,
        deliveryId,
        sessionData.orderId,
        sessionData.storeId || sessionData.merchantId,
        sessionData.riderId || null,
        sessionData.riderName || null,
        sessionData.riderPhone || null,
        sessionData.riderVehicle || null,
        sessionData.state || 'LOOKING_FOR_RIDER',
        sessionData.merchantName,
        sessionData.merchantAddress,
        Number(sessionData.merchantLat),
        Number(sessionData.merchantLng),
        sessionData.customerName,
        sessionData.customerPhone || null,
        sessionData.customerAddress,
        Number(sessionData.customerLat),
        Number(sessionData.customerLng),
        Number(sessionData.distanceKm || 0.0),
        Boolean(sessionData.isCod),
        Number(sessionData.codAmount || 0)
      ]
    );
    return res.rows[0];
  }

  async findSessionById(deliveryId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM delivery_sessions WHERE delivery_id = $1 OR order_id = $1`, [deliveryId]);
    return res.rows[0] || null;
  }

  async findActiveSessionForRider(riderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT * FROM delivery_sessions 
       WHERE rider_id = $1 AND state NOT IN ('DELIVERED', 'CANCELLED', 'DECLINED', 'FAILED')
       ORDER BY updated_at DESC LIMIT 1`,
      [riderId]
    );
    return res.rows[0] || null;
  }

  async getActiveDeliveryByCustomer(customerId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT d.* 
       FROM delivery_sessions d
       JOIN orders o ON d.order_id = o.order_id OR d.order_id = o.id
       WHERE o.customer_id = $1 AND d.state NOT IN ('DELIVERED', 'CANCELLED', 'DECLINED', 'FAILED')
       ORDER BY d.updated_at DESC LIMIT 1`,
      [customerId]
    );
    return res.rows[0] || null;
  }

  async verifyOtpAndCompleteDelivery(deliveryId, riderId, submittedOtp) {
    return this.deliverWithOtpTransactionally(deliveryId, riderId, submittedOtp);
  }

  async transitionStateTransactionally(deliveryId, newState, riderId, metadata = {}) {
    if (!this.pool) return null;
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const sessionRes = await client.query(
        `SELECT * FROM delivery_sessions WHERE (delivery_id = $1 OR order_id = $1) FOR UPDATE`,
        [deliveryId]
      );
      const session = sessionRes.rows[0];
      if (!session) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'SESSION_NOT_FOUND' };
      }
      if (riderId && session.rider_id && session.rider_id !== riderId) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 403, error: 'RIDER_MISMATCH' };
      }

      // Idempotency: same state transition is a no-op
      if (session.state === newState) {
        await client.query('COMMIT');
        return { ok: true, session, isIdempotent: true };
      }

      // Authoritative State Machine Validation
      const VALID_DELIVERY_TRANSITIONS = {
        'LOOKING_FOR_RIDER': ['OFFERED', 'ACCEPTED', 'RIDER_ASSIGNED', 'READY_FOR_PICKUP', 'CANCELLED'],
        'OFFERED': ['ACCEPTED', 'LOOKING_FOR_RIDER', 'CANCELLED'],
        'READY_FOR_PICKUP': ['ACCEPTED', 'RIDER_ASSIGNED', 'OUT_FOR_PICKUP', 'ARRIVED_STORE', 'CANCELLED'],
        'ACCEPTED': ['OUT_FOR_PICKUP', 'ARRIVED_STORE', 'CANCELLED'],
        'RIDER_ASSIGNED': ['OUT_FOR_PICKUP', 'ARRIVED_STORE', 'CANCELLED'],
        'OUT_FOR_PICKUP': ['ARRIVED_STORE', 'CANCELLED'],
        'ARRIVED_STORE': ['PICKED_UP', 'CANCELLED'],
        'PICKED_UP': ['OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'CANCELLED'],
        'OUT_FOR_DELIVERY': ['ARRIVED_CUSTOMER', 'DELIVERED', 'CANCELLED'],
        'ARRIVED_CUSTOMER': ['DELIVERED', 'CANCELLED'],
        'DELIVERED': [],
        'CANCELLED': [],
        'DECLINED': [],
        'FAILED': []
      };

      const allowedTransitions = VALID_DELIVERY_TRANSITIONS[session.state] || [];
      if (!allowedTransitions.includes(newState)) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 409,
          error: 'INVALID_DELIVERY_STATE_TRANSITION',
          message: `Cannot transition delivery from '${session.state}' to '${newState}'. State machine prohibits this transition.`
        };
      }

      await client.query(
        `UPDATE delivery_sessions 
         SET state = $1, otp_verified = COALESCE($2, otp_verified), updated_at = NOW()
         WHERE id = $3`,
        [newState, metadata.otpVerified || false, session.id]
      );

      await client.query(
        `UPDATE orders 
         SET status = $1, otp_verified_at = COALESCE($2, otp_verified_at), updated_at = NOW()
         WHERE order_id = $3`,
        [newState, metadata.otpVerified ? new Date() : null, session.order_id]
      );

      await client.query(
        `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status)
         VALUES ($1, $2, $3, $4, 'PENDING')`,
        ['DELIVERY_SESSION', session.delivery_id, 'DELIVERY_STATE_CHANGED', JSON.stringify({ deliveryId: session.delivery_id, state: newState, riderId })]
      );

      await client.query('COMMIT');
      return { ok: true, session: { ...session, state: newState } };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async deliverWithOtpTransactionally(orderId, riderId, submittedOtp) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

      // 1. Lock Delivery Session
      const sessionRes = await client.query(
        `SELECT * FROM delivery_sessions WHERE order_id = $1 OR delivery_id = $1 FOR UPDATE`,
        [orderId]
      );
      const session = sessionRes.rows[0];
      if (!session) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'DELIVERY_NOT_FOUND', message: `Delivery session for order ${orderId} not found.` };
      }

      if (session.rider_id && session.rider_id !== riderId) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'Only the assigned rider for this delivery can complete delivery.' };
      }

      if (session.state === 'DELIVERED') {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: 'ALREADY_DELIVERED', message: 'Order is already delivered.' };
      }

      // 2. Lock Order Record
      const orderRes = await client.query(
        `SELECT * FROM orders WHERE order_id = $1 OR id = $1 FOR UPDATE`,
        [session.order_id || orderId]
      );
      const order = orderRes.rows[0];
      if (!order) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
      }

      // 3. COD Precondition Check
      if (order.is_cod) {
        const codCheckRes = await client.query(
          `SELECT * FROM cod_ledger WHERE order_id = $1`,
          [order.order_id || order.id]
        );
        const codEntry = codCheckRes.rows[0];
        if (!codEntry || !['COLLECTED', 'COLLECTED_SHORTAGE'].includes(codEntry.status)) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 409, error: 'COD_NOT_COLLECTED', message: 'Cash on delivery must be collected and confirmed before customer handoff.' };
        }
      }

      // 4. Secure OTP Verification against orders.delivery_otp_hash
      const expectedOtpHash = order.delivery_otp_hash;
      const otpVerifyResult = DeliveryOtpService.verifyOtp(submittedOtp, expectedOtpHash, order.otp_attempts || 0, 5);
      if (!otpVerifyResult.ok) {
        await client.query(
          `UPDATE orders SET otp_attempts = COALESCE(otp_attempts, 0) + 1, updated_at = NOW() WHERE id = $1 OR order_id = $1`,
          [order.id || order.order_id]
        );
        await client.query('COMMIT');
        return { ok: false, httpStatus: 400, error: 'INVALID_OTP', message: otpVerifyResult.message };
      }

      // 5. Atomic Completion Transitions
      await client.query(
        `UPDATE delivery_sessions 
         SET state = 'DELIVERED', otp_verified = TRUE, updated_at = NOW()
         WHERE id = $1`,
        [session.id]
      );

      await client.query(
        `UPDATE orders 
         SET status = 'DELIVERED', otp_verified_at = NOW(), updated_at = NOW()
         WHERE id = $1 OR order_id = $1`,
        [order.id || order.order_id]
      );

      // 6. Transactional Outbox Event
      await client.query(
        `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, created_at)
         VALUES ($1, $2, $3, $4, 'PENDING', NOW())`,
        [
          'ORDER',
          order.order_id || order.id,
          'ORDER_DELIVERED',
          JSON.stringify({
            orderId: order.order_id || order.id,
            deliveryId: session.delivery_id,
            riderId,
            customerId: order.customer_id,
            deliveredAt: new Date().toISOString()
          })
        ]
      );

      await client.query('COMMIT');
      return { ok: true, httpStatus: 200, order: { ...order, status: 'DELIVERED' }, session: { ...session, state: 'DELIVERED' } };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }
}

class LocalDevelopmentDeliveryRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async deliverWithOtpTransactionally(orderId, riderId, submittedOtp) {
    const session = ((this.db.deliverySessions || {})[orderId] || Object.values(this.db.deliverySessions || {}).find(s => s.deliveryId === orderId || s.orderId === orderId));
    if (!session) return { ok: false, httpStatus: 404, error: 'DELIVERY_NOT_FOUND' };
    if (session.riderId && session.riderId !== riderId) {
      return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'Only the assigned rider can complete delivery.' };
    }
    const order = (this.db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (!order) return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
    
    const expectedOtp = session.deliveryPin || session.deliveryOtp || order.deliveryOtp;
    const otpRes = DeliveryOtpService.verifyOtp(submittedOtp, expectedOtp, order.otpAttempts || 0, 5);
    if (!otpRes.ok) return { ok: false, httpStatus: 400, error: 'INVALID_OTP', message: otpRes.message };

    session.state = 'DELIVERED';
    session.otpVerified = true;
    order.orderStatus = 'DELIVERED';
    order.status = 'DELIVERED';
    order.otpVerifiedAt = new Date().toISOString();
    this.saveDb();
    return { ok: true, httpStatus: 200, order, session };
  }

  async transitionStateTransactionally(deliveryId, newState, riderId, metadata = {}) {
    const session = ((this.db.deliverySessions || {})[deliveryId] || Object.values(this.db.deliverySessions || {}).find(s => s.deliveryId === deliveryId || s.orderId === deliveryId));
    if (!session) return { ok: false, error: 'SESSION_NOT_FOUND' };
    if (riderId && session.riderId && session.riderId !== riderId) {
      return { ok: false, error: 'RIDER_MISMATCH' };
    }
    session.state = newState;
    if (metadata.otpVerified) session.otpVerified = true;
    this.saveDb();
    return { ok: true, session };
  }

  async createSessionTransactionally(sessionData) {
    this.db.deliverySessions = this.db.deliverySessions || {};
    this.db.deliverySessions[sessionData.deliveryId] = sessionData;
    this.saveDb();
    return sessionData;
  }

  async findSessionById(deliveryId) {
    return (this.db.deliverySessions || {})[deliveryId] || Object.values(this.db.deliverySessions || {}).find(s => s.orderId === deliveryId) || null;
  }

  async findActiveSessionForRider(riderId) {
    return Object.values(this.db.deliverySessions || {}).find(s => s.riderId === riderId && !['DELIVERED', 'CANCELLED', 'DECLINED', 'FAILED'].includes(s.state)) || null;
  }
}

class TransactionalOrderRepository {
  constructor(dbPool, inventoryRepo = null) {
    this.pool = dbPool;
    this.inventoryRepo = inventoryRepo || new TransactionalInventoryRepository(dbPool);
  }

  async placeOrderTransactionally(authenticatedCustomerId, orderData, deliverySessionData = {}) {
    let customerId;
    let data;
    let sessionData;
    if (typeof authenticatedCustomerId === 'string') {
      customerId = authenticatedCustomerId;
      data = orderData || {};
      sessionData = deliverySessionData || {};
    } else {
      data = authenticatedCustomerId || {};
      customerId = data.customerId;
      sessionData = orderData || {};
    }

    if (!customerId) {
      return { ok: false, httpStatus: 400, error: 'MISSING_CUSTOMER_ID', message: 'Authoritative customerId is required from authenticated session.' };
    }
    if (data.customerId && data.customerId !== customerId) {
      return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'Customer ID does not match authenticated identity.' };
    }

    // 1. Mandatory Server-Authoritative FulfillmentDecision (Single Authority)
    const decision = data.fulfillmentDecision || null;
    if (!decision || !decision.storeId) {
      return {
        ok: false,
        httpStatus: 422,
        error: 'FULFILLMENT_DECISION_REQUIRED',
        message: 'Authoritative FulfillmentDecision from ServiceabilityService is strictly required for order placement.'
      };
    }
    const targetStoreId = decision.storeId;
    const distanceKm = decision.distanceKm != null ? Number(decision.distanceKm) : 0;

    // 2. Mandatory Address ID
    if (!data.addressId) {
      return { ok: false, httpStatus: 400, error: 'ADDRESS_ID_REQUIRED', message: 'Authoritative addressId is strictly required.' };
    }

    // 3. Mandatory Order Items
    if (!data.items || !Array.isArray(data.items) || data.items.length === 0) {
      return { ok: false, httpStatus: 400, error: 'EMPTY_ORDER_ITEMS', message: 'Order must contain at least one item.' };
    }

    // 4. Authoritative Order Type Validation
    const allowedTypes = ['QUICK_COMMERCE_10MIN', 'SCHEDULED_DELIVERY', 'SUBSCRIPTION_REFILL'];
    const requestedType = (data.orderType || 'QUICK_COMMERCE_10MIN').toUpperCase();
    if (!allowedTypes.includes(requestedType)) {
      return { ok: false, httpStatus: 400, error: 'INVALID_ORDER_TYPE', message: `Order type ${requestedType} is not supported.` };
    }
    const authoritativeOrderType = requestedType;

    // 5. Authoritative Payment Method & Status Normalization
    const ALLOWED_PAYMENT_METHODS = ['COD', 'UPI_INSTANT', 'CARD', 'WALLET', 'NET_BANKING'];
    const requestedMethod = (data.paymentMethod || 'UPI_INSTANT').toUpperCase();
    if (!ALLOWED_PAYMENT_METHODS.includes(requestedMethod)) {
      return {
        ok: false,
        httpStatus: 400,
        error: 'INVALID_PAYMENT_METHOD',
        message: `Payment method '${data.paymentMethod}' is not supported. Must be one of: ${ALLOWED_PAYMENT_METHODS.join(', ')}.`
      };
    }
    const isCod = requestedMethod === 'COD';
    const authoritativePaymentStatus = isCod ? 'COD_PENDING' : 'PAYMENT_PENDING';

    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');

      // 6. Authoritative Customer Resolution (from customers table)
      const customerRes = await client.query(
        `SELECT id, full_name, phone, is_active FROM customers WHERE id = $1`,
        [customerId]
      );
      if (customerRes.rows.length === 0 || !customerRes.rows[0].is_active) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'INVALID_CUSTOMER', message: 'Authoritative customer account does not exist or is inactive.' };
      }
      const customer = customerRes.rows[0];

      // 7. Authoritative Address Resolution (Strictly from customer_addresses table)
      const addrRes = await client.query(
        `SELECT id, address_line, city, postal_code, latitude, longitude, is_default 
         FROM customer_addresses 
         WHERE customer_id = $1 AND id = $2`,
        [customerId, data.addressId]
      );
      if (addrRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'ADDRESS_NOT_FOUND', message: 'Delivery address not found for authenticated customer.' };
      }
      const resolvedAddress = addrRes.rows[0];
      const cLat = Number(resolvedAddress.latitude);
      const cLng = Number(resolvedAddress.longitude);
      if (isNaN(cLat) || isNaN(cLng)) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'INVALID_ADDRESS_COORDINATES', message: 'Authoritative customer address has invalid coordinates.' };
      }

      // 8. Authoritative Store Resolution (from stores table)
      const storeRes = await client.query(
        `SELECT id, store_name, address, latitude, longitude, sla_minutes, is_active FROM stores WHERE id = $1 AND is_active = TRUE`,
        [targetStoreId]
      );
      if (storeRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'INVALID_STORE', message: `Store ${targetStoreId} is not an active fulfillment node.` };
      }
      const store = storeRes.rows[0];
      const mLat = Number(store.latitude);
      const mLng = Number(store.longitude);
      if (isNaN(mLat) || isNaN(mLng)) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'STORE_LOCATION_INVALID', message: 'Store is missing authoritative coordinates in database.' };
      }

      // 9. Store-Context Canonical Catalog Resolution & Duplicate Line Normalization
      const mergedBySku = new Map();
      for (const rawItem of data.items) {
        const lookupKey = rawItem.sku || rawItem.productId || rawItem.id;
        if (!lookupKey) continue;
        const qty = Math.max(1, Number(rawItem.quantity) || 1);

        const prodRes = await client.query(
          `SELECT id, sku, name, brand_name, price, mrp, discounted_price, rx_requirement, is_active
           FROM products 
           WHERE (sku = $1 OR id = $1) AND is_active = TRUE 
           LIMIT 1`,
          [lookupKey]
        );

        if (prodRes.rows.length === 0) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 404, error: 'PRODUCT_NOT_FOUND', message: `Product ${lookupKey} not found in authoritative catalog.` };
        }

        const product = prodRes.rows[0];
        const canonicalSku = product.sku;
        const unitPrice = Number(product.discounted_price || product.price);
        const mrp = Number(product.mrp || product.price);
        const rxReq = (product.rx_requirement || 'OTC').toUpperCase();
        const isRxRequired = rxReq === 'RX_REQUIRED' || rxReq === 'SCHEDULE_H';

        if (mergedBySku.has(canonicalSku)) {
          const existing = mergedBySku.get(canonicalSku);
          existing.quantity += qty;
          existing.lineTotal = Math.round(unitPrice * existing.quantity * 100) / 100;
        } else {
          mergedBySku.set(canonicalSku, {
            storeId: targetStoreId,
            productId: product.id,
            sku: canonicalSku,
            name: product.name,
            unitPrice,
            mrp,
            quantity: qty,
            lineTotal: Math.round(unitPrice * qty * 100) / 100,
            rxRequired: isRxRequired
          });
        }
      }

      const resolvedItemSnapshots = Array.from(mergedBySku.values());
      if (resolvedItemSnapshots.length === 0) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 400, error: 'EMPTY_ORDER_ITEMS', message: 'Order contains no valid items.' };
      }

      // Canonical identity guard: production inventory reservation must consume the canonical
      // triple (storeId, productId, sku). Silent sku-only reservation is forbidden in the order path.
      const nonCanonicalItem = resolvedItemSnapshots.find(it => !it || !it.storeId || !it.productId || !it.sku || !Number(it.quantity));
      if (nonCanonicalItem) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 500,
          error: 'CANONICAL_PRODUCT_ID_REQUIRED',
          message: 'Canonical order item snapshots require storeId, productId, sku, and quantity for inventory reservation.'
        };
      }

      // 10. Comprehensive Request Fingerprint Hash (Covers all material request inputs)
      const sortedItemsForHash = resolvedItemSnapshots
        .map(it => ({ productId: it.productId, sku: it.sku, qty: it.quantity }))
        .sort((a, b) => a.sku.localeCompare(b.sku));

      const requestFingerprint = crypto.createHash('sha256').update(JSON.stringify({
        customerId,
        addressId: data.addressId,
        orderType: authoritativeOrderType,
        paymentMethod: requestedMethod,
        prescriptionId: data.prescriptionId || null,
        preferredStoreId: data.preferredStoreId || data.storeId || null,
        fulfillmentStoreId: targetStoreId,
        items: sortedItemsForHash
      })).digest('hex');

      // 11. Idempotency Check with Request Hash Binding
      const idempotencyKey = data.idempotencyKey || data.idempotency_key || null;
      if (idempotencyKey) {
        const existingOrderRes = await client.query(
          `SELECT id, order_id, customer_id, store_id, status, total_amount, payment_status, request_hash, items, delivery_address
           FROM orders 
           WHERE customer_id = $1 AND idempotency_key = $2 LIMIT 1`,
          [customerId, idempotencyKey]
        );
        if (existingOrderRes.rows.length > 0) {
          const existingOrder = existingOrderRes.rows[0];
          if (existingOrder.request_hash && existingOrder.request_hash !== requestFingerprint) {
            await client.query('ROLLBACK');
            return {
              ok: false,
              httpStatus: 409,
              error: 'IDEMPOTENCY_KEY_REUSE_MISMATCH',
              message: 'Idempotency key was previously used with a different request payload.'
            };
          }
          const existingSessionRes = await client.query(
            `SELECT id, delivery_id, order_id, store_id, rider_id, state, merchant_name, customer_name
             FROM delivery_sessions WHERE order_id = $1 LIMIT 1`,
            [existingOrder.order_id || existingOrder.id]
          );
          await client.query('COMMIT');
          // Sanitize: Omit plaintext OTP and raw Delivery PIN on idempotent replay
          const { delivery_otp_hash, ...safeExistingOrder } = existingOrder;
          return {
            ok: true,
            httpStatus: 200,
            isIdempotentReplay: true,
            order: safeExistingOrder,
            session: existingSessionRes.rows[0] || null
          };
        }
      }

      // 12. Authoritative Financial Totals via Pricing Engine
      const calculatedItemsSubtotal = resolvedItemSnapshots.reduce((acc, it) => acc + it.lineTotal, 0);
      const pricingEngine = require('../pricing-engine');
      const pricingResult = pricingEngine.calculateCustomerOrderPricing({
        itemsSubtotal: calculatedItemsSubtotal,
        distanceKm,
        isCod
      });
      const deliveryFee = pricingResult.deliveryFee;
      const taxAmount = pricingResult.taxAmount;
      const totalAmount = pricingResult.totalAmount;

      // 13. Atomic Store-Scoped Stock Reservation (with inventory_ledger audit entry)
      const stockResult = await this.inventoryRepo.reserveStockTransactionally(client, store.id, resolvedItemSnapshots);
      if (!stockResult.ok) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: stockResult.error || 'OUT_OF_STOCK', sku: stockResult.sku, message: `Insufficient inventory for SKU ${stockResult.sku} in store ${store.id}.` };
      }

      // 14. Generate Secure Server-Authoritative OTP (Zero Client Trust)
      const rawOtp = DeliveryOtpService.generateSecureOtp();
      const otpHash = DeliveryOtpService.hashOtp(rawOtp);
      const otpExpiresAt = DeliveryOtpService.getOtpExpiryDate();
      const orderId = 'ord_' + crypto.randomUUID();

      const hasRxRequiredItem = resolvedItemSnapshots.some(item => item.rxRequired);
      const authoritativeInitialStatus = hasRxRequiredItem ? 'PRESCRIPTION_VERIFICATION_PENDING' : 'PLACED';

      const orderInsertQuery = `
        INSERT INTO orders (
          id, order_id, customer_id, store_id, prescription_id, order_type,
          status, total_amount, tax_amount, delivery_fee, payment_method, payment_status,
          is_cod, cod_amount, idempotency_key, request_hash, delivery_address, items, delivery_otp_hash,
          otp_expires_at, otp_attempts, created_at, updated_at
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, 0, NOW(), NOW())
        RETURNING *;
      `;
      const orderValues = [
        orderId,
        orderId,
        customer.id,
        store.id,
        data.prescriptionId || null,
        authoritativeOrderType,
        authoritativeInitialStatus,
        totalAmount,
        taxAmount,
        deliveryFee,
        requestedMethod,
        authoritativePaymentStatus,
        isCod,
        Number(isCod ? totalAmount : 0),
        idempotencyKey,
        requestFingerprint,
        JSON.stringify(resolvedAddress),
        JSON.stringify(resolvedItemSnapshots),
        otpHash,
        otpExpiresAt
      ];
      const orderRes = await client.query(orderInsertQuery, orderValues);

      // 15. Authoritative Delivery Session Creation (Forced LOOKING_FOR_RIDER with null rider)
      const deliveryId = 'del_' + crypto.randomUUID();

      const sessionInsertQuery = `
        INSERT INTO delivery_sessions (
          id, delivery_id, order_id, store_id, rider_id, rider_name, rider_phone, rider_vehicle,
          state, merchant_name, merchant_address, merchant_lat, merchant_lng,
          customer_name, customer_phone, customer_address, customer_lat, customer_lng,
          distance_km, is_cod, cod_amount, otp_verified, created_at, updated_at
        ) VALUES ($1, $2, $3, $4, NULL, NULL, NULL, NULL, 'LOOKING_FOR_RIDER', $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, FALSE, NOW(), NOW())
        RETURNING *;
      `;
      const sessionValues = [
        deliveryId,
        deliveryId,
        orderId,
        store.id,
        store.store_name,
        store.address,
        mLat,
        mLng,
        customer.full_name,
        customer.phone || null,
        resolvedAddress.address_line,
        cLat,
        cLng,
        distanceKm,
        isCod,
        Number(isCod ? totalAmount : 0)
      ];
      const sessionRes = await client.query(sessionInsertQuery, sessionValues);

      // 16. Persist COD Ledger entry if COD with Authoritative Primary Seller ID
      if (isCod) {
        const sellerRes = await client.query(
          `SELECT seller_id, id FROM sellers WHERE store_id = $1 AND status = 'ACTIVE' AND is_primary = TRUE`,
          [store.id]
        );
        if (sellerRes.rows.length === 0) {
          await client.query('ROLLBACK');
          return {
            ok: false,
            httpStatus: 422,
            error: 'STORE_MERCHANT_AUTHORITY_MISSING',
            message: `No primary active merchant account configured for store ${store.id}.`
          };
        }
        if (sellerRes.rows.length > 1) {
          await client.query('ROLLBACK');
          return {
            ok: false,
            httpStatus: 500,
            error: 'STORE_MERCHANT_AUTHORITY_CORRUPT',
            message: `Corrupt database state: multiple primary active sellers found for store ${store.id}.`
          };
        }
        const authoritativeSellerId = sellerRes.rows[0].seller_id || sellerRes.rows[0].id;

        await client.query(
          `INSERT INTO cod_ledger (
            id, order_id, seller_id, amount_expected, amount_collected, shortage_amount,
            status, collector_id, notes, reconciled, created_at, updated_at
          ) VALUES ($1, $2, $3, $4, 0, 0, 'PENDING_COLLECTION', NULL, 'Awaiting cash handoff upon delivery', FALSE, NOW(), NOW())`,
          [
            'cod_tx_' + crypto.randomUUID(),
            orderId,
            authoritativeSellerId,
            totalAmount
          ]
        );
      }

      // 17. Insert Transactional Outbox Event for DISPATCH_REQUESTED
      await client.query(
        `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at)
         VALUES ('DELIVERY_SESSION', $1, 'DISPATCH_REQUESTED', $2, 'PENDING', 0, NOW(), NOW())`,
        [
          deliveryId,
          JSON.stringify({
            deliveryId,
            orderId,
            storeId: store.id,
            customerId: customer.id,
            totalAmount,
            isCod
          })
        ]
      );

      await client.query('COMMIT');

      return {
        ok: true,
        httpStatus: 201,
        order: {
          ...orderRes.rows[0],
          rawDeliveryPin: rawOtp,
          deliveryOtp: rawOtp
        },
        session: sessionRes.rows[0]
      };
    } catch (err) {
      await client.query('ROLLBACK');
      const idempotencyKey = data.idempotencyKey || data.idempotency_key || null;
      if ((err.code === '23505' || err.code === '40001') && idempotencyKey && this.pool) {
        // Idempotent race condition: query winner's inserted order
        const existingOrderRes = await this.pool.query(
          `SELECT id, order_id, customer_id, store_id, status, total_amount, payment_status, request_hash, items, delivery_address
           FROM orders 
           WHERE customer_id = $1 AND idempotency_key = $2 LIMIT 1`,
          [customerId, idempotencyKey]
        );
        if (existingOrderRes.rows.length > 0) {
          const existingOrder = existingOrderRes.rows[0];
          const { delivery_otp_hash, ...safeExistingOrder } = existingOrder;
          return {
            ok: true,
            httpStatus: 200,
            isIdempotentReplay: true,
            order: safeExistingOrder,
            session: null
          };
        }
      }
      throw err;
    } finally {
      client.release();
    }
  }

  async findOrderById(orderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM orders WHERE order_id = $1 OR id = $1`, [orderId]);
    return res.rows[0] || null;
  }

  async getActiveCustomerOrder(customerId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT * FROM orders 
       WHERE customer_id = $1 AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED_TO_SELLER')
       ORDER BY created_at DESC LIMIT 1`,
      [customerId]
    );
    return res.rows[0] || null;
  }

  async getOrdersByStore(storeId) {
    if (!this.pool) return [];
    if (!storeId) {
      throw new Error('FATAL: storeId is strictly required to query store orders.');
    }
    const res = await this.pool.query(
      `SELECT * FROM orders 
       WHERE store_id = $1
       ORDER BY created_at DESC`,
      [storeId]
    );
    return res.rows;
  }

  async getRecentCustomerOrders(customerId, limit = 5) {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT * FROM orders 
       WHERE customer_id = $1 
       ORDER BY created_at DESC LIMIT $2`,
      [customerId, limit]
    );
    return res.rows;
  }

  async acceptOrderBySeller(orderId, storeId, sellerId) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const MAX_RETRIES = 3;
    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
      const client = await this.pool.connect();
      try {
        await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
        const orderRes = await client.query(`SELECT * FROM orders WHERE (order_id = $1 OR id = $1) FOR UPDATE`, [orderId]);
        const order = orderRes.rows[0];
        if (!order) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
        }
        if (storeId && order.store_id && order.store_id !== storeId) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 403, error: 'FORBIDDEN', message: 'Order does not belong to authorized store.' };
        }

        // Idempotency: if already in SELLER_ACCEPTED, return without error
        if (order.status === 'SELLER_ACCEPTED') {
          await client.query('COMMIT');
          return { ok: true, order, isIdempotent: true };
        }

        // Precondition Check: Must be in PLACED or PAYMENT_PENDING
        if (!['PLACED', 'PAYMENT_PENDING'].includes(order.status)) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 409, error: 'INVALID_ORDER_STATE_TRANSITION', message: `Cannot accept order in state '${order.status}'. Must be PLACED.` };
        }

        await client.query(`UPDATE orders SET status = 'SELLER_ACCEPTED', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);
        await client.query(
          `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status)
           VALUES ($1, $2, $3, $4, 'PENDING')`,
          ['ORDER', order.order_id || order.id, 'ORDER_SELLER_ACCEPTED', JSON.stringify({ orderId: order.order_id || order.id, sellerId })]
        );
        await client.query('COMMIT');
        return { ok: true, order: { ...order, status: 'SELLER_ACCEPTED' } };
      } catch (err) {
        await client.query('ROLLBACK');
        if (err.code === '40001' && attempt < MAX_RETRIES - 1) {
          await new Promise(r => setTimeout(r, 20 * (attempt + 1)));
          continue;
        }
        throw err;
      } finally {
        client.release();
      }
    }
  }

  async packOrderBySeller(orderId, storeId, sellerId) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const MAX_RETRIES = 3;
    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
      const client = await this.pool.connect();
      try {
        await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
        const orderRes = await client.query(`SELECT * FROM orders WHERE (order_id = $1 OR id = $1) FOR UPDATE`, [orderId]);
        const order = orderRes.rows[0];
        if (!order) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
        }
        if (storeId && order.store_id && order.store_id !== storeId) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 403, error: 'FORBIDDEN' };
        }

        // Idempotency: if already in PACKED, return without error
        if (order.status === 'PACKED') {
          await client.query('COMMIT');
          return { ok: true, order, isIdempotent: true };
        }

        // Precondition Check: Must be in SELLER_ACCEPTED
        if (order.status !== 'SELLER_ACCEPTED') {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 409, error: 'INVALID_ORDER_STATE_TRANSITION', message: `Cannot pack order in state '${order.status}'. Must be SELLER_ACCEPTED.` };
        }

        await client.query(`UPDATE orders SET status = 'PACKED', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);
        await client.query(
          `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status)
           VALUES ($1, $2, $3, $4, 'PENDING')`,
          ['ORDER', order.order_id || order.id, 'ORDER_PACKED', JSON.stringify({ orderId: order.order_id || order.id, sellerId })]
        );
        await client.query('COMMIT');
        return { ok: true, order: { ...order, status: 'PACKED' } };
      } catch (err) {
        await client.query('ROLLBACK');
        if (err.code === '40001' && attempt < MAX_RETRIES - 1) {
          await new Promise(r => setTimeout(r, 20 * (attempt + 1)));
          continue;
        }
        throw err;
      } finally {
        client.release();
      }
    }
  }

  async markReadyForPickup(orderId, storeId, sellerId) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const MAX_RETRIES = 3;
    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
      const client = await this.pool.connect();
      try {
        await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
        const orderRes = await client.query(`SELECT * FROM orders WHERE (order_id = $1 OR id = $1) FOR UPDATE`, [orderId]);
        const order = orderRes.rows[0];
        if (!order) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
        }
        if (storeId && order.store_id && order.store_id !== storeId) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 403, error: 'FORBIDDEN' };
        }

        // Idempotency: if already in READY_FOR_PICKUP, return without emitting duplicate outbox event
        if (order.status === 'READY_FOR_PICKUP') {
          await client.query('COMMIT');
          return { ok: true, order, isIdempotent: true };
        }

        // Precondition Check: Must be in PACKED or SELLER_ACCEPTED
        if (!['PACKED', 'SELLER_ACCEPTED'].includes(order.status)) {
          await client.query('ROLLBACK');
          return { ok: false, httpStatus: 409, error: 'INVALID_ORDER_STATE_TRANSITION', message: `Cannot mark ready for pickup in state '${order.status}'. Must be PACKED or SELLER_ACCEPTED.` };
        }

        await client.query(`UPDATE orders SET status = 'READY_FOR_PICKUP', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);
        await client.query(`UPDATE delivery_sessions SET state = 'READY_FOR_PICKUP', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);

        // Atomic Outbox Event guarantees dispatch execution on crash recovery (exactly once)
        await client.query(
          `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at)
           VALUES ($1, $2, $3, $4, 'PENDING', 0, NOW(), NOW())`,
          [
            'DELIVERY_SESSION',
            order.order_id || order.id,
            'DISPATCH_REQUESTED',
            JSON.stringify({
              orderId: order.order_id || order.id,
              storeId: order.store_id,
              customerId: order.customer_id,
              totalAmount: order.total_amount,
              isCod: order.is_cod
            })
          ]
        );

        await client.query('COMMIT');
        return { ok: true, order: { ...order, status: 'READY_FOR_PICKUP' } };
      } catch (err) {
        await client.query('ROLLBACK');
        if (err.code === '40001' && attempt < MAX_RETRIES - 1) {
          await new Promise(r => setTimeout(r, 20 * (attempt + 1)));
          continue;
        }
        throw err;
      } finally {
        client.release();
      }
    }
  }

  async cancelOrder(orderId, actorId, reason) {
    if (!this.pool) return { ok: false, error: 'NO_POOL' };
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
      const orderRes = await client.query(`SELECT * FROM orders WHERE (order_id = $1 OR id = $1) FOR UPDATE`, [orderId]);
      const order = orderRes.rows[0];
      if (!order) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
      }

      // Idempotency: if already CANCELLED, return success without releasing inventory again or emitting duplicate outbox event
      if (order.status === 'CANCELLED') {
        await client.query('COMMIT');
        return { ok: true, order, isIdempotent: true };
      }

      // State machine validation: Only allow cancellation in allowed pre-delivery states
      const ALLOWED_CANCELLATION_STATES = ['PLACED', 'PAYMENT_PENDING', 'PRESCRIPTION_VERIFICATION_PENDING', 'SELLER_ACCEPTED', 'PACKED', 'READY_FOR_PICKUP'];
      if (!ALLOWED_CANCELLATION_STATES.includes(order.status)) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 409,
          error: 'INVALID_ORDER_STATE_TRANSITION',
          message: `Cannot cancel order in state '${order.status}'. Cancellation is prohibited after delivery handoff.`
        };
      }

      // Restore inventory using the authoritative order.store_id
      const items = Array.isArray(order.items) ? order.items : (typeof order.items === 'string' ? JSON.parse(order.items) : (order.items || []));
      const storeId = order.store_id;
      if (!storeId) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 500, error: 'CORRUPT_ORDER_STATE', message: 'Order is missing authoritative store_id.' };
      }

      // Corruption guard: every canonical item snapshot must belong to the authoritative order store.
      const foreignItem = items.find(it => it && (it.storeId || it.store_id) && (it.storeId || it.store_id) !== storeId);
      if (foreignItem) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 500,
          error: 'CORRUPT_ORDER_INVENTORY_CONTEXT',
          message: `Item snapshot ${foreignItem.sku || foreignItem.productId} points at store ${foreignItem.storeId || foreignItem.store_id}, which does not match the authoritative order store ${storeId}.`
        };
      }

      // Canonical identity guard: production inventory release must operate on the canonical
      // triple (storeId, productId, sku) — sku-only silent release is forbidden.
      const nonCanonicalItem = items.find(it => !it || !it.productId || !it.sku || !Number(it.quantity));
      if (nonCanonicalItem) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 500,
          error: 'CANONICAL_PRODUCT_ID_REQUIRED',
          message: 'Canonical order item snapshots require storeId, productId, sku, and quantity for inventory release.'
        };
      }

      // Fulfillment context guard: orders.store_id must equal delivery_sessions.store_id.
      const sessionRes = await client.query(
        `SELECT store_id FROM delivery_sessions WHERE order_id = $1 LIMIT 1`,
        [order.order_id || order.id]
      );
      if (sessionRes.rows.length > 0 && sessionRes.rows[0].store_id !== storeId) {
        await client.query('ROLLBACK');
        return {
          ok: false,
          httpStatus: 500,
          error: 'CORRUPT_ORDER_FULFILLMENT_CONTEXT',
          message: 'Delivery session store does not match the authoritative order store.'
        };
      }

      const releaseRes = await this.inventoryRepo.releaseStockTransactionally(client, storeId, items);
      if (!releaseRes.ok) {
        await client.query('ROLLBACK');
        return { ok: false, httpStatus: 409, error: releaseRes.error || 'INVENTORY_RELEASE_FAILED', message: releaseRes.message };
      }

      await client.query(`UPDATE orders SET status = 'CANCELLED', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);
      await client.query(`UPDATE delivery_sessions SET state = 'CANCELLED', updated_at = NOW() WHERE order_id = $1`, [order.order_id || order.id]);

      await client.query(
        `INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, created_at)
         VALUES ($1, $2, $3, $4, 'PENDING', NOW())`,
        ['ORDER', order.order_id || order.id, 'ORDER_CANCELLED', JSON.stringify({ orderId: order.order_id || order.id, actorId, reason })]
      );

      await client.query('COMMIT');
      return { ok: true, order: { ...order, status: 'CANCELLED' } };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }
}

class LocalDevelopmentOrderRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
    this.inventoryRepo = new LocalDevelopmentInventoryRepository(db, saveDbFn);
  }

  async acceptOrderBySeller(orderId, storeId, sellerId) {
    const order = (this.db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (!order) return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
    
    const nowIso = new Date().toISOString();
    order.orderStatus = 'SELLER_ACCEPTED';
    order.status = 'SELLER_ACCEPTED';
    order.updatedAt = nowIso;
    order.trackingCheckpoints = order.trackingCheckpoints || [];
    order.trackingCheckpoints.push({
      status: 'SELLER_ACCEPTED',
      label: 'Order accepted by store and delivery partner requested',
      actor: 'SELLER',
      location: 'Fulfillment Store Hub',
      createdAt: nowIso
    });

    // 1. Resolve Store Details Dynamically
    const targetStoreId = order.storeId || storeId || order.fulfillmentStoreId || 'STORE_REWARI_01';
    let storeObj = (this.db.stores || []).find(s => s.id === targetStoreId || s.storeId === targetStoreId)
      || (this.db.stores && this.db.stores[targetStoreId])
      || (this.db.sellers || []).find(s => s.id === sellerId || s.sellerId === sellerId);

    const merchantName = storeObj?.storeName || storeObj?.store_name || storeObj?.name || 'Rewari Central Hub';
    const merchantAddress = storeObj?.address || storeObj?.streetAddress || '3126/21D Company Bagh, Circular Road, Rewari, Haryana 123401';
    const merchantLat = Number(storeObj?.latitude || storeObj?.lat || 28.1989);
    const merchantLng = Number(storeObj?.longitude || storeObj?.lng || 76.6186);

    // 2. Resolve Customer Details Dynamically
    let customerName = order.customerName || null;
    let customerPhone = order.customerPhone || null;
    let customerAddressStr = '';
    let customerLat = null;
    let customerLng = null;

    if (order.deliveryAddress) {
      if (typeof order.deliveryAddress === 'object') {
        const da = order.deliveryAddress;
        customerLat = da.latitude != null ? Number(da.latitude) : (da.lat != null ? Number(da.lat) : null);
        customerLng = da.longitude != null ? Number(da.longitude) : (da.lng != null ? Number(da.lng) : null);
        customerName = customerName || da.recipientName || da.name || da.contactName || null;
        customerPhone = customerPhone || da.recipientPhone || da.phone || da.contactPhone || null;

        const parts = [
          da.addressLine || da.streetAddress || da.address,
          da.landmark,
          da.city,
          da.state,
          da.pincode || da.postalCode
        ].filter(Boolean);
        customerAddressStr = parts.join(', ');
      } else if (typeof order.deliveryAddress === 'string') {
        customerAddressStr = order.deliveryAddress;
      }
    }

    if (!customerName || !customerPhone) {
      const customerRecord = (this.db.customers || []).find(c => c.id === order.customerId || c.customerId === order.customerId)
        || (this.db.users || []).find(u => u.id === order.customerId);
      if (customerRecord) {
        customerName = customerName || customerRecord.name || customerRecord.fullName || customerRecord.customerName || null;
        customerPhone = customerPhone || customerRecord.phone || customerRecord.phoneNumber || customerRecord.customerPhone || null;
      }
    }

    customerName = customerName || (customerPhone ? `Customer ${customerPhone.slice(-4)}` : 'Customer');
    customerPhone = customerPhone || '';
    customerAddressStr = customerAddressStr || 'Delivery Address Provided';
    customerLat = (customerLat != null && !isNaN(customerLat) && customerLat !== 0) ? customerLat : (merchantLat + 0.015);
    customerLng = (customerLng != null && !isNaN(customerLng) && customerLng !== 0) ? customerLng : (merchantLng + 0.015);

    // 3. Resolve Items Dynamically
    const items = Array.isArray(order.items) ? order.items : [];
    const itemsSummary = items.length > 0
      ? items.map(i => `${i.quantity || 1}x ${i.name || i.productName || i.title || 'Item'}`).join(', ')
      : 'Order Package';
    const itemCount = items.reduce((sum, i) => sum + (Number(i.quantity) || 1), 0) || 1;

    // 4. Calculate Real Distance & Duration (Haversine with urban road factor)
    const R = 6371;
    const dLat = (customerLat - merchantLat) * Math.PI / 180;
    const dLon = (customerLng - merchantLng) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(merchantLat * Math.PI / 180) * Math.cos(customerLat * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const deliveryDistanceKm = Math.max(0.6, Math.round(R * c * 1.35 * 10) / 10);
    const pickupDistanceKm = 0.5;
    const totalDistanceKm = Math.round((deliveryDistanceKm + pickupDistanceKm) * 10) / 10;
    const estimatedDurationMins = Math.max(5, Math.round(deliveryDistanceKm * 3.5 + 3));

    // 5. Calculate Real Rider Earnings & COD Dynamically
    const isCod = order.paymentMethod === 'COD' || order.paymentStatus === 'COD_PENDING' || Boolean(order.isCod);
    const codAmount = isCod ? Number(order.totalAmount || order.grandTotal || 0) : 0;
    const earningsAmount = Math.max(40, Math.round(35 + (totalDistanceKm * 12) + (isCod ? 15 : 0) + Math.max(0, (itemCount - 2) * 5)));

    // 6. Create or Sync Delivery Session
    this.db.deliverySessions = this.db.deliverySessions || {};
    let session = this.db.deliverySessions[order.id] || Object.values(this.db.deliverySessions).find(s => s.orderId === order.id);
    const deliveryId = session?.deliveryId || 'del_' + crypto.randomUUID();
    
    session = {
      deliveryId,
      id: deliveryId,
      orderId: order.id,
      customerId: order.customerId || 'usr_guest',
      customerName,
      customerPhone,
      customerAddress: customerAddressStr,
      customerLat,
      customerLng,
      deliveryAddress: order.deliveryAddress,
      storeId: targetStoreId,
      merchantName,
      merchantAddress,
      merchantLat,
      merchantLng,
      state: 'ASSIGNED',
      riderId: 'rdr_rewari_01',
      payoutFormatted: `₹${earningsAmount}`,
      distanceKm: totalDistanceKm,
      estimatedTimeMins: estimatedDurationMins,
      isCod,
      codAmount,
      codCollectedAmount: 0,
      codReconciled: false,
      itemCount,
      itemsSummary,
      createdAt: nowIso,
      updatedAt: nowIso
    };
    this.db.deliverySessions[deliveryId] = session;
    this.db.deliverySessions[order.id] = session;

    // 7. Create Authoritative Active Delivery Offer for Rider
    const offerId = 'off_' + crypto.randomUUID();
    const eventId = 'evt_' + crypto.randomUUID();
    const notifId = 'notif_' + crypto.randomUUID();
    this.db.offers = this.db.offers || {};

    const offerData = {
      offerId,
      id: offerId,
      eventId,
      notificationId: notifId,
      riderId: 'rdr_rewari_01',
      deliveryId: session.deliveryId,
      orderId: order.id,
      status: 'DISPATCHED',
      notificationStatus: 'DEVICE_RECEIVED',
      merchantName,
      merchantAddress,
      merchantLat,
      merchantLng,
      customerName,
      customerPhone,
      customerAddress: customerAddressStr,
      customerLat,
      customerLng,
      earningsAmount: Number(earningsAmount),
      totalEarnings: Number(earningsAmount),
      totalDistanceKm: Number(totalDistanceKm),
      deliveryDistanceKm: Number(deliveryDistanceKm),
      pickupDistanceKm: Number(pickupDistanceKm),
      estimatedDurationMins: Number(estimatedDurationMins),
      offerCreatedAt: Date.now(),
      offerExpiresAt: Date.now() + 900000, // 15 mins window
      serverTime: Date.now(),
      itemsSummary,
      itemCount,
      isCod,
      codAmount,
      createdAt: nowIso
    };
    this.db.offers[offerId] = offerData;

    // 8. Create Persistent Rider Notification
    this.db.riderNotifications = this.db.riderNotifications || [];
    this.db.riderNotifications.unshift({
      id: notifId,
      notificationId: notifId,
      eventId: eventId,
      riderId: 'rdr_rewari_01',
      category: 'ORDERS',
      priority: 'HIGH',
      title: '🚀 New Delivery Job Offer!',
      body: `New order #${order.id.slice(0, 8)} ready for pickup at ${merchantName}. Earn ₹${earningsAmount}.`,
      offerId: offerId,
      orderId: order.id,
      deliveryId: session.deliveryId,
      isRead: false,
      createdAt: nowIso
    });

    this.saveDb();
    return { ok: true, order, session, offer: offerData };
  }

  async packOrderBySeller(orderId, storeId, sellerId) {
    const order = (this.db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (!order) return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
    order.orderStatus = 'PACKED';
    order.status = 'PACKED';
    this.saveDb();
    return { ok: true, order };
  }

  async markReadyForPickup(orderId, storeId, sellerId) {
    const order = (this.db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (!order) return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
    order.orderStatus = 'READY_FOR_PICKUP';
    order.status = 'READY_FOR_PICKUP';
    const session = ((this.db.deliverySessions || {})[order.id] || Object.values(this.db.deliverySessions || {}).find(s => s.orderId === order.id));
    if (session) session.state = 'READY_FOR_PICKUP';
    this.db.outboxEvents = this.db.outboxEvents || [];
    this.db.outboxEvents.push({
      id: 'evt_outbox_' + crypto.randomUUID(),
      aggregateType: 'DELIVERY_SESSION',
      aggregateId: order.id,
      eventType: 'DISPATCH_REQUESTED',
      payload: { orderId: order.id, storeId, deliverySession: session },
      status: 'PENDING',
      createdAt: new Date().toISOString()
    });
    this.saveDb();
    return { ok: true, order };
  }

  async cancelOrder(orderId, actorId, reason) {
    const order = (this.db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (!order) return { ok: false, httpStatus: 404, error: 'ORDER_NOT_FOUND' };
    await this.inventoryRepo.releaseStockTransactionally(order.items || []);
    order.orderStatus = 'CANCELLED';
    order.status = 'CANCELLED';
    this.saveDb();
    return { ok: true, order };
  }

  async getRecentCustomerOrders(customerId, limit = 5) {
    return (this.db.orders || []).filter(o => o.customerId === customerId).slice(0, limit);
  }

  async getActiveCustomerOrder(customerId) {
    return (this.db.orders || []).find(o => o.customerId === customerId && ['PLACED', 'SELLER_ACCEPTED', 'PACKED', 'OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED'].includes(o.orderStatus || o.status)) || null;
  }

  async getOrdersByStore(storeId) {
    return (this.db.orders || []).filter(o => !storeId || o.storeId === storeId || o.fulfillmentStoreId === storeId || o.sellerId === storeId || !o.storeId || storeId === 'STORE_REWARI_01' || storeId === 'seller_demo_001' || storeId === 'STORE_MASTER_001');
  }

  async placeOrderTransactionally(arg1, arg2, arg3) {
    const customerId = typeof arg1 === 'string' ? arg1 : (arg1?.customerId || arg2?.customerId);
    const orderData = (typeof arg1 === 'object' && arg1 !== null) ? arg1 : arg2;
    const deliverySessionData = (typeof arg2 === 'object' && arg2 !== null && arg2 !== orderData) ? arg2 : (arg3 || {});
    if (customerId && orderData) orderData.customerId = customerId;
    if (orderData) {
      orderData.sellerId = orderData.sellerId || 'seller_rewari_01';
      orderData.storeId = orderData.storeId || 'STORE_REWARI_01';
      orderData.fulfillmentStoreId = orderData.fulfillmentStoreId || orderData.storeId;
    }

    // 1. Atomic Stock Debit
    const stockResult = await this.inventoryRepo.debitStockTransactionally(orderData?.items || []);
    if (!stockResult.ok) {
      return { ok: false, httpStatus: 409, error: 'OUT_OF_STOCK', sku: stockResult.sku, message: `Insufficient inventory for SKU ${stockResult.sku}.` };
    }

    // 2. Persist Order
    this.db.orders = (this.db.orders || []).filter(o => typeof o === 'object' && o !== null);
    this.db.orders.unshift(orderData);

    // 3. Persist Delivery Session
    this.db.deliverySessions = this.db.deliverySessions || {};
    if (deliverySessionData?.deliveryId) {
      this.db.deliverySessions[deliverySessionData.deliveryId] = deliverySessionData;
    }
    if (orderData?.id) {
      this.db.deliverySessions[orderData.id] = deliverySessionData;
    }

    // 4. Persist COD ledger
    if (orderData.isCod) {
      this.db.codLedger = this.db.codLedger || [];
      this.db.codLedger.unshift({
        id: 'cod_tx_' + crypto.randomUUID(),
        orderId: orderData.id,
        sellerId: orderData.sellerId || orderData.storeId,
        amountExpected: orderData.totalAmount,
        amountCollected: 0,
        shortageAmount: 0,
        status: 'PENDING_COLLECTION',
        collectorId: null,
        notes: 'Awaiting cash handoff upon delivery',
        createdAt: new Date().toISOString(),
        reconciled: false
      });
    }

    // 5. Record Transactional Outbox Events
    this.db.outboxEvents = this.db.outboxEvents || [];
    this.db.outboxEvents.push({
      id: 'evt_outbox_' + crypto.randomUUID(),
      aggregateType: 'DELIVERY_SESSION',
      aggregateId: deliverySessionData.deliveryId,
      eventType: 'DISPATCH_REQUESTED',
      payload: {
        orderId: orderData.id,
        deliveryId: deliverySessionData.deliveryId,
        customerId: orderData.customerId,
        totalAmount: orderData.totalAmount,
        isCod: orderData.isCod,
        deliverySession: deliverySessionData
      },
      status: 'PENDING',
      createdAt: new Date().toISOString()
    });

    this.db.outboxEvents.push({
      id: 'evt_outbox_' + crypto.randomUUID(),
      aggregateType: 'ORDER',
      aggregateId: orderData.id,
      eventType: 'ORDER_PLACED',
      payload: {
        orderId: orderData.id,
        deliveryId: deliverySessionData.deliveryId,
        customerId: orderData.customerId,
        totalAmount: orderData.totalAmount,
        isCod: orderData.isCod
      },
      status: 'PENDING',
      createdAt: new Date().toISOString()
    });

    this.saveDb();
    return { ok: true, httpStatus: 200, order: orderData, session: deliverySessionData };
  }

  async findOrderById(orderId) {
    return (this.db.orders || []).find((o) => o.id === orderId || o.orderId === orderId) || null;
  }
}

class TransactionalDeviceTokenRepository {
  constructor(dbPool) {
    if (!dbPool) {
      throw new Error('FATAL: PostgreSQL DB pool is required for TransactionalDeviceTokenRepository');
    }
    this.pool = dbPool;
  }

  async saveToken(riderId, tokenData) {
    const token = typeof tokenData === 'string' ? tokenData : (tokenData.token || tokenData.fcmToken);
    const platform = (tokenData && tokenData.platform) || 'ANDROID';
    const appVersion = (tokenData && tokenData.appVersion) || '1.0.0';
    const query = `
      INSERT INTO rider_device_tokens (rider_id, token, platform, app_version, updated_at)
      VALUES ($1, $2, $3, $4, NOW())
      ON CONFLICT (rider_id) 
      DO UPDATE SET token = EXCLUDED.token, platform = EXCLUDED.platform, app_version = EXCLUDED.app_version, updated_at = NOW()
      RETURNING *;
    `;
    const res = await this.pool.query(query, [riderId, token, platform, appVersion]);
    return res.rows[0];
  }

  async getTokenByRider(riderId) {
    const res = await this.pool.query(
      `SELECT * FROM rider_device_tokens WHERE rider_id = $1 ORDER BY updated_at DESC LIMIT 1`,
      [riderId]
    );
    return res.rows[0] || null;
  }
}

class LocalDevelopmentDeviceTokenRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async saveToken(riderId, tokenData) {
    this.db.riderTokens = this.db.riderTokens || {};
    const record = {
      riderId,
      token: tokenData.token || tokenData.fcmToken,
      fcmToken: tokenData.token || tokenData.fcmToken,
      deviceId: tokenData.deviceId || 'android_device_001',
      platform: tokenData.platform || 'android',
      updatedAt: new Date().toISOString()
    };
    this.db.riderTokens[riderId] = record;
    this.saveDb();
    return record;
  }

  async getTokenByRider(riderId) {
    return (this.db.riderTokens || {})[riderId] || null;
  }
}

class TransactionalRiderRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async findRiderById(riderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM riders WHERE id = $1 OR rider_id = $1`, [riderId]);
    return res.rows[0] || null;
  }

  async getActiveRiders() {
    if (!this.pool) return [];
    const res = await this.pool.query(`SELECT * FROM riders WHERE status = 'ACTIVE'`);
    return res.rows;
  }

  async saveRider(riderData) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `INSERT INTO riders (id, rider_id, phone, full_name, vehicle_number, vehicle_type, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
       ON CONFLICT (rider_id) 
       DO UPDATE SET phone = EXCLUDED.phone, full_name = EXCLUDED.full_name, vehicle_number = EXCLUDED.vehicle_number, status = EXCLUDED.status, updated_at = NOW()
       RETURNING *`,
      [
        riderData.id || ('rider_' + crypto.randomUUID()),
        riderData.riderId || riderData.id,
        riderData.phone || '+919876543210',
        riderData.fullName || riderData.name || 'Authoritative Delivery Fleet Partner',
        riderData.vehicleNumber || 'HR-26-EK-1234',
        riderData.vehicleType || 'TWO_WHEELER',
        riderData.status || 'ACTIVE'
      ]
    );
    return res.rows[0];
  }
}

class LocalDevelopmentRiderRepository {
  constructor(db) {
    this.db = db;
  }

  async findRiderById(riderId) {
    let rider = (this.db.riders || []).find((r) => r.id === riderId || r.riderId === riderId);
    if (!rider) {
      rider = {
        id: riderId || 'rdr_rewari_01',
        riderId: riderId || 'rdr_rewari_01',
        name: 'Vikram Singh',
        realName: 'Vikram Singh',
        phone: '+919876543210',
        realPhone: '+919876543210',
        vehicle: 'HR-26-AB-1234',
        realVehicle: 'HR-26-AB-1234',
        vehicleNumber: 'HR-26-AB-1234',
        vehicleType: 'TWO_WHEELER',
        tier: 'PRO_EXPRESS',
        rating: 4.9,
        status: 'ACTIVE'
      };
    }
    return rider;
  }
}

class TransactionalStoreRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async getStore(storeId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM stores WHERE id = $1`, [storeId]);
    return res.rows[0] || null;
  }
}

class LocalDevelopmentStoreRepository {
  constructor(db) {
    this.db = db;
  }

  async getStore(storeId) {
    return AUTHORITATIVE_STORE_MASTER;
  }

  async getActiveStores() {
    return [AUTHORITATIVE_STORE_MASTER];
  }

  async getAllActiveStores() {
    return [AUTHORITATIVE_STORE_MASTER];
  }
}

class TransactionalPresenceRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async getPresence(riderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(`SELECT * FROM rider_presence WHERE rider_id = $1`, [riderId]);
    if (res.rows.length === 0) return null;
    const r = res.rows[0];
    const lat = r.last_known_lat != null ? Number(r.last_known_lat) : null;
    const lng = r.last_known_lng != null ? Number(r.last_known_lng) : null;
    return {
      riderId: r.rider_id,
      status: r.status,
      lastKnownLat: lat,
      lastKnownLng: lng,
      latitude: lat,
      longitude: lng,
      last_known_lat: lat,
      last_known_lng: lng,
      lastSeenAt: r.last_seen_at,
      last_seen_at: r.last_seen_at
    };
  }

  async getEligibleOnlineRiders() {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT rp.*, r.full_name, r.phone, r.vehicle_number, r.status as rider_status, r.tier as rider_tier
       FROM rider_presence rp
       JOIN riders r ON rp.rider_id = r.rider_id
       WHERE rp.status = 'ONLINE' 
         AND r.status = 'ACTIVE'
         AND rp.last_seen_at >= NOW() - INTERVAL '15 minutes'
         AND NOT EXISTS (
           SELECT 1 FROM delivery_sessions ds 
           WHERE ds.rider_id = r.rider_id 
             AND ds.state IN ('ACCEPTED', 'ARRIVED_MERCHANT', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED')
         )
       ORDER BY rp.last_seen_at DESC`
    );
    return res.rows.map(r => {
      const lat = r.last_known_lat != null ? Number(r.last_known_lat) : null;
      const lng = r.last_known_lng != null ? Number(r.last_known_lng) : null;
      return {
        riderId: r.rider_id,
        status: r.status,
        lastSeenAt: r.last_seen_at,
        last_seen_at: r.last_seen_at,
        lastKnownLat: lat,
        lastKnownLng: lng,
        latitude: lat,
        longitude: lng,
        last_known_lat: lat,
        last_known_lng: lng,
        fullName: r.full_name,
        phone: r.phone,
        vehicleNumber: r.vehicle_number,
        tier: r.rider_tier || 'STANDARD'
      };
    });
  }

  async setShiftStatus(riderId, statusOrIsOnline, lat = null, lng = null) {
    if (!this.pool) return null;
    const status = (typeof statusOrIsOnline === 'string') ? statusOrIsOnline : (statusOrIsOnline ? 'ONLINE' : 'OFFLINE');
    const res = await this.pool.query(
      `INSERT INTO rider_presence (rider_id, status, last_known_lat, last_known_lng, last_seen_at)
       VALUES ($1, $2, $3, $4, NOW())
       ON CONFLICT (rider_id) 
       DO UPDATE SET 
         status = EXCLUDED.status, 
         last_known_lat = COALESCE(EXCLUDED.last_known_lat, rider_presence.last_known_lat),
         last_known_lng = COALESCE(EXCLUDED.last_known_lng, rider_presence.last_known_lng),
         last_seen_at = NOW()
       RETURNING *;`,
      [riderId, status, lat, lng]
    );
    return res.rows[0];
  }
}

class LocalDevelopmentPresenceRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async getPresence(riderId) {
    const p = (this.db.riderPresence || {})[riderId];
    if (p) return p;
    return {
      riderId: riderId || 'rdr_rewari_01',
      status: 'ONLINE',
      isOnline: true,
      latitude: 28.2021899,
      longitude: 76.6153954,
      lastKnownLat: 28.2021899,
      lastKnownLng: 76.6153954,
      lastSeenTimestamp: Date.now()
    };
  }

  async getEligibleOnlineRiders() {
    const list = Object.values(this.db.riderPresence || {});
    const online = list.filter(p => (p.status === 'ONLINE' || p.isOnline) && (!p.lastSeenTimestamp || p.lastSeenTimestamp >= Date.now() - 15 * 60 * 1000));
    if (online.length > 0) return online;
    return [{
      riderId: 'rdr_rewari_01',
      status: 'ONLINE',
      isOnline: true,
      latitude: 28.2021899,
      longitude: 76.6153954,
      lastKnownLat: 28.2021899,
      lastKnownLng: 76.6153954,
      lastSeenTimestamp: Date.now(),
      fullName: 'Vikram Singh',
      phone: '+919876543210',
      vehicleNumber: 'HR-26-AB-1234',
      tier: 'PRO_EXPRESS'
    }];
  }

  async setShiftStatus(riderId, isOnline) {
    this.db.riderPresence = this.db.riderPresence || {};
    this.db.riderPresence[riderId] = this.db.riderPresence[riderId] || { riderId };
    this.db.riderPresence[riderId].isOnline = isOnline;
    this.db.riderPresence[riderId].status = isOnline ? 'ONLINE' : 'OFFLINE';
    this.db.riderPresence[riderId].lastSeenTimestamp = Date.now();
    this.saveDb();
    return this.db.riderPresence[riderId];
  }
}

class TransactionalNotificationRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async findByRider(riderId, category = null) {
    if (!this.pool) return [];
    let query = `SELECT * FROM rider_notifications WHERE rider_id = $1`;
    const params = [riderId];
    if (category && category !== 'ALL') {
      query += ` AND UPPER(category) = $2`;
      params.push(category.toUpperCase());
    }
    query += ` ORDER BY created_at DESC LIMIT 100`;
    const res = await this.pool.query(query, params);
    return res.rows;
  }

  async createNotification(record) {
    if (!this.pool) return null;
    const metadata = {
      eventId: record.eventId,
      deliveryId: record.deliveryId,
      orderId: record.orderId,
      offerId: record.offerId,
      actionUrl: record.actionUrl,
      actionPayload: record.actionPayload,
      severity: record.severity,
      channel: record.channel,
      ...(record.metadata || {})
    };
    const res = await this.pool.query(
      `INSERT INTO rider_notifications (
        id, notification_id, rider_id, category, title, body, delivery_channel, status, metadata, created_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
      ON CONFLICT (notification_id) 
      DO UPDATE SET status = EXCLUDED.status, metadata = EXCLUDED.metadata
      RETURNING *`,
      [
        record.id || ('notif_' + crypto.randomUUID()),
        record.notificationId || record.id || ('notif_' + crypto.randomUUID()),
        record.riderId,
        record.category || 'DISPATCH_OFFER',
        record.title || 'Dispatch Alert',
        record.body || record.message || 'New delivery assignment available',
        record.deliveryChannel || 'FCM_PRIMARY',
        record.status || 'UNREAD',
        JSON.stringify(metadata)
      ]
    );
    return res.rows[0] || null;
  }

  async updateDeliveryOutcome(notificationId, outcomeData) {
    if (!this.pool) return;
    await this.pool.query(
      `UPDATE rider_notifications 
       SET status = $1, metadata = metadata || $2::jsonb
       WHERE notification_id = $3 OR id = $3`,
      [
        outcomeData.status || 'SENT',
        JSON.stringify({
          fcmDeliveryStatus: outcomeData.fcmDeliveryStatus || 'DELIVERED',
          deliveryMode: outcomeData.deliveryMode || 'PRIMARY',
          lastError: outcomeData.lastError || null
        }),
        notificationId
      ]
    );
  }

  async markRead(notificationId, riderId) {
    if (!this.pool) return false;
    const res = await this.pool.query(
      `UPDATE rider_notifications 
       SET read_at = NOW(), status = 'READ'
       WHERE (notification_id = $1 OR id = $1) AND (rider_id = $2 OR $2 IS NULL)
       RETURNING *`,
      [notificationId, riderId]
    );
    return res.rowCount > 0;
  }

  async getNotificationsForRecipient(recipientId) {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT * FROM rider_notifications 
       WHERE rider_id = $1 OR id = $1
       ORDER BY created_at DESC LIMIT 100`,
      [recipientId]
    );
    return res.rows.map(r => {
      const meta = typeof r.metadata === 'string' ? JSON.parse(r.metadata) : (r.metadata || {});
      return {
        id: r.id || r.notification_id,
        recipientId: r.rider_id,
        title: r.title,
        message: r.body,
        category: r.category,
        severity: meta.severity || 'HIGH',
        read: !!r.read_at || r.status === 'READ',
        createdAt: r.created_at
      };
    });
  }
}

class LocalDevelopmentNotificationRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async getNotificationsForRecipient(recipientId) {
    const list = this.db.notifications || this.db.riderNotifications || [];
    return list.filter(n => n.recipientId === recipientId || n.userId === recipientId || n.riderId === recipientId);
  }

  async markRead(notificationId, riderId) {
    const list = this.db.notifications || this.db.riderNotifications || [];
    const n = list.find(item => (item.id === notificationId || item.notificationId === notificationId) && (item.recipientId === riderId || item.riderId === riderId || item.userId === riderId));
    if (n) {
      n.read = true;
      n.readAt = new Date().toISOString();
      this.saveDb();
      return true;
    }
    return false;
  }

  async findByRider(riderId, category = null) {
    let list = this.db.riderNotifications || [];
    list = list.filter((n) => n.riderId === riderId);
    if (category && category !== 'ALL') {
      list = list.filter((n) => (n.category || '').toUpperCase() === category.toUpperCase());
    }
    return list;
  }

  async createNotification(record) {
    this.db.riderNotifications = this.db.riderNotifications || [];
    const exists = this.db.riderNotifications.some(n => n.id === record.id || n.notificationId === record.notificationId);
    if (!exists) {
      this.db.riderNotifications.unshift(record);
      this.saveDb();
    }
    return record;
  }

  async updateDeliveryOutcome(notificationId, outcomeData) {
    const notif = (this.db.riderNotifications || []).find(
      (n) => n.id === notificationId || n.notificationId === notificationId
    );
    if (notif) {
      notif.status = outcomeData.status || notif.status;
      notif.fcmDeliveryStatus = outcomeData.fcmDeliveryStatus;
      notif.deliveryMode = outcomeData.deliveryMode;
      notif.lastError = outcomeData.lastError;
      this.saveDb();
    }
  }

  async markRead(notificationId, riderId) {
    const notif = (this.db.riderNotifications || []).find(
      (n) => (n.id === notificationId || n.notificationId === notificationId) && n.riderId === riderId
    );
    if (notif) {
      notif.read = true;
      notif.readAt = new Date().toISOString();
      this.saveDb();
      return true;
    }
    return false;
  }
}

class TransactionalTelemetryRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async recordTelemetry(dataOrRiderId, eventTypeOrDeliveryId = null, payload = null) {
    if (!this.pool) return;
    let riderId = null;
    let deliveryId = null;
    let lat = 0;
    let lng = 0;
    let heading = 0;
    let speed = 0;
    let accuracy = 0;

    if (typeof dataOrRiderId === 'object' && dataOrRiderId !== null) {
      riderId = dataOrRiderId.riderId || dataOrRiderId.rider_id;
      deliveryId = dataOrRiderId.deliveryId || dataOrRiderId.delivery_id || null;
      lat = Number(dataOrRiderId.latitude || dataOrRiderId.lat || 0);
      lng = Number(dataOrRiderId.longitude || dataOrRiderId.lng || 0);
      heading = Number(dataOrRiderId.heading || 0);
      speed = Number(dataOrRiderId.speed || 0);
      accuracy = Number(dataOrRiderId.accuracy || 0);
    } else {
      riderId = dataOrRiderId;
      deliveryId = typeof eventTypeOrDeliveryId === 'string' && eventTypeOrDeliveryId.startsWith('del_') ? eventTypeOrDeliveryId : null;
      if (payload) {
        lat = Number(payload.latitude || payload.lat || 0);
        lng = Number(payload.longitude || payload.lng || 0);
        heading = Number(payload.heading || 0);
        speed = Number(payload.speed || 0);
        accuracy = Number(payload.accuracy || 0);
      }
    }

    if (riderId) {
      await this.pool.query(
        `INSERT INTO rider_telemetry (rider_id, delivery_id, latitude, longitude, heading, speed, accuracy, recorded_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())`,
        [riderId, deliveryId, lat, lng, heading, speed, accuracy]
      );
    }
  }

  async getLatestTelemetryForRider(riderId) {
    if (!this.pool) return null;
    const res = await this.pool.query(
      `SELECT * FROM rider_telemetry WHERE rider_id = $1 ORDER BY recorded_at DESC LIMIT 1`,
      [riderId]
    );
    return res.rows[0] || null;
  }
}

class LocalDevelopmentTelemetryRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async recordTelemetry(riderId, eventType, payload) {
    this.db.fcmTelemetry = this.db.fcmTelemetry || [];
    this.db.fcmTelemetry.push({
      riderId,
      eventType,
      payload,
      timestamp: Date.now()
    });
    this.saveDb();
  }
}

class InternalDispatchCommand {
  constructor({ deliverySession, targetRiderId = null, correlationId = null }) {
    if (!deliverySession) {
      throw new Error('INVALID_DISPATCH_COMMAND: deliverySession is strictly required.');
    }
    this.deliverySession = deliverySession;
    this.targetRiderId = targetRiderId;
    this.correlationId = correlationId;
  }
}

class DispatchService {
  constructor({
    storeRepo,
    presenceRepo,
    riderRepo,
    offerRepo,
    serviceabilityRepo = null,
    routeResolver = null,
    pricingCalculator = null,
    isProduction = false
  }) {
    this.storeRepo = storeRepo;
    this.presenceRepo = presenceRepo;
    this.riderRepo = riderRepo;
    this.offerRepo = offerRepo;
    this.serviceabilityRepo = serviceabilityRepo;
    this.isProduction = isProduction;

    if (isProduction && !routeResolver) {
      throw new Error('FATAL_CONFIGURATION_ERROR: DispatchService strictly requires a real routeResolver (OSRM) in production mode. Synthetic default router is forbidden.');
    }

    this.routeResolver = routeResolver || (async (lat1, lon1, lat2, lon2) => {
      const R = 6371;
      const dLat = (lat2 - lat1) * Math.PI / 180;
      const dLon = (lon2 - lon1) * Math.PI / 180;
      const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      const dist = Math.round(R * c * 1.25 * 10) / 10;
      const mins = Math.max(4, Math.round(dist * 3));
      return { ok: true, distanceKm: dist, durationMins: mins };
    });
    this.pricingCalculator = pricingCalculator || (require('../pricing-engine').calculateAuthoritativeEarnings);
  }

  async processDispatch(commandOrSession, explicitTargetRiderId = null) {
    let deliverySession = commandOrSession;
    let targetRiderId = explicitTargetRiderId;

    if (commandOrSession && commandOrSession.deliverySession) {
      deliverySession = commandOrSession.deliverySession;
      targetRiderId = commandOrSession.targetRiderId || explicitTargetRiderId;
    }

    if (!deliverySession) {
      console.warn('[DispatchService] processDispatch called without deliverySession');
      return null;
    }

    const deliveryId = deliverySession.deliveryId || deliverySession.id;
    const orderId = deliverySession.orderId;
    const cLat = deliverySession.customerLat;
    const cLng = deliverySession.customerLng;

    if (cLat == null || cLng == null || isNaN(Number(cLat)) || isNaN(Number(cLng))) {
      console.warn(`[DispatchService] Customer coordinates missing for delivery ${deliveryId}. Dispatch deferred.`);
      return null;
    }

    // 1. Resolve Store Master from authoritative repository
    let store = null;
    const storeId = deliverySession.storeId || deliverySession.store_id || deliverySession.merchantId;
    if (this.storeRepo) {
      store = await this.storeRepo.getStore(storeId);
    }
    if (!store && !this.isProduction) {
      store = AUTHORITATIVE_STORE_MASTER;
    }
    if (!store) {
      throw new Error(`STORE_NOT_FOUND: Store ${storeId} not found in StoreRepository.`);
    }

    const mLat = Number(store.latitude != null ? store.latitude : deliverySession.merchantLat);
    const mLng = Number(store.longitude != null ? store.longitude : deliverySession.merchantLng);
    if (isNaN(mLat) || isNaN(mLng)) {
      throw new Error(`INVALID_STORE_COORDINATES: Store ${storeId} has invalid coordinates.`);
    }

    // 2. Resolve Candidate Rider from authoritative presence & fleet
    let selectedRiderId = targetRiderId;
    if (selectedRiderId) {
      if (this.presenceRepo) {
        const presence = await this.presenceRepo.getPresence(selectedRiderId);
        const isOnline = presence && (presence.status === 'ONLINE' || presence.isOnline);
        const isFresh = presence && presence.last_seen_at && (new Date(presence.last_seen_at).getTime() >= Date.now() - 15 * 60 * 1000);
        if (!presence || !isOnline || (this.isProduction && !isFresh)) {
          throw new Error(`RIDER_NOT_ELIGIBLE: Target rider ${selectedRiderId} is offline or presence is stale.`);
        }
      }
      if (this.riderRepo) {
        const riderProfile = await this.riderRepo.findRiderById(selectedRiderId);
        if (!riderProfile || (riderProfile.status && riderProfile.status !== 'ACTIVE')) {
          throw new Error(`RIDER_NOT_ELIGIBLE: Target rider ${selectedRiderId} is inactive or not found.`);
        }
      }
    } else if (this.presenceRepo) {
      const eligibleRiders = typeof this.presenceRepo.getEligibleOnlineRiders === 'function'
        ? await this.presenceRepo.getEligibleOnlineRiders()
        : [];
      if (eligibleRiders.length > 0) {
        selectedRiderId = eligibleRiders[0].riderId;
      }
    }

    if (!selectedRiderId) {
      throw new Error('NO_RIDERS_AVAILABLE: No active eligible fleet rider available for dispatch.');
    }

    // 3. Resolve Store -> Customer Route (Authoritative OSRM)
    const deliveryRoute = await this.routeResolver(mLat, mLng, Number(cLat), Number(cLng));
    if (!deliveryRoute || !deliveryRoute.ok) {
      throw new Error(`ROUTE_UNAVAILABLE: Routing calculation failed between store (${mLat}, ${mLng}) and customer (${cLat}, ${cLng}).`);
    }
    const storeToCustomerDistKm = Number(deliveryRoute.distanceKm);
    const storeToCustomerDurMins = Number(deliveryRoute.durationMins);

    // 4. Resolve Rider -> Store Route if presence available
    let riderToStoreDistKm = null;
    let riderToStoreDurMins = 0;
    if (this.presenceRepo) {
      const rPres = await this.presenceRepo.getPresence(selectedRiderId);
      const rLat = rPres ? (rPres.lastKnownLat != null ? rPres.lastKnownLat : (rPres.last_known_lat != null ? rPres.last_known_lat : rPres.latitude)) : null;
      const rLng = rPres ? (rPres.lastKnownLng != null ? rPres.lastKnownLng : (rPres.last_known_lng != null ? rPres.last_known_lng : rPres.longitude)) : null;
      if (rLat != null && rLng != null && !isNaN(Number(rLat)) && !isNaN(Number(rLng))) {
        const pickupRoute = await this.routeResolver(Number(rLat), Number(rLng), mLat, mLng);
        if (pickupRoute.ok) {
          riderToStoreDistKm = pickupRoute.distanceKm;
          riderToStoreDurMins = pickupRoute.durationMins;
        }
      }
    }

    const totalDistanceKm = riderToStoreDistKm != null
      ? Math.round((riderToStoreDistKm + storeToCustomerDistKm) * 10) / 10
      : storeToCustomerDistKm;
    const totalDurationMins = riderToStoreDurMins + storeToCustomerDurMins;

    // 5. Resolve Rider Tier from RiderRepository
    let riderTier = 'STANDARD';
    if (this.riderRepo) {
      const riderProfile = await this.riderRepo.findRiderById(selectedRiderId);
      if (this.isProduction && (!riderProfile || !riderProfile.tier)) {
        throw new Error(`FATAL_PRICING_ERROR: Rider ${selectedRiderId} has no provisioned pricing tier in RiderRepository.`);
      }
      riderTier = (riderProfile && riderProfile.tier) ? riderProfile.tier : 'STANDARD';
    }

    // 6. Calculate Authoritative Earnings & Pricing Snapshot
    const isCod = Boolean(deliverySession.isCod || deliverySession.is_cod);
    const codAmount = isCod ? Number(deliverySession.codAmount || deliverySession.cod_amount || 0) : 0;
    const pricingResult = this.pricingCalculator({
      distanceKm: totalDistanceKm,
      isCod: isCod,
      isColdChain: Boolean(deliverySession.isColdChain || deliverySession.is_cold_chain),
      itemCount: deliverySession.itemCount || 1,
      riderTier: riderTier
    });

    const now = Date.now();
    const offerExpiresAt = now + 900000;
    const offerId = 'off_' + crypto.randomUUID();

    const customerName = deliverySession.customerName || deliverySession.customer_name;
    const customerAddress = deliverySession.customerAddress || deliverySession.customer_address;
    const merchantName = store?.store_name || store?.storeName || deliverySession.merchantName || deliverySession.merchant_name;
    const merchantAddress = store?.address || deliverySession.merchantAddress || deliverySession.merchant_address;

    if (!customerName || !customerAddress || !merchantName || !merchantAddress) {
      throw new Error(`INTEGRITY_ERROR: Incomplete customer or merchant details for delivery session ${deliveryId}.`);
    }

    const offer = {
      offerId,
      eventId: 'evt_' + crypto.randomUUID(),
      notificationId: 'notif_' + crypto.randomUUID(),
      deliveryId: deliveryId,
      orderId: orderId,
      storeId: storeId || store.id || store.storeId,
      riderId: selectedRiderId,
      status: 'CREATED',
      earningsAmount: pricingResult.totalEarnings,
      pricingSnapshot: pricingResult.pricingSnapshot,
      pickupDistanceKm: riderToStoreDistKm,
      deliveryDistanceKm: storeToCustomerDistKm,
      totalDistanceKm: totalDistanceKm,
      estimatedDurationMins: totalDurationMins,
      isCod: isCod,
      codAmount: codAmount,
      customerName: customerName,
      customerAddress: customerAddress,
      customerLat: Number(cLat),
      customerLng: Number(cLng),
      merchantName: merchantName,
      merchantAddress: merchantAddress,
      merchantLat: mLat,
      merchantLng: mLng,
      offerCreatedAt: now,
      offerExpiresAt: offerExpiresAt,
      history: [{ status: 'CREATED', timestamp: new Date().toISOString(), riderId: selectedRiderId }]
    };

    // 7. Persist Offer Transactionally (which writes NEW_DISPATCH_OFFER into outbox)
    await this.offerRepo.createOfferTransactionally(offer);
    return offer;
  }
}

class TransactionalCartRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async getCart(customerId) {
    if (!this.pool) return [];
    const res = await this.pool.query(
      `SELECT items FROM carts WHERE customer_id = $1`,
      [customerId]
    );
    if (res.rows.length === 0) return [];
    const raw = res.rows[0].items;
    const items = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
    return items.map(r => ({
      id: r.id || r.sku,
      sku: r.sku,
      name: r.name || r.sku,
      quantity: Number(r.quantity) || 1,
      price: Number(r.price || 0),
      discountedPrice: Number(r.discountedPrice || r.price || 0)
    }));
  }

  async addItem(customerId, item) {
    if (!this.pool) return [];
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const res = await client.query(`SELECT id, items FROM carts WHERE customer_id = $1 FOR UPDATE`, [customerId]);
      let items = [];
      if (res.rows.length > 0) {
        const raw = res.rows[0].items;
        items = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
        const existing = items.find(i => i.sku === item.sku);
        if (existing) {
          existing.quantity = (Number(existing.quantity) || 1) + (Number(item.quantity) || 1);
        } else {
          items.push({
            id: item.id || ('cart_item_' + crypto.randomUUID()),
            sku: item.sku,
            name: item.name || item.sku,
            quantity: Number(item.quantity) || 1,
            price: Number(item.price || 0),
            discountedPrice: Number(item.discountedPrice || item.price || 0)
          });
        }
        await client.query(`UPDATE carts SET items = $1, updated_at = NOW() WHERE customer_id = $2`, [JSON.stringify(items), customerId]);
      } else {
        items = [{
          id: item.id || ('cart_item_' + crypto.randomUUID()),
          sku: item.sku,
          name: item.name || item.sku,
          quantity: Number(item.quantity) || 1,
          price: Number(item.price || 0),
          discountedPrice: Number(item.discountedPrice || item.price || 0)
        }];
        await client.query(
          `INSERT INTO carts (id, customer_id, items, created_at, updated_at) VALUES ($1, $2, $3, NOW(), NOW())`,
          ['cart_' + crypto.randomUUID(), customerId, JSON.stringify(items)]
        );
      }
      await client.query('COMMIT');
      return items;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async updateItemQty(customerId, sku, quantity) {
    if (!this.pool) return [];
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const res = await client.query(`SELECT items FROM carts WHERE customer_id = $1 FOR UPDATE`, [customerId]);
      if (res.rows.length === 0) {
        await client.query('ROLLBACK');
        return [];
      }
      let items = typeof res.rows[0].items === 'string' ? JSON.parse(res.rows[0].items) : (res.rows[0].items || []);
      if (quantity <= 0) {
        items = items.filter(i => i.sku !== sku);
      } else {
        const existing = items.find(i => i.sku === sku);
        if (existing) {
          existing.quantity = quantity;
        }
      }
      await client.query(`UPDATE carts SET items = $1, updated_at = NOW() WHERE customer_id = $2`, [JSON.stringify(items), customerId]);
      await client.query('COMMIT');
      return items;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async removeItem(customerId, sku) {
    return this.updateItemQty(customerId, sku, 0);
  }

  async clearCart(customerId) {
    if (!this.pool) return [];
    await this.pool.query(`UPDATE carts SET items = '[]'::jsonb, updated_at = NOW() WHERE customer_id = $1`, [customerId]);
    return [];
  }
}

class LocalDevelopmentCartRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async getCart(customerId) {
    this.db.carts = this.db.carts || {};
    return this.db.carts[customerId] || [];
  }

  async addItem(customerId, item) {
    this.db.carts = this.db.carts || {};
    const items = this.db.carts[customerId] || [];
    const existing = items.find(i => i.sku === item.sku);
    if (existing) {
      existing.quantity = (existing.quantity || 1) + (item.quantity || 1);
    } else {
      items.push({ ...item, quantity: item.quantity || 1 });
    }
    this.db.carts[customerId] = items;
    this.saveDb();
    return items;
  }

  async updateItemQty(customerId, sku, quantity) {
    this.db.carts = this.db.carts || {};
    const items = this.db.carts[customerId] || [];
    const existing = items.find(i => i.sku === sku);
    if (existing) {
      existing.quantity = quantity;
    }
    this.saveDb();
    return items;
  }

  async removeItem(customerId, sku) {
    this.db.carts = this.db.carts || {};
    this.db.carts[customerId] = (this.db.carts[customerId] || []).filter(i => i.sku !== sku);
    this.saveDb();
    return this.db.carts[customerId] || [];
  }

  async clearCart(customerId) {
    this.db.carts = this.db.carts || {};
    this.db.carts[customerId] = [];
    this.saveDb();
    return [];
  }
}

class TransactionalPaymentRepository {
  constructor(dbPool) {
    this.pool = dbPool;
  }

  async createOrGetPaymentIntent(paymentData) {
    if (!this.pool) return null;
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const existing = await client.query(`SELECT * FROM payments WHERE order_id = $1 LIMIT 1`, [paymentData.orderId]);
      if (existing.rows.length > 0) {
        await client.query('COMMIT');
        return existing.rows[0];
      }
      const paymentId = paymentData.paymentId || paymentData.id || ('pay_' + crypto.randomUUID());
      const pkId = paymentData.id || paymentId;
      const res = await client.query(
        `INSERT INTO payments (
          id, payment_id, order_id, amount, currency, status, method, provider, provider_ref, metadata, created_at, updated_at
        ) VALUES ($1, $2, $3, $4, 'INR', $5, $6, $7, $8, $9, NOW(), NOW())
        RETURNING *`,
        [
          pkId,
          paymentId,
          paymentData.orderId,
          Number(paymentData.amount || 0),
          paymentData.status || 'PENDING',
          paymentData.method || paymentData.paymentMethod || 'UPI_INSTANT',
          paymentData.provider || 'RAZORPAY',
          paymentData.providerRef || paymentData.gatewayReference || ('ref_' + crypto.randomUUID()),
          JSON.stringify(paymentData.metadata || {})
        ]
      );
      await client.query('COMMIT');
      return res.rows[0];
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async capturePaymentTransactionally(paymentId, capturedAmount) {
    if (!this.pool) return null;
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
      const res = await client.query(
        `UPDATE payments SET status = 'CAPTURED', amount = COALESCE($1, amount), updated_at = NOW()
         WHERE id = $2 OR payment_id = $2 OR order_id = $2
         RETURNING *`,
        [capturedAmount, paymentId]
      );
      await client.query('COMMIT');
      return res.rows[0] || null;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }
}

class LocalDevelopmentPaymentRepository {
  constructor(db, saveDbFn) {
    this.db = db;
    this.saveDb = saveDbFn || (() => {});
  }

  async createOrGetPaymentIntent(paymentData) {
    this.db.payments = this.db.payments || [];
    const existing = this.db.payments.find(p => p.orderId === paymentData.orderId);
    if (existing) return existing;
    const rec = {
      id: paymentData.id || ('pay_' + Date.now()),
      orderId: paymentData.orderId,
      customerId: paymentData.customerId,
      amount: paymentData.amount,
      currency: 'INR',
      status: paymentData.status || 'PENDING',
      paymentMethod: paymentData.paymentMethod || 'UPI_INSTANT',
      gatewayReference: paymentData.gatewayReference || ('gw_' + Date.now()),
      createdAt: new Date().toISOString()
    };
    this.db.payments.push(rec);
    this.saveDb();
    return rec;
  }

  async capturePaymentTransactionally(paymentId, capturedAmount) {
    this.db.payments = this.db.payments || [];
    const pay = this.db.payments.find(p => p.id === paymentId || p.orderId === paymentId);
    if (!pay) return null;
    pay.status = 'CAPTURED';
    if (capturedAmount) pay.amount = capturedAmount;
    pay.updatedAt = new Date().toISOString();
    this.saveDb();
    return pay;
  }
}

class ProductionNotificationService {
  constructor(notificationRepo, offerRepo, deliveryRepo, deviceTokenRepo, fcmSender, sseBroadcaster, telemetryRepo = null, isProduction = false) {
    if (isProduction) {
      if (!notificationRepo || !offerRepo || !deviceTokenRepo || !fcmSender || !sseBroadcaster) {
        throw new Error('FATAL_CONFIGURATION_ERROR: ProductionNotificationService requires all persistence and transport dependencies in production mode.');
      }
    }
    this.notificationRepo = notificationRepo;
    this.offerRepo = offerRepo;
    this.deliveryRepo = deliveryRepo;
    this.deviceTokenRepo = deviceTokenRepo;
    this.fcmSender = fcmSender;
    this.sseBroadcaster = sseBroadcaster;
    this.telemetryRepo = telemetryRepo;
  }

  async dispatchOfferNotification(offerId, targetRiderId, initialOffer = null) {
    let offer = initialOffer;
    if (this.offerRepo && !offer) {
      offer = await this.offerRepo.findOfferById(offerId);
    }
    if (!offer) {
      throw new Error(`OFFER_NOT_FOUND: Authoritative offer ${offerId} not found in database.`);
    }

    const riderId = targetRiderId || offer.riderId || offer.rider_id;
    if (!riderId) {
      throw new Error(`TARGET_RIDER_ID_REQUIRED: Missing recipient for offer ${offerId}.`);
    }

    const earnings = Math.floor(offer.earningsAmount || offer.earnings_amount);
    const totalDist = offer.totalDistanceKm || offer.total_distance_km;
    const duration = offer.estimatedDurationMins || offer.estimated_duration_mins;
    const mName = offer.merchantName || offer.merchant_name;
    const cAddr = offer.customerAddress || offer.customer_address;
    const conciseDrop = cAddr ? (cAddr.split(',')[0] || cAddr).trim() : 'Drop address unavailable';

    const notifId = offer.notificationId || offer.notification_id;
    const notifRecord = {
      id: notifId,
      notificationId: notifId,
      eventId: offer.eventId || offer.event_id,
      type: 'NEW_ORDER_OFFER',
      category: 'ORDERS',
      priority: 'HIGH',
      riderId: riderId,
      orderId: offer.orderId || offer.order_id,
      deliveryId: offer.deliveryId || offer.delivery_id,
      offerId: offer.offerId || offer.offer_id,
      title: `🚨 NEW DELIVERY · ₹${earnings}`,
      body: `${totalDist} km (~${duration} min) • Pickup: ${mName} • Drop: ${conciseDrop}`,
      actionUrl: `commerceos://rider/offer/${offer.offerId || offer.offer_id}`,
      actionPayload: offer,
      createdAt: new Date().toISOString(),
      expiresAt: Number(offer.offerExpiresAt || offer.offer_expires_at),
      readAt: null,
      status: 'PENDING',
      channel: 'PUSH_AND_INAPP'
    };

    if (this.notificationRepo) {
      await this.notificationRepo.createNotification(notifRecord);
    }

    let sseOk = false;
    if (this.sseBroadcaster) {
      sseOk = await this.sseBroadcaster(riderId, 'NEW_ORDER_OFFER', offer);
    }

    let fcmOk = false;
    let deviceToken = null;
    if (this.deviceTokenRepo) {
      if (typeof this.deviceTokenRepo.getActiveToken === 'function') {
        deviceToken = await this.deviceTokenRepo.getActiveToken(riderId);
      } else if (typeof this.deviceTokenRepo.getTokenByRider === 'function') {
        const tokenRec = await this.deviceTokenRepo.getTokenByRider(riderId);
        deviceToken = tokenRec ? (tokenRec.token || tokenRec.fcm_token || tokenRec.fcmToken) : null;
      }
    }

    if (this.fcmSender && deviceToken) {
      if (typeof this.fcmSender.sendPushNotification === 'function') {
        fcmOk = await this.fcmSender.sendPushNotification(deviceToken, {
          notificationId: notifId,
          offerId: offer.offerId || offer.offer_id,
          orderId: offer.orderId || offer.order_id,
          deliveryId: offer.deliveryId || offer.delivery_id,
          title: notifRecord.title,
          body: notifRecord.body,
          data: {
            offerId: offer.offerId || offer.offer_id,
            orderId: offer.orderId || offer.order_id,
            deliveryId: offer.deliveryId || offer.delivery_id,
            earningsAmount: String(earnings),
            totalDistanceKm: String(totalDist),
            estimatedDurationMins: String(duration),
            offerExpiresAt: String(offer.offerExpiresAt || offer.offer_expires_at),
            merchantName: mName,
            customerAddress: cAddr,
            merchantAddress: offer.merchantAddress || offer.merchant_address || ''
          }
        });
      } else if (typeof this.fcmSender === 'function') {
        fcmOk = await this.fcmSender(riderId, notifRecord.title, notifRecord.body, {
          eventType: 'NEW_DISPATCH_OFFER',
          offerId: offer.offerId || offer.offer_id,
          notificationId: notifId
        }, deviceToken);
      }
    }

    if (this.telemetryRepo) {
      await this.telemetryRepo.recordTelemetry(riderId, 'OFFER_DISPATCH_ATTEMPT', {
        offerId,
        notificationId: notifId,
        fcmOk,
        sseOk,
        deviceTokenPresent: Boolean(deviceToken)
      });
    }

    if (fcmOk) {
      if (this.notificationRepo) {
        await this.notificationRepo.updateDeliveryOutcome(notifId, {
          status: 'DELIVERED_FCM',
          fcmDeliveryStatus: 'FCM_ACCEPTED',
          deliveryMode: 'PRIMARY',
          lastError: null
        });
      }
      if (this.offerRepo && this.offerRepo.updateDeliveryStatus) {
        await this.offerRepo.updateDeliveryStatus(offer.offerId || offer.offer_id, 'FCM_ACCEPTED');
      }
      return { ok: true, success: true, deliveryMode: 'DELIVERED_PRIMARY', mode: 'PRIMARY', notifId, fcmOk: true, sseOk: false };
    }

    if (sseOk) {
      if (this.notificationRepo) {
        await this.notificationRepo.updateDeliveryOutcome(notifId, {
          status: 'DELIVERED_SSE_FALLBACK',
          fcmDeliveryStatus: 'FCM_FAILED',
          deliveryMode: 'FALLBACK_SSE',
          lastError: 'FCM delivery failed; delivered via real-time SSE stream'
        });
      }
      if (this.offerRepo && this.offerRepo.updateDeliveryStatus) {
        await this.offerRepo.updateDeliveryStatus(offer.offerId || offer.offer_id, 'FCM_FAILED_SSE_ACTIVE');
      }
      return { ok: true, success: true, deliveryMode: 'DELIVERED_DEGRADED', mode: 'DEGRADED_FALLBACK', notifId, fcmOk: false, sseOk: true };
    }

    if (this.notificationRepo) {
      await this.notificationRepo.updateDeliveryOutcome(notifId, {
        status: 'FAILED',
        fcmDeliveryStatus: 'FCM_FAILED',
        deliveryMode: 'NONE',
        lastError: 'Both FCM and SSE delivery channels failed'
      });
    }
    if (this.offerRepo && this.offerRepo.updateDeliveryStatus) {
      await this.offerRepo.updateDeliveryStatus(offer.offerId || offer.offer_id, 'FCM_FAILED_AND_UNREACHABLE');
    }
    throw new Error(`DISPATCH_CHANNELS_UNAVAILABLE: FCM delivery failed and rider ${riderId} has no active SSE connection.`);
  }

  async dispatchDeliveryStateEvent(deliveryId, eventType) {
    if (!this.sseBroadcaster) return;
    let session = null;
    if (this.deliveryRepo) {
      session = await this.deliveryRepo.findSessionById(deliveryId);
    }
    if (session) {
      await this.sseBroadcaster(null, eventType, session, deliveryId);
    }
  }
}

class OutboxProcessor {
  constructor(dbPool, eventDispatcher) {
    if (!eventDispatcher || typeof eventDispatcher !== 'function') {
      throw new Error('FATAL: OutboxProcessor requires a valid eventDispatcher function');
    }
    this.pool = dbPool;
    this.dispatcher = eventDispatcher;
    this.isProcessing = false;
    this.intervalHandle = null;
  }

  start(intervalMs = 2000) {
    if (this.intervalHandle) return;
    this.intervalHandle = setInterval(() => {
      this.processPendingEvents().catch((err) => {
        console.error('[OutboxProcessor] Uncaught error in outbox worker loop:', err.message);
      });
    }, intervalMs);
    console.log('[OutboxProcessor] Worker started with active event dispatcher.');
  }

  stop() {
    if (this.intervalHandle) {
      clearInterval(this.intervalHandle);
      this.intervalHandle = null;
    }
  }

  async processPendingEvents() {
    if (this.isProcessing || !this.pool) return 0;
    this.isProcessing = true;

    const BACKOFF_SCHEDULE_SEC = [5, 15, 30, 60, 120];
    const MAX_RETRIES = 5;

    let claimedEvents = [];

    const claimClient = await this.pool.connect();
    try {
      await claimClient.query('BEGIN');
      const claimRes = await claimClient.query(
        `SELECT * FROM outbox_events 
         WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= NOW()) AND retry_count < $1
         ORDER BY created_at ASC LIMIT 20 FOR UPDATE SKIP LOCKED`,
        [MAX_RETRIES]
      );
      claimedEvents = claimRes.rows;

      if (claimedEvents.length > 0) {
        const ids = claimedEvents.map((e) => e.id);
        await claimClient.query(
          `UPDATE outbox_events 
           SET status = 'PROCESSING' 
           WHERE id = ANY($1::bigint[])`,
          [ids]
        );
      }
      await claimClient.query('COMMIT');
    } catch (err) {
      await claimClient.query('ROLLBACK');
      console.error('[OutboxProcessor] Failed to claim pending events:', err.message);
      this.isProcessing = false;
      return 0;
    } finally {
      claimClient.release();
    }

    if (claimedEvents.length === 0) {
      this.isProcessing = false;
      return 0;
    }

    let processedCount = 0;
    for (const event of claimedEvents) {
      const execClient = await this.pool.connect();
      try {
        await this.dispatcher(event);

        await execClient.query(
          `UPDATE outbox_events 
           SET status = 'SENT', processed_at = NOW() 
           WHERE id = $1`,
          [event.id]
        );
        processedCount++;
      } catch (err) {
        const newRetryCount = (event.retry_count || 0) + 1;
        const delaySec = BACKOFF_SCHEDULE_SEC[Math.min(newRetryCount - 1, BACKOFF_SCHEDULE_SEC.length - 1)] || 120;
        const status = newRetryCount >= MAX_RETRIES ? 'DEAD_LETTER' : 'PENDING';

        await execClient.query(
          `UPDATE outbox_events 
           SET status = $1, 
               retry_count = $2, 
               last_error = $3, 
               next_attempt_at = NOW() + ($4 || ' seconds')::INTERVAL
           WHERE id = $5`,
          [status, newRetryCount, err.message, `${delaySec}`, event.id]
        );
      } finally {
        execClient.release();
      }
    }

    this.isProcessing = false;
    return processedCount;
  }
}

async function initApplicationRepositories(options = {}) {
  const isProdEnv = process.env.COMMERCEOS_ENV === 'production' || 
                    process.env.COMMERCEOS_PERSISTENCE_MODE === 'postgres' || 
                    process.env.NODE_ENV === 'production';
  const forceLocal = options.forceLocal === true || process.env.COMMERCEOS_PERSISTENCE_MODE === 'local' || process.env.COMMERCEOS_ENV === 'local_test';

  const {
    pgPool = null,
    db = {},
    saveDbFn = () => {},
    fcmSender = null,
    sseBroadcaster = null,
    routeResolver = null,
    pricingCalculator = null
  } = options;

  const hasPostgresConfig = Boolean(pgPool || (process.env.DATABASE_URL && process.env.DATABASE_URL.trim()));
  const shouldUsePostgres = hasPostgresConfig && !forceLocal;

  if (shouldUsePostgres) {
    let pool = pgPool;
    if (!pool && process.env.DATABASE_URL) {
      try {
        const { Pool } = require('pg');
        pool = new Pool({ connectionString: process.env.DATABASE_URL });
      } catch (err) {
        throw new Error(`FATAL_STARTUP_ERROR: Failed to initialize PostgreSQL pool from DATABASE_URL: ${err.message}`);
      }
    }

    if (pool) {
      console.log('[PersistenceLayer] Mode: PRODUCTION (PostgreSQL Transactional Repositories)');
    const catalogRepo = new TransactionalCatalogRepository(pool);
    const customerRepo = new TransactionalCustomerRepository(pool);
    const addressRepo = new TransactionalAddressRepository(pool);
    const prescriptionRepo = new TransactionalPrescriptionRepository(pool);
    const codLedgerRepo = new TransactionalCodLedgerRepository(pool);
    const inventoryRepo = new TransactionalInventoryRepository(pool);
    const offerRepo = new TransactionalOfferRepository(pool);
    const orderRepo = new TransactionalOrderRepository(pool);
    const deliveryRepo = new TransactionalDeliveryRepository(pool);
    const notificationRepo = new TransactionalNotificationRepository(pool);
    const deviceTokenRepo = new TransactionalDeviceTokenRepository(pool);
    const telemetryRepo = new TransactionalTelemetryRepository(pool);
    const riderRepo = new TransactionalRiderRepository(pool);
    const storeRepo = new TransactionalStoreRepository(pool);
    const presenceRepo = new TransactionalPresenceRepository(pool);
    const serviceabilityRepo = new TransactionalServiceabilityRepository(pool);
    const cartRepo = new TransactionalCartRepository(pool);
    const paymentRepo = new TransactionalPaymentRepository(pool);
    const sellerRepo = new TransactionalSellerRepository(pool);
    const auditRepo = new TransactionalAuditRepository(pool);

    const serviceabilityService = new ServiceabilityService(serviceabilityRepo, storeRepo);

    const dispatchService = new DispatchService({
      storeRepo,
      presenceRepo,
      riderRepo,
      offerRepo,
      serviceabilityRepo,
      routeResolver,
      pricingCalculator,
      isProduction: true
    });

    const notificationService = new ProductionNotificationService(
      notificationRepo,
      offerRepo,
      deliveryRepo,
      deviceTokenRepo,
      fcmSender,
      sseBroadcaster,
      telemetryRepo,
      true
    );

    const outboxProcessor = new OutboxProcessor(pool, async (event) => {
      const payload = typeof event.payload === 'string' ? JSON.parse(event.payload) : event.payload;
      if (event.event_type === 'NEW_DISPATCH_OFFER') {
        await notificationService.dispatchOfferNotification(payload.offerId, payload.targetRiderId, payload.offer);
      } else if (event.event_type === 'DISPATCH_REQUESTED') {
        await dispatchService.processDispatch(payload.deliverySession || payload);
      } else if (event.event_type === 'RIDER_ACCEPTED' || event.event_type === 'DELIVERY_STATE_CHANGED') {
        await notificationService.dispatchDeliveryStateEvent(payload.deliveryId, event.event_type);
      }
    });

    outboxProcessor.start();

    return {
      isProduction: true,
      catalogRepo,
      customerRepo,
      sellerRepo,
      auditRepo,
      addressRepo,
      prescriptionRepo,
      codLedgerRepo,
      inventoryRepo,
      offerRepo,
      orderRepo,
      deliveryRepo,
      notificationRepo,
      deviceTokenRepo,
      telemetryRepo,
      riderRepo,
      storeRepo,
      presenceRepo,
      serviceabilityRepo,
      cartRepo,
      paymentRepo,
      serviceabilityService,
      dispatchService,
      notificationService,
      outboxProcessor
    };
    }
  }

  console.log('[PersistenceLayer] Initialized LocalDevelopmentRepository harness.');
  const catalogRepo = new LocalDevelopmentCatalogRepository(db, saveDbFn);
  const customerRepo = new LocalDevelopmentCustomerRepository(db, saveDbFn);
  const addressRepo = new LocalDevelopmentAddressRepository(db);
  const prescriptionRepo = new LocalDevelopmentPrescriptionRepository(db, saveDbFn);
  const codLedgerRepo = new LocalDevelopmentCodLedgerRepository(db, saveDbFn);
  const inventoryRepo = new LocalDevelopmentInventoryRepository(db, saveDbFn);
  const offerRepo = new LocalDevelopmentOfferRepository(db, saveDbFn);
  const orderRepo = new LocalDevelopmentOrderRepository(db, saveDbFn);
  const deliveryRepo = new LocalDevelopmentDeliveryRepository(db, saveDbFn);
  const notificationRepo = new LocalDevelopmentNotificationRepository(db, saveDbFn);
  const deviceTokenRepo = new LocalDevelopmentDeviceTokenRepository(db, saveDbFn);
  const telemetryRepo = new LocalDevelopmentTelemetryRepository(db, saveDbFn);
  const riderRepo = new LocalDevelopmentRiderRepository(db);
  const storeRepo = new LocalDevelopmentStoreRepository(db);
  const presenceRepo = new LocalDevelopmentPresenceRepository(db, saveDbFn);
  const serviceabilityRepo = new LocalDevelopmentServiceabilityRepository(db);
  const cartRepo = new LocalDevelopmentCartRepository(db, saveDbFn);
  const paymentRepo = new LocalDevelopmentPaymentRepository(db, saveDbFn);
  const sellerRepo = new LocalDevelopmentSellerRepository(db, saveDbFn);
  const auditRepo = new LocalDevelopmentAuditRepository(db, saveDbFn);

  const serviceabilityService = new ServiceabilityService(serviceabilityRepo, storeRepo);

  const dispatchService = new DispatchService({
    storeRepo,
    presenceRepo,
    riderRepo,
    offerRepo,
    serviceabilityRepo,
    routeResolver,
    pricingCalculator,
    isProduction: false
  });

  const notificationService = new ProductionNotificationService(
    notificationRepo,
    offerRepo,
    deliveryRepo,
    deviceTokenRepo,
    fcmSender,
    sseBroadcaster,
    telemetryRepo,
    false
  );

  return {
    isProduction: false,
    catalogRepo,
    customerRepo,
    sellerRepo,
    auditRepo,
    addressRepo,
    prescriptionRepo,
    codLedgerRepo,
    inventoryRepo,
    offerRepo,
    orderRepo,
    deliveryRepo,
    notificationRepo,
    deviceTokenRepo,
    telemetryRepo,
    riderRepo,
    storeRepo,
    presenceRepo,
    serviceabilityRepo,
    cartRepo,
    paymentRepo,
    serviceabilityService,
    dispatchService,
    notificationService,
    outboxProcessor: null
  };
}

function createProductionRepositories(pool, options = {}) {
  if (!pool) {
    throw new Error('FATAL: A valid PostgreSQL pool is strictly required to instantiate production repositories.');
  }
  const catalogRepo = new TransactionalCatalogRepository(pool);
  const customerRepo = new TransactionalCustomerRepository(pool);
  const addressRepo = new TransactionalAddressRepository(pool);
  const prescriptionRepo = new TransactionalPrescriptionRepository(pool);
  const codLedgerRepo = new TransactionalCodLedgerRepository(pool);
  const inventoryRepo = new TransactionalInventoryRepository(pool);
  const offerRepo = new TransactionalOfferRepository(pool);
  const orderRepo = new TransactionalOrderRepository(pool);
  const deliveryRepo = new TransactionalDeliveryRepository(pool);
  const notificationRepo = new TransactionalNotificationRepository(pool);
  const deviceTokenRepo = new TransactionalDeviceTokenRepository(pool);
  const telemetryRepo = new TransactionalTelemetryRepository(pool);
  const riderRepo = new TransactionalRiderRepository(pool);
  const storeRepo = new TransactionalStoreRepository(pool);
  const presenceRepo = new TransactionalPresenceRepository(pool);
  const serviceabilityRepo = new TransactionalServiceabilityRepository(pool);
  const cartRepo = new TransactionalCartRepository(pool);
  const paymentRepo = new TransactionalPaymentRepository(pool);
  const sellerRepo = new TransactionalSellerRepository(pool);
  const auditRepo = new TransactionalAuditRepository(pool);

  const serviceabilityService = new ServiceabilityService(serviceabilityRepo, storeRepo);

  const dispatchService = new DispatchService({
    storeRepo,
    presenceRepo,
    riderRepo,
    offerRepo,
    serviceabilityRepo,
    routeResolver: options.routeResolver || null,
    pricingCalculator: options.pricingCalculator || null,
    isProduction: true
  });

  const notificationService = new ProductionNotificationService(
    notificationRepo,
    offerRepo,
    deliveryRepo,
    deviceTokenRepo,
    options.fcmSender || null,
    options.sseBroadcaster || null,
    telemetryRepo,
    true
  );

  const eventDispatcher = async (event) => {
    let payload = event.payload;
    if (typeof payload === 'string') {
      try { payload = JSON.parse(payload); } catch { payload = {}; }
    }

    if (event.event_type === 'DISPATCH_REQUESTED') {
      let deliverySession = payload.deliverySession || payload;
      if (!deliverySession.orderId && event.aggregate_id) {
        const sRes = await pool.query('SELECT * FROM delivery_sessions WHERE delivery_id = $1 OR order_id = $1', [event.aggregate_id]);
        if (sRes.rows.length > 0) deliverySession = sRes.rows[0];
      }
      return await dispatchService.processDispatch(deliverySession);
    }

    if (event.event_type === 'NEW_DISPATCH_OFFER') {
      const offer = payload.offer || payload;
      return await notificationService.dispatchOfferNotification(offer);
    }

    if (['ORDER_PLACED', 'RIDER_ACCEPTED', 'DELIVERY_STATUS_CHANGED', 'ORDER_CANCELLED', 'ORDER_PACKED'].includes(event.event_type)) {
      if (options.sseBroadcaster) {
        await options.sseBroadcaster(event.aggregate_id || 'global', event.event_type, payload);
      }
      return { ok: true };
    }

    throw new Error(`UNSUPPORTED_OUTBOX_EVENT_TYPE: ${event.event_type}`);
  };

  const outboxProcessor = new OutboxProcessor(pool, eventDispatcher);

  return {
    isProduction: true,
    catalogRepo,
    customerRepo,
    sellerRepo,
    auditRepo,
    addressRepo,
    prescriptionRepo,
    codLedgerRepo,
    inventoryRepo,
    offerRepo,
    orderRepo,
    deliveryRepo,
    notificationRepo,
    deviceTokenRepo,
    telemetryRepo,
    riderRepo,
    storeRepo,
    presenceRepo,
    serviceabilityRepo,
    cartRepo,
    paymentRepo,
    serviceabilityService,
    dispatchService,
    notificationService,
    outboxProcessor,
    eventDispatcher
  };
}

function createLocalDevelopmentRepositories(db, saveDbFn = () => {}, options = {}) {
  const catalogRepo = new LocalDevelopmentCatalogRepository(db, saveDbFn);
  const customerRepo = new LocalDevelopmentCustomerRepository(db, saveDbFn);
  const addressRepo = new LocalDevelopmentAddressRepository(db);
  const prescriptionRepo = new LocalDevelopmentPrescriptionRepository(db, saveDbFn);
  const codLedgerRepo = new LocalDevelopmentCodLedgerRepository(db, saveDbFn);
  const inventoryRepo = new LocalDevelopmentInventoryRepository(db, saveDbFn);
  const offerRepo = new LocalDevelopmentOfferRepository(db, saveDbFn);
  const orderRepo = new LocalDevelopmentOrderRepository(db, saveDbFn);
  const deliveryRepo = new LocalDevelopmentDeliveryRepository(db, saveDbFn);
  const notificationRepo = new LocalDevelopmentNotificationRepository(db, saveDbFn);
  const deviceTokenRepo = new LocalDevelopmentDeviceTokenRepository(db, saveDbFn);
  const telemetryRepo = new LocalDevelopmentTelemetryRepository(db, saveDbFn);
  const riderRepo = new LocalDevelopmentRiderRepository(db);
  const storeRepo = new LocalDevelopmentStoreRepository(db);
  const presenceRepo = new LocalDevelopmentPresenceRepository(db, saveDbFn);
  const serviceabilityRepo = new LocalDevelopmentServiceabilityRepository(db);
  const cartRepo = new LocalDevelopmentCartRepository(db, saveDbFn);
  const paymentRepo = new LocalDevelopmentPaymentRepository(db, saveDbFn);
  const sellerRepo = new LocalDevelopmentSellerRepository(db, saveDbFn);
  const auditRepo = new LocalDevelopmentAuditRepository(db, saveDbFn);

  const serviceabilityService = new ServiceabilityService(serviceabilityRepo, storeRepo);

  const dispatchService = new DispatchService({
    storeRepo,
    presenceRepo,
    riderRepo,
    offerRepo,
    serviceabilityRepo,
    routeResolver: options.routeResolver || null,
    pricingCalculator: options.pricingCalculator || null,
    isProduction: false
  });

  const notificationService = new ProductionNotificationService(
    notificationRepo,
    offerRepo,
    deliveryRepo,
    deviceTokenRepo,
    options.fcmSender || null,
    options.sseBroadcaster || null,
    telemetryRepo,
    false
  );

  return {
    isProduction: false,
    catalogRepo,
    customerRepo,
    sellerRepo,
    auditRepo,
    addressRepo,
    prescriptionRepo,
    codLedgerRepo,
    inventoryRepo,
    offerRepo,
    orderRepo,
    deliveryRepo,
    notificationRepo,
    deviceTokenRepo,
    telemetryRepo,
    riderRepo,
    storeRepo,
    presenceRepo,
    serviceabilityRepo,
    cartRepo,
    paymentRepo,
    serviceabilityService,
    dispatchService,
    notificationService,
    outboxProcessor: null
  };
}

module.exports = {
  DeliveryOtpService,
  TransactionalCatalogRepository,
  LocalDevelopmentCatalogRepository,
  TransactionalCustomerRepository,
  LocalDevelopmentCustomerRepository,
  TransactionalSellerRepository,
  LocalDevelopmentSellerRepository,
  TransactionalAuditRepository,
  LocalDevelopmentAuditRepository,
  TransactionalAddressRepository,
  LocalDevelopmentAddressRepository,
  TransactionalPrescriptionRepository,
  LocalDevelopmentPrescriptionRepository,
  TransactionalCodLedgerRepository,
  LocalDevelopmentCodLedgerRepository,
  TransactionalServiceabilityRepository,
  LocalDevelopmentServiceabilityRepository,
  TransactionalCartRepository,
  LocalDevelopmentCartRepository,
  TransactionalPaymentRepository,
  LocalDevelopmentPaymentRepository,
  ServiceabilityService,
  TransactionalInventoryRepository,
  LocalDevelopmentInventoryRepository,
  TransactionalOfferRepository,
  LocalDevelopmentOfferRepository,
  TransactionalOrderRepository,
  LocalDevelopmentOrderRepository,
  TransactionalDeliveryRepository,
  LocalDevelopmentDeliveryRepository,
  TransactionalNotificationRepository,
  LocalDevelopmentNotificationRepository,
  TransactionalDeviceTokenRepository,
  LocalDevelopmentDeviceTokenRepository,
  TransactionalTelemetryRepository,
  LocalDevelopmentTelemetryRepository,
  TransactionalRiderRepository,
  LocalDevelopmentRiderRepository,
  TransactionalStoreRepository,
  LocalDevelopmentStoreRepository,
  TransactionalPresenceRepository,
  LocalDevelopmentPresenceRepository,
  InternalDispatchCommand,
  FulfillmentDecision,
  NotificationDeliveryResult,
  DispatchService,
  ProductionNotificationService,
  OutboxProcessor,
  createProductionRepositories,
  createLocalDevelopmentRepositories,
  initApplicationRepositories
};
