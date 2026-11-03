import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SearchPage from '@/pages/Search/SearchPage';
import { catalogService } from '@/services/catalogService';
import { orderService } from '@/services/orderService';
import { userService } from '@/services/userService';
import { adminUser, menuItem, orderSummary, pageOf } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({
  orderService: { searchOrders: vi.fn() },
}));
vi.mock('@/services/userService', () => ({
  userService: { searchUsers: vi.fn() },
}));
vi.mock('@/services/catalogService', () => ({
  catalogService: { searchMenuItems: vi.fn() },
}));

const renderSearch = (text: string) =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/search" element={<SearchPage />} />
      <Route path="/admin/customers" element={<p>Customer list</p>} />
    </Routes>,
    { route: `/admin/search?q=${encodeURIComponent(text)}` },
  );

describe('SearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    vi.mocked(userService.searchUsers).mockResolvedValue(pageOf([adminUser()]));
    vi.mocked(catalogService.searchMenuItems).mockResolvedValue(pageOf([menuItem()]));
  });

  it('looks for a number as an order number and in customers, not in dishes', async () => {
    renderSearch('42');

    expect(await screen.findByRole('link', { name: '#42' })).toBeInTheDocument();
    expect(orderService.searchOrders).toHaveBeenCalledWith({ orderId: 42 }, 0, 5);
    expect(userService.searchUsers).toHaveBeenCalledWith(
      { search: '42', sort: 'createdAt,desc' },
      0,
      5,
    );
    expect(catalogService.searchMenuItems).not.toHaveBeenCalled();
  });

  it('looks for text in customers and dishes', async () => {
    renderSearch('biryani');

    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
    expect(await screen.findByText('Priya Sharma')).toBeInTheDocument();
    expect(orderService.searchOrders).not.toHaveBeenCalled();
  });

  it('opens the customer list with the same search when there are more results', async () => {
    vi.mocked(userService.searchUsers).mockResolvedValue(pageOf([adminUser()], 12));
    const { store } = renderSearch('sharma');

    await userEvent.click(await screen.findByRole('button', { name: 'Show all 12' }));

    expect(screen.getByText('Customer list')).toBeInTheDocument();
    expect(store.getState().customerList.filters.search).toBe('sharma');
  });

  it('asks for something to search when the box was empty', () => {
    renderSearch('');

    expect(screen.getByText('Search orders, customers and dishes')).toBeInTheDocument();
    expect(userService.searchUsers).not.toHaveBeenCalled();
  });
});
