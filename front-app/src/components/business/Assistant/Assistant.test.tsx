import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { toApiError, type ApiError } from '@/api/apiErrorHandler';
import { AssistantWidget } from '@/components/business/Assistant';
import { assistantService } from '@/services/assistantService';
import { makeStore } from '@/store/store';
import { renderWithProviders } from '@/test/renderWithProviders';
import type {
  AssistantChatResponse,
  AssistantReply,
  ChangePendingAction,
  ConfirmActionResponse,
  OrderPendingAction,
  PendingAction,
} from '@/types/assistant';
import type { UserRole } from '@/types/user';

vi.mock('@/services/assistantService', () => ({
  ASSISTANT_CHAT_TIMEOUT_MS: 180_000,
  assistantService: {
    chat: vi.fn(),
    getConversation: vi.fn(),
    deleteConversation: vi.fn(),
    confirmAction: vi.fn(),
    cancelAction: vi.fn(),
    getSuggestions: vi.fn(),
    getStatus: vi.fn(),
  },
}));

const service = vi.mocked(assistantService);

function Probe() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname}</p>;
}

/** Where each role's own area is: the admin in /admin, the owner in /owner, the partner in /partner */
const AREA_ROUTES: Record<UserRole, string> = {
  PLATFORM_ADMIN: '/admin/orders',
  RESTAURANT_ADMIN: '/owner/orders',
  DELIVERY_PARTNER: '/partner',
  CUSTOMER: '/restaurants',
};

function renderAssistant(role: UserRole = 'PLATFORM_ADMIN') {
  const person = {
    id: role === 'CUSTOMER' ? 7 : 1,
    role,
    fullName: 'Priya',
    email: 'p@example.com',
  };
  const session = {
    adminUser: role === 'PLATFORM_ADMIN' ? { id: 1, role } : null,
    customer: role === 'CUSTOMER' ? person : null,
    owner: role === 'RESTAURANT_ADMIN' ? person : null,
    partner: role === 'DELIVERY_PARTNER' ? person : null,
  };
  const route = AREA_ROUTES[role];
  return renderWithProviders(
    <>
      <AssistantWidget />
      <Probe />
    </>,
    { store: makeStore({ session }), route },
  );
}

const reply = (overrides: Partial<AssistantReply> = {}): AssistantReply => ({
  text: 'Here you go.',
  cards: [],
  links: [],
  pendingAction: null,
  toolsUsed: [],
  ...overrides,
});

const answer = (
  replyOverrides: Partial<AssistantReply> = {},
  conversationId = 'conv-1',
): AssistantChatResponse => ({
  conversationId,
  messageId: 'm-1',
  reply: reply(replyOverrides),
  model: 'qwen3',
  durationMs: 1200,
});

function failure(
  status: number,
  headers: Record<string, string> = {},
  data: unknown = {},
): ApiError {
  return toApiError(
    new AxiosError('failed', 'ERR_BAD_RESPONSE', undefined, null, {
      status,
      statusText: '',
      data,
      headers: new AxiosHeaders(headers),
      config: { headers: new AxiosHeaders() },
    }),
  );
}

const proposal = (overrides: Partial<OrderPendingAction> = {}): OrderPendingAction => ({
  actionId: 'act-1',
  kind: 'PLACE_ORDER',
  expiresAt: new Date(Date.now() + 9 * 60_000).toISOString(),
  summary: {
    restaurantName: 'Green Bowl',
    items: [{ name: 'Paneer Wrap', quantity: 2, unitPrice: 120, lineTotal: 240 }],
    deliveryAddress: '12 Main Street, Pune',
    paymentMethod: 'Cash on delivery',
    itemsTotal: 240,
    discount: 20,
    deliveryFee: 30,
    tax: 12,
    total: 262,
    currency: 'INR',
    notes: null,
    warnings: ['The restaurant closes in 20 minutes'],
  },
  ...overrides,
});

const confirmed: ConfirmActionResponse = {
  status: 'CREATED',
  order: { id: '55', status: 'PLACED', total: 262, currency: 'INR' },
  message: 'Your order was placed.',
  link: { label: 'View order', path: '/orders/55' },
};

async function openPanel() {
  await userEvent.click(screen.getByRole('button', { name: 'Ask the assistant' }));
  return screen.getByRole('dialog', { name: 'AI assistant' });
}

async function ask(text: string) {
  await userEvent.type(
    screen.getByRole('textbox', { name: 'Ask the assistant' }),
    `${text}{Enter}`,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  service.getStatus.mockResolvedValue({ available: true, model: 'qwen3', message: null });
  service.getSuggestions.mockResolvedValue([
    'How many orders were delivered today?',
    'Which restaurants are waiting for approval?',
  ]);
  service.getConversation.mockResolvedValue(null);
  service.deleteConversation.mockResolvedValue(undefined);
  service.cancelAction.mockResolvedValue(undefined);
});

describe('the panel', () => {
  it('opens with the cursor in the question box, closes with Esc and gives the focus back', async () => {
    renderAssistant();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    const dialog = await openPanel();
    expect(within(dialog).getByText('Administrator')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Ask the assistant' })).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask the assistant' })).toHaveFocus();

    await openPanel();
    await userEvent.click(screen.getByRole('button', { name: 'Close assistant' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it.each([
    ['PLATFORM_ADMIN', 'Administrator'],
    ['RESTAURANT_ADMIN', 'Restaurant owner'],
    ['CUSTOMER', 'Customer'],
    ['DELIVERY_PARTNER', 'Delivery partner'],
  ] as const)('shows the %s role chip and the suggested questions', async (role, label) => {
    renderAssistant(role);
    const dialog = await openPanel();
    expect(within(dialog).getByText(label)).toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: 'How many orders were delivered today?' }),
    ).toBeInTheDocument();
  });

  it('minimizes into a small bar while the answer is on its way, and says when it is there', async () => {
    let deliver: (response: AssistantChatResponse) => void = () => undefined;
    service.chat.mockReturnValue(
      new Promise((resolve) => {
        deliver = resolve;
      }),
    );
    renderAssistant();
    await openPanel();
    await ask('How many orders today?');

    await userEvent.click(screen.getByRole('button', { name: 'Minimize assistant' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const bar = within(screen.getByRole('region', { name: 'Assistant, minimized' }));
    expect(bar.getByRole('button', { name: 'Open the assistant' })).toHaveFocus();
    expect(bar.getByText('Thinking...')).toBeInTheDocument();
    // The round button gives way to the bar
    expect(screen.queryByRole('button', { name: 'Ask the assistant' })).not.toBeInTheDocument();

    // The conversation goes on behind the bar
    await act(async () => {
      deliver(answer({ text: 'Twelve orders so far.' }));
      await Promise.resolve();
    });
    expect(await bar.findByText('New answer: open to read it')).toBeInTheDocument();

    await userEvent.click(bar.getByRole('button', { name: 'Open the assistant' }));
    const dialog = screen.getByRole('dialog', { name: 'AI assistant' });
    expect(within(dialog).getByText('Twelve orders so far.')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Assistant, minimized' })).not.toBeInTheDocument();

    // Minimized again with nothing new: it just waits; its cross closes it for good
    await userEvent.click(screen.getByRole('button', { name: 'Minimize assistant' }));
    const again = within(screen.getByRole('region', { name: 'Assistant, minimized' }));
    expect(again.getByText('Your chat is waiting')).toBeInTheDocument();
    await userEvent.click(again.getByRole('button', { name: 'Close assistant' }));
    expect(screen.queryByRole('region', { name: 'Assistant, minimized' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask the assistant' })).toBeInTheDocument();
    expect(service.chat).toHaveBeenCalledTimes(1);
  });

  it('maximizes to a large window and back, and remembers the choice', async () => {
    localStorage.removeItem('front-app.assistant.maximized');
    renderAssistant();
    const dialog = await openPanel();
    expect(dialog).toHaveAttribute('data-size', 'sheet');

    await userEvent.click(screen.getByRole('button', { name: 'Maximize assistant' }));
    expect(screen.getByRole('dialog', { name: 'AI assistant' })).toHaveAttribute(
      'data-size',
      'maximized',
    );
    expect(localStorage.getItem('front-app.assistant.maximized')).toBe('on');

    // Closed and opened again: still large
    await userEvent.click(screen.getByRole('button', { name: 'Close assistant' }));
    expect(await openPanel()).toHaveAttribute('data-size', 'maximized');

    await userEvent.click(screen.getByRole('button', { name: 'Restore assistant size' }));
    expect(screen.getByRole('dialog', { name: 'AI assistant' })).toHaveAttribute(
      'data-size',
      'sheet',
    );
    expect(localStorage.getItem('front-app.assistant.maximized')).toBe('off');
    localStorage.removeItem('front-app.assistant.maximized');
  });

  it('says the assistant is offline, without blocking typing, and checks again on Retry', async () => {
    service.getStatus.mockResolvedValueOnce({ available: false, model: null, message: 'Down' });
    renderAssistant();
    await openPanel();
    expect(await screen.findByText('Assistant is offline')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Ask the assistant' })).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(screen.queryByText('Assistant is offline')).not.toBeInTheDocument());
    expect(service.getStatus).toHaveBeenCalledTimes(2);
  });
});

describe('asking', () => {
  it('shows Thinking, then the answer with every kind of card, links and the tools used', async () => {
    let finish: (value: AssistantChatResponse) => void = () => undefined;
    service.chat.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    renderAssistant();
    await openPanel();

    await userEvent.click(
      await screen.findByRole('button', { name: 'How many orders were delivered today?' }),
    );
    expect(await screen.findByText('Thinking...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
    expect(service.chat).toHaveBeenCalledWith(
      { conversationId: null, message: 'How many orders were delivered today?' },
      expect.any(AbortSignal),
    );

    finish(
      answer({
        text: '**42** orders were delivered.\n\n- 40 on time\n- 2 late',
        cards: [
          {
            kind: 'stats',
            title: 'Order statistics',
            stats: [{ label: 'Delivered', value: '42', hint: 'since midnight' }],
          },
          {
            kind: 'table',
            title: 'Top restaurants',
            columns: [
              { key: 'name', label: 'Restaurant', align: 'left' },
              { key: 'orders', label: 'Orders', align: 'right' },
            ],
            rows: [{ name: 'Green Bowl', orders: 17 }],
          },
          {
            kind: 'entities',
            entityType: 'RESTAURANT',
            title: 'Pending restaurants',
            items: [
              {
                id: '9',
                title: 'Spice Route',
                subtitle: 'Indian',
                badges: ['PENDING'],
                meta: [{ label: 'City', value: 'Pune' }],
                imageUrl: null,
                link: { label: 'Open restaurant', path: '/admin/restaurants/9' },
              },
            ],
          },
        ],
        links: [{ label: 'Open orders', path: '/admin/orders' }],
        toolsUsed: ['admin_order_stats', 'admin_list_orders', 'some_new_tool'],
      }),
    );

    expect(await screen.findByText('orders were delivered.', { exact: false })).toBeInTheDocument();
    expect(screen.queryByText('Thinking...')).not.toBeInTheDocument();
    expect(screen.getByText('40 on time')).toBeInTheDocument();
    expect(screen.getByText('Delivered')).toBeInTheDocument();
    expect(screen.getByText('since midnight')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Orders' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Green Bowl' })).toBeInTheDocument();
    expect(screen.getByText('Spice Route')).toBeInTheDocument();
    expect(screen.getByText('PENDING')).toBeInTheDocument();
    expect(screen.getByText('Used: Order statistics, Orders, Some new tool')).toBeInTheDocument();

    // The next question continues the same conversation
    service.chat.mockResolvedValueOnce(answer({ text: 'Second answer' }));
    await ask('And yesterday?');
    expect(await screen.findByText('Second answer')).toBeInTheDocument();
    expect(service.chat).toHaveBeenLastCalledWith(
      { conversationId: 'conv-1', message: 'And yesterday?' },
      expect.any(AbortSignal),
    );
    expect(sessionStorage.getItem('front-app.assistant.conversation.1')).toBe('conv-1');
  });

  it('helps a delivery partner with their delivery: where to go, whom to call, what to collect', async () => {
    service.chat.mockResolvedValue(
      answer({
        text: 'You are on the way to Spice Hub. Collect 562.9 INR in cash.',
        cards: [
          {
            kind: 'entities',
            entityType: 'DELIVERY',
            title: 'Delivery #31',
            items: [
              {
                id: '31',
                title: 'Delivery #31 (order #42)',
                subtitle: 'Spice Hub',
                badges: ['ACCEPTED'],
                meta: [
                  { label: 'Drop-off', value: '12 MG Road, Bengaluru 560001' },
                  { label: 'Phone', value: '+919876543210' },
                  { label: 'Cash', value: 'Collect 562.9 INR' },
                ],
                imageUrl: null,
                link: { label: 'Open delivery', path: '/partner/deliveries/31' },
              },
            ],
          },
        ],
        toolsUsed: ['partner_current_delivery'],
      }),
    );
    renderAssistant('DELIVERY_PARTNER');
    await openPanel();
    expect(
      screen.getByText(/Ask me about your current delivery, where to go, the cash to collect/),
    ).toBeInTheDocument();

    await ask('Which delivery is assigned to me?');

    const card = await screen.findByRole('region', { name: 'Delivery #31' });
    expect(within(card).getByText('Collect 562.9 INR')).toBeInTheDocument();
    expect(within(card).getByText('+919876543210')).toBeInTheDocument();
    expect(screen.getByText('Used: Current delivery')).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('link', { name: 'Open delivery' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/partner/deliveries/31');
  });

  it('follows a link with the router and gives a phone its page back', async () => {
    service.chat.mockResolvedValue(
      answer({ links: [{ label: 'Open orders', path: '/admin/orders/list' }] }),
    );
    renderAssistant();
    await openPanel();
    await ask('orders');
    await userEvent.click(await screen.findByRole('link', { name: 'Open orders' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/orders/list');
    // jsdom has no matchMedia: the screen counts as a phone, so the panel closes
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows markup as plain text and ignores links that leave the application', async () => {
    service.chat.mockResolvedValue(
      answer({
        text: '<script>window.hacked = true</script> <img src=x onerror=alert(1)> **bold** `code` https://evil.example [x](https://evil.example)',
        links: [
          { label: 'Evil scheme', path: 'javascript:alert(1)' },
          { label: 'Evil host', path: '//evil.example/x' },
          { label: 'Evil absolute', path: 'https://evil.example' },
          { label: 'Good link', path: '/admin/orders' },
        ],
        cards: [
          {
            kind: 'entities',
            entityType: 'FOOD',
            title: 'Foods',
            items: [
              {
                id: '1',
                title: 'Bad item',
                subtitle: null,
                badges: [],
                meta: [],
                imageUrl: null,
                link: { label: 'Evil item link', path: 'http://evil.example/a' },
              },
            ],
          },
        ],
      }),
    );
    const { container } = renderAssistant();
    await openPanel();
    await ask('hello');

    expect(await screen.findByText(/<script>window.hacked = true<\/script>/)).toBeInTheDocument();
    expect(container.ownerDocument.querySelector('script[src], img[onerror]')).toBeNull();
    expect(document.querySelector('dialog img, [role="dialog"] img')).toBeNull();
    expect(screen.getByText('bold').tagName).toBe('STRONG');
    expect(screen.getByText('code').tagName).toBe('CODE');
    // Not turned into a link
    expect(screen.queryByRole('link', { name: /evil\.example|x/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Good link' })).toHaveAttribute(
      'href',
      '/admin/orders',
    );
    for (const name of ['Evil scheme', 'Evil host', 'Evil absolute', 'Evil item link']) {
      expect(screen.queryByText(name)).not.toBeInTheDocument();
    }
  });
});

describe('errors', () => {
  it('429: shows the server message and the wait, and Retry sends the same question again', async () => {
    service.chat
      .mockRejectedValueOnce(
        failure(429, { 'retry-after': '12' }, { detail: 'You asked too many questions.' }),
      )
      .mockResolvedValueOnce(answer({ text: 'Now it works' }));
    renderAssistant();
    await openPanel();
    await ask('my question');

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('The assistant is busy')).toBeInTheDocument();
    expect(
      within(alert).getByText(/You asked too many questions\. Please wait 12 seconds/),
    ).toBeInTheDocument();
    expect(screen.getByText('my question')).toBeInTheDocument();

    await userEvent.click(within(alert).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Now it works')).toBeInTheDocument();
    expect(service.chat).toHaveBeenLastCalledWith(
      { conversationId: null, message: 'my question' },
      expect.any(AbortSignal),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // The question is shown once, not twice
    expect(screen.getAllByText('my question')).toHaveLength(1);
  });

  it.each([
    [503, 'The assistant is temporarily unavailable'],
    [504, 'The assistant took too long to answer'],
  ])('%i: a plain title, never the raw server text', async (status, title) => {
    service.chat.mockRejectedValueOnce(
      failure(status, {}, { detail: 'java.lang.NullPointerException' }),
    );
    renderAssistant();
    await openPanel();
    await ask('q');
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText(title)).toBeInTheDocument();
    expect(within(alert).getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.queryByText(/NullPointer/)).not.toBeInTheDocument();
  });

  it('a network failure and a refused account each get their own message', async () => {
    service.chat.mockRejectedValueOnce(toApiError(new AxiosError('Network Error', 'ERR_NETWORK')));
    renderAssistant();
    await openPanel();
    await ask('first');
    expect(await screen.findByText('The assistant could not be reached')).toBeInTheDocument();

    service.chat.mockRejectedValueOnce(failure(403));
    await ask('second');
    expect(
      await screen.findByText('The assistant is not available for this account'),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Retry' })).toHaveLength(1);
  });

  it('cancels a question in flight and ignores the late answer', async () => {
    let finish: (value: AssistantChatResponse) => void = () => undefined;
    service.chat.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    renderAssistant();
    await openPanel();
    await ask('slow question');
    await screen.findByText('Thinking...');
    const signal = service.chat.mock.calls[0][1] as AbortSignal;
    expect(signal.aborted).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(signal.aborted).toBe(true);
    expect(screen.queryByText('Thinking...')).not.toBeInTheDocument();
    expect(screen.getByText('You cancelled this question')).toBeInTheDocument();
    expect(screen.getByText('slow question')).toBeInTheDocument();

    finish(answer({ text: 'Too late' }));
    await Promise.resolve();
    expect(screen.queryByText('Too late')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

describe('the conversation', () => {
  it('is restored after a reload from the stored conversation id', async () => {
    sessionStorage.setItem('front-app.assistant.conversation.1', 'conv-9');
    service.getConversation.mockResolvedValue({
      conversationId: 'conv-9',
      messages: [
        {
          id: '1',
          role: 'USER',
          text: 'Earlier question',
          cards: [],
          links: [],
          pendingAction: null,
          at: null,
        },
        {
          id: '2',
          role: 'ASSISTANT',
          text: 'Earlier answer',
          cards: [],
          links: [],
          pendingAction: null,
          at: null,
        },
      ],
    });
    renderAssistant();
    await openPanel();
    expect(await screen.findByText('Earlier answer')).toBeInTheDocument();
    expect(screen.getByText('Earlier question')).toBeInTheDocument();
    expect(service.getConversation).toHaveBeenCalledWith('conv-9');

    service.chat.mockResolvedValueOnce(answer({ text: 'Newer answer' }, 'conv-9'));
    await ask('Follow up');
    expect(await screen.findByText('Newer answer')).toBeInTheDocument();
    expect(screen.getByText('Earlier answer')).toBeInTheDocument();
    expect(service.chat).toHaveBeenCalledWith(
      { conversationId: 'conv-9', message: 'Follow up' },
      expect.any(AbortSignal),
    );
  });

  it('keeps the thread when the panel is closed and opened again', async () => {
    service.chat.mockResolvedValue(answer({ text: 'Kept answer' }));
    renderAssistant();
    await openPanel();
    await ask('keep me');
    await screen.findByText('Kept answer');
    await userEvent.keyboard('{Escape}');
    await openPanel();
    expect(screen.getByText('Kept answer')).toBeInTheDocument();
    expect(screen.getByText('keep me')).toBeInTheDocument();
  });

  it('"New chat" deletes the conversation and starts clean', async () => {
    service.chat.mockResolvedValue(answer({ text: 'Old answer' }, 'conv-3'));
    renderAssistant();
    await openPanel();
    await ask('old question');
    await screen.findByText('Old answer');

    await userEvent.click(screen.getByRole('button', { name: 'New chat' }));
    expect(service.deleteConversation).toHaveBeenCalledWith('conv-3');
    expect(screen.queryByText('Old answer')).not.toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.assistant.conversation.1')).toBeNull();
    expect(screen.getByText(/Hello!/)).toBeInTheDocument();

    service.chat.mockResolvedValueOnce(answer({ text: 'Fresh' }, 'conv-4'));
    await ask('new question');
    await screen.findByText('Fresh');
    expect(service.chat).toHaveBeenLastCalledWith(
      { conversationId: null, message: 'new question' },
      expect.any(AbortSignal),
    );
  });

  it('does not send a blank question, and Shift+Enter writes a new line instead of sending', async () => {
    renderAssistant();
    await openPanel();
    const box = screen.getByRole('textbox', { name: 'Ask the assistant' });
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
    await userEvent.type(box, '   {Enter}');
    expect(service.chat).not.toHaveBeenCalled();
    await userEvent.clear(box);
    await userEvent.type(box, 'line one{Shift>}{Enter}{/Shift}line two');
    expect(box).toHaveValue('line one\nline two');
    expect(service.chat).not.toHaveBeenCalled();
  });
});

describe('the order proposal', () => {
  async function askForOrder(pendingAction: PendingAction = proposal()) {
    service.chat.mockResolvedValue(answer({ text: 'Please check your order.', pendingAction }));
    renderAssistant('CUSTOMER');
    await openPanel();
    await ask('order two paneer wraps');
    return screen.findByRole('region', { name: 'Order summary' });
  }

  it('shows the summary and confirms once, even on a double click', async () => {
    let finish: (value: ConfirmActionResponse) => void = () => undefined;
    service.confirmAction.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const card = await askForOrder();
    expect(within(card).getByText('Green Bowl')).toBeInTheDocument();
    expect(within(card).getByText('Paneer Wrap')).toBeInTheDocument();
    expect(within(card).getByText('12 Main Street, Pune')).toBeInTheDocument();
    expect(within(card).getByText('Cash on delivery')).toBeInTheDocument();
    expect(within(card).getByText('Total')).toBeInTheDocument();
    expect(within(card).getByText(/expires in 9:0\d|expires in 8:5\d/)).toBeInTheDocument();
    expect(within(card).getByRole('alert')).toHaveTextContent(
      'The restaurant closes in 20 minutes',
    );

    await userEvent.dblClick(within(card).getByRole('button', { name: 'Confirm order' }));
    expect(service.confirmAction).toHaveBeenCalledTimes(1);
    expect(service.confirmAction).toHaveBeenCalledWith('act-1');
    expect(within(card).getByRole('button', { name: /Confirm order/ })).toBeDisabled();

    finish(confirmed);
    expect(await within(card).findByText('Your order was placed.')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'View order' })).toHaveAttribute(
      'href',
      '/orders/55',
    );
    expect(within(card).queryByRole('button', { name: /Confirm order/ })).not.toBeInTheDocument();
  });

  it('cancels the proposal', async () => {
    const card = await askForOrder();
    await userEvent.click(within(card).getByRole('button', { name: 'Cancel' }));
    expect(service.cancelAction).toHaveBeenCalledWith('act-1');
    expect(await within(card).findByText('Order request cancelled.')).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument();
  });

  it('an expired proposal cannot be confirmed', async () => {
    const card = await askForOrder(
      proposal({ expiresAt: new Date(Date.now() - 1000).toISOString() }),
    );
    expect(within(card).getByText('This order request expired. Ask again.')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Confirm order' })).toBeDisabled();
    expect(within(card).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(service.confirmAction).not.toHaveBeenCalled();
  });

  it('a 410 answer ends the proposal as expired', async () => {
    service.confirmAction.mockRejectedValue(failure(410, {}, { detail: 'Gone' }));
    const card = await askForOrder();
    await userEvent.click(within(card).getByRole('button', { name: 'Confirm order' }));
    expect(
      await within(card).findByText('This order request expired. Ask again.'),
    ).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Confirm order' })).toBeDisabled();
  });

  it('a 409 answer takes the buttons away and says why', async () => {
    service.confirmAction.mockRejectedValue(
      failure(409, {}, { detail: 'This request was already cancelled.' }),
    );
    const card = await askForOrder();
    await userEvent.click(within(card).getByRole('button', { name: 'Confirm order' }));
    expect(
      await within(card).findByText('This request was already cancelled.'),
    ).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument();
  });

  it('a temporary failure keeps the card usable, and a second try can succeed', async () => {
    service.confirmAction.mockRejectedValueOnce(failure(503)).mockResolvedValueOnce(confirmed);
    const card = await askForOrder();
    await userEvent.click(within(card).getByRole('button', { name: 'Confirm order' }));
    expect(await within(card).findByText(/could not be placed just now/)).toBeInTheDocument();
    const again = within(card).getByRole('button', { name: 'Confirm order' });
    expect(again).toBeEnabled();

    await userEvent.click(again);
    expect(await within(card).findByText('Your order was placed.')).toBeInTheDocument();
    expect(service.confirmAction).toHaveBeenCalledTimes(2);
  });

  it('a confirmed proposal stays confirmed after the panel is closed and opened', async () => {
    service.confirmAction.mockResolvedValue(confirmed);
    const card = await askForOrder();
    await userEvent.click(within(card).getByRole('button', { name: 'Confirm order' }));
    await within(card).findByText('Your order was placed.');
    await userEvent.keyboard('{Escape}');
    await openPanel();
    const reopened = screen.getByRole('region', { name: 'Order summary' });
    expect(
      within(reopened).queryByRole('button', { name: 'Confirm order' }),
    ).not.toBeInTheDocument();
    expect(within(reopened).getByText('Your order was placed.')).toBeInTheDocument();
  });
});

const change = (overrides: Partial<ChangePendingAction> = {}): ChangePendingAction => ({
  actionId: 'act_9',
  kind: 'MARK_DELIVERED',
  expiresAt: new Date(Date.now() + 9 * 60_000).toISOString(),
  details: {
    title: 'Delivered: delivery #31 (order #42)',
    rows: [
      { label: 'Customer', value: 'Priya Sharma' },
      { label: 'Cash', value: 'Collect 562.90 INR' },
    ],
    note: 'Confirm only after collecting exactly 562.90 INR in cash.',
    confirmLabel: 'Delivered',
  },
  ...overrides,
});

describe('a prepared change (not an order)', () => {
  async function askForChange(pendingAction: PendingAction = change()) {
    service.chat.mockResolvedValue(
      answer({ text: 'Press Delivered once you have the cash.', pendingAction }),
    );
    renderAssistant('DELIVERY_PARTNER');
    await openPanel();
    await ask('I delivered it');
    return screen.findByRole('region', { name: 'Change to confirm' });
  }

  it('shows what will change and makes it only with its own button, once', async () => {
    let finish: (value: ConfirmActionResponse) => void = () => undefined;
    service.confirmAction.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const card = await askForChange();
    expect(
      within(card).getByRole('heading', { name: 'Delivered: delivery #31 (order #42)' }),
    ).toBeInTheDocument();
    expect(within(card).getByText('Collect 562.90 INR')).toBeInTheDocument();
    expect(
      within(card).getByText('Confirm only after collecting exactly 562.90 INR in cash.'),
    ).toBeInTheDocument();
    expect(service.confirmAction).not.toHaveBeenCalled();

    await userEvent.dblClick(within(card).getByRole('button', { name: 'Delivered' }));
    expect(service.confirmAction).toHaveBeenCalledTimes(1);
    expect(service.confirmAction).toHaveBeenCalledWith('act_9');

    finish({
      status: 'DONE',
      order: null,
      message: 'Delivery #31 is marked delivered.',
      link: { label: 'Open delivery', path: '/partner/deliveries/31' },
    });
    expect(await within(card).findByText('Delivery #31 is marked delivered.')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'Open delivery' })).toHaveAttribute(
      'href',
      '/partner/deliveries/31',
    );
    expect(within(card).queryByRole('button', { name: 'Delivered' })).not.toBeInTheDocument();
  });

  it('a refusal of the platform says why and takes the buttons away', async () => {
    service.confirmAction.mockRejectedValue(
      failure(422, {}, { detail: 'Cash on delivery: collect exactly INR 562.90' }),
    );
    const card = await askForChange();
    await userEvent.click(within(card).getByRole('button', { name: 'Delivered' }));
    expect(
      await within(card).findByText('Cash on delivery: collect exactly INR 562.90'),
    ).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Delivered' })).not.toBeInTheDocument();
  });

  it('"Not now" drops it and nothing is changed', async () => {
    const card = await askForChange();
    await userEvent.click(within(card).getByRole('button', { name: 'Not now' }));
    expect(service.cancelAction).toHaveBeenCalledWith('act_9');
    expect(service.confirmAction).not.toHaveBeenCalled();
    expect(await within(card).findByText('Dismissed. Nothing was changed.')).toBeInTheDocument();
  });

  it('a temporary failure says nothing was changed and keeps the button', async () => {
    service.confirmAction.mockRejectedValue(failure(503));
    const card = await askForChange();
    await userEvent.click(within(card).getByRole('button', { name: 'Delivered' }));
    expect(await within(card).findByText(/Nothing was changed/)).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Delivered' })).toBeEnabled();
  });

  it('an expired change cannot be confirmed', async () => {
    const card = await askForChange(
      change({ expiresAt: new Date(Date.now() - 1000).toISOString() }),
    );
    expect(
      within(card).getByText('This request expired. Ask the assistant again.'),
    ).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Delivered' })).toBeDisabled();
  });
});

// ---------- Voice: the browser's SpeechRecognition and speechSynthesis, played by fakes ----------

/** Plays the browser's speech recognition: the test says what was "heard" and when the person stopped. */
class FakeRecognition {
  static current: FakeRecognition | null = null;
  lang = '';
  continuous = true;
  interimResults = false;
  maxAlternatives = 0;
  onstart: (() => void) | null = null;
  onresult: ((event: unknown) => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn(() => {
    FakeRecognition.current = this;
    this.onstart?.();
  });
  stop = vi.fn(() => this.onend?.());
  abort = vi.fn(() => {
    this.onerror?.({ error: 'aborted' });
    this.onend?.();
  });

  hear(transcript: string, isFinal: boolean) {
    const result = Object.assign([{ transcript, confidence: 0.9 }], { isFinal });
    act(() => this.onresult?.({ results: [result] }));
  }

  end() {
    act(() => this.onend?.());
  }

  fail(error: string) {
    act(() => {
      this.onerror?.({ error });
      this.onend?.();
    });
  }
}

class FakeUtterance {
  lang = '';
  voice: unknown = null;
  onend: (() => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  text: string;
  constructor(text: string) {
    this.text = text;
  }
}

function installVoiceInput() {
  FakeRecognition.current = null;
  vi.stubGlobal('webkitSpeechRecognition', FakeRecognition);
  vi.stubGlobal('isSecureContext', true);
}

function installVoiceOutput() {
  const synth = { speak: vi.fn(), cancel: vi.fn(), getVoices: vi.fn(() => []) };
  vi.stubGlobal('speechSynthesis', synth);
  vi.stubGlobal('SpeechSynthesisUtterance', FakeUtterance);
  return synth;
}

const spokenTexts = (synth: ReturnType<typeof installVoiceOutput>) =>
  synth.speak.mock.calls.map(([utterance]) => (utterance as FakeUtterance).text);

describe('voice', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  async function startSpeaking() {
    await userEvent.click(screen.getByRole('button', { name: 'Speak your question' }));
    const recognition = FakeRecognition.current;
    if (!recognition) {
      throw new Error('the microphone did not start');
    }
    return recognition;
  }

  it('sends a spoken question like a typed one, showing the words while they are spoken', async () => {
    installVoiceInput();
    service.chat.mockResolvedValue(answer({ text: 'You delivered 2 orders today.' }));
    renderAssistant('DELIVERY_PARTNER');
    await openPanel();

    const recognition = await startSpeaking();
    expect(recognition.interimResults).toBe(true);
    expect(recognition.lang).toBe(navigator.language || 'en-US');
    expect(screen.getByText('Listening... speak now')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Stop recording' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    recognition.hear('how many deliveries', false);
    expect(screen.getByRole('textbox', { name: 'Ask the assistant' })).toHaveValue(
      'how many deliveries',
    );
    recognition.hear('how many deliveries today', true);
    recognition.end();

    await waitFor(() =>
      expect(service.chat).toHaveBeenCalledWith(
        { conversationId: null, message: 'how many deliveries today' },
        expect.any(AbortSignal),
      ),
    );
    expect(await screen.findByText('You delivered 2 orders today.')).toBeInTheDocument();
    expect(screen.getByText('how many deliveries today')).toBeInTheDocument();
    expect(screen.queryByText('Listening... speak now')).not.toBeInTheDocument();
  });

  it('Stop recording sends what was heard; Cancel recording sends nothing', async () => {
    installVoiceInput();
    service.chat.mockResolvedValue(answer());
    renderAssistant('CUSTOMER');
    await openPanel();

    const dropped = await startSpeaking();
    dropped.hear('cancel my order', false);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel recording' }));
    expect(dropped.abort).toHaveBeenCalled();
    expect(service.chat).not.toHaveBeenCalled();
    expect(screen.getByRole('textbox', { name: 'Ask the assistant' })).toHaveValue('');

    const kept = await startSpeaking();
    kept.hear('show my orders', false);
    await userEvent.click(screen.getByRole('button', { name: 'Stop recording' }));
    expect(kept.stop).toHaveBeenCalled();
    await waitFor(() => expect(service.chat).toHaveBeenCalledTimes(1));
    expect(service.chat.mock.calls[0][0].message).toBe('show my orders');
  });

  it.each([
    ['not-allowed', 'Microphone access is blocked'],
    ['audio-capture', 'No microphone was found'],
    ['network', 'The speech service could not be reached'],
  ])('a %s error explains what to do and sends nothing', async (code, message) => {
    installVoiceInput();
    renderAssistant();
    await openPanel();

    (await startSpeaking()).fail(code);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(service.chat).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByText(new RegExp(message))).not.toBeInTheDocument();
  });

  it('says when nothing was heard', async () => {
    installVoiceInput();
    renderAssistant();
    await openPanel();

    (await startSpeaking()).end();

    expect(await screen.findByRole('alert')).toHaveTextContent('I did not hear anything');
    expect(service.chat).not.toHaveBeenCalled();
  });

  it('without voice input in the browser, the microphone explains and typing still works', async () => {
    service.chat.mockResolvedValue(answer());
    renderAssistant();
    await openPanel();

    await userEvent.click(screen.getByRole('button', { name: 'Speak your question' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Voice input is not available in this browser',
    );

    await ask('orders today');
    await waitFor(() => expect(service.chat).toHaveBeenCalledTimes(1));
  });

  it('reads an answer aloud as plain sentences, and stops it', async () => {
    const synth = installVoiceOutput();
    service.chat.mockResolvedValue(
      answer({ text: 'There are **2** orders:\n- #42 placed\n- #43 ready' }),
    );
    renderAssistant();
    await openPanel();
    await ask('orders');

    const listen = await screen.findByRole('button', { name: 'Listen to this answer' });
    expect(synth.speak).not.toHaveBeenCalled();
    await userEvent.click(listen);

    expect(spokenTexts(synth).join(' ')).toBe(
      'There are 2 orders: number 42 placed. number 43 ready.',
    );
    const stop = screen.getByRole('button', { name: 'Stop reading aloud' });
    expect(stop).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(stop);
    expect(synth.cancel).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Listen to this answer' })).toBeInTheDocument();
  });

  it('"Read answers aloud" reads each new answer by itself and is remembered', async () => {
    const synth = installVoiceOutput();
    service.chat.mockResolvedValue(answer({ text: 'Your order is on its way.' }));
    renderAssistant('CUSTOMER');
    await openPanel();

    const toggle = screen.getByRole('button', { name: 'Read answers aloud' });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem('front-app.assistant.voice-replies')).toBe('on');

    await ask('where is my order');
    await waitFor(() => expect(spokenTexts(synth)).toEqual(['Your order is on its way.']));
  });

  it('starting the microphone stops an answer being read', async () => {
    installVoiceInput();
    const synth = installVoiceOutput();
    service.chat.mockResolvedValue(answer({ text: 'Two orders.' }));
    renderAssistant();
    await openPanel();
    await ask('orders');
    await userEvent.click(await screen.findByRole('button', { name: 'Listen to this answer' }));
    synth.cancel.mockClear();

    await startSpeaking();

    expect(synth.cancel).toHaveBeenCalled();
  });

  it('without speech output there is no Listen button and no read-aloud switch', async () => {
    service.chat.mockResolvedValue(answer({ text: 'Two orders.' }));
    renderAssistant();
    await openPanel();
    await ask('orders');

    expect(await screen.findByText('Two orders.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Listen to this answer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Read answers aloud' })).not.toBeInTheDocument();
  });
});

describe('the conversation', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('welcomes the user by first name when it is known, and plainly otherwise', async () => {
    renderAssistant('DELIVERY_PARTNER');
    await openPanel();
    expect(screen.getByText('Hi Priya!')).toBeInTheDocument();
    expect(screen.getByText('Try asking')).toBeInTheDocument();
  });

  it('greets an admin without a known name plainly', async () => {
    renderAssistant('PLATFORM_ADMIN');
    await openPanel();
    expect(screen.getByText('Hi there!')).toBeInTheDocument();
  });

  it('shows when each message was sent and received', async () => {
    service.chat.mockResolvedValue(answer({ text: 'Two orders.' }));
    renderAssistant();
    await openPanel();
    await ask('orders');

    await screen.findByText('Two orders.');
    const times = within(screen.getByRole('log', { name: 'Conversation' })).getAllByText(
      /^\d{1,2}:\d{2}/,
    );
    expect(times).toHaveLength(2);
    expect(times[0].tagName).toBe('TIME');
  });

  it('copies an answer to the clipboard and says so', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    service.chat.mockResolvedValue(answer({ text: 'There are **2** orders.' }));
    renderAssistant();
    await openPanel();
    await ask('orders');

    await userEvent.click(await screen.findByRole('button', { name: 'Copy this answer' }));

    expect(writeText).toHaveBeenCalledWith('There are **2** orders.');
    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
  });
});
