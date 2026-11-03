import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';

import {
  formatCountdown,
  readCards,
  readPendingAction,
  resolutionAfterActionError,
  readReply,
  safeAppPath,
  safeImageUrl,
  toolLabel,
  toolsUsedText,
} from '@/utils/assistantUtils';

describe('safeAppPath', () => {
  it.each(['/orders', '/admin/orders/5?status=PLACED', '/restaurants/3#menu'])(
    'accepts %s',
    (path) => {
      expect(safeAppPath(path)).toBe(path);
    },
  );

  it.each([
    'orders',
    '//evil.example',
    '/a//b',
    'https://evil.example',
    'javascript:alert(1)',
    '/redirect?to=http://evil.example',
    '/a\\b',
    '/with space',
    '/tab\there',
    '',
    null,
    undefined,
    42,
  ])('rejects %s', (path) => {
    expect(safeAppPath(path)).toBeNull();
  });
});

describe('safeImageUrl', () => {
  it('allows web addresses and site paths only', () => {
    expect(safeImageUrl('https://cdn.example/a.png')).toBe('https://cdn.example/a.png');
    expect(safeImageUrl('/images/a.png')).toBe('/images/a.png');
    expect(safeImageUrl('javascript:alert(1)')).toBeNull();
    expect(safeImageUrl('data:image/png;base64,AAAA')).toBeNull();
    expect(safeImageUrl('//evil.example/a.png')).toBeNull();
    expect(safeImageUrl(null)).toBeNull();
  });
});

describe('reading answers defensively', () => {
  it('turns missing parts into empty ones and drops what is not understood', () => {
    expect(readReply(undefined)).toEqual({
      text: '',
      cards: [],
      links: [],
      pendingAction: null,
      toolsUsed: [],
    });
    const reply = readReply({
      text: 'Hi',
      cards: [{ kind: 'chart' }, { kind: 'stats', title: 'T' }, 'nonsense', null],
      links: [
        { label: 'Ok', path: '/orders' },
        { label: 'Bad', path: 'https://x.y' },
        { path: '/a' },
      ],
      toolsUsed: ['a', 5, null],
      unknownField: true,
    });
    expect(reply.cards).toEqual([{ kind: 'stats', title: 'T', stats: [] }]);
    expect(reply.links).toEqual([{ label: 'Ok', path: '/orders' }]);
    expect(reply.toolsUsed).toEqual(['a', '5']);
  });

  it('keeps table cells for the declared columns only', () => {
    const [card] = readCards([
      {
        kind: 'table',
        title: 'T',
        columns: [{ key: 'a', label: 'A', align: 'right' }, { key: 'b' }],
        rows: [{ a: 1, b: { nested: true }, extra: 'x' }],
      },
    ]);
    expect(card).toEqual({
      kind: 'table',
      title: 'T',
      columns: [
        { key: 'a', label: 'A', align: 'right' },
        { key: 'b', label: 'b', align: 'left' },
      ],
      rows: [{ a: 1, b: null }],
    });
  });

  it('refuses a proposal without an id, a summary or a valid expiry', () => {
    expect(readPendingAction({ actionId: 'a', summary: {}, expiresAt: 'soon' })).toBeNull();
    expect(readPendingAction({ summary: {}, expiresAt: '2026-10-10T10:00:00Z' })).toBeNull();
    expect(
      readPendingAction({ actionId: 'a', summary: {}, expiresAt: '2026-10-10T10:00:00Z' }),
    ).toMatchObject({ actionId: 'a', summary: { items: [], warnings: [], currency: 'INR' } });
  });

  it('reads a change other than an order from its details, keeping only complete rows', () => {
    expect(
      readPendingAction({
        actionId: 'act_1',
        kind: 'CANCEL_MY_ORDER',
        summary: null,
        expiresAt: '2026-10-10T10:00:00Z',
        details: {
          title: ' Cancel order #42 ',
          rows: [{ label: 'Total', value: '562.90 INR' }, { label: 'Reason' }, 'junk'],
          note: '',
          confirmLabel: 'Cancel order',
        },
      }),
    ).toEqual({
      actionId: 'act_1',
      kind: 'CANCEL_MY_ORDER',
      expiresAt: '2026-10-10T10:00:00Z',
      details: {
        title: 'Cancel order #42',
        rows: [{ label: 'Total', value: '562.90 INR' }],
        note: null,
        confirmLabel: 'Cancel order',
      },
    });
  });

  it('refuses a change without a title, a button text or a kind', () => {
    const base = { actionId: 'act_1', expiresAt: '2026-10-10T10:00:00Z' };
    const details = { title: 'Accept order #42', rows: [], confirmLabel: 'Accept order' };
    expect(
      readPendingAction({ ...base, kind: 'ACCEPT_ORDER', details: { ...details, title: '' } }),
    ).toBeNull();
    expect(
      readPendingAction({
        ...base,
        kind: 'ACCEPT_ORDER',
        details: { ...details, confirmLabel: ' ' },
      }),
    ).toBeNull();
    expect(readPendingAction({ ...base, details })).toBeNull();
    expect(readPendingAction({ ...base, kind: 'PLACE_ORDER', details })).toBeNull();
  });
});

describe('a failed confirm', () => {
  const failure = (status: number, detail?: string) =>
    new AxiosError('failed', 'ERR_BAD_RESPONSE', undefined, null, {
      status,
      statusText: '',
      data: detail ? { detail } : {},
      headers: new AxiosHeaders(),
      config: { headers: new AxiosHeaders() },
    });

  it('ends a refused change with the platform reason, or a plain one', () => {
    expect(
      resolutionAfterActionError(failure(422, 'The order is ACCEPTED'), 'confirm', 'change'),
    ).toEqual({ status: 'failed', message: 'The order is ACCEPTED' });
    expect(resolutionAfterActionError(failure(409), 'confirm', 'change')).toEqual({
      status: 'failed',
      message: 'This can no longer be done: something changed. Ask the assistant again.',
    });
    expect(resolutionAfterActionError(failure(404), 'confirm', 'change')).toEqual({
      status: 'failed',
      message: 'This could not be done. Ask the assistant again.',
    });
  });

  it('keeps the card usable after a temporary failure', () => {
    expect(resolutionAfterActionError(failure(503), 'confirm', 'change')).toBeNull();
    expect(resolutionAfterActionError(failure(410), 'confirm', 'change')).toEqual({
      status: 'expired',
    });
  });
});

describe('labels', () => {
  it('translates tool names and prettifies unknown ones', () => {
    expect(toolLabel('admin_order_stats')).toBe('Order statistics');
    expect(toolLabel('brand_new_tool')).toBe('Brand new tool');
    expect(toolsUsedText(['admin_list_orders', 'owner_orders'])).toBe('Orders');
    expect(toolLabel('partner_prepare_delivery_step')).toBe('Delivery update');
  });

  it('formats the countdown', () => {
    expect(formatCountdown(522_100)).toBe('8:43');
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(-5000)).toBe('0:00');
  });
});
