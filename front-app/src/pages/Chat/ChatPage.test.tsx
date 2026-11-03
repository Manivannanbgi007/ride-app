import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import ChatPage from '@/pages/Chat/ChatPage';
import { chatService } from '@/services/chatService';
import { userService } from '@/services/userService';
import {
  adminProfile,
  chatConversation,
  chatMessage,
  chatMessagePage,
  pageOf,
} from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { AdminUserSummary } from '@/types/user';

vi.mock('@/services/chatService', () => ({
  CHAT_PAGE_SIZE: 30,
  chatService: {
    getConversations: vi.fn(),
    startConversation: vi.fn(),
    getConversation: vi.fn(),
    getMessages: vi.fn(),
    sendMessage: vi.fn(),
    markRead: vi.fn(),
    setPinned: vi.fn(),
    setMuted: vi.fn(),
    getUnreadCount: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({
  userService: { getMyProfile: vi.fn(), searchUsers: vi.fn() },
}));

const apiError = (status: number, kind: ApiError['kind']) =>
  new ApiError({ kind, status, userMessage: 'nope', technicalMessage: 'nope' });

const user = (overrides: Partial<AdminUserSummary>): AdminUserSummary => ({
  id: 7,
  fullName: 'Priya Sharma',
  email: 'priya@example.com',
  phone: null,
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-09-01T10:00:00Z',
  lastLoginAt: null,
  ...overrides,
});

const renderChat = (route = '/admin/chat') =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/chat/:conversationId?" element={<ChatPage />} />
      <Route path="/admin/customers/:userId" element={<p>Customer page</p>} />
    </Routes>,
    { route },
  );

const priya = chatConversation();
const ravi = chatConversation({
  id: 6,
  participant: { userId: 8, name: 'Ravi Kumar', role: 'RESTAURANT_ADMIN', online: true },
  lastMessage: {
    preview: 'Thanks, noted',
    at: '2026-10-08T10:20:00Z',
    fromStaff: true,
    read: true,
  },
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userService.getMyProfile).mockResolvedValue(adminProfile);
  vi.mocked(chatService.getUnreadCount).mockResolvedValue(0);
  vi.mocked(chatService.getConversations).mockResolvedValue(pageOf([priya, ravi]));
  vi.mocked(chatService.getConversation).mockResolvedValue(priya);
  vi.mocked(chatService.getMessages).mockResolvedValue(
    chatMessagePage([
      chatMessage({ id: 1, body: 'Where is my order?' }),
      chatMessage({
        id: 2,
        body: 'Let me check',
        fromStaff: true,
        senderUserId: 1,
        senderName: 'Eren Yeager',
        senderRole: 'PLATFORM_ADMIN',
        readAt: '2026-10-08T10:17:00Z',
      }),
    ]),
  );
  vi.mocked(chatService.markRead).mockResolvedValue(undefined);
  vi.mocked(chatService.setPinned).mockResolvedValue(undefined);
  vi.mocked(chatService.setMuted).mockResolvedValue(undefined);
});

describe('ChatPage conversation list', () => {
  it('shows pinned and recent conversations, the unread count and the delivery ticks', async () => {
    vi.mocked(chatService.getConversations).mockResolvedValue(
      pageOf([
        { ...priya, pinned: true, unreadCount: 2 },
        ravi,
        chatConversation({
          id: 9,
          participant: { userId: 9, name: 'Asha Nair', role: 'DELIVERY_PARTNER', online: false },
          lastMessage: {
            preview: 'On it',
            at: '2026-10-08T10:21:00Z',
            fromStaff: true,
            read: false,
          },
        }),
      ]),
    );
    renderChat();

    const pinned = await screen.findByRole('region', { name: 'Pinned Message' });
    const recent = screen.getByRole('region', { name: 'Recent Message' });
    expect(within(pinned).getByText('Priya Sharma')).toBeInTheDocument();
    expect(within(pinned).getByLabelText('2 unread messages')).toBeInTheDocument();
    // Sent by staff and read: two ticks ("Read"); sent and not yet read: one ("Sent")
    expect(within(recent).getByText('Ravi Kumar')).toBeInTheDocument();
    expect(within(recent).getByText('Read')).toBeInTheDocument();
    expect(within(recent).getByText('Sent')).toBeInTheDocument();
    // The online dot has a text for screen readers; an offline one has none
    expect(within(recent).getAllByText('Online')).toHaveLength(1);
  });

  it('leaves out a section with nothing in it, and says so when there are no conversations', async () => {
    renderChat();
    expect(await screen.findByRole('region', { name: 'Recent Message' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Pinned Message' })).not.toBeInTheDocument();
  });

  it('asks to start one when there are none', async () => {
    vi.mocked(chatService.getConversations).mockResolvedValue(pageOf([]));
    renderChat();
    expect(await screen.findByText('No conversations yet')).toBeInTheDocument();
    expect(screen.getByText('Start one with +')).toBeInTheDocument();
  });

  it('shows an error with a retry', async () => {
    vi.mocked(chatService.getConversations).mockRejectedValueOnce(apiError(500, 'server'));
    renderChat();

    await screen.findByText('Unable to load the conversations');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Ravi Kumar')).toBeInTheDocument();
  });

  it('searches by what is typed, once typing pauses', async () => {
    renderChat();
    await screen.findByText('Ravi Kumar');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search conversations' }), 'ravi');

    await waitFor(() => expect(chatService.getConversations).toHaveBeenCalledWith('ravi', 0, 20));
  });

  it('loads more conversations on request', async () => {
    vi.mocked(chatService.getConversations)
      .mockResolvedValueOnce(pageOf([priya], 2, 0, 1))
      .mockResolvedValueOnce(pageOf([ravi], 2, 1, 1));
    renderChat();

    await userEvent.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByText('Ravi Kumar')).toBeInTheDocument();
    expect(chatService.getConversations).toHaveBeenLastCalledWith('', 1, 20);
  });
});

describe('ChatPage new chat', () => {
  it('finds a user (not a platform admin), starts the conversation and opens it', async () => {
    vi.mocked(userService.searchUsers).mockResolvedValue(
      pageOf([
        user({ id: 3, fullName: 'Admin Two', role: 'PLATFORM_ADMIN', email: 'a2@example.com' }),
        user({ id: 11, fullName: 'Meera Iyer', email: 'meera@example.com' }),
      ]),
    );
    const meera = chatConversation({
      id: 20,
      participant: { userId: 11, name: 'Meera Iyer', role: 'CUSTOMER', online: null },
      lastMessage: null,
    });
    vi.mocked(chatService.startConversation).mockResolvedValue(meera);
    vi.mocked(chatService.getConversation).mockResolvedValue(meera);
    vi.mocked(chatService.getMessages).mockResolvedValue(chatMessagePage([]));
    renderChat();
    await screen.findByText('Ravi Kumar');

    await userEvent.click(screen.getByRole('button', { name: 'New chat' }));
    const dialog = await screen.findByRole('dialog', { name: 'New chat' });

    expect(await within(dialog).findByText('Meera Iyer')).toBeInTheDocument();
    expect(within(dialog).getByText('meera@example.com')).toBeInTheDocument();
    expect(within(dialog).getByText('Customer')).toBeInTheDocument();
    expect(within(dialog).queryByText('Admin Two')).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: /Meera Iyer/ }));

    expect(chatService.startConversation).toHaveBeenCalledWith(11);
    await waitFor(() => expect(chatService.getConversation).toHaveBeenCalledWith(20));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Meera Iyer' })).toBeInTheDocument();
  });

  it('says why a conversation could not be started', async () => {
    vi.mocked(userService.searchUsers).mockResolvedValue(pageOf([user({ id: 11 })]));
    vi.mocked(chatService.startConversation).mockRejectedValue(apiError(404, 'notFound'));
    renderChat();
    await screen.findByText('Ravi Kumar');

    await userEvent.click(screen.getByRole('button', { name: 'New chat' }));
    const dialog = await screen.findByRole('dialog', { name: 'New chat' });
    await userEvent.click(await within(dialog).findByRole('button', { name: /Priya Sharma/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nope');
  });
});

describe('ChatPage open conversation', () => {
  it('shows the header, the messages with who wrote them, and marks the conversation read', async () => {
    vi.mocked(chatService.getConversation).mockResolvedValue({ ...priya, unreadCount: 2 });
    renderChat('/admin/chat/5');

    expect(await screen.findByRole('heading', { name: 'Priya Sharma' })).toBeInTheDocument();
    expect(await screen.findByText('Where is my order?')).toBeInTheDocument();
    expect(within(screen.getByRole('log')).getByText('8 Oct 2026')).toBeInTheDocument();
    // The sender's name above Support's bubble, ticks below it (read)
    expect(screen.getByText('Eren Yeager')).toBeInTheDocument();
    const log = screen.getByRole('log', { name: 'Messages' });
    expect(within(log).getByText('Read')).toBeInTheDocument();
    await waitFor(() => expect(chatService.markRead).toHaveBeenCalledWith(5));
    expect(chatService.getMessages).toHaveBeenCalledWith(5, { beforeId: undefined, q: '' });
  });

  it('does not mark a conversation read that has nothing unread', async () => {
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');
    await new Promise((resolve) => setTimeout(resolve, 600));
    expect(chatService.markRead).not.toHaveBeenCalled();
  });

  it('marks it read from the menu', async () => {
    vi.mocked(chatService.getConversation).mockResolvedValue({ ...priya, unreadCount: 1 });
    vi.mocked(chatService.markRead).mockRejectedValue(apiError(500, 'server'));
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');
    await waitFor(() => expect(chatService.markRead).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Priya Sharma' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Mark as read' }));

    await waitFor(() => expect(chatService.markRead).toHaveBeenCalledTimes(2));
  });

  it('shows "Conversation not found" for an address that is not an id', async () => {
    renderChat('/admin/chat/abc');
    expect(await screen.findByText('Conversation not found')).toBeInTheDocument();
  });

  it('shows the online status of the participant', async () => {
    vi.mocked(chatService.getConversation).mockResolvedValue(ravi);
    renderChat('/admin/chat/6');
    const header = (await screen.findByRole('heading', { name: 'Ravi Kumar' })).closest('header')!;
    expect(within(header).getAllByText('Online').length).toBeGreaterThan(0);
    expect(within(header).getByText('Restaurant owner')).toBeInTheDocument();
  });

  it('mutes and unmutes the conversation', async () => {
    vi.mocked(chatService.setMuted).mockImplementation(() => {
      vi.mocked(chatService.getConversation).mockResolvedValue({ ...priya, muted: true });
      return Promise.resolve();
    });
    renderChat('/admin/chat/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Mute conversation' }));

    expect(chatService.setMuted).toHaveBeenCalledWith(5, true);
    expect(await screen.findByRole('button', { name: 'Unmute conversation' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('pins the conversation from the menu, and says when it failed', async () => {
    vi.mocked(chatService.setPinned).mockImplementationOnce(() => {
      vi.mocked(chatService.getConversation).mockResolvedValue({ ...priya, pinned: true });
      return Promise.resolve();
    });
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Priya Sharma' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Pin' }));
    expect(chatService.setPinned).toHaveBeenCalledWith(5, true);

    vi.mocked(chatService.setPinned).mockRejectedValueOnce(apiError(500, 'server'));
    await userEvent.click(screen.getByRole('button', { name: 'Actions for Priya Sharma' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Unpin' }));
    expect(await screen.findByText('Unable to do that. Please try again.')).toBeInTheDocument();
  });

  it('shows who the conversation is with, and links a customer to their page', async () => {
    renderChat('/admin/chat/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Conversation info' }));

    const dialog = await screen.findByRole('dialog', { name: 'Conversation info' });
    expect(within(dialog).getByText('Priya Sharma')).toBeInTheDocument();
    expect(within(dialog).getByText('#7')).toBeInTheDocument();
    expect(within(dialog).getByText('Customer')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('link', { name: 'View customer' }));
    expect(await screen.findByText('Customer page')).toBeInTheDocument();
  });

  it('gives no customer link for a restaurant owner', async () => {
    vi.mocked(chatService.getConversation).mockResolvedValue(ravi);
    renderChat('/admin/chat/6');
    await userEvent.click(await screen.findByRole('button', { name: 'Conversation info' }));

    const dialog = await screen.findByRole('dialog', { name: 'Conversation info' });
    expect(within(dialog).queryByRole('link')).not.toBeInTheDocument();
  });

  it('searches inside the conversation with the q parameter, and clears it', async () => {
    const all = await chatService.getMessages(5);
    vi.mocked(chatService.getMessages).mockImplementation((_id, options) =>
      Promise.resolve(options?.q ? chatMessagePage([]) : all),
    );
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');

    await userEvent.click(screen.getByRole('button', { name: 'Search in conversation' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search messages' }), 'refund');
    await waitFor(() =>
      expect(chatService.getMessages).toHaveBeenCalledWith(5, { beforeId: undefined, q: 'refund' }),
    );
    expect(await screen.findByText('No messages match your search.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(screen.queryByRole('searchbox', { name: 'Search messages' })).not.toBeInTheDocument();
    expect(
      await within(screen.getByRole('log')).findByText('Where is my order?'),
    ).toBeInTheDocument();
  });

  it('loads earlier messages above the ones shown, in order', async () => {
    vi.mocked(chatService.getMessages)
      .mockResolvedValueOnce(
        chatMessagePage(
          [chatMessage({ id: 11, body: 'eleven' }), chatMessage({ id: 12, body: 'twelve' })],
          true,
          11,
        ),
      )
      .mockResolvedValueOnce(
        chatMessagePage([
          chatMessage({ id: 9, body: 'nine' }),
          chatMessage({ id: 10, body: 'ten' }),
        ]),
      );
    renderChat('/admin/chat/5');

    await userEvent.click(await screen.findByRole('button', { name: 'Load earlier messages' }));

    await screen.findByText('nine');
    expect(chatService.getMessages).toHaveBeenLastCalledWith(5, { beforeId: 11, q: '' });
    const log = screen.getByRole('log', { name: 'Messages' });
    expect(
      within(log)
        .getAllByText(/^(nine|ten|eleven|twelve)$/)
        .map((node) => node.textContent),
    ).toEqual(['nine', 'ten', 'eleven', 'twelve']);
    expect(screen.queryByRole('button', { name: 'Load earlier messages' })).not.toBeInTheDocument();
  });

  it('shows each message once and in id order even when pages overlap', async () => {
    vi.mocked(chatService.getMessages)
      .mockResolvedValueOnce(
        chatMessagePage(
          [chatMessage({ id: 3, body: 'three' }), chatMessage({ id: 4, body: 'four' })],
          true,
          3,
        ),
      )
      .mockResolvedValueOnce(
        chatMessagePage([
          chatMessage({ id: 2, body: 'two' }),
          chatMessage({ id: 3, body: 'three' }),
        ]),
      );
    renderChat('/admin/chat/5');

    await userEvent.click(await screen.findByRole('button', { name: 'Load earlier messages' }));

    await screen.findByText('two');
    expect(screen.getAllByText('three')).toHaveLength(1);
    const log = screen.getByRole('log', { name: 'Messages' });
    expect(
      within(log)
        .getAllByText(/^(two|three|four)$/)
        .map((node) => node.textContent),
    ).toEqual(['two', 'three', 'four']);
  });
});

describe('ChatPage composer', () => {
  const serverMessage = (clientMessageId: string, body: string) =>
    chatMessage({
      id: 50,
      body,
      clientMessageId,
      fromStaff: true,
      senderUserId: 1,
      senderName: 'Platform Administrator',
      senderRole: 'PLATFORM_ADMIN',
    });

  it('sends with Enter, shows the bubble at once and replaces it by the server message', async () => {
    let finish: (message: ReturnType<typeof serverMessage>) => void = () => undefined;
    vi.mocked(chatService.sendMessage).mockImplementation(
      (_id, request) =>
        new Promise((resolve) => {
          finish = (message) => resolve({ ...message, clientMessageId: request.clientMessageId });
        }),
    );
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');

    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'On my way{Enter}');

    expect(await screen.findByText('On my way')).toBeInTheDocument();
    expect(screen.getByText('Sending…')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('');
    const [, request] = vi.mocked(chatService.sendMessage).mock.calls[0];
    expect(request).toEqual({ body: 'On my way', clientMessageId: expect.any(String) as string });

    finish(serverMessage(request.clientMessageId, 'On my way'));

    await waitFor(() => expect(screen.queryByText('Sending…')).not.toBeInTheDocument());
    expect(screen.getAllByText('On my way')).toHaveLength(1);
    expect(screen.getByText('Sent')).toBeInTheDocument();
  });

  it('keeps a failed message with "Failed to send. Retry" and retries with the same client id', async () => {
    vi.mocked(chatService.sendMessage)
      .mockRejectedValueOnce(apiError(429, 'tooManyRequests'))
      .mockImplementationOnce((_id, request) =>
        Promise.resolve(serverMessage(request.clientMessageId, 'Hello again')),
      );
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');

    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'Hello again');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    const retry = await screen.findByRole('button', { name: 'Failed to send. Retry' });
    expect(screen.getByText('You are sending messages too quickly')).toBeInTheDocument();
    expect(screen.getByText('Hello again')).toBeInTheDocument();

    await userEvent.click(retry);

    await waitFor(() => expect(chatService.sendMessage).toHaveBeenCalledTimes(2));
    const [first, second] = vi.mocked(chatService.sendMessage).mock.calls;
    expect(second[1].clientMessageId).toBe(first[1].clientMessageId);
    expect(second[1].body).toBe('Hello again');
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Failed to send. Retry' }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getAllByText('Hello again')).toHaveLength(1);
  });

  it('says when chat is temporarily unavailable', async () => {
    vi.mocked(chatService.sendMessage).mockRejectedValue(apiError(503, 'unavailable'));
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');

    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'Hi{Enter}');

    expect(
      await screen.findByText('Chat is temporarily unavailable. Please try again in a moment.'),
    ).toBeInTheDocument();
  });

  it('makes a new line with Shift+Enter and sends with Enter', async () => {
    vi.mocked(chatService.sendMessage).mockImplementation((_id, request) =>
      Promise.resolve(serverMessage(request.clientMessageId, request.body)),
    );
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');
    const field = screen.getByRole('textbox', { name: 'Message' });

    await userEvent.type(field, 'one{Shift>}{Enter}{/Shift}two');
    expect(field).toHaveValue('one\ntwo');
    expect(chatService.sendMessage).not.toHaveBeenCalled();

    await userEvent.type(field, '{Enter}');
    expect(chatService.sendMessage).toHaveBeenCalledTimes(1);
    expect(vi.mocked(chatService.sendMessage).mock.calls[0][1].body).toBe('one\ntwo');
  });

  it('keeps Send off for a blank message, and Cancel clears the draft', async () => {
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');
    const field = screen.getByRole('textbox', { name: 'Message' });
    const send = screen.getByRole('button', { name: 'Send' });

    expect(send).toBeDisabled();
    await userEvent.type(field, '   ');
    expect(send).toBeDisabled();
    await userEvent.type(field, 'draft');
    expect(send).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(field).toHaveValue('');
    expect(chatService.sendMessage).not.toHaveBeenCalled();
  });

  it('shows a counter near the limit and has no rich text, attach or image buttons', async () => {
    renderChat('/admin/chat/5');
    await screen.findByText('Where is my order?');
    const field = screen.getByRole('textbox', { name: 'Message' });

    expect(field).toHaveAttribute('maxlength', '2000');
    expect(screen.queryByText(/\/2000/)).not.toBeInTheDocument();
    await userEvent.click(field);
    await userEvent.paste('x'.repeat(1900));
    expect(screen.getByText('1900/2000')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /bold|italic|attach|image/i }),
    ).not.toBeInTheDocument();
  });

  it('draws message text as plain text, never as markup', async () => {
    vi.mocked(chatService.getMessages).mockResolvedValue(
      chatMessagePage([
        chatMessage({ id: 1, body: '<img src=x onerror=alert(1)> https://example.com' }),
      ]),
    );
    renderChat('/admin/chat/5');

    expect(
      await screen.findByText('<img src=x onerror=alert(1)> https://example.com'),
    ).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
    expect(screen.queryByRole('link', { name: /example\.com/ })).not.toBeInTheDocument();
  });
});
