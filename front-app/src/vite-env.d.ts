/// <reference types="vite/client" />

/** The settings in the .env files (see .env for what each one means). Read them through utils/env.ts. */
interface ImportMetaEnv {
  readonly VITE_APP_NAME: string;
  readonly VITE_APP_ENV: string;
  readonly VITE_API_BASE_URL: string;
  readonly VITE_API_TIMEOUT_MS: string;
  readonly VITE_FEATURE_API_LOGGING: string;
  readonly VITE_FEATURE_QUERY_DEVTOOLS: string;
  readonly VITE_FEATURE_ENVIRONMENT_BADGE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
