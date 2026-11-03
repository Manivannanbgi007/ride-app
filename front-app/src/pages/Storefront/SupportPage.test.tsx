import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SupportLink } from '@/layouts/customer/SupportLink';
import SupportPage from '@/pages/Storefront/SupportPage';
import { chatService } from '@/services/chatService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { chatConversation, chatMessage, chatMessagePage, priya } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/chatService', () => ({
  CHAT_PAGE_SIZE: 30,
  chatService: {
    getMyConversation: vi.fn(),
    getMyMessages: vi.fn(),
    sendMyMessage: vi.fn(),
    markMyConversationRead: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({ userService: { getMyProfile: vi.fn() } }));

const signedIn = () => makeStore({ session: { adminUser: null, customer: priya } });

const renderSupport = () =>
  renderWithProviders(
    <Routes>
      <Route path="/customer/support" element={<SupportPage />} />
    </Routes>,
    { route: '/customer/support', store: signedIn() },
  );

const mine = chatConversation({ id: 5 });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userService.getMyProfile).mockResolvedValue({
    id: 7,
    email: priya.email,
    fullName: priya.fullName,
    phone: null,
    role: 'CUSTOMER',
    status: 'ACTIVE',
    createdAt: '2026-09-01T10:00:00Z',
    lastLoginAt: null,
  });
  vi.mocked(chatService.markMyConversationRead).mockResolvedValue(undefined);
});

describe('SupportPage', () => {
  it('invites a customer without a conversation to start one, and the first message creates it', async () => {
    vi.mocked(chatService.getMyConversation).mockResolvedValue(null);
    vi.mocked(chatService.sendMyMessage).mockImplementation((request) =>
      Promise.resolve(
        chatMessage({ id: 1, body: request.body, clientMessageId: request.clientMessageId }),
      ),
    );
    renderSupport();

    expect(await screen.findByText('Start a conversation with Support')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Support' })).toBeInTheDocument();
    expect(screen.getByText('We usually reply within a few hours')).toBeInTheDocument();
    expect(chatService.getMyMessages).not.toHaveBeenCalled();

    vi.mocked(chatService.getMyConversation).mockResolvedValue(mine);
    vi.mocked(chatService.getMyMessages).mockResolvedValue(
      chatMessagePage([chatMessage({ id: 1, body: 'My order is late' })]),
    );
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Message' }),
      'My order is late{Enter}',
    );

    expect(chatService.sendMyMessage).toHaveBeenCalledWith({
      body: 'My order is late',
      clientMessageId: expect.any(String) as string,
    });
    expect(await screen.findByText('My order is late')).toBeInTheDocument();
    expect(screen.queryByText('Start a conversation with Support')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText('My order is late')).toHaveLength(1));
  });

  it('shows the conversation: own messages on the right, Support with its name, and marks it read', async () => {
    vi.mocked(chatService.getMyConversation).mockResolvedValue({ ...mine, unreadCount: 1 });
    vi.mocked(chatService.getMyMessages).mockResolvedValue(
      chatMessagePage([
        chatMessage({ id: 1, body: 'My order is late', readAt: '2026-10-08T10:16:00Z' }),
        chatMessage({
          id: 2,
          body: 'Checking now',
          fromStaff: true,
          senderUserId: 1,
          senderName: 'Eren Yeager',
          senderRole: 'PLATFORM_ADMIN',
        }),
      ]),
    );
    renderSupport();

    const log = await screen.findByRole('log', { name: 'Messages' });
    expect(await within(log).findByText('Checking now')).toBeInTheDocument();
    expect(within(log).getByText('Eren Yeager')).toBeInTheDocument();
    expect(within(log).getByText('Read')).toBeInTheDocument();
    expect(chatService.getMyMessages).toHaveBeenCalledWith({ beforeId: undefined });
    await waitFor(() => expect(chatService.markMyConversationRead).toHaveBeenCalledTimes(1));
  });

  it('says it could not load, with a retry', async () => {
    vi.mocked(chatService.getMyConversation).mockRejectedValueOnce(new Error('boom'));
    vi.mocked(chatService.getMyConversation).mockResolvedValue(null);
    renderSupport();

    await screen.findByText('Unable to load this conversation');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Start a conversation with Support')).toBeInTheDocument();
  });
});

describe('SupportLink', () => {
  it('shows the unread messages of the conversation on the header icon', async () => {
    vi.mocked(chatService.getMyConversation).mockResolvedValue({ ...mine, unreadCount: 3 });
    renderWithProviders(<SupportLink />, { store: signedIn() });

    expect(await screen.findByRole('link', { name: 'Support chat (3 unread)' })).toHaveAttribute(
      'href',
      '/customer/support',
    );
  });

  it('shows nothing for a visitor', () => {
    renderWithProviders(<SupportLink />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
