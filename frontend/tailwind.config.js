/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        monash: {
          blue: '#006DAE',
          navy: '#002B49',
          ink: '#0A1A2F',
          gold: '#C9A227',
          mist: '#F5F7FA',
        },
        navy: {
          900: '#00182A',
          700: '#003A63',
        },
        blue: {
          600: '#006DAE',
          500: '#1E86C4',
          100: '#CFE4F2',
          50: '#EAF3F9',
        },
        gold: {
          700: '#A5821B',
          600: '#C9A227',
          500: '#B8911F',
          100: '#F7EFD2',
        },
        ink: {
          900: '#0A1A2F',
          700: '#40536B',
          500: '#5B6B80',
          400: '#64748B',
        },
        line: {
          200: '#E3E7EC',
          300: '#CBD5E0',
        },
        surface: '#FFFFFF',
        canvas: '#F5F7FA',
        track: '#E9EEF4',
        chip: '#EEF2F6',
        success: {
          600: '#0F7B4F',
          50: '#E8F5EE',
        },
        warning: {
          700: '#8A5A00',
          50: '#FFF6E0',
        },
        danger: {
          800: '#7A1811',
          700: '#8F1D17',
          600: '#B3261E',
          300: '#C99A96',
          50: '#FDECEA',
        },
        footer: {
          muted: '#A9BDCE',
          faint: '#7E97AC',
        },
      },
      fontFamily: {
        display: ['"Fraunces"', 'Georgia', '"Times New Roman"', 'serif'],
        sans: ['"Inter"', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
        mono: ['ui-monospace', '"SF Mono"', '"Cascadia Mono"', 'Menlo', 'monospace'],
      },
      fontSize: {
        'display-1': ['56px', { lineHeight: '60px', letterSpacing: '-0.02em', fontWeight: '600' }],
        'display-2': ['44px', { lineHeight: '48px', letterSpacing: '-0.02em', fontWeight: '600' }],
        h1: ['36px', { lineHeight: '42px', letterSpacing: '-0.015em', fontWeight: '600' }],
        h2: ['28px', { lineHeight: '34px', letterSpacing: '-0.01em', fontWeight: '600' }],
        h3: ['22px', { lineHeight: '28px', fontWeight: '600' }],
        h4: ['18px', { lineHeight: '26px', fontWeight: '600' }],
        'body-lg': ['17px', { lineHeight: '28px' }],
        body: ['15px', { lineHeight: '24px' }],
        'body-sm': ['13px', { lineHeight: '20px' }],
        label: ['13px', { lineHeight: '16px', letterSpacing: '0.01em', fontWeight: '600' }],
        overline: ['11px', { lineHeight: '16px', letterSpacing: '0.08em', fontWeight: '600' }],
        button: ['15px', { lineHeight: '16px', letterSpacing: '0.01em', fontWeight: '600' }],
        'button-sm': ['13px', { lineHeight: '16px', letterSpacing: '0.01em', fontWeight: '600' }],
      },
      borderRadius: {
        xs: '4px',
        sm: '6px',
        md: '10px',
        lg: '14px',
        xl: '20px',
        '2xl': '28px',
        pill: '999px',
        block: '8px',
        toast: '12px',
      },
      boxShadow: {
        e1: '0 1px 2px rgba(10,26,47,.06), 0 1px 3px rgba(10,26,47,.04)',
        e2: '0 4px 12px rgba(10,26,47,.08)',
        e3: '0 12px 32px rgba(10,26,47,.12)',
        e4: '0 24px 64px rgba(2,43,73,.18)',
        glow: '0 40px 120px rgba(0,43,73,.35)',
      },
      transitionTimingFunction: {
        standard: 'cubic-bezier(.2,.8,.2,1)',
        enter: 'cubic-bezier(.16,1,.3,1)',
        exit: 'cubic-bezier(.4,0,1,1)',
      },
      transitionDuration: {
        1: '120ms',
        2: '200ms',
        3: '320ms',
        4: '560ms',
      },
      spacing: {
        18: '72px',
      },
    },
  },
  plugins: [],
}
