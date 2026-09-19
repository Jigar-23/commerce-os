# Commerce OS Agent Instructions & Rules

All AI agents, coding assistants, and contributors operating within this repository must strictly adhere to the rules defined below and in [RULEBOOK.md](file:///Users/jigar/Desktop/new-project/commerce-os/RULEBOOK.md).

---

## CRITICAL RULE 1: Mandatory Cross-Platform Feature Parity (iOS ⟷ Android)

Whenever any feature, UI change, workflow, business logic, or bug fix is implemented for an iOS app, it **MUST** also be coded for the corresponding Android app (and vice-versa). No platform is permitted to drift behind.

### App Mappings:
1. **Customer Mobile Application:**
   * **iOS:** `apps/ios/CommerceOS` (Swift / SwiftUI)
   * **Android:** `apps/android/app` (Kotlin / Jetpack Compose)
2. **Rider / Delivery Partner Application:**
   * **iOS:** `apps/ios/CommerceOSRider` (Swift / SwiftUI)
   * **Android:** `apps/android/rider-app` (Kotlin / Jetpack Compose)

### Enforcement Protocol:
* When coding a task like *"Add live order tracking map"* or *"Implement OTP doorstep verification"*, inspect both the iOS and Android codebases.
* If a new feature or endpoint call is added to `apps/ios/CommerceOS`, code the equivalent in `apps/android/app` in the same work session.
* Do not mark a mobile feature complete until both iOS and Android codebases reflect the feature.

---

## RULE 2: Shared API Contracts & Data Schema Alignment
* Both iOS and Android must communicate with the exact same backend endpoints defined in `platform/server` and `services/*`.
* Data models, field names, serialization strategies, and error handling must match exactly across Swift (`Codable`) and Kotlin (`kotlinx.serialization` or `Gson`).
* Do not introduce client-specific custom routes unless strictly necessary for platform push notification registration (APNs vs FCM).

---

## RULE 3: Secure Hardware-Backed Storage Parity
* JWT tokens, refresh tokens, and sensitive auth data must always be stored in:
  * **iOS:** iOS Keychain (`RiderKeychainHelper`, `KeychainStore`).
  * **Android:** `EncryptedSharedPreferences` or Android Keystore.
* Never store auth tokens in unencrypted `UserDefaults` or standard `SharedPreferences`.

---

## RULE 4: Design System & UX Standards
* Maintain identical visual hierarchy, color palette, status tokens, and UX states (Loading, Empty, Error, Pull-to-Refresh) across SwiftUI and Jetpack Compose.
* Keep platform-specific idioms intact (SwiftUI navigation & state management vs. Jetpack Compose & StateFlow).

---

## RULE 5: Verification & Build Validation
Before concluding any mobile coding task, verify both targets:
1. **iOS Target Verification:**
   ```bash
   node apps/ios/verify-all-ios-targets.js
   ```
2. **Android Target Verification:**
   ```bash
   cd apps/android && ./gradlew assembleDebug
   ```
3. **API Contract Audit:**
   ```bash
   node apps/android/test-android-contract-audit.js
   ```
