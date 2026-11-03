/** The path of the live connection on the API Gateway (it passes it on to notification-service). */
export const LIVE_PATH = '/ws/v1/live';

/**
 * The address of the live connection: the API Gateway's address with ws:// (or wss:// for https) instead of
 * http://, and the access token of the person it is for. A browser cannot set headers on a WebSocket, so the token
 * travels in the query (notification-service accepts it there, and only for this path); never write this address to
 * a log.
 */
export function liveUrl(apiBaseUrl: string, accessToken: string): string {
  const base = apiBaseUrl.replace(/\/+$/, '').replace(/^http/, 'ws');
  const query = new URLSearchParams({ access_token: accessToken });
  return `${base}${LIVE_PATH}?${query.toString()}`;
}
