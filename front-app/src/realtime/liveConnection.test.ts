import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LiveConnection, type LiveStatus, type SocketLike } from '@/realtime/liveConnection';
import type { LiveEvent } from '@/types/live';

/** Stands in for the browser's WebSocket: the test decides what the "server" does. */
class FakeSocket implements SocketLike {
  static all: FakeSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((message: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  readonly url: string;

  constructor(url: string) {
    this.url = url;
    FakeSocket.all.push(this);
  }

  close(): void {
    this.closed = true;
    this.onclose?.();
  }

  send(message: object): void {
    this.onmessage?.({ data: JSON.stringify(message) });
  }

  static get latest(): FakeSocket {
    return FakeSocket.all[FakeSocket.all.length - 1];
  }
}

const event = (id: string, overrides: Partial<LiveEvent> = {}): LiveEvent => ({
  id,
  type: 'ORDER_STATUS_CHANGED',
  occurredAt: '2026-10-09T10:00:00Z',
  orderId: 5,
  status: 'PREPARING',
  ...overrides,
});

describe('LiveConnection', () => {
  const events: LiveEvent[] = [];
  const statuses: LiveStatus[] = [];
  const onResync = vi.fn();
  let connection: LiveConnection;

  const make = (extra: Partial<ConstructorParameters<typeof LiveConnection>[0]> = {}) =>
    new LiveConnection({
      url: () => 'ws://gateway/ws/v1/live?access_token=token-of-user-7',
      onEvent: (received) => events.push(received),
      onStatus: (status) => statuses.push(status),
      onResync,
      createSocket: (url) => new FakeSocket(url),
      initialDelayMs: 1_000,
      maxDelayMs: 8_000,
      silenceLimitMs: 60_000,
      random: () => 1, // the longest wait of each range: predictable
      ...extra,
    });

  beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.all = [];
    events.length = 0;
    statuses.length = 0;
    onResync.mockClear();
    connection = make();
  });

  afterEach(() => {
    connection.stop();
    vi.useRealTimers();
  });

  it('connects, and is open once the server has accepted the connection', () => {
    connection.start();
    expect(statuses).toEqual(['connecting']);
    expect(FakeSocket.latest.url).toBe('ws://gateway/ws/v1/live?access_token=token-of-user-7');

    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });

    expect(statuses).toEqual(['connecting', 'open']);
    expect(onResync).not.toHaveBeenCalled(); // the first connection has nothing to catch up on
  });

  it('passes events on, once each, and ignores heartbeats and anything it does not know', () => {
    connection.start();
    const socket = FakeSocket.latest;
    socket.send({ kind: 'HELLO', at: 'now' });

    socket.send({ kind: 'EVENT', at: 'now', event: event('e1') });
    socket.send({ kind: 'EVENT', at: 'now', event: event('e1') }); // the same event again
    socket.send({ kind: 'HEARTBEAT', at: 'now' });
    socket.send({ kind: 'EVENT', at: 'now', event: { id: 'e2', type: 'SOMETHING_NEW' } });
    socket.send({ kind: 'SOMETHING_NEW' });
    socket.onmessage?.({ data: 'not json {' });
    socket.onmessage?.({ data: new ArrayBuffer(2) });
    socket.send({ kind: 'EVENT', at: 'now', event: event('e3', { status: 'READY_FOR_PICKUP' }) });

    expect(events.map((received) => received.id)).toEqual(['e1', 'e3']);
  });

  it('reconnects after a drop, waiting longer each time, and starts over once it is accepted', () => {
    connection.start();
    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });

    // 1st drop: waits 1 s; the retry fails at once: waits 2 s; then 4 s; never more than the 8 s limit
    for (const wait of [1_000, 2_000, 4_000, 8_000, 8_000]) {
      const before = FakeSocket.all.length;
      FakeSocket.latest.close();
      expect(statuses.at(-1)).toBe('reconnecting');
      vi.advanceTimersByTime(wait - 1);
      expect(FakeSocket.all).toHaveLength(before); // not yet
      vi.advanceTimersByTime(1);
      expect(FakeSocket.all).toHaveLength(before + 1);
    }

    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });
    expect(statuses.at(-1)).toBe('open');
    FakeSocket.latest.close();
    vi.advanceTimersByTime(1_000); // back to the short wait
    expect(FakeSocket.all).toHaveLength(7);
  });

  it('waits a random part of the range, so browsers do not all return together', () => {
    connection.stop();
    connection = make({ random: () => 0 });
    connection.start();
    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });

    FakeSocket.latest.close();
    vi.advanceTimersByTime(499);
    expect(FakeSocket.all).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(FakeSocket.all).toHaveLength(2); // half of the 1 s ceiling
  });

  it('asks for everything again when it is back after a drop (events may have been missed)', () => {
    connection.start();
    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });
    FakeSocket.latest.close();
    vi.advanceTimersByTime(1_000);
    expect(onResync).not.toHaveBeenCalled(); // not before the server has accepted it again

    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });

    expect(onResync).toHaveBeenCalledTimes(1);
  });

  it('drops a connection that has gone silent, and keeps one that hears the heartbeat', () => {
    connection.start();
    const first = FakeSocket.latest;
    first.send({ kind: 'HELLO', at: 'now' });

    vi.advanceTimersByTime(50_000);
    first.send({ kind: 'HEARTBEAT', at: 'now' }); // alive: the silence timer starts over
    vi.advanceTimersByTime(50_000);
    expect(first.closed).toBe(false);

    vi.advanceTimersByTime(10_000); // 60 s without a word
    expect(first.closed).toBe(true);
    expect(statuses.at(-1)).toBe('reconnecting');
    vi.advanceTimersByTime(1_000);
    expect(FakeSocket.all).toHaveLength(2);
  });

  it('does not listen to a connection it has already let go of', () => {
    connection.start();
    const first = FakeSocket.latest;
    first.send({ kind: 'HELLO', at: 'now' });
    vi.advanceTimersByTime(60_000); // silent: dropped
    vi.advanceTimersByTime(1_000); // the new one

    first.send({ kind: 'EVENT', at: 'now', event: event('late') });

    expect(events).toEqual([]);
  });

  it('tries again at once when the network is back, instead of waiting out the delay', () => {
    connection.start();
    FakeSocket.latest.send({ kind: 'HELLO', at: 'now' });
    FakeSocket.latest.close();
    expect(FakeSocket.all).toHaveLength(1);

    window.dispatchEvent(new Event('online'));

    expect(FakeSocket.all).toHaveLength(2);
  });

  it('keeps trying when a connection cannot even be created, and stops for good on stop()', () => {
    let failures = 2;
    connection.stop();
    connection = make({
      createSocket: (url) => {
        if (failures-- > 0) {
          throw new Error('blocked');
        }
        return new FakeSocket(url);
      },
    });
    connection.start();
    expect(statuses.at(-1)).toBe('reconnecting');
    vi.advanceTimersByTime(1_000);
    vi.advanceTimersByTime(2_000);
    expect(FakeSocket.all).toHaveLength(1);

    connection.stop();
    expect(statuses.at(-1)).toBe('closed');
    expect(FakeSocket.latest.closed).toBe(true);
    vi.advanceTimersByTime(120_000);
    expect(FakeSocket.all).toHaveLength(1); // nothing is retried after stop()
  });

  it('is started once however often start() is called', () => {
    connection.start();
    connection.start();

    expect(FakeSocket.all).toHaveLength(1);
  });
});
