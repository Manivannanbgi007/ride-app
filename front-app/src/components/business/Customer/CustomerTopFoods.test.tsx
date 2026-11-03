import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CustomerTopFoods } from '@/components/business/Customer/CustomerTopFoods';
import { orderService } from '@/services/orderService';
import { customerTopFood } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({
  orderService: { getCustomerTopFoods: vi.fn() },
}));

const answer = (items: ReturnType<typeof customerTopFood>[]) => ({
  customerId: 7,
  period: 'MONTHLY' as const,
  from: '2026-09-09',
  items,
});

describe('CustomerTopFoods', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists the dishes with counts, spend and a link to the dish', async () => {
    vi.mocked(orderService.getCustomerTopFoods).mockResolvedValue(answer([customerTopFood()]));
    renderWithProviders(<CustomerTopFoods customerId={7} />);

    const link = await screen.findByRole('link', { name: 'Chicken Biryani' });
    expect(link).toHaveAttribute('href', '/admin/foods/11');
    expect(screen.getByText('Biryani')).toBeInTheDocument();
    expect(screen.getByText('Ordered 4 times · 6 pcs')).toBeInTheDocument();
    expect(screen.getByText('₹1,800.00')).toBeInTheDocument();
    expect(screen.getByText('₹300.00 each')).toBeInTheDocument();
    expect(orderService.getCustomerTopFoods).toHaveBeenCalledWith(7, 'MONTHLY', 5);
  });

  it('asks again with the chosen period', async () => {
    vi.mocked(orderService.getCustomerTopFoods).mockResolvedValue(answer([customerTopFood()]));
    renderWithProviders(<CustomerTopFoods customerId={7} />);
    await screen.findByText('Chicken Biryani');

    await userEvent.click(screen.getByRole('tab', { name: 'Weekly' }));
    expect(orderService.getCustomerTopFoods).toHaveBeenLastCalledWith(7, 'WEEKLY', 5);
    await userEvent.click(screen.getByRole('tab', { name: 'Daily' }));
    expect(orderService.getCustomerTopFoods).toHaveBeenLastCalledWith(7, 'DAILY', 5);
  });

  it('says so when there are no orders in the period', async () => {
    vi.mocked(orderService.getCustomerTopFoods).mockResolvedValue(answer([]));
    renderWithProviders(<CustomerTopFoods customerId={7} />);

    expect(await screen.findByText('No orders in this period')).toBeInTheDocument();
  });

  it('shows the error and tries again', async () => {
    vi.mocked(orderService.getCustomerTopFoods).mockRejectedValueOnce(new Error('boom'));
    vi.mocked(orderService.getCustomerTopFoods).mockResolvedValue(answer([customerTopFood()]));
    renderWithProviders(<CustomerTopFoods customerId={7} />);

    expect(await screen.findByText('Unable to load the most ordered food')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
  });
});
