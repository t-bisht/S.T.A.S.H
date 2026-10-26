/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },

  server: {
    port: 7002,
    open: true,
    proxy: {
      "/admin": {
        target: "http://127.0.0.1:7084",
        changeOrigin: true,
        // No rewrite — /admin/* passes through unchanged; admin_engine owns the prefix.
      },
    },
  },

  build: {
    outDir: "dist",
    sourcemap: true,
  },

  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
