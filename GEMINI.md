# Commerce OS Rules & Guidelines

See [RULEBOOK.md](file:///Users/jigar/Desktop/new-project/commerce-os/RULEBOOK.md) and [AGENTS.md](file:///Users/jigar/Desktop/new-project/commerce-os/AGENTS.md) for the complete engineering rulebook.

## Mandatory Rule: Cross-Platform Parity (iOS ⟷ Android)
* Any feature or modification added to iOS (`apps/ios/CommerceOS` or `apps/ios/CommerceOSRider`) must also be implemented in the corresponding Android app (`apps/android/app` or `apps/android/rider-app`), and vice-versa.
* Maintain shared backend contracts, identical data models, and secure hardware storage (Keychain on iOS, EncryptedSharedPreferences on Android).
