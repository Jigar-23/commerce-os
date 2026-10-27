# Commerce OS Rules & Guidelines

See [WORKING_PRINCIPLES.md](file:///Users/jigar/Desktop/new-project/commerce-os/WORKING_PRINCIPLES.md), [RULEBOOK.md](file:///Users/jigar/Desktop/new-project/commerce-os/RULEBOOK.md), and [AGENTS.md](file:///Users/jigar/Desktop/new-project/commerce-os/AGENTS.md) for the complete engineering rulebook.

## Supreme Invariant: Turnkey Universal White-Label System
* Always check [WORKING_PRINCIPLES.md](file:///Users/jigar/Desktop/new-project/commerce-os/WORKING_PRINCIPLES.md) before making ANY change.
* The codebase is a single universal commerce engine. Clients and verticals (pharmacy, grocery, electronics) are spun up with **minimal configuration changes** (`DATABASE_URL`, server URL, package names, branding, and feature flags).
* Never hardcode store IDs, client names, or vertical assumptions into common logic.
* **NEVER CHANGE THE DATABASE (`DATABASE_URL`)**: The database is authoritative and immutable. In case of failure, fail closed and wait for the USER to explicitly tell you which database to fallback to.
* **DATABASE 24-HR KEEP-ALIVE**: Ensure the automated keep-alive heartbeat runs to keep cloud databases active.

## Mandatory Rule: Cross-Platform Parity (iOS ⟷ Android)
* Any feature or modification added to iOS (`apps/ios/CommerceOS` or `apps/ios/CommerceOSRider`) must also be implemented in the corresponding Android app (`apps/android/app` or `apps/android/rider-app`), and vice-versa.
* Maintain shared backend contracts, identical data models, and secure hardware storage (Keychain on iOS, EncryptedSharedPreferences on Android).
