import { describe, expect, it } from 'vitest';

import { toCsv } from '@/utils/csvUtils';
import { formatDate, parseIsoDate, toIsoDate } from '@/utils/dateUtils';
import { formatCurrency, formatNumber, formatPercent, initialsOf } from '@/utils/formatUtils';

describe('formatting', () => {
  it('shows unknown figures as a dash, never as zero', () => {
    expect(formatNumber(null)).toBe('—');
    expect(formatCurrency(undefined, 'INR')).toBe('—');
    expect(formatPercent(null)).toBe('—');
  });

  it('shows numbers and money the Indian way', () => {
    expect(formatNumber(1234567)).toBe('12,34,567');
    expect(formatCurrency(18591.1, 'INR')).toBe('₹18,591.10');
    expect(formatPercent(81.6)).toBe('82%');
  });

  it('makes initials for avatars', () => {
    expect(initialsOf('Platform Administrator')).toBe('PA');
    expect(initialsOf('  biryani house  ')).toBe('BH');
  });

  it('reads API dates as calendar days, without shifting them', () => {
    expect(toIsoDate(parseIsoDate('2026-10-08'))).toBe('2026-10-08');
    expect(formatDate('2026-10-08')).toBe('8 Oct 2026');
  });

  it('quotes CSV values that contain a comma, quote or line break', () => {
    expect(
      toCsv([
        ['Date', 'Name'],
        ['2026-10-08', 'Pizza, "Large"'],
        [null, 3],
      ]),
    ).toBe('Date,Name\r\n2026-10-08,"Pizza, ""Large"""\r\n,3');
  });
});
