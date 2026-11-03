import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type * as EnvModule from '@/utils/env';

import { LiveUpdatesProvider } from '@/realtime/LiveUpdatesProvider';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import { tokenStore } from '@/api/tokenStore';
import {
  customerSignedIn,
  customerSignedOut,
  ownerSignedIn,
  ownerSignedOut,
} from '@/store/slices/sessionSlice';
import { store as appStore } from '@/store/store';
import { renderWithProviders } from '@/test/renderWithProviders';
import { QUERY_KEYS } from '@/utils/queryKeys';

// The live updates are switched off in the other tests: here they are on, against a stand-in WebSocket
vi.mock('@/utils/env', async (importOriginal) => {
  const actual = await importOriginal<typeof EnvModule>();
  return {
    ...actual,
    env: {
      ...actual.env,
      apiBaseUrl: 'http://gateway.test',
      features: { ...actual.env.features, liveUpdates: true },
    },
  };
});

class FakeWebSocket {
  static all: FakeWebSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((message: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  readonly url: string;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.all.push(this);
  }

  close(): void {
    this.closed = true;
    this.onclose?.();
  }

  send(message: object): void {
    this.onmessage?.({ data: JSON.stringify(message) });
  }

  static get latest(): FakeWebSocket {
    return FakeWebSocket.all[FakeWebSocket.all.length - 1];
  }
}

function Status() {
  return <p>connection: {useLiveStatus()}</p>;
}

const orderEvent = (id: string, status: string) => ({
  kind: 'EVENT',
  at: 'now',
  event: {
    id,
    type: 'ORDER_STATUS_CHANGED',
    occurredAt: '2026-10-09T10:00:00Z',
    orderId: 5,
    status,
  },
});

/** A signed-in customer with an access token in memory, in the application's store too (the token is read there) */
const signInCustomer = (id: number) => {
  appStore.dispatch(
    customerSignedIn({ id, role: 'CUSTOMER', fullName: 'Asha Nair', email: 'a@example.com' }),
  );
  tokenStore.set('customer', {
    value: `customer-${id}-token`,
    expiresAt: Number.MAX_SAFE_INTEGER,
    userId: id,
  });
};

const renderProvider = (route: string, signedIn = false) => {
  if (signedIn) {
    signInCustomer(7);
  }
  const view = renderWithProviders(
    <LiveUpdatesProvider>
      <Status />
    </LiveUpdatesProvider>,
    { route },
  );
  if (signedIn) {
    view.store.dispatch(
      customerSignedIn({
        id: 7,
        role: 'CUSTOMER',
        fullName: 'Priya Sharma',
        email: 'p@example.com',
      }),
    );
  }
  return view;
};

describe('LiveUpdatesProvider', () => {
  beforeEach(() => {
    FakeWebSocket.all = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    appStore.dispatch(customerSignedOut());
    appStore.dispatch(ownerSignedOut());
    tokenStore.clear('customer');
    tokenStore.clear('owner');
  });

  it("refreshes a restaurant owner's orders, and tells them only of new and cancelled orders, in their words", async () => {
    const owner = {
      id: 3,
      role: 'RESTAURANT_ADMIN' as const,
      fullName: 'Arjun',
      email: 'o@example.com',
    };
    appStore.dispatch(ownerSignedIn(owner));
    tokenStore.set('owner', {
      value: 'owner-3-token',
      expiresAt: Number.MAX_SAFE_INTEGER,
      userId: 3,
    });
    const { queryClient, store } = renderWithProviders(
      <LiveUpdatesProvider>
        <Status />
      </LiveUpdatesProvider>,
      { route: '/owner/orders' },
    );
    store.dispatch(ownerSignedIn(owner));
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const ownerEvent = (id: string, status: string) => ({
      kind: 'EVENT',
      at: 'now',
      event: {
        id,
        type: 'ORDER_STATUS_CHANGED',
        occurredAt: 'now',
        orderId: 5,
        restaurantId: 1,
        status,
      },
    });

    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    expect(FakeWebSocket.latest.url).toBe(
      'ws://gateway.test/ws/v1/live?access_token=owner-3-token',
    );
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });

    // Their own step: the page updates, but no second message (the button already said so)
    FakeWebSocket.latest.send(ownerEvent('e1', 'ACCEPTED'));
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.owner.ordersAll(3, 1) }),
    );
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: QUERY_KEYS.customer.allOrders(3) });
    expect(screen.queryByText(/Order #5: /)).not.toBeInTheDocument();

    // What they did not do themselves
    FakeWebSocket.latest.send(ownerEvent('e2', 'PLACED'));
    expect(await screen.findByText('New order #5: accept it or reject it.')).toBeInTheDocument();
    FakeWebSocket.latest.send(ownerEvent('e3', 'CANCELLED'));
    expect(await screen.findByText('Order #5 was cancelled.')).toBeInTheDocument();
    // Never the customer's wording
    expect(screen.queryByText(/Order #5: /)).not.toBeInTheDocument();
  });

  it('connects as the admin in the admin area, and refreshes the dashboard and order lists on an event', async () => {
    const { queryClient } = renderProvider('/admin/dashboard');
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    expect(screen.getByText('connection: connecting')).toBeInTheDocument();
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    // The admin area's access token (test/setup.ts), never the user's id or role
    expect(FakeWebSocket.latest.url).toBe(
      'ws://gateway.test/ws/v1/live?access_token=test-admin-token',
    );
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });
    expect(await screen.findByText('connection: open')).toBeInTheDocument();

    FakeWebSocket.latest.send(orderEvent('e1', 'PREPARING'));
    FakeWebSocket.latest.send(orderEvent('e2', 'READY_FOR_PICKUP'));

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.dashboardAll }),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.orders.all });
    // Two events in the same moment: each screen's data is read once
    expect(
      invalidate.mock.calls.filter(([filters]) => filters?.queryKey === QUERY_KEYS.dashboardAll),
    ).toHaveLength(1);
    // An admin follows the whole platform: no pop-up for every order
    expect(screen.queryByText(/Order #5/)).not.toBeInTheDocument();
  });

  it('connects as the signed-in customer elsewhere, refreshes only their data and says what happened to their order', async () => {
    const { queryClient } = renderProvider('/my-orders', true);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    expect(FakeWebSocket.latest.url).toBe(
      'ws://gateway.test/ws/v1/live?access_token=customer-7-token',
    );
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });
    FakeWebSocket.latest.send(orderEvent('e1', 'OUT_FOR_DELIVERY'));

    expect(await screen.findByText('Order #5: Out for delivery')).toBeInTheDocument();
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.customer.allOrders(7) }),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.inboxAll });
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: QUERY_KEYS.dashboardAll });
  });

  it('reads the support inbox again when a chat message or a read arrives for an admin', async () => {
    const { queryClient } = renderProvider('/admin/chat');
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });

    FakeWebSocket.latest.send({
      kind: 'EVENT',
      at: 'now',
      event: {
        id: 'c1',
        type: 'CHAT_MESSAGE',
        occurredAt: '2026-10-09T10:00:00Z',
        conversationId: 31,
        messageId: 99,
        fromStaff: false,
      },
    });
    FakeWebSocket.latest.send({
      kind: 'EVENT',
      at: 'now',
      event: {
        id: 'c2',
        type: 'CHAT_READ',
        occurredAt: '2026-10-09T10:00:01Z',
        conversationId: 31,
        readByStaff: false,
        upToMessageId: 99,
      },
    });

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.chat.staffAll }),
    );
    // Both events in the same moment: the inbox, the unread number and the messages are read once
    expect(
      invalidate.mock.calls.filter(([filters]) => filters?.queryKey === QUERY_KEYS.chat.staffAll),
    ).toHaveLength(1);
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: QUERY_KEYS.dashboardAll });
  });

  it("reads a customer's own conversation again when Support writes, and only theirs", async () => {
    const { queryClient } = renderProvider('/support', true);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });

    FakeWebSocket.latest.send({
      kind: 'EVENT',
      at: 'now',
      event: {
        id: 'c3',
        type: 'CHAT_MESSAGE',
        occurredAt: '2026-10-09T10:00:00Z',
        conversationId: 31,
        messageId: 100,
        fromStaff: true,
      },
    });

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.chat.mineAll(7) }),
    );
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: QUERY_KEYS.chat.staffAll });
    // A chat message is not an order: no order toast
    expect(screen.queryByText(/Order #/)).not.toBeInTheDocument();
  });

  it('opens no connection for a visitor who has not signed in', () => {
    renderProvider('/');

    expect(FakeWebSocket.all).toHaveLength(0);
    expect(screen.getByText('connection: closed')).toBeInTheDocument();
  });

  it('opens the signed-in customer connection when they sign in, and closes it when they leave', async () => {
    const { store, unmount } = renderProvider('/');
    expect(FakeWebSocket.all).toHaveLength(0);

    signInCustomer(7);
    store.dispatch(
      customerSignedIn({
        id: 7,
        role: 'CUSTOMER',
        fullName: 'Priya Sharma',
        email: 'p@example.com',
      }),
    );
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    const socket = FakeWebSocket.latest;

    unmount();

    expect(socket.closed).toBe(true);
  });

  it('closes the connection of the previous user when another user signs in', async () => {
    const { store } = renderProvider('/', true);
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    const first = FakeWebSocket.latest;

    signInCustomer(8);
    store.dispatch(
      customerSignedIn({ id: 8, role: 'CUSTOMER', fullName: 'Asha Nair', email: 'a@example.com' }),
    );

    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(2));
    expect(first.closed).toBe(true);
    expect(FakeWebSocket.latest.url).toContain('access_token=customer-8-token');
  });

  it('reads everything on screen again after the connection was lost and came back', async () => {
    const { queryClient } = renderProvider('/admin/dashboard');
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(1));
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });
    FakeWebSocket.latest.close();
    await screen.findByText('connection: reconnecting');

    await waitFor(() => expect(FakeWebSocket.all).toHaveLength(2), { timeout: 3_000 });
    FakeWebSocket.latest.send({ kind: 'HELLO', at: 'now' });

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith(undefined));
    expect(screen.getByText('connection: open')).toBeInTheDocument();
  });
});
