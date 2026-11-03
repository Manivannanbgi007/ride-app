import { describe, expect, it } from 'vitest';

import { avatarTone, describePeriod, periodScope, tagTone } from '@/utils/reviewUtils';

describe('reviewUtils', () => {
  it('turns a period into the days the API is asked with', () => {
    const today = new Date(2026, 9, 8);

    expect(periodScope({ kind: '7' }, today)).toEqual({ from: '2026-10-02', to: '2026-10-08' });
    expect(periodScope({ kind: 'all' }, today)).toEqual({});
    expect(periodScope({ kind: 'custom', from: '2026-09-01', to: '' }, today)).toEqual({
      from: '2026-09-01',
    });
  });

  it('describes a period in words', () => {
    expect(describePeriod({})).toBe('All time');
    expect(describePeriod({ from: '2026-10-02', to: '2026-10-08' })).toBe(
      '2 Oct 2026 - 8 Oct 2026',
    );
  });

  it('gives the same name the same avatar colour', () => {
    expect(avatarTone('James Kowalski')).toBe(avatarTone('James Kowalski'));
  });

  it('colours tags by what they are about', () => {
    expect(tagTone('GOOD_SERVICE')).toBe('service');
    expect(tagTone('DELICIOUS')).toBe('praise');
    expect(tagTone('GOOD_VALUE')).toBe('other');
  });
});
