import { describe, expect, it } from 'vitest';

import {
  activityLabel,
  activityRange,
  canGoForward,
  clampToToday,
  rollingDaysOf,
  rollingModeOf,
  rollingRange,
  shiftAnchor,
} from '@/utils/orderActivityUtils';

// Thursday, 8 October 2026
const TODAY = '2026-10-08';

describe('activityRange', () => {
  it('is the single day chosen', () => {
    expect(activityRange('day', '2026-10-03', TODAY)).toEqual({
      fromDate: '2026-10-03',
      toDate: '2026-10-03',
    });
  });

  it('runs a finished week from Monday to Sunday, whichever of its days was chosen', () => {
    const week = { fromDate: '2026-09-28', toDate: '2026-10-04' };
    expect(activityRange('week', '2026-09-28', TODAY)).toEqual(week);
    expect(activityRange('week', '2026-10-01', TODAY)).toEqual(week);
    expect(activityRange('week', '2026-10-04', TODAY)).toEqual(week);
  });

  it('covers a whole finished month, with its own length (also a leap February)', () => {
    expect(activityRange('month', '2026-09-15', TODAY)).toEqual({
      fromDate: '2026-09-01',
      toDate: '2026-09-30',
    });
    expect(activityRange('month', '2024-02-10', TODAY)).toEqual({
      fromDate: '2024-02-01',
      toDate: '2024-02-29',
    });
    expect(activityRange('month', '2026-02-10', TODAY).toDate).toBe('2026-02-28');
  });

  it('cuts the running week and month at today', () => {
    expect(activityRange('week', TODAY, TODAY)).toEqual({
      fromDate: '2026-10-05',
      toDate: '2026-10-08',
    });
    expect(activityRange('month', TODAY, TODAY)).toEqual({
      fromDate: '2026-10-01',
      toDate: '2026-10-08',
    });
  });

  it('handles a week that crosses a month and a year', () => {
    expect(activityRange('week', '2026-01-01', TODAY)).toEqual({
      fromDate: '2025-12-29',
      toDate: '2026-01-04',
    });
  });
});

describe('shiftAnchor and canGoForward', () => {
  it('steps by one day, week or month', () => {
    expect(shiftAnchor('day', '2026-10-03', -1, TODAY)).toBe('2026-10-02');
    expect(shiftAnchor('week', '2026-10-03', -1, TODAY)).toBe('2026-09-26');
    expect(shiftAnchor('month', '2026-03-31', -1, TODAY)).toBe('2026-02-28');
  });

  it('never goes past today', () => {
    expect(shiftAnchor('day', TODAY, 1, TODAY)).toBe(TODAY);
    expect(shiftAnchor('week', '2026-10-05', 1, TODAY)).toBe(TODAY);
    expect(shiftAnchor('month', '2026-09-30', 1, TODAY)).toBe(TODAY);
    expect(clampToToday('2027-01-01', TODAY)).toBe(TODAY);
  });

  it('allows going forward only while a later day, week or month has started', () => {
    expect(canGoForward('day', '2026-10-07', TODAY)).toBe(true);
    expect(canGoForward('day', TODAY, TODAY)).toBe(false);
    expect(canGoForward('week', '2026-10-01', TODAY)).toBe(true);
    expect(canGoForward('week', TODAY, TODAY)).toBe(false);
    expect(canGoForward('month', '2026-09-30', TODAY)).toBe(true);
    expect(canGoForward('month', TODAY, TODAY)).toBe(false);
  });

  it('is false when today is the last day of the week or month', () => {
    expect(canGoForward('week', '2026-10-08', '2026-10-11')).toBe(false);
    expect(canGoForward('month', '2026-10-01', '2026-10-31')).toBe(false);
  });
});

describe('activityLabel', () => {
  it('names a day, a week and a month', () => {
    expect(activityLabel('day', { fromDate: '2026-10-03', toDate: '2026-10-03' })).toBe(
      '3 Oct 2026',
    );
    expect(activityLabel('week', { fromDate: '2026-09-28', toDate: '2026-10-04' })).toBe(
      '28 Sept 2026 - 4 Oct 2026',
    );
    expect(activityLabel('month', { fromDate: '2026-09-01', toDate: '2026-09-30' })).toBe(
      'September 2026',
    );
  });
});

describe('the rolling ranges', () => {
  it('end today and count today in', () => {
    expect(rollingRange(7, TODAY)).toEqual({ fromDate: '2026-10-02', toDate: TODAY });
    // Across the start of a month
    expect(rollingRange(30, TODAY)).toEqual({ fromDate: '2026-09-09', toDate: TODAY });
    expect(rollingRange(90, '2026-01-15')).toEqual({
      fromDate: '2025-10-18',
      toDate: '2026-01-15',
    });
  });

  it('know their length, and a calendar day, week or month has none', () => {
    expect(rollingDaysOf(rollingModeOf(30))).toBe(30);
    expect(rollingDaysOf('last7')).toBe(7);
    expect(rollingDaysOf('month')).toBeNull();
  });
});
