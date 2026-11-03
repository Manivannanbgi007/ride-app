/**
 * The application's settings, read once from the build's environment files (.env + .env.<mode>).
 * Code reads `env.apiBaseUrl` and so on, never `import.meta.env` directly: this file is the one list of settings.
 */
export interface AppEnvironment {
  appName: string;
  /** Development, Demo, UAT, Pre-Production or Production */
  environmentName: string;
  apiBaseUrl: string;
  apiTimeoutMs: number;
  features: {
    apiLogging: boolean;
    queryDevtools: boolean;
    environmentBadge: boolean;
    /** The simulated payment provider's test values on the payment page (never in production) */
    paymentTestHints: boolean;
    /** Live updates over a WebSocket (orders, deliveries and so on appear without a refresh) */
    liveUpdates: boolean;
    /** The live map of an order uses simulated positions (services/mock) instead of delivery-service */
    fakeLiveTracking: boolean;
    /**
     * The local platform's test accounts under the sign-in forms, signed in with one click. Only while developing
     * (npm run dev): never part of a build, whatever the flag says (components/business/Auth/DevAccounts.tsx)
     */
    devAccounts: boolean;
  };
  /** The tiles of the live map of an order (Leaflet): OpenStreetMap's unless the environment names another server */
  map: {
    /** A {z}/{x}/{y} tile address, e.g. https://tile.openstreetmap.org/{z}/{x}/{y}.png */
    tileUrl: string;
    /** The credit the tile provider requires (HTML, shown in the map's corner) */
    attribution: string;
  };
}

type RawEnvironment = Record<string, string | boolean | undefined>;

/**
 * OpenStreetMap's own tile servers: free, no key, but for light use only (their tile usage policy:
 * https://operations.osmfoundation.org/policies/tiles/). A busy production site sets VITE_MAP_TILE_URL to its own or a
 * paid tile server.
 */
export const OPENSTREETMAP_TILES = {
  tileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
} as const;

/** A setting is missing or wrong: the application cannot start (shown on the page and in the console). */
export class ConfigurationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigurationError';
  }
}

export function readEnvironment(raw: RawEnvironment): AppEnvironment {
  const text = (name: string): string => {
    const value = raw[name];
    return typeof value === 'string' ? value.trim() : '';
  };
  const required = (name: string): string => {
    const value = text(name);
    if (!value) {
      throw new ConfigurationError(
        `${name} is not set: add it to the .env file of this environment.`,
      );
    }
    return value;
  };
  const flag = (name: string): boolean => text(name).toLowerCase() === 'true';

  const timeout = Number(required('VITE_API_TIMEOUT_MS'));
  if (!Number.isInteger(timeout) || timeout <= 0) {
    throw new ConfigurationError(
      'VITE_API_TIMEOUT_MS must be a number of milliseconds, e.g. 15000.',
    );
  }

  return {
    appName: required('VITE_APP_NAME'),
    environmentName: required('VITE_APP_ENV'),
    apiBaseUrl: required('VITE_API_BASE_URL'),
    apiTimeoutMs: timeout,
    features: {
      apiLogging: flag('VITE_FEATURE_API_LOGGING'),
      queryDevtools: flag('VITE_FEATURE_QUERY_DEVTOOLS'),
      environmentBadge: flag('VITE_FEATURE_ENVIRONMENT_BADGE'),
      paymentTestHints: flag('VITE_FEATURE_PAYMENT_TEST_HINTS'),
      liveUpdates: flag('VITE_FEATURE_LIVE_UPDATES'),
      fakeLiveTracking: flag('VITE_FEATURE_FAKE_LIVE_TRACKING'),
      devAccounts: flag('VITE_FEATURE_DEV_ACCOUNTS'),
    },
    map: {
      tileUrl: text('VITE_MAP_TILE_URL') || OPENSTREETMAP_TILES.tileUrl,
      // Another tile server comes with its own credit; OpenStreetMap's otherwise
      attribution: text('VITE_MAP_TILE_ATTRIBUTION') || OPENSTREETMAP_TILES.attribution,
    },
  };
}

/** Used when the environment file is wrong, so that the application can still say what is wrong. */
const UNCONFIGURED: AppEnvironment = {
  appName: 'Front-App',
  environmentName: 'Not configured',
  apiBaseUrl: '',
  apiTimeoutMs: 15_000,
  features: {
    apiLogging: false,
    queryDevtools: false,
    environmentBadge: true,
    paymentTestHints: false,
    liveUpdates: false,
    fakeLiveTracking: false,
    devAccounts: false,
  },
  map: { ...OPENSTREETMAP_TILES },
};

function loadEnvironment(): { environment: AppEnvironment; problem: string | null } {
  try {
    return {
      environment: readEnvironment(import.meta.env),
      problem: null,
    };
  } catch (error) {
    if (error instanceof ConfigurationError) {
      return { environment: UNCONFIGURED, problem: error.message };
    }
    throw error;
  }
}

const loaded = loadEnvironment();

export const env: AppEnvironment = loaded.environment;

/** Set when a setting is missing or wrong: main.tsx then shows it instead of the application. */
export const configurationProblem: string | null = loaded.problem;
