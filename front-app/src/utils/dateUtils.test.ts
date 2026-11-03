import { describe, expect, it } from 'vitest';

import { formatRelativeTime } from '@/utils/dateUtils';

const NOW = new Date('2026-10-09T12:00:00Z');
const ago = (seconds: number) => new Date(NOW.getTime() - seconds * 1000).toISOString();

describe('formatRelativeTime', () => {
  it('says "just now" under a minute', () => {
    expect(formatRelativeTime(ago(20), NOW)).toBe('just now');
    // A clock a little ahead of the server's never shows a negative time
    expect(formatRelativeTime(ago(-5), NOW)).toBe('just now');
  });

  it('counts minutes, hours and days', () => {
    expect(formatRelativeTime(ago(60), NOW)).toBe('1 minute ago');
    expect(formatRelativeTime(ago(45 * 60), NOW)).toBe('45 minutes ago');
    expect(formatRelativeTime(ago(3600), NOW)).toBe('1 hour ago');
    expect(formatRelativeTime(ago(5 * 3600 + 120), NOW)).toBe('5 hours ago');
    expect(formatRelativeTime(ago(2 * 86_400), NOW)).toBe('2 days ago');
  });

  it('shows the date after 30 days', () => {
    expect(formatRelativeTime('2026-06-24T10:15:00Z', NOW)).toBe('24 Jun 2026');
  });
});
