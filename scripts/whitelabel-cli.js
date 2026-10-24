#!/usr/bin/env node

/**
 * Commerce OS — Turnkey White-Label Engine CLI
 * 
 * Automates 100% of white-label configuration and rebranding across:
 * - Android Customer & Rider Apps (applicationId, app label, Maps API key, Base URL)
 * - iOS Customer & Rider Targets in project.yml (Bundle IDs, Display Names)
 * - Cloud & Backend Server Environment (.env.production, secrets, UPI VPA)
 * - Credential & Keystore Integrity Audits (google-services.json, release.jks)
 */

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');

// Helper to resolve workspace paths
function getPath(...parts) {
  return path.join(ROOT_DIR, ...parts);
}

// Colorized console output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
  magenta: '\x1b[35m'
};

function log(msg, color = colors.reset) {
  console.log(`${color}${msg}${colors.reset}`);
}

function logStep(step, title) {
  console.log(`\n${colors.cyan}[Step ${step}]${colors.reset} ${colors.bright}${title}${colors.reset}`);
}

function logSuccess(msg) {
  console.log(`  ${colors.green}✔ ${msg}${colors.reset}`);
}

function logWarning(msg) {
  console.log(`  ${colors.yellow}⚠ ${msg}${colors.reset}`);
}

function logError(msg) {
  console.log(`  ${colors.red}✖ ${msg}${colors.reset}`);
}

// -----------------------------------------------------------------------------
// Config Validation Schema
// -----------------------------------------------------------------------------
function validateConfig(config) {
  const errors = [];

  if (!config.clientId || typeof config.clientId !== 'string') {
    errors.push('Missing or invalid "clientId" (alphanumeric string with hyphens, e.g. "freshcart-grocery")');
  }
  if (!config.clientName || typeof config.clientName !== 'string') {
    errors.push('Missing or invalid "clientName"');
  }
  if (!config.customerApp || typeof config.customerApp !== 'object') {
    errors.push('Missing "customerApp" object');
  } else {
    if (!config.customerApp.name) errors.push('Missing "customerApp.name"');
    if (!config.customerApp.androidPackage || !config.customerApp.androidPackage.includes('.')) {
      errors.push('Invalid "customerApp.androidPackage" (must be in format com.client.customer)');
    }
    if (!config.customerApp.iosBundleId || !config.customerApp.iosBundleId.includes('.')) {
      errors.push('Invalid "customerApp.iosBundleId" (must be in format com.client.customer)');
    }
  }

  if (!config.riderApp || typeof config.riderApp !== 'object') {
    errors.push('Missing "riderApp" object');
  } else {
    if (!config.riderApp.name) errors.push('Missing "riderApp.name"');
    if (!config.riderApp.androidPackage || !config.riderApp.androidPackage.includes('.')) {
      errors.push('Invalid "riderApp.androidPackage" (must be in format com.client.rider)');
    }
    if (!config.riderApp.iosBundleId || !config.riderApp.iosBundleId.includes('.')) {
      errors.push('Invalid "riderApp.iosBundleId" (must be in format com.client.rider)');
    }
  }

  if (!config.api || !config.api.baseUrl) {
    errors.push('Missing "api.baseUrl"');
  }

  if (!config.credentials) {
    errors.push('Missing "credentials" object');
  } else {
    if (!config.credentials.mapsApiKey) errors.push('Missing "credentials.mapsApiKey"');
    if (!config.credentials.jwtSecret || config.credentials.jwtSecret.length < 32) {
      errors.push('Invalid "credentials.jwtSecret" (must be at least 32 characters)');
    }
  }

  if (!config.merchant || !config.merchant.upiVpa || !config.merchant.upiVpa.includes('@')) {
    errors.push('Invalid "merchant.upiVpa" (must be a valid VPA, e.g. storename@bank)');
  }

  if (!config.database || !config.database.databaseUrl) {
    errors.push('Missing "database.databaseUrl"');
  }

  return errors;
}

// -----------------------------------------------------------------------------
// 1. Android Transformation Engine
// -----------------------------------------------------------------------------
function configureAndroid(config) {
  logStep(1, 'Configuring Android Modules (:app and :rider-app)');

  // 1.1 Customer App: build.gradle.kts
  const appGradlePath = getPath('apps/android/app/build.gradle.kts');
  if (fs.existsSync(appGradlePath)) {
    let appGradle = fs.readFileSync(appGradlePath, 'utf8');

    // Replace applicationId
    appGradle = appGradle.replace(
      /applicationId\s*=\s*"[^"]+"/,
      `applicationId = "${config.customerApp.androidPackage}"`
    );

    // Replace versionCode & versionName if provided
    if (config.customerApp.versionCode) {
      appGradle = appGradle.replace(
        /versionCode\s*=\s*\d+/,
        `versionCode = ${config.customerApp.versionCode}`
      );
    }
    if (config.customerApp.versionName) {
      appGradle = appGradle.replace(
        /versionName\s*=\s*"[^"]+"/,
        `versionName = "${config.customerApp.versionName}"`
      );
    }

    // Replace default fallback API URL & Maps Key
    appGradle = appGradle.replace(
      /val\s+defaultDevUrl\s*=\s*System\.getenv\("COMMERCEOS_DEV_API_URL"\)\s*\?:[^\n]+/,
      `val defaultDevUrl = System.getenv("COMMERCEOS_DEV_API_URL") ?: "${config.api.baseUrl}"`
    );
    appGradle = appGradle.replace(
      /val\s+mapsApiKey\s*=\s*System\.getenv\("GOOGLE_MAPS_API_KEY"\)\s*\?:[^\n]+/,
      `val mapsApiKey = System.getenv("GOOGLE_MAPS_API_KEY") ?: "${config.credentials.mapsApiKey}"`
    );

    fs.writeFileSync(appGradlePath, appGradle, 'utf8');
    logSuccess(`Updated :app/build.gradle.kts (applicationId: ${config.customerApp.androidPackage})`);
  }

  // 1.2 Customer App: AndroidManifest.xml
  const appManifestPath = getPath('apps/android/app/src/main/AndroidManifest.xml');
  if (fs.existsSync(appManifestPath)) {
    let manifest = fs.readFileSync(appManifestPath, 'utf8');
    manifest = manifest.replace(
      /android:label="[^"]*"/g,
      `android:label="${config.customerApp.name}"`
    );
    fs.writeFileSync(appManifestPath, manifest, 'utf8');
    logSuccess(`Updated :app/AndroidManifest.xml (label: "${config.customerApp.name}")`);
  }

  // 1.3 Rider App: build.gradle.kts
  const riderGradlePath = getPath('apps/android/rider-app/build.gradle.kts');
  if (fs.existsSync(riderGradlePath)) {
    let riderGradle = fs.readFileSync(riderGradlePath, 'utf8');

    riderGradle = riderGradle.replace(
      /applicationId\s*=\s*"[^"]+"/,
      `applicationId = "${config.riderApp.androidPackage}"`
    );

    if (config.riderApp.versionCode) {
      riderGradle = riderGradle.replace(
        /versionCode\s*=\s*\d+/,
        `versionCode = ${config.riderApp.versionCode}`
      );
    }
    if (config.riderApp.versionName) {
      riderGradle = riderGradle.replace(
        /versionName\s*=\s*"[^"]+"/,
        `versionName = "${config.riderApp.versionName}"`
      );
    }

    riderGradle = riderGradle.replace(
      /val\s+defaultDevUrl\s*=\s*System\.getenv\("COMMERCEOS_DEV_API_URL"\)\s*\?:[^\n]+/,
      `val defaultDevUrl = System.getenv("COMMERCEOS_DEV_API_URL") ?: "${config.api.baseUrl}"`
    );
    riderGradle = riderGradle.replace(
      /val\s+mapsApiKey\s*=\s*System\.getenv\("GOOGLE_MAPS_API_KEY"\)\s*\?:[^\n]+/,
      `val mapsApiKey = System.getenv("GOOGLE_MAPS_API_KEY") ?: "${config.credentials.mapsApiKey}"`
    );

    fs.writeFileSync(riderGradlePath, riderGradle, 'utf8');
    logSuccess(`Updated :rider-app/build.gradle.kts (applicationId: ${config.riderApp.androidPackage})`);
  }

  // 1.4 Rider App: AndroidManifest.xml
  const riderManifestPath = getPath('apps/android/rider-app/src/main/AndroidManifest.xml');
  if (fs.existsSync(riderManifestPath)) {
    let manifest = fs.readFileSync(riderManifestPath, 'utf8');
    manifest = manifest.replace(
      /android:label="[^"]*"/g,
      `android:label="${config.riderApp.name}"`
    );
    fs.writeFileSync(riderManifestPath, manifest, 'utf8');
    logSuccess(`Updated :rider-app/AndroidManifest.xml (label: "${config.riderApp.name}")`);
  }
}

// -----------------------------------------------------------------------------
// 2. iOS Transformation Engine (project.yml)
// -----------------------------------------------------------------------------
function configureIOS(config) {
  logStep(2, 'Configuring Native iOS Project Targets (project.yml)');

  const projectYmlPath = getPath('apps/ios/project.yml');
  if (!fs.existsSync(projectYmlPath)) {
    logWarning('apps/ios/project.yml not found, skipping iOS XcodeGen update');
    return;
  }

  let yml = fs.readFileSync(projectYmlPath, 'utf8');

  // Customer Target
  // Replace PRODUCT_BUNDLE_IDENTIFIER in CommerceOS target
  const custBundleRegex = /(targets:\s*[\s\S]*?CommerceOS:\s*[\s\S]*?base:\s*[\s\S]*?PRODUCT_BUNDLE_IDENTIFIER:\s*)[^\n]+/;
  yml = yml.replace(custBundleRegex, `$1${config.customerApp.iosBundleId}`);

  // Replace CFBundleDisplayName in CommerceOS target
  const custNameRegex = /(targets:\s*[\s\S]*?CommerceOS:\s*[\s\S]*?properties:\s*[\s\S]*?CFBundleDisplayName:\s*)"[^"]*"/;
  yml = yml.replace(custNameRegex, `$1"${config.customerApp.name}"`);

  // Replace CFBundleIdentifier in CommerceOS target
  const custIdRegex = /(targets:\s*[\s\S]*?CommerceOS:\s*[\s\S]*?properties:\s*[\s\S]*?CFBundleIdentifier:\s*)"[^"]*"/;
  yml = yml.replace(custIdRegex, `$1"${config.customerApp.iosBundleId}"`);

  // Rider Target
  // Replace PRODUCT_BUNDLE_IDENTIFIER in CommerceOSRider target
  const riderBundleRegex = /(CommerceOSRider:\s*[\s\S]*?base:\s*[\s\S]*?PRODUCT_BUNDLE_IDENTIFIER:\s*)[^\n]+/;
  yml = yml.replace(riderBundleRegex, `$1${config.riderApp.iosBundleId}`);

  // Replace CFBundleDisplayName in CommerceOSRider target
  const riderNameRegex = /(CommerceOSRider:\s*[\s\S]*?properties:\s*[\s\S]*?CFBundleDisplayName:\s*)"[^"]*"/;
  yml = yml.replace(riderNameRegex, `$1"${config.riderApp.name}"`);

  // Replace CFBundleIdentifier in CommerceOSRider target
  const riderIdRegex = /(CommerceOSRider:\s*[\s\S]*?properties:\s*[\s\S]*?CFBundleIdentifier:\s*)"[^"]*"/;
  yml = yml.replace(riderIdRegex, `$1"${config.riderApp.iosBundleId}"`);

  fs.writeFileSync(projectYmlPath, yml, 'utf8');
  logSuccess(`Updated apps/ios/project.yml:`);
  logSuccess(`  - CommerceOS bundle: ${config.customerApp.iosBundleId} ("${config.customerApp.name}")`);
  logSuccess(`  - CommerceOSRider bundle: ${config.riderApp.iosBundleId} ("${config.riderApp.name}")`);
}

// -----------------------------------------------------------------------------
// 3. Environment & Server Deployment Profile Engine
// -----------------------------------------------------------------------------
function configureEnvironment(config) {
  logStep(3, 'Generating Server & Production Environment Configurations');

  const envContent = `# ==============================================================================
# Commerce OS Authoritative Production Environment
# Client ID: ${config.clientId} (${config.clientName})
# Generated by whitelabel-cli on ${new Date().toISOString()}
# ==============================================================================

NODE_ENV=production
PORT=${config.api.port || 8080}

# Authoritative Isolated Database
DATABASE_URL=${config.database.databaseUrl}

# Routing & Telemetry Service
OSRM_BASE_URL=${config.api.osrmBaseUrl || 'https://router.project-osrm.org'}

# Cryptographic Token Invariants
JWT_SECRET=${config.credentials.jwtSecret}
JWT_ISSUER=${config.credentials.jwtIssuer || 'commerceos-auth'}
JWT_AUDIENCE=${config.credentials.jwtAudience || 'commerceos-clients'}
COMMERCEOS_OTP_PEPPER=${config.credentials.otpPepper}

# Firebase Cloud Messaging (Data-Only Invariant)
FCM_SERVER_KEY=${config.credentials.fcmServerKey}
FCM_ENDPOINT_URL=https://fcm.googleapis.com/fcm/send

# Direct 0%-Fee NPCI UPI Payments
MERCHANT_UPI_VPA=${config.merchant.upiVpa}
MERCHANT_UPI_NAME="${config.merchant.upiName}"

# Fulfillment & Store Configuration
DEFAULT_STORE_ID=${config.merchant.defaultStoreId || 'store_main_01'}
MULTI_STORE_ENABLED=true
MAX_SERVICE_RADIUS_KM=${config.merchant.serviceRadiusKm || 10.0}
TARGET_DELIVERY_SLA_MINUTES=${config.merchant.slaMinutes || 10}

# Customer Support
SUPPORT_PHONE=${config.merchant.supportPhone || '+1-800-COMMERCE'}
SUPPORT_EMAIL=${config.merchant.supportEmail || 'support@commerceos.io'}
`;

  // Write .env.production
  const prodEnvPath = getPath('.env.production');
  fs.writeFileSync(prodEnvPath, envContent, 'utf8');
  logSuccess(`Generated root ${path.basename(prodEnvPath)}`);

  // Save client-specific copy in clients/
  const clientDir = getPath('clients');
  if (!fs.existsSync(clientDir)) {
    fs.mkdirSync(clientDir, { recursive: true });
  }
  const clientEnvPath = path.join(clientDir, `${config.clientId}.env`);
  fs.writeFileSync(clientEnvPath, envContent, 'utf8');
  logSuccess(`Generated backup environment profile: clients/${config.clientId}.env`);
}

// -----------------------------------------------------------------------------
// 4. Security & Keystore Audit Gate
// -----------------------------------------------------------------------------
function auditCredentialsAndKeystores(config) {
  logStep(4, 'Verifying Android/iOS Keys, Services & Keystores');

  // Check Customer App google-services.json
  const custGsPath = getPath('apps/android/app/google-services.json');
  if (fs.existsSync(custGsPath)) {
    try {
      const gs = JSON.parse(fs.readFileSync(custGsPath, 'utf8'));
      const pkgs = (gs.client || []).map(c => c.client_info?.android_client_info?.package_name);
      if (pkgs.includes(config.customerApp.androidPackage)) {
        logSuccess(`Customer google-services.json contains matching package: ${config.customerApp.androidPackage}`);
      } else {
        logWarning(`Customer google-services.json is present, but does NOT contain ${config.customerApp.androidPackage}`);
      }
    } catch {
      logWarning(`Unable to parse customer google-services.json`);
    }
  } else {
    logWarning(`apps/android/app/google-services.json is MISSING. Push notifications will be disabled in debug/dev.`);
    console.log(`     👉 Download google-services.json from Firebase Console for ${config.customerApp.androidPackage}`);
  }

  // Check Rider App google-services.json
  const riderGsPath = getPath('apps/android/rider-app/google-services.json');
  if (fs.existsSync(riderGsPath)) {
    try {
      const gs = JSON.parse(fs.readFileSync(riderGsPath, 'utf8'));
      const pkgs = (gs.client || []).map(c => c.client_info?.android_client_info?.package_name);
      if (pkgs.includes(config.riderApp.androidPackage)) {
        logSuccess(`Rider google-services.json contains matching package: ${config.riderApp.androidPackage}`);
      } else {
        logWarning(`Rider google-services.json is present, but does NOT contain ${config.riderApp.androidPackage}`);
      }
    } catch {
      logWarning(`Unable to parse rider google-services.json`);
    }
  } else {
    logWarning(`apps/android/rider-app/google-services.json is MISSING.`);
    console.log(`     👉 Download google-services.json from Firebase Console for ${config.riderApp.androidPackage}`);
  }

  // Check Release Keystore
  const keystorePath = getPath('apps/android/commerceos-release.jks');
  if (fs.existsSync(keystorePath)) {
    logSuccess(`Android release keystore exists at apps/android/commerceos-release.jks`);
  } else {
    logWarning(`Android release keystore NOT found at apps/android/commerceos-release.jks`);
    console.log(`     👉 Generate with: keytool -genkey -v -keystore apps/android/commerceos-release.jks -alias ${config.clientId} -keyalg RSA -keysize 2048 -validity 10000`);
  }

  // Print Google Cloud SHA-1 Maps binding reminder
  console.log(`\n${colors.bright}📌 CRITICAL GOOGLE MAPS SHA-1 REMINDER:${colors.reset}`);
  console.log(`   To prevent blank map tiles in production:`);
  console.log(`   1. Extract SHA-1: ${colors.yellow}keytool -list -v -keystore apps/android/commerceos-release.jks -alias <alias>${colors.reset}`);
  console.log(`   2. In Google Cloud Console ➔ Credentials ➔ Edit API Key (${config.credentials.mapsApiKey.slice(0, 10)}...):`);
  console.log(`      Add App Restriction:`);
  console.log(`        • ${config.customerApp.androidPackage} + [Release SHA-1]`);
  console.log(`        • ${config.riderApp.androidPackage} + [Release SHA-1]`);
}

// -----------------------------------------------------------------------------
// 5. Status Inspector
// -----------------------------------------------------------------------------
function inspectStatus() {
  console.log(`\n==============================================================================`);
  console.log(`  COMMERCE OS CURRENT WHITE-LABEL CONFIGURATION STATUS`);
  console.log(`==============================================================================\n`);

  // Android
  const appGradlePath = getPath('apps/android/app/build.gradle.kts');
  if (fs.existsSync(appGradlePath)) {
    const content = fs.readFileSync(appGradlePath, 'utf8');
    const appIdMatch = content.match(/applicationId\s*=\s*"([^"]+)"/);
    console.log(`  Customer Android Package: ${colors.bright}${appIdMatch ? appIdMatch[1] : 'Unknown'}${colors.reset}`);
  }

  const riderGradlePath = getPath('apps/android/rider-app/build.gradle.kts');
  if (fs.existsSync(riderGradlePath)) {
    const content = fs.readFileSync(riderGradlePath, 'utf8');
    const appIdMatch = content.match(/applicationId\s*=\s*"([^"]+)"/);
    console.log(`  Rider Android Package:    ${colors.bright}${appIdMatch ? appIdMatch[1] : 'Unknown'}${colors.reset}`);
  }

  // iOS
  const ymlPath = getPath('apps/ios/project.yml');
  if (fs.existsSync(ymlPath)) {
    const content = fs.readFileSync(ymlPath, 'utf8');
    const custMatch = content.match(/CommerceOS:[\s\S]*?PRODUCT_BUNDLE_IDENTIFIER:\s*([^\n]+)/);
    const riderMatch = content.match(/CommerceOSRider:[\s\S]*?PRODUCT_BUNDLE_IDENTIFIER:\s*([^\n]+)/);
    console.log(`  Customer iOS Bundle ID:   ${colors.bright}${custMatch ? custMatch[1].trim() : 'Unknown'}${colors.reset}`);
    console.log(`  Rider iOS Bundle ID:      ${colors.bright}${riderMatch ? riderMatch[1].trim() : 'Unknown'}${colors.reset}`);
  }

  // Production Env
  const envPath = getPath('.env.production');
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf8');
    const dbMatch = content.match(/DATABASE_URL=([^\n]+)/);
    const upiMatch = content.match(/MERCHANT_UPI_VPA=([^\n]+)/);
    console.log(`  Database URL:             ${colors.bright}${dbMatch ? dbMatch[1] : 'Not Set'}${colors.reset}`);
    console.log(`  Zero-Fee Merchant UPI:    ${colors.bright}${upiMatch ? upiMatch[1] : 'Not Set'}${colors.reset}`);
  } else {
    console.log(`  Production Environment:   ${colors.yellow}.env.production does not exist yet${colors.reset}`);
  }

  console.log(`\n==============================================================================\n`);
}

// -----------------------------------------------------------------------------
// CLI Dispatcher
// -----------------------------------------------------------------------------
function printUsage() {
  console.log(`
${colors.bright}Commerce OS White-Label CLI${colors.reset}

Usage:
  node scripts/whitelabel-cli.js apply <path/to/client.json>
  node scripts/whitelabel-cli.js validate <path/to/client.json>
  node scripts/whitelabel-cli.js status
  node scripts/whitelabel-cli.js generate-template <grocery|pharmacy> <output.json>

Examples:
  node scripts/whitelabel-cli.js apply clients/grocery-template.json
  node scripts/whitelabel-cli.js apply clients/pharmacy-template.json
  node scripts/whitelabel-cli.js status
`);
}

function main() {
  const args = process.argv.slice(2);
  const command = args[0];

  if (!command || command === '--help' || command === '-h') {
    printUsage();
    process.exit(0);
  }

  if (command === 'status') {
    inspectStatus();
    process.exit(0);
  }

  if (command === 'validate') {
    const configPath = args[1];
    if (!configPath || !fs.existsSync(configPath)) {
      logError(`Config file not found: ${configPath}`);
      process.exit(1);
    }
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    const errors = validateConfig(config);
    if (errors.length > 0) {
      logError(`Validation failed for ${configPath}:`);
      errors.forEach(e => console.log(`  - ${e}`));
      process.exit(1);
    }
    logSuccess(`Configuration "${config.clientName}" (${config.clientId}) is 100% valid!`);
    process.exit(0);
  }

  if (command === 'apply') {
    const configPath = args[1];
    if (!configPath || !fs.existsSync(configPath)) {
      logError(`Config file not found: ${configPath}`);
      process.exit(1);
    }
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    const errors = validateConfig(config);
    if (errors.length > 0) {
      logError(`Cannot apply invalid configuration:`);
      errors.forEach(e => console.log(`  - ${e}`));
      process.exit(1);
    }

    console.log(`\n==============================================================================`);
    console.log(`  APPLYING WHITE-LABEL BRAND: ${config.clientName.toUpperCase()}`);
    console.log(`  Client ID: ${config.clientId} | Vertical: ${config.vertical.toUpperCase()}`);
    console.log(`==============================================================================`);

    configureAndroid(config);
    configureIOS(config);
    configureEnvironment(config);
    auditCredentialsAndKeystores(config);

    console.log(`\n${colors.green}${colors.bright}✔ SUCCESS: White-label configuration successfully applied for ${config.clientName}!${colors.reset}\n`);
    process.exit(0);
  }

  if (command === 'generate-template') {
    const type = args[1] || 'grocery';
    const outPath = args[2] || `clients/${type}-custom.json`;
    const templateSource = getPath(`clients/${type === 'pharmacy' ? 'pharmacy' : 'grocery'}-template.json`);
    if (!fs.existsSync(templateSource)) {
      logError(`Template source not found: ${templateSource}`);
      process.exit(1);
    }
    fs.copyFileSync(templateSource, outPath);
    logSuccess(`Created template at ${outPath}`);
    process.exit(0);
  }

  logError(`Unknown command: ${command}`);
  printUsage();
  process.exit(1);
}

main();
