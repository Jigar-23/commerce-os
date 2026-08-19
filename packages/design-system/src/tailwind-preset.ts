import { tokens } from './tokens';

/**
 * Commerce OS — Authoritative Tailwind Preset
 *
 * All applications (Web, Seller, Warehouse, Delivery, Admin) inherit directly from this preset.
 */
export const commerceOsPreset = {
  theme: {
    extend: {
      colors: {
        // Semantic Roles
        surface: tokens.colors.surface,
        content: tokens.colors.content,
        border: tokens.colors.border,
        action: tokens.colors.action,

        // Core Palettes
        brand: tokens.colors.brand,
        navy: tokens.colors.primaryNavy,
        accent: tokens.colors.accent,
        amber: tokens.colors.amber,
        rose: tokens.colors.rose,
        emerald: tokens.colors.emerald,
        neutral: tokens.colors.neutral,
      },
      fontFamily: tokens.typography.fontFamily,
      fontSize: tokens.typography.fontSize,
      boxShadow: tokens.shadows,
      borderRadius: tokens.borderRadius,
      spacing: tokens.spacing,
      zIndex: tokens.zIndex,
      keyframes: {
        shimmer: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'slide-up': {
          '0%': { transform: 'translateY(100%)' },
          '100%': { transform: 'translateY(0)' },
        },
        'slide-left': {
          '0%': { transform: 'translateX(100%)' },
          '100%': { transform: 'translateX(0)' },
        },
        'scale-in': {
          '0%': { transform: 'scale(0.95)', opacity: '0' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'pulse-subtle': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.6' },
        },
      },
      animation: {
        shimmer: 'shimmer 2s infinite',
        'fade-in': 'fade-in 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
        'slide-up': 'slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        'slide-left': 'slide-left 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        'scale-in': 'scale-in 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
        'pulse-subtle': 'pulse-subtle 2s infinite',
      },
    },
  },
};

export default commerceOsPreset;
