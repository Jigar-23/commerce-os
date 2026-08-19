/**
 * Commerce OS — Universal Semantic Design Tokens
 *
 * Single authoritative source of truth for design tokens across Customer, Seller, Rider, and Admin interfaces.
 */

export const tokens = {
  colors: {
    // 1. Raw Palette Primitives
    brand: {
      50: '#F0FDF4',
      100: '#DCFCE7',
      200: '#BBF7D0',
      300: '#86EFAC',
      400: '#4ADE80',
      500: '#16A34A', // Primary Commerce Green (High Trust & Vitality)
      600: '#15803D', // Hover / Interactive
      700: '#166534', // Active / Dark Accent
      800: '#14532D',
      900: '#052E16',
    },
    primaryNavy: {
      50: '#F8FAFC',
      100: '#F1F5F9',
      200: '#E2E8F0',
      500: '#334155',
      700: '#1E293B',
      800: '#0F172A',
      900: '#0B132B', // Deep Luxury Midnight Navy
      950: '#030712',
    },
    accent: {
      50: '#EEF2FF',
      100: '#E0E7FF',
      200: '#C7D2FE',
      500: '#4F46E5', // Electric Indigo (SLA & Live Express Dispatch)
      600: '#4338CA',
      700: '#3730A3',
    },
    amber: {
      50: '#FFFBEB',
      100: '#FEF3C7',
      200: '#FDE68A',
      500: '#F59E0B', // Warning / Temperature / Urgent
      600: '#D97706',
    },
    rose: {
      50: '#FFF1F2',
      100: '#FFE4E6',
      200: '#FECDD3',
      500: '#E11D48', // Prescription Required / Expiry Alert
      600: '#BE123C',
    },
    emerald: {
      50: '#ECFDF5',
      100: '#D1FAE5',
      200: '#A7F3D0',
      500: '#10B981', // OTC Safe / Verified Badge
      600: '#059669',
    },
    neutral: {
      0: '#FFFFFF',
      50: '#F8FAFC',
      100: '#F1F5F9',
      200: '#E2E8F0',
      300: '#CBD5E1',
      400: '#94A3B8',
      500: '#64748B',
      600: '#475569',
      700: '#334155',
      800: '#1E293B',
      900: '#0F172A',
      950: '#020617',
    },

    // 2. Semantic Surfaces
    surface: {
      canvas: '#F8FAFC',
      card: '#FFFFFF',
      elevated: '#FFFFFF',
      subtle: '#F1F5F9',
      muted: '#E2E8F0',
      inverse: '#0B132B',
      brandSubtle: '#F0FDF4',
      accentSubtle: '#EEF2FF',
      dangerSubtle: '#FFF1F2',
      warningSubtle: '#FFFBEB',
      successSubtle: '#ECFDF5',
      editorialSubtle: '#FAF5FF',
      infoSubtle: '#EFF6FF',
      operationalSubtle: '#ECFEFF',
    },

    // 3. Semantic Content / Typography
    content: {
      primary: '#0F172A',
      secondary: '#475569',
      muted: '#94A3B8',
      subtle: '#CBD5E1',
      inverse: '#FFFFFF',
      brand: '#16A34A',
      brandDark: '#166534',
      accent: '#4F46E5',
      danger: '#E11D48',
      warning: '#D97706',
      success: '#15803D',
      editorial: '#7E22CE',
      info: '#1D4ED8',
      operational: '#0891B2',
    },

    // 4. Semantic Borders
    border: {
      subtle: '#F1F5F9',
      default: '#E2E8F0',
      strong: '#CBD5E1',
      brand: '#16A34A',
      brandSubtle: '#BBF7D0',
      focus: '#16A34A',
      danger: '#FECDD3',
      warning: '#FDE68A',
      success: '#BBF7D0',
      accent: '#C7D2FE',
      editorial: '#E9D5FF',
      info: '#BFDBFE',
      operational: '#A5F3FC',
    },

    // 5. Semantic Interactive Actions
    action: {
      primaryBg: '#16A34A',
      primaryHover: '#15803D',
      primaryActive: '#166534',
      primaryText: '#FFFFFF',
      secondaryBg: '#F1F5F9',
      secondaryHover: '#E2E8F0',
      secondaryActive: '#CBD5E1',
      secondaryText: '#0F172A',
      dangerBg: '#E11D48',
      dangerHover: '#BE123C',
      dangerText: '#FFFFFF',
      speedBg: '#4F46E5',
      speedHover: '#4338CA',
      speedText: '#FFFFFF',
      warningBg: '#F59E0B',
      warningHover: '#D97706',
      warningText: '#FFFFFF',
      editorialBg: '#9333EA',
      editorialHover: '#7E22CE',
      editorialText: '#FFFFFF',
      infoBg: '#2563EB',
      infoHover: '#1D4ED8',
      infoText: '#FFFFFF',
      operationalBg: '#0891B2',
      operationalHover: '#0E7490',
      operationalText: '#FFFFFF',
    },

    // 6. Canonical Backward Compatibility Alias
    semantic: {
      bgPrimary: '#F8FAFC',
      bgSecondary: '#FFFFFF',
      bgElevated: '#FFFFFF',
      cardSurface: '#FFFFFF',
      borderSubtle: '#E2E8F0',
      borderFocus: '#16A34A',
      textPrimary: '#0F172A',
      textSecondary: '#475569',
      textMuted: '#94A3B8',
      textInverse: '#FFFFFF',
      pricePrimary: '#0F172A',
      priceDiscount: '#16A34A',
      priceMrp: '#94A3B8',
    },
  },

  typography: {
    fontFamily: {
      sans: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      display: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      mono: 'JetBrains Mono, SFMono-Regular, Menlo, Monaco, monospace',
    },
    fontSize: {
      '2xs': ['0.6875rem', { lineHeight: '0.875rem', letterSpacing: '0.02em' }], // 11px
      xs: ['0.75rem', { lineHeight: '1rem', letterSpacing: '0.01em' }],          // 12px
      sm: ['0.875rem', { lineHeight: '1.25rem', letterSpacing: '0' }],           // 14px
      base: ['1rem', { lineHeight: '1.5rem', letterSpacing: '-0.01em' }],        // 16px
      lg: ['1.125rem', { lineHeight: '1.75rem', letterSpacing: '-0.015em' }],     // 18px
      xl: ['1.25rem', { lineHeight: '1.75rem', letterSpacing: '-0.02em' }],      // 20px
      '2xl': ['1.5rem', { lineHeight: '2rem', letterSpacing: '-0.025em' }],       // 24px
      '3xl': ['1.875rem', { lineHeight: '2.25rem', letterSpacing: '-0.03em' }],   // 30px
      '4xl': ['2.25rem', { lineHeight: '2.5rem', letterSpacing: '-0.035em' }],    // 36px
    },
    fontWeight: {
      normal: '400',
      medium: '500',
      semibold: '600',
      bold: '700',
      extrabold: '800',
      black: '900',
    },
  },

  spacing: {
    none: '0px',
    '3xs': '2px',
    '2xs': '4px',
    xs: '6px',
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '20px',
    '2xl': '24px',
    '3xl': '32px',
    '4xl': '40px',
    '5xl': '48px',
    '6xl': '64px',
  },

  borderRadius: {
    none: '0px',
    xs: '4px',
    sm: '6px',
    md: '10px',
    lg: '14px',
    xl: '18px',
    '2xl': '24px',
    '3xl': '32px',
    full: '9999px',
  },

  shadows: {
    subtle: '0 1px 2px 0 rgba(15, 23, 42, 0.04)',
    card: '0 2px 8px -2px rgba(15, 23, 42, 0.06), 0 1px 3px 0 rgba(15, 23, 42, 0.04)',
    cardHover: '0 12px 24px -6px rgba(15, 23, 42, 0.12), 0 4px 8px -2px rgba(15, 23, 42, 0.04)',
    floatingBar: '0 20px 40px -10px rgba(15, 23, 42, 0.22), 0 8px 16px -4px rgba(15, 23, 42, 0.1)',
    modal: '0 25px 50px -12px rgba(15, 23, 42, 0.25)',
    glowBrand: '0 0 24px -4px rgba(22, 163, 74, 0.35)',
    glowAccent: '0 0 24px -4px rgba(79, 70, 229, 0.35)',
  },

  controlHeight: {
    xs: '28px',
    sm: '32px',
    md: '40px',
    lg: '48px',
    xl: '56px',
  },

  animation: {
    easing: {
      standard: 'cubic-bezier(0.16, 1, 0.3, 1)',
      entrance: 'cubic-bezier(0, 0, 0.2, 1)',
      exit: 'cubic-bezier(0.4, 0, 1, 1)',
      spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
    },
    duration: {
      instant: '75ms',
      fast: '150ms',
      normal: '250ms',
      slow: '400ms',
    },
  },

  zIndex: {
    base: 0,
    card: 1,
    elevated: 10,
    stickyHeader: 50,
    floatingCart: 70,
    modalBackdrop: 90,
    modal: 100,
    popover: 110,
    toast: 120,
  },
} as const;

export type DesignTokens = typeof tokens;
