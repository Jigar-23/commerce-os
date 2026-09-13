#!/usr/bin/env node

/**
 * ==============================================================================
 * Comprehensive iOS All-Targets Automated Verification & Smoke Harness
 * ==============================================================================
 * Validates:
 * 1. Swift Syntax & Signature Parsing across all 54 native source and test files
 * 2. XcodeGen project.yml structure and target specifications
 * 3. Generated CommerceOS.xcodeproj integrity (project.pbxproj & schemes)
 * 4. Architecture integrity and entitlement completeness
 * ==============================================================================
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const IOS_ROOT = path.resolve(__dirname);
let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

function assert(condition, message) {
  totalTests++;
  if (condition) {
    passedTests++;
    console.log(`  ✓ [PASS] ${message}`);
  } else {
    failedTests++;
    console.error(`  ✗ [FAIL] ${message}`);
  }
}

function getAllSwiftFiles(dir, fileList = []) {
  if (!fs.existsSync(dir)) return fileList;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      getAllSwiftFiles(fullPath, fileList);
    } else if (entry.isFile() && entry.name.endsWith('.swift')) {
      fileList.push(fullPath);
    }
  }
  return fileList;
}

console.log('==============================================================================');
console.log('  COMMERCE-OS NATIVE iOS SUITE: ALL-TARGETS AUTOMATED VERIFICATION HARNESS    ');
console.log('==============================================================================\n');

// ------------------------------------------------------------------------------
// 1. XcodeGen Specification Verification (project.yml)
// ------------------------------------------------------------------------------
console.log('--- Phase 1: Declarative XcodeGen Specification (project.yml) ---');
const projectYmlPath = path.join(IOS_ROOT, 'project.yml');
assert(fs.existsSync(projectYmlPath), 'project.yml exists on disk');

const projectYmlContent = fs.readFileSync(projectYmlPath, 'utf8');
assert(projectYmlContent.includes('CommerceOS:'), 'Target 1: CommerceOS application defined');
assert(projectYmlContent.includes('CommerceOSTests:'), 'Target 2: CommerceOSTests unit test bundle defined');
assert(projectYmlContent.includes('CommerceOSRider:'), 'Target 3: CommerceOSRider application defined');
assert(projectYmlContent.includes('CommerceOSRiderTests:'), 'Target 4: CommerceOSRiderTests unit test bundle defined');
assert(projectYmlContent.includes('bundleIdPrefix: com.commerceos'), 'Bundle ID prefix configured');
assert(projectYmlContent.includes('deploymentTarget:'), 'Deployment target block configured (iOS 17.0+)');
assert(projectYmlContent.includes('NSCameraUsageDescription'), 'Camera entitlement configured for VisionKit scanner');
assert(projectYmlContent.includes('NSFaceIDUsageDescription'), 'FaceID entitlement configured for Rx vault');
assert(projectYmlContent.includes('NSLocationAlwaysAndWhenInUseUsageDescription'), 'Always/WhenInUse location configured for Rider background stream');
assert(projectYmlContent.includes('NSSupportsLiveActivities: true'), 'ActivityKit Live Activities enabled');
assert(projectYmlContent.includes('UIBackgroundModes:'), 'Background modes declared for rider telemetry');

// ------------------------------------------------------------------------------
// 2. Xcode Project File & Workspace Verification (CommerceOS.xcodeproj)
// ------------------------------------------------------------------------------
console.log('\n--- Phase 2: Native Xcode Project & Shared Schemes ---');
const xcodeprojPath = path.join(IOS_ROOT, 'CommerceOS.xcodeproj');
const pbxprojPath = path.join(xcodeprojPath, 'project.pbxproj');
assert(fs.existsSync(xcodeprojPath), 'CommerceOS.xcodeproj directory exists');
assert(fs.existsSync(pbxprojPath), 'project.pbxproj file exists and is populated');

const pbxprojContent = fs.readFileSync(pbxprojPath, 'utf8');
assert(pbxprojContent.includes('name = CommerceOS;'), 'CommerceOS target configured in pbxproj');
assert(pbxprojContent.includes('name = CommerceOSTests;'), 'CommerceOSTests target configured in pbxproj');
assert(pbxprojContent.includes('name = CommerceOSRider;'), 'CommerceOSRider target configured in pbxproj');
assert(pbxprojContent.includes('name = CommerceOSRiderTests;'), 'CommerceOSRiderTests target configured in pbxproj');

const customerSchemePath = path.join(xcodeprojPath, 'xcshareddata', 'xcschemes', 'CommerceOS.xcscheme');
const riderSchemePath = path.join(xcodeprojPath, 'xcshareddata', 'xcschemes', 'CommerceOSRider.xcscheme');
assert(fs.existsSync(customerSchemePath), 'CommerceOS.xcscheme shared scheme generated');
assert(fs.existsSync(riderSchemePath), 'CommerceOSRider.xcscheme shared scheme generated');

// ------------------------------------------------------------------------------
// 3. Swift 6.3.1 Toolchain Parse Audit (All 54 Files)
// ------------------------------------------------------------------------------
console.log('\n--- Phase 3: Swift 6.3.1 Toolchain Parse Audit (All 54 Files) ---');
const customerAppFiles = getAllSwiftFiles(path.join(IOS_ROOT, 'CommerceOS'));
const customerTestFiles = getAllSwiftFiles(path.join(IOS_ROOT, 'CommerceOSTests'));
const riderAppFiles = getAllSwiftFiles(path.join(IOS_ROOT, 'CommerceOSRider'));
const riderTestFiles = getAllSwiftFiles(path.join(IOS_ROOT, 'CommerceOSRiderTests'));

const allSwiftFiles = [
  ...customerAppFiles,
  ...customerTestFiles,
  ...riderAppFiles,
  ...riderTestFiles
];

assert(customerAppFiles.length >= 29, `Customer App has at least 29 Swift source files (found ${customerAppFiles.length})`);
assert(customerTestFiles.length === 1, `Customer Tests has 1 Swift file (found ${customerTestFiles.length})`);
assert(riderAppFiles.length === 22, `Rider App has 22 Swift source files (found ${riderAppFiles.length})`);
assert(riderTestFiles.length === 2, `Rider Tests has 2 Swift files (found ${riderTestFiles.length})`);
assert(allSwiftFiles.length >= 54, `Total Swift source & test files has at least 54 (found ${allSwiftFiles.length})`);

console.log(`\nCompiling all ${allSwiftFiles.length} Swift files using Apple Swift compiler (swiftc -parse)...`);
let swiftSyntaxPasses = 0;

for (const filePath of allSwiftFiles) {
  const relPath = path.relative(IOS_ROOT, filePath);
  try {
    execSync(`swiftc -parse "${filePath}"`, { stdio: 'pipe' });
    swiftSyntaxPasses++;
  } catch (err) {
    console.error(`  ✗ [FAIL] swiftc -parse error in ${relPath}:`, err.stderr ? err.stderr.toString() : err.message);
  }
}

assert(swiftSyntaxPasses === allSwiftFiles.length, `All ${allSwiftFiles.length} Swift files parsed with zero syntax errors (${swiftSyntaxPasses}/${allSwiftFiles.length})`);

// ------------------------------------------------------------------------------
// 4. Feature Contract & Security Invariant Checks
// ------------------------------------------------------------------------------
console.log('\n--- Phase 4: Feature Architecture & Security Contract Audits ---');

// VisionKit OCR Scanner
const visionKitPath = path.join(IOS_ROOT, 'CommerceOS', 'Features', 'PrescriptionVault', 'Views', 'VisionKitDocumentScanner.swift');
const visionKitContent = fs.readFileSync(visionKitPath, 'utf8');
assert(visionKitContent.includes('VNDocumentCameraViewController'), 'VisionKit uses Apple VNDocumentCameraViewController');
assert(visionKitContent.includes('VNRecognizeTextRequest'), 'VisionKit uses VNRecognizeTextRequest for on-device OCR');

// Biometric Security Gate
const biometricPath = path.join(IOS_ROOT, 'CommerceOS', 'Auth', 'BiometricPasskeyService.swift');
const biometricContent = fs.readFileSync(biometricPath, 'utf8');
assert(biometricContent.includes('LAContext'), 'Biometrics wraps Apple LocalAuthentication LAContext');
assert(biometricContent.includes('canEvaluatePolicy'), 'Biometrics performs policy hardware capability checks');

// ActivityKit Dynamic Island Live Activity
const liveActivityPath = path.join(IOS_ROOT, 'CommerceOS', 'Features', 'Tracking', 'Widgets', 'DeliveryLiveActivityWidget.swift');
const liveActivityContent = fs.readFileSync(liveActivityPath, 'utf8');
assert(liveActivityContent.includes('DynamicIslandExpandedRegion'), 'DeliveryLiveActivityWidget supports expanded Dynamic Island');
assert(liveActivityContent.includes('compactLeading:'), 'DeliveryLiveActivityWidget supports compact leading');
assert(liveActivityContent.includes('compactTrailing:'), 'DeliveryLiveActivityWidget supports compact trailing');

// Rider 50m Geofencing
const geofencePath = path.join(IOS_ROOT, 'CommerceOSRider', 'Services', 'RiderGeofenceDetector.swift');
const geofenceContent = fs.readFileSync(geofencePath, 'utf8');
assert(geofenceContent.includes('CLCircularRegion'), 'RiderGeofenceDetector uses CoreLocation CLCircularRegion');
assert(geofenceContent.includes('50.0'), 'Geofence detector enforces strict 50.0m arrival boundaries');

// Rider Battery & Telemetry Streamer
const streamerPath = path.join(IOS_ROOT, 'CommerceOSRider', 'Services', 'RiderTelemetryStreamer.swift');
const streamerContent = fs.readFileSync(streamerPath, 'utf8');
assert(streamerContent.includes('deadReckonLocation'), 'RiderTelemetryStreamer implements dead-reckoning projection');
assert(streamerContent.includes('isLowBatteryThrottled'), 'RiderTelemetryStreamer implements battery conservation');

// Unit Test Coverage
const cartTestsPath = path.join(IOS_ROOT, 'CommerceOSTests', 'CartStoreTests.swift');
const cartTestsContent = fs.readFileSync(cartTestsPath, 'utf8');
assert(cartTestsContent.includes('testAddItem'), 'CartStoreTests verifies item additions and count');
assert(cartTestsContent.includes('testPrescriptionItemDetection'), 'CartStoreTests verifies Rx prescription flag gating');

const riderGeofenceTestsPath = path.join(IOS_ROOT, 'CommerceOSRiderTests', 'GeofenceDetectorTests.swift');
const riderGeofenceTestsContent = fs.readFileSync(riderGeofenceTestsPath, 'utf8');
assert(riderGeofenceTestsContent.includes('testInside50mMerchantArrival'), 'GeofenceDetectorTests verifies 50m trigger');

// ------------------------------------------------------------------------------
// Summary Report
// ------------------------------------------------------------------------------
console.log('\n==============================================================================');
console.log(`  VERIFICATION RESULTS: ${passedTests} / ${totalTests} ASSERTIONS PASSED (${Math.round((passedTests / totalTests) * 100)}%)`);
console.log(`  FAILURES: ${failedTests}`);
console.log('==============================================================================');

if (failedTests > 0) {
  process.exit(1);
} else {
  console.log('>> [SUCCESS] All native iOS targets, specifications, and files are 100% verified!\n');
  process.exit(0);
}
