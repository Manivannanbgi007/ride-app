import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CustomerHeader } from '@/layouts/customer/CustomerHeader';
import { chatService } from '@/services/chatService';
import { notificationService } from '@/services/notificationService';
import { orderService } from '@/services/orderService';
import { makeStore } from '@/store/store';
import { emptyCart, priya } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({ orderService: { getCart: vi.fn() } }));
vi.mock('@/services/notificationService', () => ({
  notificationService: { getUnreadCount: vi.fn(), getInbox: vi.fn() },
}));
vi.mock('@/services/chatService', () => ({ chatService: { getMyConversation: vi.fn() } }));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(orderService.getCart).mockResolvedValue(emptyCart);
  vi.mocked(notificationService.getUnreadCount).mockResolvedValue(0);
  vi.mocked(chatService.getMyConversation).mockResolvedValue(null);
});

describe('CustomerHeader', () => {
  it('leads a signed-in customer to their dashboard', () => {
    renderWithProviders(<CustomerHeader />, {
      route: '/customer/restaurants',
      store: makeStore({ session: { adminUser: null, customer: priya } }),
    });

    const main = within(screen.getByRole('navigation', { name: 'Main' }));
    expect(main.getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'href',
      '/customer/dashboard',
    );
    // On a phone the main row has no room: an icon next to the cart stands in
    expect(screen.getByRole('link', { name: 'My dashboard' })).toHaveAttribute(
      'href',
      '/customer/dashboard',
    );
  });

  it('shows no dashboard to a visitor', () => {
    renderWithProviders(<CustomerHeader />, { route: '/customer' });

    const main = within(screen.getByRole('navigation', { name: 'Main' }));
    expect(main.getByRole('link', { name: 'Restaurants' })).toBeInTheDocument();
    expect(main.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'My dashboard' })).not.toBeInTheDocument();
  });
});
