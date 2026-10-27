# Commerce OS — Core Working Principles & Architecture Invariants

> **MANDATORY DIRECTIVE FOR ALL DEVELOPERS AND AI AGENTS:**  
> Before making **ANY** modification, bug fix, refactor, or feature addition to this codebase, you **MUST** read and strictly adhere to the principles outlined in this document. Any change violating these principles is considered a breaking defect.

---

## 1. The Core Vision: The Turnkey "Minimal Change" Engine

### The Golden Philosophy
Commerce OS is **ONE single, production-hardened universal commerce engine** designed to be structured into **any e-commerce vertical** (Quick Pharmacy, Supermarket/Grocery, Electronics, Fresh Produce, Fashion, or Specialty Retail) with **minimal, configuration-only changes ("lil changes")**.

You do **not** write separate apps for different industries. You do **not** rebuild order workflows, dispatch pipelines, or payment gates for each client.

### The "Turnkey Deploy" Invariant
Deploying the platform for a new client or vertical must **only** require updating the configuration layer:
1. **Database URL (`DATABASE_URL`)**: Points to the client's dedicated PostgreSQL instance.
2. **Server API URL (`API_GATEWAY_URL` / `API_BASE_URL`)**: Points to the client's deployed backend gateway.
3. **App Identifiers & Brand Metadata**:
   - Android `applicationId` and `app_name`
   - iOS `PRODUCT_BUNDLE_IDENTIFIER` and `APP_DISPLAY_NAME`
   - App icons, splash screens, and theme color tokens
4. **Third-Party Service Credentials**:
   - Google Maps API Key (bound to the client's package name & keystore SHA-1)
   - Firebase/FCM project credentials (`google-services.json`, `GoogleService-Info.plist`)
5. **Vertical Feature Toggles**:
   - `features.prescriptions` (enabled for pharmacy, disabled for grocery/apparel)
   - `features.coldChainPackaging` (enabled for temperature-sensitive meds/dairy, disabled for dry goods)
   - `features.fefoInventory` (First-Expiry-First-Out for perishable goods, standard FIFO for dry retail)
   - `features.instantDelivery10Min` vs `features.scheduledSlots`

The core engine—state machines, real-time tracking, outbox event streaming, address pinpointing, cart management, and 0-fee payment gates—**remains 100% frozen, untouched, and zero-touch**.

---

## 2. Universal Cross-Platform Parity (Zero Platform Drift)

Commerce OS spans 5 unified client surfaces communicating with a single authoritative PostgreSQL backend:
1. **Customer Web Storefront**: Next.js App Router (`apps/web`)
2. **Customer Android Mobile App**: Kotlin + Jetpack Compose (`apps/android/app`)
3. **Customer iOS Mobile App**: Swift + SwiftUI (`apps/ios/CommerceOS`)
4. **Rider / Delivery Partner Apps**:
   - Android Rider App: Kotlin + Jetpack Compose (`apps/android/rider-app`)
   - iOS Rider App: Swift + SwiftUI (`apps/ios/CommerceOSRider`)
5. **Merchant & Operations Portals**: Seller Dashboard (`apps/seller`), Warehouse (`apps/warehouse`), Admin (`apps/admin`)

### Rules of Parity:
* **The 1:1 Parity Rule**: If a feature, fix, or validation is added to Android, the exact equivalent **MUST** be implemented in iOS and Web, and vice-versa.
* **Shared Data Contracts**: Field names, serialization types, error codes, and DTO schemas must be identical across TypeScript, Kotlin, and Swift.
* **Single Authoritative Source**: Business logic (pricing, discounts, delivery fees, serviceability, stock availability, OTP hashes) lives **only on the server**. Client applications are reactive renderers of server state, never independent authorities.

---

## 3. The Anti-Hardcoding & Zero-Assumption Rules

To ensure any client can be spun up without breaking existing functionality:
* **NO Hardcoded Store / Hub Identifiers**: Never hardcode store IDs (e.g. `STORE_REWARI_01`) or hardcoded geocoordinates into core code or business queries. All store resolution must be dynamic via spatial proximity or customer address context.
* **NO Hardcoded Vertical Logic**: Never assume an order requires a prescription or cold-chain handling unless explicitly dictated by the catalog item metadata (`item.prescriptionRequired`, `item.coldChain`) and enabled by the tenant configuration.
* **NO Production Mock Fallbacks**: Never use fake tokens (e.g., `jwt_demo_partner_token`), master OTP bypasses (`123456`), or synthetic tracking objects (`"Partner Assigning"`) in production paths. When an operation fails or data is unavailable, the system must **fail closed** with an explicit error code.

---

## 4. Micro-Interaction & Decision-Tree Resilience

Users interact with apps through thousands of branching decision paths. The system must remain deterministic and robust across all possible flows:

1. **State Preservation Across Transitions**:
   - Navigating back from an address form or checkout step must **never** wipe uncommitted user input. Draft states (e.g. `cachedDraftAddress`) must be preserved until explicitly submitted or discarded.
   - Switching between login and registration, or re-authenticating, must preserve the user's intended target route (`?redirect=/checkout`).
2. **Idempotency & Double-Submission Guards**:
   - Every order submission must carry a persistent idempotency key across network retries.
   - Doorstep delivery completion must be idempotent: repeated delivery verification attempts on an already-completed order must safely return the completed state without corrupting inventory or emitting duplicate financial events.
3. **Resilient Session & Auth Lifecycle**:
   - Mobile and Web auth interceptors must handle token refresh concurrency gracefully using double-checked locking: concurrent 401s must wait on the active refresh operation and re-use the new token rather than logging the user out.

---

## 5. Security, Payments & Hardware-Backed Storage

1. **Zero-Fee Direct UPI & Cash-on-Delivery (COD)**:
   - Built-in support for direct UPI intent (`upi://pay`) without paying 1.5%–2% fees to payment aggregators.
   - Strict UTR validation (minimum 6 alphanumeric characters) backed by a database-level unique index (`idx_payments_upi_utr`) to eliminate replay and double-spending attacks.
2. **Cryptographic Delivery OTP**:
   - 4-to-6 digit delivery PINs are generated server-side and stored **strictly as HMAC-SHA256 hashes** with salt/pepper (`delivery_otp_hash`).
   - Comparison uses constant-time cryptographic verification (`crypto.timingSafeEqual`) to prevent timing attacks.
3. **Hardware-Backed Credential Storage**:
   - iOS: Secure Keychain (`KeychainStore`, `RiderKeychainHelper`).
   - Android: `EncryptedSharedPreferences` / Android Keystore.
   - Web: Secure HTTP-only cookies / encrypted session store.

---

## 6. Pre-Commit Verification Checklist

Before marking any task complete or committing code to version control, execute the following mandatory verification steps:

- [ ] **Static Guards & Platform Suite**:
  ```bash
  ./platform/run-all-tests.sh
  ```
  *(Must achieve 100% pass rate: 102/102 static assertion guards, 40/40 full-system integrity tests).*
- [ ] **Web Monorepo Lint & Typecheck**:
  ```bash
  pnpm lint
  ```
- [ ] **Web Production Build**:
  ```bash
  pnpm build
  ```
- [ ] **Android Compilation & Unit Tests**:
  ```bash
  cd apps/android
  ./gradlew :app:compileDebugKotlin :rider-app:compileDebugKotlin :app:testDebugUnitTest
  ```
- [ ] **iOS Build Verification**:
  ```bash
  cd apps/ios
  xcodebuild -scheme CommerceOS -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
  xcodebuild -scheme CommerceOSRider -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
  ```
- [ ] **Zero Hardcoding Audit**: Verify no hardcoded store IDs, bypass tokens, or client-specific URLs were introduced into common code.
