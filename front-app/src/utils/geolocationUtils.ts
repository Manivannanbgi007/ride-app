/** GeolocationPositionError.PERMISSION_DENIED: the person (or the browser's settings) said no */
export const PERMISSION_DENIED = 1;

/**
 * Whether this page can ask for the device's position: 'insecure' outside https (browsers refuse every request
 * there; localhost counts as secure), 'unsupported' in a browser without location.
 */
export function geolocationSupport(): 'ok' | 'unsupported' | 'insecure' {
  if (!window.isSecureContext) {
    return 'insecure';
  }
  return 'geolocation' in navigator ? 'ok' : 'unsupported';
}

/** Degrees kept to 6 decimals (about 10 cm): what the services store. */
export function roundCoordinate(degrees: number): number {
  return Math.round(degrees * 1_000_000) / 1_000_000;
}
