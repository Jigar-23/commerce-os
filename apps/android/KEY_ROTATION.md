# Android Release Signing Key Rotation Notice

## Security Incident & Invalidation Notice
- **Historically Exposed Keystore**: `commerceos-release.jks` (alias `commerceos`)
- **Exposed Certificate SHA-256 Fingerprint**: `08:5D:02:68:C1:84:C1:F0:F9:63:21:9B:37:EF:FD:C2:EC:CE:D8:71:7B:40:27:73:0A:79:47:5B:14:81:ED:D6`
- **Exposed Certificate SHA-1 Fingerprint**: `15:49:09:BC:61:4E:0A:B9:1E:CD:95:62:BC:0B:03:17:74:71:F8:C8`
- **Exposed Certificate Serial Number**: `0xef44f4c102beeae`
- **Status**: **PERMANENTLY REVOKED AND INVALIDATED FOR PUBLISHING**

## Enforcement Policy
1. The exposed keystore binary was purged from active tracking and added to `.gitignore`.
2. Both `:app:build.gradle.kts` and `:rider-app:build.gradle.kts` enforce automated cryptographic validation:
   - Release signing credentials (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) must be supplied via secure environment variables.
   - Any keystore certificate matching the revoked SHA-256 fingerprint (`08:5D:02:68:...`) is strictly rejected at Gradle configuration time with a fatal `REVOKED_SIGNING_KEY_REJECTED` exception.
3. Production releases must use freshly provisioned upload keys managed through Google Play App Signing with hardware HSM isolation.
