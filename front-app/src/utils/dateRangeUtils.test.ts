import { describe, expect, it } from 'vitest';

import {
  dayCountLabel,
  daysIn,
  formatRange,
  isIsoDay,
  matchingPreset,
  presetIdOf,
  presetRange,
  previousRangeOf,
  rangeProblem,
} from '@/utils/dateRangeUtils';

// Monday 2 November 2026
const today = new Date(2026, 10, 2);

describe('presetRange', () => {
  it('works the ready-made ranges out from today', () => {
    expect(presetRange('today', today)).toEqual({ fromDate: '2026-11-02', toDate: '2026-11-02' });
    expect(presetRange('yesterday', today)).toEqual({
      fromDate: '2026-11-01',
      toDate: '2026-11-01',
    });
    expect(presetRange('7', today)).toEqual({ fromDate: '2026-10-27', toDate: '2026-11-02' });
    expect(presetRange('90', today)).toEqual({ fromDate: '2026-08-05', toDate: '2026-11-02' });
    expect(presetRange('this-month', today)).toEqual({
      fromDate: '2026-11-01',
      toDate: '2026-11-02',
    });
    expect(presetRange('last-month', today)).toEqual({
      fromDate: '2026-10-01',
      toDate: '2026-10-31',
    });
  });

  it('crosses the start of a year', () => {
    expect(presetRange('last-month', new Date(2027, 0, 15))).toEqual({
      fromDate: '2026-12-01',
      toDate: '2026-12-31',
    });
    expect(presetRange('yesterday', new Date(2027, 0, 1)).fromDate).toBe('2026-12-31');
  });
});

describe('matchingPreset and presetIdOf', () => {
  it('names days that are a ready-made range today, the first match winning', () => {
    expect(matchingPreset({ fromDate: '2026-10-27', toDate: '2026-11-02' }, today)).toBe('7');
    expect(matchingPreset({ fromDate: '2026-11-02', toDate: '2026-11-02' }, today)).toBe('today');
    expect(matchingPreset({ fromDate: '2026-10-28', toDate: '2026-11-02' }, today)).toBe(undefined);
  });

  it('knows only the ready-made ranges', () => {
    expect(presetIdOf('last-month')).toBe('last-month');
    expect(presetIdOf('12')).toBe(undefined);
    expect(presetIdOf(null)).toBe(undefined);
  });
});

describe('daysIn and previousRangeOf', () => {
  it('counts both ends, and gives the same number of days just before', () => {
    const week = { fromDate: '2026-10-27', toDate: '2026-11-02' };
    expect(daysIn(week)).toBe(7);
    expect(previousRangeOf(week)).toEqual({ fromDate: '2026-10-20', toDate: '2026-10-26' });
    expect(previousRangeOf({ fromDate: '2026-11-01', toDate: '2026-11-01' })).toEqual({
      fromDate: '2026-10-31',
      toDate: '2026-10-31',
    });
  });
});

describe('rangeProblem', () => {
  it('accepts a range of up to 366 days that ends today at the latest', () => {
    expect(rangeProblem({ fromDate: '2025-11-02', toDate: '2026-11-02' }, '2026-11-02')).toBe(
      undefined,
    );
  });

  it('explains each way a range cannot be used', () => {
    expect(rangeProblem({ fromDate: '2026-10-01' }, '2026-11-02')).toBe(
      'Choose the first and the last day.',
    );
    expect(rangeProblem({ fromDate: '2026-10-01', toDate: 'soon' }, '2026-11-02')).toBe(
      'These are not real dates.',
    );
    expect(rangeProblem({ fromDate: '2026-10-09', toDate: '2026-10-01' }, '2026-11-02')).toBe(
      'The last day is before the first day.',
    );
    expect(rangeProblem({ fromDate: '2026-11-01', toDate: '2026-11-03' }, '2026-11-02')).toBe(
      'The days cannot be in the future.',
    );
    expect(rangeProblem({ fromDate: '2025-11-01', toDate: '2026-11-02' }, '2026-11-02')).toBe(
      'Choose at most 366 days.',
    );
  });

  it('knows real calendar days', () => {
    expect(isIsoDay('2028-02-29')).toBe(true);
    expect(isIsoDay('2026-02-29')).toBe(false);
    expect(isIsoDay('2026-1-05')).toBe(false);
  });
});

describe('formatRange and dayCountLabel', () => {
  it('writes a range as short as it stays clear', () => {
    expect(formatRange({ fromDate: '2026-10-30', toDate: '2026-10-30' })).toBe('Fri, 30 Oct 2026');
    expect(formatRange({ fromDate: '2026-10-03', toDate: '2026-10-09' })).toBe('3 – 9 Oct 2026');
    expect(formatRange({ fromDate: '2026-10-27', toDate: '2026-11-02' })).toBe(
      '27 Oct – 2 Nov 2026',
    );
    expect(formatRange({ fromDate: '2025-12-28', toDate: '2026-01-03' })).toBe(
      '28 Dec 2025 – 3 Jan 2026',
    );
  });

  it('counts the days in words', () => {
    expect(dayCountLabel(1)).toBe('1 day');
    expect(dayCountLabel(366)).toBe('366 days');
  });
});
