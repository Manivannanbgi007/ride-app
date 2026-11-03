import { describe, expect, it } from 'vitest';

import { newUuid } from '@/utils/idUtils';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newUuid', () => {
  it('makes a different version 4 UUID each time', () => {
    const first = newUuid();
    expect(first).toMatch(UUID_V4);
    expect(newUuid()).not.toBe(first);
  });

  it('still makes one on a page opened by the network address, where randomUUID is missing', () => {
    // crypto.randomUUID is only defined on secure pages (https, localhost): take it away, as such a page has it
    const original = Object.getOwnPropertyDescriptor(crypto, 'randomUUID');
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      const first = newUuid();
      expect(first).toMatch(UUID_V4);
      expect(newUuid()).not.toBe(first);
    } finally {
      if (original) {
        Object.defineProperty(crypto, 'randomUUID', original);
      } else {
        Reflect.deleteProperty(crypto, 'randomUUID');
      }
    }
  });
});
