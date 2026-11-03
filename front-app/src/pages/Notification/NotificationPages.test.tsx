import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import NotificationDetailsPage from '@/pages/Notification/NotificationDetailsPage';
import NotificationsPage from '@/pages/Notification/NotificationsPage';
import { notificationService } from '@/services/notificationService';
import { pageOf } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { AdminNotification, InboxNotification } from '@/types/notification';

vi.mock('@/services/notificationService', () => ({
  notificationService: {
    getUnreadCount: vi.fn(),
    getInbox: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
    searchNotifications: vi.fn(),
    getSummary: vi.fn(),
    getNotification: vi.fn(),
    resend: vi.fn(),
    getAuditLogs: vi.fn(),
  },
}));

function inboxItem(overrides: Partial<InboxNotification> = {}): InboxNotification {
  return {
    id: 5,
    type: 'ORDER_DELIVERED',
    title: 'Order #42 delivered',
    message: 'Enjoy your meal!',
    orderId: 42,
    sentAt: '2026-10-08T11:00:00Z',
    readAt: null,
    ...overrides,
  };
}

function adminNotification(overrides: Partial<AdminNotification> = {}): AdminNotification {
  return {
    id: 70,
    type: 'WELCOME',
    channel: 'SMS',
    status: 'FAILED',
    recipientUserId: 7,
    restaurantId: null,
    orderId: null,
    recipientAddress: '+919800000007',
    subject: 'Welcome to FoodHub',
    body: 'Hi Priya, welcome!',
    attempts: 5,
    nextAttemptAt: null,
    lastError: 'SMS gateway timeout',
    sourceEventId: 'evt-1',
    requestId: null,
    createdAt: '2026-10-08T09:00:00Z',
    sentAt: null,
    readAt: null,
    ...overrides,
  };
}

const renderDetails = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/notifications/:notificationId" element={<NotificationDetailsPage />} />
    </Routes>,
    { route: '/admin/notifications/70' },
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(notificationService.getUnreadCount).mockResolvedValue(1);
  vi.mocked(notificationService.getInbox).mockResolvedValue(
    pageOf([
      inboxItem(),
      inboxItem({
        id: 4,
        title: 'Order #41 placed',
        message: 'The restaurant has your order.',
        readAt: '2026-10-07T10:00:00Z',
      }),
    ]),
  );
  vi.mocked(notificationService.getAuditLogs).mockResolvedValue(pageOf([]));
});

describe('NotificationsPage', () => {
  it("shows the acting user's notifications and marks them read", async () => {
    vi.mocked(notificationService.markRead).mockResolvedValue(
      inboxItem({ readAt: '2026-10-08T12:00:00Z' }),
    );
    vi.mocked(notificationService.markAllRead).mockResolvedValue({ unread: 0 });
    renderWithProviders(<NotificationsPage />, { route: '/admin/notifications' });

    expect(await screen.findByText('Order #42 delivered')).toBeInTheDocument();
    expect(await screen.findByRole('tab', { name: 'My notifications (1)' })).toBeInTheDocument();
    // Only the unread one can be marked read
    expect(screen.getAllByRole('button', { name: /^Mark ".*" as read$/ })).toHaveLength(1);

    await userEvent.click(
      screen.getByRole('button', { name: 'Mark "Order #42 delivered" as read' }),
    );
    expect(notificationService.markRead).toHaveBeenCalledWith(5);

    await userEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    expect(notificationService.markAllRead).toHaveBeenCalled();
    expect(
      await screen.findByText('All your notifications are marked as read.'),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Unread only'));
    expect(notificationService.getInbox).toHaveBeenLastCalledWith(true, 0, 20);
  });

  it("shows today's totals and every notification, and finds the failed ones", async () => {
    vi.mocked(notificationService.getSummary).mockResolvedValue({
      fromDate: '2026-10-08',
      toDate: '2026-10-08',
      total: 42,
      byStatus: { SENT: 40, FAILED: 2 },
      byChannel: { EMAIL: 20, PUSH: 22 },
    });
    vi.mocked(notificationService.searchNotifications).mockResolvedValue(
      pageOf([adminNotification()]),
    );
    renderWithProviders(<NotificationsPage />, { route: '/admin/notifications?tab=all' });

    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(notificationService.getSummary).toHaveBeenCalledWith(undefined, undefined);
    expect(await screen.findByText('SMS gateway timeout')).toBeInTheDocument();
    expect(screen.getByText('Welcome to FoodHub')).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Status'), 'Failed');
    expect(notificationService.searchNotifications).toHaveBeenLastCalledWith(
      { status: 'FAILED' },
      0,
      20,
    );
  });
});

describe('NotificationDetailsPage', () => {
  it('resends a failed notification with a reason', async () => {
    vi.mocked(notificationService.getNotification).mockResolvedValue(adminNotification());
    vi.mocked(notificationService.resend).mockResolvedValue(
      adminNotification({ status: 'PENDING', attempts: 0, lastError: null }),
    );
    renderDetails();

    expect(await screen.findByText('SMS gateway timeout')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Resend' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Gateway is back');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Resend' }));

    expect(notificationService.resend).toHaveBeenCalledWith(70, 'Gateway is back');
    expect(
      await screen.findByText('Notification #70 will be sent again in a moment.'),
    ).toBeInTheDocument();
  });

  it('does not resend a notification that was sent', async () => {
    vi.mocked(notificationService.getNotification).mockResolvedValue(
      adminNotification({ status: 'SENT', lastError: null, sentAt: '2026-10-08T09:00:01Z' }),
    );
    renderDetails();

    expect(await screen.findByRole('button', { name: 'Resend' })).toBeDisabled();
    expect(screen.getByText('It was sent already.')).toBeInTheDocument();
  });
});
