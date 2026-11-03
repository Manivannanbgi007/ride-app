import { vi } from 'vitest';

type Success = (position: GeolocationPosition) => void;
type Failure = (error: GeolocationPositionError) => void;

interface FakeFix {
  lat: number;
  lng: number;
  accuracy?: number;
  heading?: number | null;
}

/** A position as the browser reports it. */
export function positionAt({
  lat,
  lng,
  accuracy = 10,
  heading = null,
}: FakeFix): GeolocationPosition {
  const coords = {
    latitude: lat,
    longitude: lng,
    accuracy,
    heading,
    altitude: null,
    altitudeAccuracy: null,
    speed: null,
  } as GeolocationCoordinates;
  return { coords, timestamp: Date.now() } as GeolocationPosition;
}

/** A browser location error: 1 permission denied, 2 position unavailable, 3 timeout. */
export function geolocationError(code: 1 | 2 | 3): GeolocationPositionError {
  return {
    code,
    message: 'test',
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  };
}

/**
 * A phone's location for jsdom (it has none): watches and one-off requests are recorded, and the test decides
 * what the phone reports (report, fail). `secure: false` plays a page served over plain http.
 */
export function installFakeGeolocation({ secure = true }: { secure?: boolean } = {}) {
  const watches = new Map<number, { success: Success; failure?: Failure }>();
  let nextId = 1;
  const geolocation = {
    watchPosition: vi.fn((success: Success, failure?: Failure) => {
      const id = nextId++;
      watches.set(id, { success, failure });
      return id;
    }),
    clearWatch: vi.fn((id: number) => {
      watches.delete(id);
    }),
    getCurrentPosition: vi.fn<(success: Success, failure?: Failure) => void>(),
  };
  Object.defineProperty(navigator, 'geolocation', { value: geolocation, configurable: true });
  Object.defineProperty(window, 'isSecureContext', { value: secure, configurable: true });

  return {
    geolocation,
    /** How many watches are running */
    activeWatches: () => watches.size,
    /** The phone reports this position to every running watch */
    report: (fix: FakeFix) => {
      for (const { success } of [...watches.values()]) {
        success(positionAt(fix));
      }
    },
    /** Every running watch gets this error */
    fail: (code: 1 | 2 | 3) => {
      for (const { failure } of [...watches.values()]) {
        failure?.(geolocationError(code));
      }
    },
  };
}

/** Back to jsdom's own (no location). */
export function removeFakeGeolocation() {
  Reflect.deleteProperty(navigator, 'geolocation');
  Reflect.deleteProperty(window, 'isSecureContext');
}
