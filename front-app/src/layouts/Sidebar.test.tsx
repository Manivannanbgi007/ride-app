import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MainLayout } from '@/layouts/MainLayout';
import { chatService } from '@/services/chatService';
import { notificationService } from '@/services/notificationService';
import { userService } from '@/services/userService';
import { adminProfile } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/userService', () => ({ userService: { getMyProfile: vi.fn() } }));
vi.mock('@/services/chatService', () => ({ chatService: { getUnreadCount: vi.fn() } }));
vi.mock('@/services/notificationService', () => ({
  notificationService: { getUnreadCount: vi.fn() },
}));

/** jsdom has no matchMedia: answers every query as a wide (true) or small (false) screen. */
function screenIs(wide: boolean) {
  window.matchMedia = vi.fn((query: string) => ({
    matches: wide,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

const renderAdmin = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin" element={<MainLayout />}>
        <Route path="orders" element={<p>Orders page</p>} />
      </Route>
    </Routes>,
    { route: '/admin/orders' },
  );

/** The tooltip repeats the link's title (the title itself stays in the link for screen readers). */
const shownTimes = (text: string) => screen.queryAllByText(text).length;

beforeEach(() => {
  vi.mocked(userService.getMyProfile).mockResolvedValue(adminProfile);
  vi.mocked(notificationService.getUnreadCount).mockResolvedValue(0);
  vi.mocked(chatService.getUnreadCount).mockResolvedValue(0);
});

afterEach(() => {
  // jsdom has none: remove the stand-in again
  Reflect.deleteProperty(window, 'matchMedia');
  localStorage.clear();
});

describe('Sidebar on a wide screen', () => {
  it('narrows to its icons and back, with titles on hover, and remembers the choice', async () => {
    screenIs(true);
    renderAdmin();

    const toggle = screen.getByRole('button', { name: 'Collapse the menu' });
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await userEvent.hover(screen.getByRole('link', { name: 'Customers' }));
    expect(shownTimes('Customers')).toBe(1);

    await userEvent.click(toggle);
    expect(screen.getByRole('button', { name: 'Expand the menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(localStorage.getItem('front-app.sidebar-collapsed')).toBe('true');
    // Still a named link for screen readers, and its title shows beside the icon on hover
    const customers = screen.getByRole('link', { name: 'Customers' });
    await userEvent.hover(customers);
    expect(shownTimes('Customers')).toBe(2);
    await userEvent.unhover(customers);
    // It slips away (a short animation), then is gone
    await waitFor(() => expect(shownTimes('Customers')).toBe(1));

    await userEvent.click(screen.getByRole('button', { name: 'Expand the menu' }));
    expect(screen.getByRole('button', { name: 'Collapse the menu' })).toBeInTheDocument();
    expect(localStorage.getItem('front-app.sidebar-collapsed')).toBe('false');
  });

  it('opens narrowed when the admin left it so', () => {
    screenIs(true);
    localStorage.setItem('front-app.sidebar-collapsed', 'true');
    renderAdmin();

    expect(screen.getByRole('button', { name: 'Expand the menu' })).toBeInTheDocument();
  });
});

describe('Sidebar on a small screen', () => {
  it('slides in over the page from the menu button', async () => {
    screenIs(false);
    renderAdmin();

    const sidebar = document.getElementById('admin-sidebar');
    expect(sidebar).toHaveClass('-translate-x-full');
    await userEvent.click(screen.getByRole('button', { name: 'Open the navigation' }));
    expect(sidebar).toHaveClass('translate-x-0');
  });
});

describe('Sidebar chat entry', () => {
  it('lists Chat after Calendar with the number of unread conversations', async () => {
    screenIs(true);
    vi.mocked(chatService.getUnreadCount).mockResolvedValue(4);
    renderAdmin();

    const nav = screen.getByRole('navigation', { name: 'Main' });
    const labels = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent);
    const calendar = labels.findIndex((text) => text?.startsWith('Calendar'));
    expect(labels[calendar + 1]).toMatch(/^Chat/);
    expect(await within(nav).findByRole('link', { name: 'Chat, 4 unread' })).toHaveAttribute(
      'href',
      '/admin/chat',
    );
    // The header's chat icon carries the same number
    expect(await screen.findByRole('link', { name: 'Chat (4 unread)' })).toBeInTheDocument();
  });

  it('shows no badge when nothing is unread', async () => {
    screenIs(true);
    renderAdmin();

    const nav = screen.getByRole('navigation', { name: 'Main' });
    await waitFor(() => expect(chatService.getUnreadCount).toHaveBeenCalled());
    expect(within(nav).getByRole('link', { name: 'Chat' })).toBeInTheDocument();
  });
});
