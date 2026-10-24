const { commerceOsPreset } = require('@commerce-os/design-system');

/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [commerceOsPreset],
  content: [
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    '../../packages/ui/src/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  plugins: [],
};
