import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RestaurantDetailsPage from '@/pages/Restaurant/RestaurantDetailsPage';
import { restaurantService } from '@/services/restaurantService';
import { adminRestaurant, pageOf, restaurantDetails } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/restaurantService', () => ({
  restaurantService: {
    searchRestaurants: vi.fn(),
    getRestaurant: vi.fn(),
    changeStatus: vi.fn(),
    getAuditLogs: vi.fn(),
  },
}));

const renderRestaurant = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/restaurants/:restaurantId" element={<RestaurantDetailsPage />} />
      <Route path="/admin/foods" element={<p>Food list</p>} />
    </Routes>,
    { route: '/admin/restaurants/3' },
  );

describe('RestaurantDetailsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(restaurantService.getAuditLogs).mockResolvedValue(pageOf([]));
  });

  it('offers approve and reject for a restaurant waiting for approval, and approves it', async () => {
    vi.mocked(restaurantService.getRestaurant).mockResolvedValue(
      adminRestaurant({ status: 'PENDING', approvedAt: null, statusReason: null }),
    );
    vi.mocked(restaurantService.changeStatus).mockResolvedValue(
      restaurantDetails({ status: 'APPROVED' }),
    );
    renderRestaurant();

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Activate' })).not.toBeInTheDocument();

    const dialog = screen.getByRole('dialog', { name: 'Approve Biryani House?' });
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Food license verified');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }));

    expect(restaurantService.changeStatus).toHaveBeenCalledWith(
      3,
      'approve',
      'Food license verified',
    );
    expect(await screen.findByText('Biryani House: approved (not live yet).')).toBeInTheDocument();
  });

  it('shows the business, owner, hours and documents of a live restaurant', async () => {
    vi.mocked(restaurantService.getRestaurant).mockResolvedValue(adminRestaurant());
    renderRestaurant();

    expect(await screen.findByText('Biryani House Pvt Ltd')).toBeInTheDocument();
    expect(screen.getByText('Ravi Kumar')).toBeInTheDocument();
    expect(screen.getByText('11:00-23:00')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open/ })).toHaveAttribute('target', '_blank');
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suspend' })).toBeInTheDocument();
  });

  it('still shows the restaurant when the owner cannot be loaded', async () => {
    vi.mocked(restaurantService.getRestaurant).mockResolvedValue(
      adminRestaurant({}, { lookupStatus: 'UNAVAILABLE', fullName: null, email: null }),
    );
    renderRestaurant();

    expect(await screen.findByText(/owner details cannot be loaded right now/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Account #20' })).toBeInTheDocument();
  });

  it("opens the food list filtered to the restaurant's dishes", async () => {
    vi.mocked(restaurantService.getRestaurant).mockResolvedValue(adminRestaurant());
    const { store } = renderRestaurant();

    await userEvent.click(await screen.findByRole('button', { name: 'Menu items' }));

    expect(screen.getByText('Food list')).toBeInTheDocument();
    expect(store.getState().foodList.filters.restaurantId).toBe(3);
  });
});
