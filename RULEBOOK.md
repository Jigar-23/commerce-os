# Commerce OS Engineering Rulebook

This rulebook establishes mandatory engineering standards and architectural rules for all contributors (human developers and AI agents) working within the `commerce-os` codebase.

---

## Rule 1: Cross-Platform Feature Parity (iOS ⟷ Android)

> **Mandatory Rule:** Any feature, enhancement, screen, flow, or bugfix implemented for one mobile platform MUST also be coded and ported to the other platform.

### 1.1 Pairings
* **Customer Mobile App:**
  * iOS: `apps/ios/CommerceOS` (Swift / SwiftUI)
  * Android: `apps/android/app` (Kotlin / Jetpack Compose)
* **Rider Mobile App:**
  * iOS: `apps/ios/CommerceOSRider` (Swift / SwiftUI)
  * Android: `apps/android/rider-app` (Kotlin / Jetpack Compose)

### 1.2 Zero Platform Drift
* **No Half-Baked Releases:** Never implement a new feature in iOS and leave Android behind, or vice versa.
* If a new UI component, checkout step, live tracking map, or notification handler is added in `apps/ios/CommerceOS`, the equivalent screen, ViewModel, and service must be implemented in `apps/android/app`.
* If a delivery confirmation or order batching logic is updated in `apps/ios/CommerceOSRider`, the exact same behavior must be coded in `apps/android/rider-app`.

---

## Rule 2: Shared API Contracts & Data Schema Alignment

### 2.1 Backend Contract Parity
* Both iOS and Android apps must communicate with the exact same backend endpoints (`platform/server`, `services/*`).
* Do not create platform-specific endpoints unless strictly dictated by push notification services (APNs vs. FCM).
* Request bodies, query parameters, response JSON schemas, and HTTP status code handling must match between Swift and Kotlin networking layers.

### 2.2 Shared Types & Models
* When adding or updating a data model (e.g. `Order`, `Batch`, `RiderLocation`, `Product`), both platforms' model structs/data classes must have identical fields and serialization rules (`Codable` in Swift, `kotlinx.serialization` or `Gson` in Kotlin).

---

## Rule 3: Security & Session Persistence Parity

### 3.1 Hardware-Backed Secure Storage
* Sensitive credentials (JWT tokens, refresh tokens, auth keys) must use secure hardware-backed storage on both platforms:
  * **iOS:** iOS Keychain (`KeychainStore` / `RiderKeychainHelper`).
  * **Android:** `EncryptedSharedPreferences` / Android Keystore.
* Never store plain authentication tokens in `UserDefaults` or standard `SharedPreferences`.

### 3.2 Identical Auth Lifecycles
* Session durations, OTP challenge timeouts (10 minutes), refresh policies, and logout triggers must behave identically across iOS and Android.

---

## Rule 4: Design System & UX Consistency

### 4.1 Visual Consistency
* Both platforms must share the same brand design system:
  * Colors, typography scales, spacing, border radii, and elevation.
  * Status badges, order status colors (`PENDING`, `ACCEPTED`, `PICKED_UP`, `DELIVERED`).
* Standardized UX states: Both platforms must have matching Loading skeletons, Error dialogs, Empty states, and Pull-to-Refresh behaviors.

### 4.2 Platform-Idiomatic Implementation
* While the visual look and behavior must match, implementation should follow platform idioms:
  * **iOS:** Modern SwiftUI, SF Symbols, standard navigation stacks, ViewModels (`@Observable` or `ObservableObject`).
  * **Android:** Modern Jetpack Compose, Material 3 icons/components adapted to brand style, Compose ViewModels with `StateFlow`.

---

## Rule 5: Verification & Build Standards

### 5.1 Verification Checklist Before Merging
Before completing any mobile feature task:
1. **iOS Build Check:** Run target verification or `xcodebuild` for both `CommerceOS` and `CommerceOSRider`.
   ```bash
   node apps/ios/verify-all-ios-targets.js
   ```
2. **Android Build Check:** Compile both Android modules to verify clean builds without syntax or contract regressions:
   ```bash
   cd apps/android && ./gradlew assembleDebug
   ```
3. **Contract Audit:** Run contract audit scripts to ensure all API routes match:
   ```bash
   node apps/android/test-android-contract-audit.js
   ```

---

## Rule 6: Monorepo Cleanliness & Code Integrity

* **Preserve Documentation & Comments:** Do not delete existing architectural notes, comments, or docstrings unless explicitly refactoring that specific subsystem.
* **No Phantom Dependencies:** Do not add external native libraries/pods/Gradle plugins without verifying compatibility across both platforms.
* **Environment Integrity:** Ensure `.env` configurations and API base URLs remain consistent across local, staging, and production environments.
