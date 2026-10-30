# Commerce OS — Engineering Principles, Invariants & Hiccup Inventory

**Document Version**: 1.0.0  
**Target Architecture**: Turnkey, White-Label Quick-Commerce Platform (Turnkey Baseline for $N$ Multi-Vertical Deployments)  
**Authoritative Codebase**: `/Users/jigar/Desktop/new-project/commerce-os`

---

## 1. Mission Statement & Golden Objective

Commerce OS is an industrial-standard, turnkey quick-commerce engine designed to be cloned, rebranded, and deployed for **any vertical** (Pharmacy, Supermarket/Grocery, Electronics, Fresh Meat, Flower Delivery). 

Regardless of whether the client sells paracetamol or milk:
* **The Business Logic is Identical**: Catalog browsing ➔ Address geocoding ➔ Geospatial store resolution ➔ Stock reservation with row locks ➔ Outbox event dispatch ➔ Rider push notification ➔ Dynamic route mapping ➔ OTP handoff ➔ Financial settlement.
* **The Distinctions are Purely Configuration**: Brand identity (logos, theme colors), Application IDs / package names, independent databases, dedicated server endpoints, and third-party API credentials (Google Maps, Firebase).

Our objective is to deliver a **100% deterministic, zero-flakiness core** where everything works out-of-the-box without mysterious intermittent failures.

---

## 2. Core Architectural Invariants (The Constitution)

Every line of code written in this repository must strictly adhere to these seven fundamental principles:

### Invariant I: The Zero-Fallback / Fail-Closed Principle
* **Rule**: Production code must **NEVER** silently fall back to hardcoded development strings, demo coordinates (e.g., Rewari coordinates `28.202224, 76.615418`), or placeholder stores (`STORE_REWARI_01`) when real data or serviceability fails.
* **Enforcement**: If a customer's address is out of delivery bounds, the server must fail closed with `HTTP 422 STORE_NOT_SERVICEABLE`. It must never redirect the customer to an arbitrary default store.
* **Tracking Invariant**: Tracking APIs must **never** return a fabricated `"Partner Assigning"` mock DTO when a delivery session is missing. If dispatch failed, the system must return an explicit `AWAITING_DISPATCH` state and alert the operations dashboard.

### Invariant II: Zero Mock Tokens & Zero Test Auth in Production Builds
* **Rule**: Release binaries and production environments must contain **zero** demo login shortcuts (`jwt_demo_partner_token`), hardcoded phone numbers (`+919817916180`), or master OTP bypasses (`123456`, `000000`).
* **Enforcement**: Auth endpoints must strictly authenticate against PostgreSQL `customers`, `riders`, and `sellers` tables using cryptographic KDF (Scrypt) or cryptographically verified SMS OTP challenges.

### Invariant III: Absolute Concurrency & Deduplication Invariants
* **Rule**: No operation may race, corrupt data, or create duplicate side-effects under high concurrency.
* **Order Checkout**: Must use PostgreSQL `BEGIN ISOLATION LEVEL SERIALIZABLE` with `FOR UPDATE` inventory locking and request-level `Idempotency-Key` validation.
* **Mobile Auth Refresh**: Mobile token refresh interceptors must use double-checked locking: concurrent 401s must wait on the active refresh thread and re-read the updated session token instead of returning `null` and forcing false logouts.
* **Address Defaulting**: Customer address updates for `is_default = true` must execute inside a transaction protected by `pg_advisory_xact_lock(hashtext('addr_lock_' || customer_id))` and backed by a partial unique index.

### Invariant IV: Complete Route-Schema Contract Parity
* **Rule**: Database tables, columns, and foreign keys in `platform/schema.sql` must 100% match the queries executed by repository classes and frontend DTOs.
* **Enforcement**: Every schema alteration (`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`) must be documented in versioned migration files so existing production databases never crash with `undefined_column` or `relation does not exist` errors.

### Invariant V: Zero-Fee Direct Payment Standard
* **Rule**: In-app online payments must support direct, 0% fee peer-to-merchant UPI intent (`upi://pay`) without paying 1.5%–2% fees to aggregators like Razorpay.
* **Anti-Replay**: Every submitted bank UTR must be validated for format (min 6 characters) and checked against a database-level unique constraint (`idx_payments_upi_utr`) to eliminate replay/double-spending vulnerabilities.

### Invariant VI: Server-Authoritative State & Zero Client Trust
* **Rule**: Financial totals, delivery fees, discount calculations, delivery OTPs, and rider assignments are strictly computed by the server. Clients may never dictate pricing, delivery SLA minutes, or OTP hashes.

### Invariant VII: FCM Data-Only Push Architecture
* **Rule**: All push notifications sent to Rider or Customer apps must use data-only payloads (`data: { ... }`, zero top-level `notification` object). This allows background services to process payloads deterministically even when the application is killed or the device is in Doze mode.

### Invariant VIII: Strict Prohibition of Localhost in Server & Database Configurations
* **Rule**: We NEVER use `localhost`, `127.0.0.1`, or local synthetic loopbacks as the default server URL or database URL unless explicitly asked by the user for an isolated manual run.
* **Database**: All services must strictly connect to the authoritative remote cloud PostgreSQL database (`DATABASE_URL`). Zero local SQLite or local mock databases.
* **Server**: All mobile and web clients must connect to the authoritative remote backend API Gateway URL (`API_GATEWAY_URL` / `API_BASE_URL`). Real mobile devices must never be pointed at `localhost` / `127.0.0.1`, which refers to the phone itself and causes 30-second connection timeouts.

---

## 3. Comprehensive Hiccup Inventory Across Commerce OS

Below is the complete, exhaustive catalog of hiccups, edge cases, and hidden flakiness identified across the entire repository, categorized by domain and priority:

### Domain A: Client Apps (Customer Android, Customer iOS, Rider Android, Rider iOS)

| ID | Location | Hiccup Description | Operational Impact | Severity | Status / Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A-01** | `apps/android/app/.../ApiService.kt` | **Token Refresh Concurrency False Logout**: Sibling 401s while a refresh is in-flight immediately receive `null` from `refreshTokensBlocking()`, triggering `onSessionExpired()`. | Active users browsing products are abruptly kicked back to the login screen. | **P0** | Implement double-checked locking in OkHttp interceptor. |
| **A-02** | `apps/android/rider-app/.../RiderAuthScreen.kt` | **Instant Demo Access & Fake Token**: Lines 225-252 contain "Quick Partner Shift" button that injects `jwt_demo_partner_token`. Lines 377-393 allow typing `123456` on verification failure to inject `jwt_rider_access_token`. | Riders appear logged in locally, but all subsequent real backend API calls fail with 401 or contaminate dispatch queues. | **P0** | Remove demo shortcuts from release builds; enforce fail-closed verification. |
| **A-03** | `apps/ios/CommerceOSRider/.../RiderRootView.swift` & `RiderAPIClient.swift` | **Hardcoded Test Phone & Master OTP**: Default phone `9817916180` and default OTP `123456` hardcoded in UI state and static properties (`defaultRiderPhone`, `defaultRiderId`). | Leaks test credentials and obscures real authentication failures during testing. | **P1** | Strip hardcoded constants; require user input and real OTP verification. |
| **A-04** | `apps/ios/CommerceOS/.../CartScreen.swift` & `OrderRepository.swift` | **Non-Idempotent iOS Retries**: iOS generates a new `"ios_order_<UUID>"` for every submission attempt rather than reusing the attempt key across retries. | Network timeouts or double-taps can create duplicate orders and double-charge customers. | **P1** | Persist attempt key across retries until definitive success or user cancellation. |
| **A-05** | `apps/ios/CommerceOS/.../OrderTrackingScreen.swift` | **Missing `Last-Event-ID` on iOS SSE**: Reconnection creates fresh SSE streams without passing `Last-Event-ID`. | Riders' transient GPS coordinates and delivery status transitions during brief disconnections are permanently missed. | **P1** | Store last received event ID and pass in `Last-Event-ID` HTTP header on reconnect. |
| **A-06** | `apps/android/app/.../AndroidManifest.xml` | **Hardcoded Google Maps API Key**: `<meta-data android:name="com.google.android.geo.API_KEY" value="AIzaSy..." />` is hardcoded. | As soon as `applicationId` or signing keystore is changed for a new client, Google Maps breaks silently (blank map tiles). | **P0** | Inject Maps API Key from Gradle `manifestPlaceholders`. |

---

### Domain B: Backend Core & Realtime Engine (`production-server.js`)

| ID | Location | Hiccup Description | Operational Impact | Severity | Status / Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **B-01** | `production-server.js` (`/api/v1/orders`) | **Hardcoded Rewari Fallback**: Serviceability failures silently fall back to `STORE_REWARI_01` and Rewari coordinates instead of returning a hard rejection. | Customers located in Mumbai, Delhi, or Bangalore get assigned to a dark store hundreds of kilometers away. | **P0** | Fail closed with `HTTP 422 STORE_NOT_SERVICEABLE`. |
| **B-02** | `production-server.js` (`/api/v1/delivery/order/:id`) | **Synthetic Tracking DTO**: When `delivery_sessions` row is missing or delayed, tracking returns a fabricated `"Partner Assigning"` object. | Hides backend dispatch failures from administrators; customer sees fake route progress. | **P0** | Return authentic state `AWAITING_DISPATCH` without synthetic coordinates. |
| **B-03** | `production-server.js` (`/complete-cod`) | **COD Handoff Route Mismatch**: Android Rider sends `collectedAmount` to `/complete-cod` without OTP. Server previously demanded an OTP, causing collection failures. | Deliveries blocked at doorstep; rider app claimed local success while database ledger remained uncollected. | **P0** | **RESOLVED**: Separated `/complete-cod` into direct ledger reconciliation endpoint. |
| **B-04** | `production-server.js` (`/reverse-geocode`) | **Hardcoded Rewari Geocoding**: Reverse geocode endpoint returned `"Model Town, Rewari, 123401"` for any coordinates. | Auto-detected addresses nationwide were tagged with Haryana pin codes. | **P0** | **RESOLVED**: Replaced with live OSM Nominatim reverse geocode & coordinate fallback. |
| **B-05** | `production-server.js` (`/checkout-from-cart`) | **Forced COD Override**: Cart checkout hardcoded `paymentMethod = 'COD'`, ignoring customer's choice of `UPI`. | Prevented customers from paying online via direct UPI. | **P0** | **RESOLVED**: Dynamic `paymentMethod` passthrough enabled. |
| **B-06** | `production-server.js` (Telemetry Ingestion) | **In-Memory Debounce Sets**: `inFlightRouteCalculations` and `lastRerouteTimes` use Node.js process-local `Set` and `Map`. | Multi-instance clustering (e.g. 2 Render/AWS containers) results in duplicate concurrent OSRM calculations. | **P1** | Move calculation locks to Redis distributed locks (`SET NX EX`). |

---

### Domain C: PostgreSQL Schema & Data Integrity (`schema.sql`)

| ID | Location | Hiccup Description | Operational Impact | Severity | Status / Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **C-01** | `schema.sql` vs Repositories | **Missing Live Migrations**: Columns `declined_at`, `aadhaar_number`, and `store_id` were added in code without migration scripts on existing live DBs. | Rider offer decline and seller fleet views threw `undefined_column` 500 errors on existing databases. | **P0** | **RESOLVED**: Added startup `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` hooks. |
| **C-02** | `schema.sql` (`customer_addresses`) | **Missing Partial Unique Index for Default Address**: Multiple concurrent requests could mark multiple addresses as `is_default = true`. | Customer profile showed duplicate default shipping addresses. | **P0** | **RESOLVED**: Added `idx_customer_addresses_default` and advisory locking. |
| **C-03** | `schema.sql` (`payments`) | **Missing Anti-Replay Constraint on UPI UTR**: UTR reference numbers had no unique constraint. | Allowed malicious users to reuse the same bank transaction reference for multiple orders. | **P0** | **RESOLVED**: Added partial unique index `idx_payments_upi_utr`. |

---

## 4. Turnkey White-Label Playbook: Scaling to $N$ Deployments

When selling or handing off Commerce OS to a new client (e.g. **MedFast Pharmacy**, **QuickGrocery**, or **TechMart Electronics**), follow this exact standardized sequence:

```mermaid
flowchart TD
    subgraph Step 1: Branding & Identity
        A["1. Define Brand Tokens\n• App Name\n• Package Name (e.g. com.client.app)\n• Primary/Accent Hex Colors\n• Vector Icons & Splash Drawables"]
    end

    subgraph Step 2: Cloud & Credentials
        B["2. Google Cloud & Firebase Setup\n• Create Google Cloud Project\n• Enable Maps SDK & Places API\n• Bind API Key to [Package Name + SHA-1 Keystore]\n• Download google-services.json & GoogleService-Info.plist"]
    end

    subgraph Step 3: Infrastructure Isolation
        C["3. Deploy Cloud Infrastructure\n• Provision Dedicated PostgreSQL DB\n• Run schema.sql + seed_stores.sql\n• Deploy Node.js Platform Server\n• Configure Environment Variables"]
    end

    subgraph Step 4: Build & Release
        D["4. Compile Release Binaries\n• Sign Android APK with client-release.jks\n• Sign iOS IPA with Client Apple Team Profile\n• Verify with apksigner v2"]
    end

    A --> B --> C --> D
```

### The 7-Point Client Onboarding Runbook

#### 1. Package Name & Bundle ID Isolation
* Change Android `applicationId` in [`app/build.gradle.kts`](file:///Users/jigar/Desktop/new-project/commerce-os/apps/android/app/build.gradle.kts#L12) (Customer App) and [`rider-app/build.gradle.kts`](file:///Users/jigar/Desktop/new-project/commerce-os/apps/android/rider-app/build.gradle.kts#L11) (Rider App).
  * Customer: `com.<clientname>.customer`
  * Rider: `com.<clientname>.rider`
* Change iOS Bundle Identifiers in Xcode project configuration.
* *Invariant*: Never reuse package names across clients, or apps will conflict on user devices.

#### 2. Google Maps API Key Binding (The SHA-1 Rule)
> [!IMPORTANT]
> Google Maps will render as a blank grey screen unless registered correctly:
> 1. Extract the release keystore's SHA-1 fingerprint:
>    `keytool -list -v -keystore client-release.jks -alias client`
> 2. In Google Cloud Console ➔ Credentials ➔ Edit API Key:
>    Add Android app restriction with **Package Name** + **SHA-1 Fingerprint**.
> 3. Pass the key into `build.gradle.kts` via `manifestPlaceholders["MAPS_API_KEY"] = "AIzaSy..."`.

#### 3. Firebase Cloud Messaging (Push Notifications)
* Register both Android apps (`com.<clientname>.customer` and `com.<clientname>.rider`) in the client's Firebase console.
* Download `google-services.json` into `apps/android/app/` and `apps/android/rider-app/`.
* Download `GoogleService-Info.plist` into iOS app directories.
* Generate a Firebase Service Account Private Key JSON (`firebase-adminsdk.json`) and configure `FCM_SERVER_KEY` / credentials in the backend environment.

#### 4. Isolated Database Provisioning
* Provision a dedicated PostgreSQL instance (e.g. Supabase, AWS RDS, or Railway).
* Run [`platform/schema.sql`](file:///Users/jigar/Desktop/new-project/commerce-os/platform/schema.sql) to create all 22 authoritative tables, constraints, and partial indexes.
* Seed the client's dark stores/warehouses:
  * Store IDs, operational coordinates `(latitude, longitude)`, service radius (e.g. 5km), and merchant account linkage.

#### 5. Backend Server Environment Configuration
Each deployed instance requires its own `.env` file with zero shared secrets:
```bash
PORT=8080
DATABASE_URL=postgres://user:pass@host:5432/client_db
OSRM_BASE_URL=https://router.project-osrm.org
JWT_SECRET=super_secret_unique_to_client_hs256
JWT_ISSUER=commerceos-auth
JWT_AUDIENCE=commerceos-clients
COMMERCEOS_OTP_PEPPER=random_hex_pepper_64chars
FCM_SERVER_KEY=client_firebase_server_key
FCM_ENDPOINT_URL=https://fcm.googleapis.com/fcm/send
MERCHANT_UPI_VPA=clientstore@okaxis
MERCHANT_UPI_NAME="Client Store Name"
DEFAULT_STORE_ID=store_main_hub_01
MULTI_STORE_ENABLED=true
```

#### 6. Web & Admin Portal Deployment
* Deploy Next.js Web Storefront (`apps/web`), Seller Dashboard (`apps/seller`), and Admin Dashboard (`apps/admin`) to Vercel/Render.
* Set environment variable: `NEXT_PUBLIC_API_URL=https://api.clientdomain.com`.
* Update logo assets and favicon in `public/`.

#### 7. Release Signing & Verification Gate
* Build release binaries:
  ```bash
  cd apps/android
  ./gradlew assembleRelease
  ```
* Verify signature using Android SDK:
  ```bash
  apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
  ```
* Run the authoritative test suite before handover:
  ```bash
  node platform/test-production-static-guards.test.js
  node platform/test-route-inventory-sync.test.js
  node platform/test-concurrency-and-dedup.js
  node platform/test-upi-payment-and-address-concurrency.test.js
  ```

---

## 5. Ongoing Quality & Maintenance Guidelines

1. **Commit Rule**: Never commit `.env` files, keystore passwords, or hardcoded API tokens to version control.
2. **Schema Rule**: Never modify a table definition in `schema.sql` without also creating a backwards-compatible `ALTER TABLE` statement in production initialization scripts.
3. **Guard Rule**: The 102 static assertion guards in `platform/test-production-static-guards.test.js` are absolute. A build that fails a single guard is considered broken and must not be released.
