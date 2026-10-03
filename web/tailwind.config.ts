import type { Config } from "tailwindcss";

/*
 * Tailwind config — design tokens live here, never inline in components.
 * - `brand`     : primary accent palette for Stash (money/emerald family).
 * - `surface`   : neutral background scale for cards, page, etc.
 * - `fontFamily`: Inter as default sans, falls back to system UI.
 */
const config: Config = {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],

  theme: {
    extend: {
      colors: {
        // Primary accent — used for CTAs, highlights, logo glyph.
        brand: {
          50:  "#ecfdf5",
          100: "#d1fae5",
          200: "#a7f3d0",
          400: "#34d399",
          500: "#10b981",
          600: "#059669",
          700: "#047857",
          900: "#064e3b",
        },
        // Neutral surfaces — page background, cards, borders, text.
        surface: {
          0:   "#ffffff",
          50:  "#fafafa",
          100: "#f5f5f5",
          200: "#e5e5e5",
          500: "#737373",
          700: "#404040",
          900: "#171717",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
    },
  },

  plugins: [],
};

export default config;
