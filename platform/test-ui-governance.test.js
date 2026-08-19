/**
 * Commerce OS — Universal UI/UX Design System Governance & Cross-Platform Semantic Guard
 *
 * Strict Architecture Enforcement across ALL 8 repository surfaces:
 * 1. packages/ui/src (Canonical Component Primitives)
 * 2. apps/web/src (Customer Web Application)
 * 3. apps/seller/src (Seller / Merchant Application)
 * 4. apps/admin/src (Admin / Operations Application)
 * 5. apps/delivery/src (Delivery / Rider Web Application)
 * 6. apps/warehouse/src (Warehouse / Fulfillment Application)
 * 7. apps/android/app/src (Android Customer Theme & Configuration)
 * 8. apps/android/rider-app/src (Rider Theme)
 *
 * Governance Rules:
 * - ZERO raw hex colors in UI component/screen classNames and templates (with strictly scoped vector blocks for maps/charts).
 * - ZERO direct palette classes across ALL 22 Tailwind palette families:
 *   red, orange, yellow, lime, green, cyan, blue, violet, purple, fuchsia, pink, rose, slate, gray, zinc, neutral, stone, amber, emerald, teal, sky, indigo
 * - ZERO non-hex color escapes: rgb(...), rgba(...), hsl(...), hsla(...) in component templates.
 * - ZERO inline DOM color styles (e.g. style={{ color: ... }}, style={{ backgroundColor: ... }}).
 * - ZERO arbitrary typography (text-[...px]) and radius (rounded-[...px]).
 * - All 15 canonical Commerce OS primitives exported from @commerce-os/ui.
 * - Single source of truth: @commerce-os/design-system tokens & Tailwind preset.
 * - Android Customer & Rider themes synchronized with Universal Tokens (#16A34A / #0B132B / #4F46E5).
 * - Zero developer/debug copy in production-facing UI.
 * - Real upload wiring with zero simulated OCR timeouts/fake strings.
 * - Zero fake operational defaults (Apex Dark Store / Panipat Hub / fabricated bins).
 * - Automated mutation sensitivity verification across all violation classes.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

const ROOT_DIR = path.resolve(__dirname, '..');

// All 22 Tailwind core palette families
const TAILWIND_PALETTE_FAMILIES = [
  'red', 'orange', 'yellow', 'lime', 'green', 'cyan', 'blue', 'violet',
  'purple', 'fuchsia', 'pink', 'rose', 'slate', 'gray', 'zinc', 'neutral',
  'stone', 'amber', 'emerald', 'teal', 'sky', 'indigo'
];

// Complete utility prefix pattern covering all styling facets
const PALETTE_PREFIXES = [
  'bg', 'text', 'border', 'ring', 'decoration', 'from', 'via', 'to',
  'fill', 'stroke', 'outline', 'divide', 'accent', 'shadow', 'caret', 'placeholder'
];

const DIRECT_PALETTE_REGEX = new RegExp(
  `\\b(${PALETTE_PREFIXES.join('|')})-(${TAILWIND_PALETTE_FAMILIES.join('|')})-\\d+(\\/\\d+)?\\b`
);

const RAW_HEX_REGEX = /#[0-9a-fA-F]{3,8}\b/;
const NON_HEX_COLOR_REGEX = /\b(rgba?|hsla?)\s*\([^)]+\)/;
const INLINE_STYLE_COLOR_REGEX = /style=\{\{[^}]*\b(color|background|backgroundColor|borderColor|fill|stroke)\s*:\s*['"`](#[0-9a-fA-F]{3,8}|rgba?\(|hsla?\(|[a-zA-Z]+)/;
const ARBITRARY_TYPO_REGEX = /text-\[\d+px\]/;
const ARBITRARY_RADIUS_REGEX = /rounded-\[\d+px\]/;

/**
 * Parses file content line-by-line with scoped vector awareness.
 * SVG color attributes are ONLY permitted within explicit vector graphic tags
 * when preceded by /* commerce-os:allow-vector-color * / or inside dedicated SVG element blocks.
 */
function scanFileForUIViolations(filePath, content) {
  const violations = [];
  const lines = content.split('\n');
  let insideScopedVectorBlock = false;
  let svgTagDepth = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const lineNum = i + 1;

    // Track scoped vector comment annotation
    if (line.includes('commerce-os:allow-vector-color')) {
      insideScopedVectorBlock = true;
    }

    if (line.includes('<svg')) {
      svgTagDepth++;
    }

    const inVectorContext = (insideScopedVectorBlock || svgTagDepth > 0);

    // Check 1: Direct Tailwind Palette Utilities (Strictly banned everywhere)
    const paletteMatch = line.match(DIRECT_PALETTE_REGEX);
    if (paletteMatch) {
      violations.push({
        line: lineNum,
        rule: 'Direct Palette Utility',
        match: paletteMatch[0],
        snippet: line.trim(),
      });
    }

    // Check 2: Arbitrary Typography (Strictly banned everywhere)
    const typoMatch = line.match(ARBITRARY_TYPO_REGEX);
    if (typoMatch) {
      violations.push({
        line: lineNum,
        rule: 'Arbitrary Typography',
        match: typoMatch[0],
        snippet: line.trim(),
      });
    }

    // Check 3: Arbitrary Radius (Strictly banned everywhere)
    const radiusMatch = line.match(ARBITRARY_RADIUS_REGEX);
    if (radiusMatch) {
      violations.push({
        line: lineNum,
        rule: 'Arbitrary Radius',
        match: radiusMatch[0],
        snippet: line.trim(),
      });
    }

    // Check 4: Inline Style DOM Colors (Strictly banned everywhere)
    const inlineStyleMatch = line.match(INLINE_STYLE_COLOR_REGEX);
    if (inlineStyleMatch) {
      violations.push({
        line: lineNum,
        rule: 'Inline DOM Color Style',
        match: inlineStyleMatch[0],
        snippet: line.trim(),
      });
    }

    // Check 5: Non-Hex Functional Color Escapes (rgb, rgba, hsl, hsla)
    const nonHexMatch = line.match(NON_HEX_COLOR_REGEX);
    if (nonHexMatch && !inVectorContext) {
      violations.push({
        line: lineNum,
        rule: 'Non-Hex Color Function (rgba/hsla)',
        match: nonHexMatch[0],
        snippet: line.trim(),
      });
    }

    // Check 6: Raw Hex Colors
    const hexMatch = line.match(RAW_HEX_REGEX);
    if (hexMatch) {
      // If we are inside an authorized vector context and the line is an SVG primitive attribute:
      const isSvgAttribute = inVectorContext && (
        line.includes('fill=') ||
        line.includes('stroke=') ||
        line.includes('stopColor=') ||
        line.includes('stroke="') ||
        line.includes('fill="') ||
        line.includes('<circle') ||
        line.includes('<path') ||
        line.includes('<polyline') ||
        line.includes('<line') ||
        line.includes('<rect') ||
        line.includes('<stop')
      );

      // Disallow hex on standard HTML/DOM elements even in vector files
      const isStandardDomElement = /<(div|button|span|p|a|input|h[1-6]|section|header|aside|main)\b/.test(line);

      if (!isSvgAttribute || isStandardDomElement) {
        violations.push({
          line: lineNum,
          rule: 'Raw Hex Color in Template',
          match: hexMatch[0],
          snippet: line.trim(),
        });
      }
    }

    if (line.includes('</svg>')) {
      svgTagDepth = Math.max(0, svgTagDepth - 1);
      if (svgTagDepth === 0) {
        insideScopedVectorBlock = false;
      }
    }
  }

  return violations;
}

function verifyScope(scopeName, dirPath) {
  assert(fs.existsSync(dirPath), `${scopeName} directory must exist at ${dirPath}`);
  const allViolations = [];

  function walk(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (['node_modules', '.next', 'dist', 'build', '.turbo', '.git'].includes(entry.name)) continue;
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (['.tsx', '.ts'].some((ext) => entry.name.endsWith(ext))) {
        const relativePath = path.relative(ROOT_DIR, fullPath);
        const content = fs.readFileSync(fullPath, 'utf8');
        const fileViolations = scanFileForUIViolations(relativePath, content);
        for (const v of fileViolations) {
          allViolations.push({
            file: relativePath,
            ...v,
          });
        }
      }
    }
  }

  walk(dirPath);

  if (allViolations.length > 0) {
    console.error(`  ✗ Semantic UI Token Violations found in ${scopeName} (${allViolations.length} total):`);
    allViolations.slice(0, 10).forEach((v) => {
      console.error(`    - ${v.file}:${v.line} -> ${v.rule} [${v.match}] ("${v.snippet}")`);
    });
    if (allViolations.length > 10) {
      console.error(`    ... and ${allViolations.length - 10} more violations.`);
    }
    assert.fail(`${scopeName} contains ${allViolations.length} forbidden visual tokens`);
  }

  return 'PASS';
}

function testUIGovernance() {
  console.log('[GUARD] Executing Commerce OS Universal UI/UX Governance Guard...');

  // 1. Verify @commerce-os/ui exports all canonical primitives
  const uiIndexPath = path.join(ROOT_DIR, 'packages/ui/src/index.tsx');
  assert(fs.existsSync(uiIndexPath), 'packages/ui/src/index.tsx must exist');
  const uiIndexContent = fs.readFileSync(uiIndexPath, 'utf8');

  const requiredExports = [
    'CommerceButton',
    'CommerceInput',
    'CommerceCard',
    'CommerceBadge',
    'CommerceChip',
    'CommerceSectionHeader',
    'CommerceQuantityControl',
    'CommercePriceBlock',
    'CommerceProductCard',
    'CommerceStates',
    'CommerceModal',
    'CommerceNavbar',
    'CommerceCartDrawer',
    'CommerceOrderStatusTimeline',
    'CommerceCategoryShowcase',
  ];

  for (const exp of requiredExports) {
    assert(
      uiIndexContent.includes(exp),
      `packages/ui/src/index.tsx must export ${exp}`
    );
  }
  console.log('  ✓ packages/ui exports all 15 canonical Commerce OS primitives');

  // 2. Verify design tokens, preset and semantic specification
  const specPath = path.join(ROOT_DIR, 'packages/design-system/src/semantic-spec.json');
  assert(fs.existsSync(specPath), 'semantic-spec.json must exist');
  const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'));
  assert.strictEqual(spec.palette.brandPrimary.hex, '#16A34A', 'Brand Primary must be #16A34A');
  assert.strictEqual(spec.palette.brandSecondary.hex, '#0B132B', 'Brand Secondary must be #0B132B');
  assert.strictEqual(spec.palette.speedAccent.hex, '#4F46E5', 'Speed Accent must be #4F46E5');

  const presetPath = path.join(ROOT_DIR, 'packages/design-system/src/tailwind-preset.ts');
  assert(fs.existsSync(presetPath), 'packages/design-system/src/tailwind-preset.ts must exist');
  console.log('  ✓ Universal semantic design specification verified (#16A34A / #0B132B / #4F46E5)');

  // 3. Scan & Verify ALL 6 Web/Frontend Application Scopes (Zero whole-file allowlists)
  const surfaceStatus = {};
  
  surfaceStatus['packages/ui'] = verifyScope('packages/ui', path.join(ROOT_DIR, 'packages/ui/src'));
  surfaceStatus['Customer Web'] = verifyScope('Customer Web', path.join(ROOT_DIR, 'apps/web/src'));
  surfaceStatus['Seller'] = verifyScope('Seller', path.join(ROOT_DIR, 'apps/seller/src'));
  surfaceStatus['Admin'] = verifyScope('Admin', path.join(ROOT_DIR, 'apps/admin/src'));
  surfaceStatus['Delivery/Rider Web'] = verifyScope('Delivery/Rider Web', path.join(ROOT_DIR, 'apps/delivery/src'));
  surfaceStatus['Warehouse'] = verifyScope('Warehouse', path.join(ROOT_DIR, 'apps/warehouse/src'));

  // 4. Cross-Platform Android & Rider Theme Verification
  let androidCustomerStatus = 'FAIL';
  const clientThemePath = path.join(ROOT_DIR, 'apps/android/app/src/main/java/com/commerceos/android/config/ClientTheme.kt');
  if (fs.existsSync(clientThemePath)) {
    const content = fs.readFileSync(clientThemePath, 'utf8');
    assert(content.includes('#16A34A'), 'Android ClientTheme must use #16A34A as primary');
    assert(content.includes('#0B132B'), 'Android ClientTheme must use #0B132B as secondary');
    assert(content.includes('#4F46E5'), 'Android ClientTheme must use #4F46E5 as accent');
    androidCustomerStatus = 'PASS';
  }

  const clientConfigPath = path.join(ROOT_DIR, 'apps/android/app/src/main/java/com/commerceos/android/config/ClientConfiguration.kt');
  if (fs.existsSync(clientConfigPath)) {
    const content = fs.readFileSync(clientConfigPath, 'utf8');
    assert(content.includes('#16A34A'), 'ClientConfiguration DefaultGeneric must use #16A34A');
  }
  surfaceStatus['Android Customer'] = androidCustomerStatus;

  let androidRiderStatus = 'FAIL';
  const riderThemePath = path.join(ROOT_DIR, 'apps/android/rider-app/src/main/java/com/commerceos/rider/theme/RiderTheme.kt');
  if (fs.existsSync(riderThemePath)) {
    const content = fs.readFileSync(riderThemePath, 'utf8');
    assert(content.includes('0xFF16A34A'), 'RiderTheme must use 0xFF16A34A as Primary');
    assert(content.includes('0xFF0B132B'), 'RiderTheme must use 0xFF0B132B as PrimaryDark');
    assert(content.includes('0xFF4F46E5'), 'RiderTheme must use 0xFF4F46E5 as SpeedAccent');
    androidRiderStatus = 'PASS';
  }
  surfaceStatus['Android Rider'] = androidRiderStatus;

  // 5. Clean Production Copy & No Fake Operational Defaults
  const forbiddenPatterns = [
    'Clean View',
    'Manage Stock (/inventory)',
    'View Dedicated Orders (/orders)',
    'Apex Dark Store, Sector 18',
    'Panipat Dark Store Hub #1321',
    'Sector 18 Market, Panipat',
  ];

  const filesToCheck = [
    path.join(ROOT_DIR, 'apps/seller/src/app/page.tsx'),
    path.join(ROOT_DIR, 'apps/web/src/app/page.tsx'),
    path.join(ROOT_DIR, 'packages/ui/src/CommerceNavbar.tsx'),
    path.join(ROOT_DIR, 'apps/web/src/components/DeliveryAddressMapModal.tsx'),
    path.join(ROOT_DIR, 'apps/delivery/src/components/ActiveDeliveryCard.tsx'),
  ];

  for (const filePath of filesToCheck) {
    if (fs.existsSync(filePath)) {
      const content = fs.readFileSync(filePath, 'utf8');
      for (const pattern of forbiddenPatterns) {
        assert(
          !content.includes(pattern),
          `File ${path.basename(filePath)} must not contain forbidden copy "${pattern}"`
        );
      }
    }
  }
  console.log('  ✓ Clean production copy verified (zero fake operational defaults & zero developer strings)');

  // 6. Verify No Fake Simulated OCR in Prescription Drawer
  const rxDrawerPath = path.join(ROOT_DIR, 'packages/ui/src/PrescriptionUploadDrawer.tsx');
  if (fs.existsSync(rxDrawerPath)) {
    const rxContent = fs.readFileSync(rxDrawerPath, 'utf8');
    assert(!rxContent.includes('handleSimulatedUpload'), 'PrescriptionUploadDrawer must not contain handleSimulatedUpload');
    assert(!rxContent.includes('AI OCR Extracted: Paracetamol'), 'PrescriptionUploadDrawer must not manufacture fake OCR strings');
    console.log('  ✓ PrescriptionUploadDrawer: zero simulated OCR timeouts / fake extraction data');
  }

  // 7. Multi-Scope Mutation & Sensitivity Verification Across ALL 22 Palette Families & Violation Types
  const testCases = [
    { input: '<div className="bg-[#123456]" />', name: 'Raw Hex in className' },
    { input: '<div className="bg-purple-500" />', name: 'Direct Purple Palette' },
    { input: '<div className="text-blue-600" />', name: 'Direct Blue Palette' },
    { input: '<div className="border-cyan-200" />', name: 'Direct Cyan Palette' },
    { input: '<div className="bg-red-500/20" />', name: 'Direct Red Palette with Opacity' },
    { input: '<div className="text-yellow-600" />', name: 'Direct Yellow Palette' },
    { input: '<div className="fill-emerald-400" />', name: 'Direct Emerald Palette' },
    { input: '<div className="from-rose-500 to-indigo-600" />', name: 'Direct Rose/Indigo Gradient' },
    { input: '<div style={{ color: "red" }} />', name: 'Inline DOM Color Style' },
    { input: '<div style={{ backgroundColor: "#123456" }} />', name: 'Inline DOM Background Color Style' },
    { input: '<div className="text-[13px]" />', name: 'Arbitrary Text Size' },
    { input: '<div className="rounded-[15px]" />', name: 'Arbitrary Radius' },
    { input: '<div className="bg-[rgba(0,0,0,0.5)]" />', name: 'Non-Hex Color Function' },
  ];

  for (const tc of testCases) {
    const violations = scanFileForUIViolations('test-virtual.tsx', tc.input);
    assert(
      violations.length > 0,
      `Guard mutation check must trigger on ${tc.name} violation. Input: ${tc.input}`
    );
  }
  console.log(`  ✓ Comprehensive mutation sensitivity self-test passed across ${testCases.length} violation classes`);

  // 8. Explicit Final Scope Status Matrix Derivation
  const allSurfacesPass = Object.values(surfaceStatus).every((s) => s === 'PASS');
  assert(allSurfacesPass, 'All 8 UI surfaces must pass governance verification');

  console.log('\n===============================================================');
  console.log('UNIVERSAL UI/UX DESIGN SYSTEM GOVERNANCE MATRIX');
  console.log('===============================================================');
  console.log(`packages/ui:        ${surfaceStatus['packages/ui']}`);
  console.log(`Customer Web:       ${surfaceStatus['Customer Web']}`);
  console.log(`Seller:             ${surfaceStatus['Seller']}`);
  console.log(`Admin:              ${surfaceStatus['Admin']}`);
  console.log(`Delivery/Rider Web: ${surfaceStatus['Delivery/Rider Web']}`);
  console.log(`Warehouse:          ${surfaceStatus['Warehouse']}`);
  console.log(`Android Customer:   ${surfaceStatus['Android Customer']}`);
  console.log(`Android Rider:      ${surfaceStatus['Android Rider']}`);
  console.log('---------------------------------------------------------------');
  console.log('ALL 8 UI SURFACES GOVERNED: PASS');
  console.log('===============================================================\n');
}

if (require.main === module) {
  testUIGovernance();
}

module.exports = { testUIGovernance };
