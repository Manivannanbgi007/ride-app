import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// One configuration for every environment: the environment only changes the .env.<mode> file that is read
// (npm run build:uat -> vite build --mode uat -> .env + .env.uat).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // import { Button } from '@/components/common/Button' instead of '../../../components/common/Button'
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // The API Gateway allows this origin (CORS_ALLOWED_ORIGINS)
    port: 5180,
    strictPort: true,
  },
  preview: {
    port: 4173,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Tests read their own fixed settings, never a developer's .env.local
    env: {
      VITE_APP_NAME: 'Front-App',
      VITE_APP_ENV: 'Test',
      VITE_API_BASE_URL: 'http://gateway.test',
      VITE_API_TIMEOUT_MS: '15000',
      VITE_FEATURE_API_LOGGING: 'false',
      VITE_FEATURE_QUERY_DEVTOOLS: 'false',
      VITE_FEATURE_ENVIRONMENT_BADGE: 'false',
      VITE_FEATURE_PAYMENT_TEST_HINTS: 'true',
      // The application's tests do not open WebSockets (the live updates have tests of their own)
      VITE_FEATURE_LIVE_UPDATES: 'false',
      // The sign-in pages without the test accounts (DevAccountsPanel has tests of its own)
      VITE_FEATURE_DEV_ACCOUNTS: 'false',
    },
  },
});
