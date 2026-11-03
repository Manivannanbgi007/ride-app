import { describe, expect, it } from 'vitest';

import { passwordStrength } from '@/utils/passwordStrength';

describe('passwordStrength', () => {
  it('has nothing to say about an empty password', () => {
    expect(passwordStrength('')).toEqual({ level: 0, label: '' });
  });

  it('calls a password shorter than 8 characters weak, whatever it contains', () => {
    expect(passwordStrength('a').level).toBe(1);
    expect(passwordStrength('Ab1!xyz').level).toBe(1);
    expect(passwordStrength('Ab1!xyz').label).toBe('Weak');
  });

  it('rates longer and more varied passwords higher', () => {
    expect(passwordStrength('abcdefgh').level).toBe(1);
    expect(passwordStrength('abc12345').level).toBe(1);
    expect(passwordStrength('Eater1234')).toEqual({ level: 2, label: 'Fair' });
    expect(passwordStrength('Eater12345678')).toEqual({ level: 3, label: 'Good' });
    expect(passwordStrength('Eater1234!longer')).toEqual({ level: 4, label: 'Strong' });
  });

  it('never goes beyond the four segments of the meter', () => {
    expect(passwordStrength('A-very-Long-passphrase-with-Numbers-1234-and-symbols!').level).toBe(4);
  });
});
