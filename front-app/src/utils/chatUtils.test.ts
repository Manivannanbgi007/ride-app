import { describe, expect, it } from 'vitest';

import { chatMessage, chatMessagePage } from '@/test/fixtures';
import {
  flattenMessagePages,
  formatConversationTime,
  formatDayLabel,
  mergeWithPending,
  newClientMessageId,
} from '@/utils/chatUtils';

const now = new Date(2026, 9, 8, 15, 0, 0);
const at = (day: number, hour: number, minute = 0) =>
  new Date(2026, 9, day, hour, minute).toISOString();

describe('formatConversationTime', () => {
  it('says how long ago within the hour', () => {
    expect(formatConversationTime(at(8, 14, 58), now)).toBe('2min ago');
    expect(formatConversationTime(new Date(now.getTime() - 20_000).toISOString(), now)).toBe(
      'Just now',
    );
  });

  it('shows the time today, "Yesterday", then the date', () => {
    expect(formatConversationTime(at(8, 2, 45), now)).toBe('02:45 AM');
    expect(formatConversationTime(at(7, 23, 59), now)).toBe('Yesterday');
    expect(formatConversationTime(at(3, 10), now)).toBe('3 Oct 2026');
  });
});

describe('formatDayLabel', () => {
  it('labels today, yesterday and older days', () => {
    expect(formatDayLabel(at(8, 1), now)).toBe('Today');
    expect(formatDayLabel(at(7, 20), now)).toBe('Yesterday');
    expect(formatDayLabel(at(1, 20), now)).toBe('1 Oct 2026');
  });
});

describe('flattenMessagePages', () => {
  it('joins pages oldest first, once each, ordered by id', () => {
    const newest = chatMessagePage([chatMessage({ id: 4 }), chatMessage({ id: 3 })], true, 3);
    const older = chatMessagePage([
      chatMessage({ id: 3, body: 'newer copy' }),
      chatMessage({ id: 1 }),
    ]);

    expect(flattenMessagePages([newest, older]).map((m) => m.id)).toEqual([1, 3, 4]);
    expect(flattenMessagePages(undefined)).toEqual([]);
  });

  it('lets a later page win for the same id (a read tick that arrived meanwhile)', () => {
    const first = chatMessagePage([chatMessage({ id: 1, readAt: null })]);
    const second = chatMessagePage([chatMessage({ id: 1, readAt: '2026-10-08T10:00:00Z' })]);

    expect(flattenMessagePages([first, second])[0].readAt).not.toBeNull();
  });
});

describe('mergeWithPending', () => {
  const own = { senderName: 'Admin', fromStaff: true };
  const pending = (clientMessageId: string) => ({
    clientMessageId,
    body: 'hello',
    createdAt: '2026-10-08T10:00:00Z',
    status: 'sending' as const,
  });

  it('puts unconfirmed messages after the server ones', () => {
    const merged = mergeWithPending([chatMessage({ id: 9 })], [pending('a')], own);

    expect(merged.map((m) => m.clientMessageId)).toEqual(['client-9', 'a']);
    expect(merged[1].pending?.status).toBe('sending');
  });

  it('drops a pending message the server already has (matched by client id)', () => {
    const merged = mergeWithPending(
      [chatMessage({ id: 9, clientMessageId: 'a' })],
      [pending('a')],
      own,
    );

    expect(merged).toHaveLength(1);
    expect(merged[0].pending).toBeUndefined();
  });
});

describe('newClientMessageId', () => {
  it('makes a different UUID each time', () => {
    const a = newClientMessageId();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(newClientMessageId()).not.toBe(a);
  });
});
