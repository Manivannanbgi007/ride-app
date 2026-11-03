import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CustomerSpendCard } from '@/components/business/Customer/CustomerSpendCard';
import { orderService } from '@/services/orderService';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({
  orderService: { getCustomerSummary: vi.fn() },
}));

const summary = {
  customerId: 7,
  totalOrders: 12,
  activeOrders: 1,
  deliveredOrders: 9,
  cancelledOrders: 2,
  rejectedOrders: 0,
  totalSpent: 4520.5,
  lastOrderAt: '2026-10-08T10:15:00Z',
  currency: 'INR',
};

const card = (
  <CustomerSpendCard
    customerId={7}
    customerName="Priya Sharma"
    memberSince="2026-09-15T09:00:00Z"
  />
);

describe('CustomerSpendCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the total spent, the order counts, the name and the member-since month', async () => {
    vi.mocked(orderService.getCustomerSummary).mockResolvedValue(summary);
    renderWithProviders(card);

    expect(await screen.findByText('₹4,520.50')).toBeInTheDocument();
    expect(screen.getByText('Total spent')).toBeInTheDocument();
    expect(screen.getByText('9 delivered · 12 orders')).toBeInTheDocument();
    expect(screen.getByText('Customer since 09/26')).toBeInTheDocument();
    expect(screen.getByText('Priya Sharma')).toBeInTheDocument();
  });

  it('shows an error that can be retried', async () => {
    vi.mocked(orderService.getCustomerSummary).mockRejectedValueOnce(new Error('boom'));
    vi.mocked(orderService.getCustomerSummary).mockResolvedValue(summary);
    renderWithProviders(card);

    expect(await screen.findByText("Unable to load this customer's orders")).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('₹4,520.50')).toBeInTheDocument();
  });
});
