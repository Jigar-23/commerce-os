# Commerce OS — Identity & Authentication Platform Specification (Milestone Contract #001)

> **Status:** 100% Production-Grade Complete  
> **Domain Code:** `DOM-IAM`  

---

## 🏛️ Architecture Overview & Threat Model

The Identity & Authentication Platform provides zero-trust security for all microservices across the Commerce OS monorepo. It features Argon2id & BCrypt credential hashing, WebAuthn/FIDO2 Hardware Passkeys, Refresh Token Rotation (RTR), TOTP Multi-Factor Authentication (MFA), and active device session revocation.

### 🔑 Core Security Mechanisms
1. **Short-Lived Access JWTs**: 15-minute expiration carrying `userId`, `email`, `roles`, and `tenant_id`.
2. **Refresh Token Rotation (RTR)**: 30-day single-use refresh tokens stored with SHA-256 hashes in PostgreSQL `user_sessions`.
3. **WebAuthn / FIDO2 Passkeys**: Hardware biometrics (Touch ID, Face ID, YubiKey) supported via public-key cryptography.
4. **Active Session Revocation**: Remote invalidation of tokens across cluster memory and database ledgers.

---

## 📊 Entity Relationship Diagram (ERD)

```
+------------------------------------+
|            USER_ACCOUNTS           |
+------------------------------------+
| id (UUID PK)                       |
| email (VARCHAR UNIQUE)             |
| phone (VARCHAR UNIQUE)             |
| password_hash (VARCHAR)            |
| email_verified (BOOLEAN)           |
| phone_verified (BOOLEAN)           |
| mfa_enabled (BOOLEAN)              |
| mfa_secret_key (VARCHAR)           |
+------------------------------------+
                   |
     +-------------+-------------+------------------+
     | 1:N                       | 1:N              | 1:N
+----+----------------+  +-------+--------+  +------+------------+
|    USER_SESSIONS    |  | PASSKEY_CREDS  |  |   AUDIT_LOGS      |
+---------------------+  +----------------+  +-------------------+
| id (UUID PK)        |  | id (UUID PK)   |  | id (UUID PK)      |
| refresh_token_hash  |  | credential_id  |  | event_type        |
| ip_address          |  | public_key     |  | ip_address        |
| device_id           |  | sign_count     |  | user_agent        |
| is_revoked (BOOL)   |  | last_used_at   |  | status (SUCCESS)  |
+---------------------+  +----------------+  +-------------------+
```

---

## 🔌 API Endpoints Summary

- `POST /api/v1/auth/register` — User signup & verification token dispatch
- `POST /api/v1/auth/login` — Password & MFA authentication
- `POST /api/v1/auth/webauthn/challenge` — WebAuthn passkey challenge
- `POST /api/v1/auth/mfa/setup` — TOTP QR code & backup codes setup
- `GET /api/v1/auth/sessions/{userId}` — List active trusted devices
- `POST /api/v1/auth/sessions/{sessionId}/revoke` — Invalidate remote active session
