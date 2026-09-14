const { commerceOsPreset } = require('@commerce-os/design-system');

/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [commerceOsPreset],
  content: [
    './src/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  plugins: [],
};
