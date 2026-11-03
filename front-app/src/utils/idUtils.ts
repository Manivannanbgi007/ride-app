/**
 * A new random UUID (version 4), e.g. for an idempotency key or a chat message's client id.
 *
 * `crypto.randomUUID()` exists only on secure pages (https, or http on localhost): opened by the machine's network
 * address (http://192.168.x.x) it is missing, so the same kind of UUID is made from `crypto.getRandomValues()`, which
 * every page has.
 */
export function newUuid(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  // The version (4) and variant (10xx) bits of RFC 9562
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
