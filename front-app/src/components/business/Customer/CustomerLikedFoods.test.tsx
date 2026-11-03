import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CustomerLikedFoods } from '@/components/business/Customer/CustomerLikedFoods';
import { reviewService } from '@/services/reviewService';
import { likedDish } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/reviewService', () => ({
  reviewService: { getCustomerLikedDishes: vi.fn() },
}));

const dishes = [
  likedDish({ menuItemId: 1, dishName: 'Spaghetti', ratingCount: 2, averageRating: 5 }),
  likedDish({ menuItemId: 2, dishName: 'Burger', ratingCount: 6, averageRating: 4.5 }),
  likedDish({ menuItemId: 3, dishName: 'Pizza', ratingCount: 1, averageRating: 4 }),
];

describe('CustomerLikedFoods', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the legend with each dish share of the ratings and its count', async () => {
    vi.mocked(reviewService.getCustomerLikedDishes).mockResolvedValue({
      customerId: 7,
      period: 'ALL',
      ratingCount: 10,
      dishes,
    });
    renderWithProviders(<CustomerLikedFoods customerId={7} />);

    expect(await screen.findByText('Spaghetti (20%)')).toBeInTheDocument();
    expect(screen.getByText('Burger (60%)')).toBeInTheDocument();
    expect(screen.getByText('Pizza (10%)')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Spaghetti 5\.0 out of 5/ })).toBeInTheDocument();
    expect(reviewService.getCustomerLikedDishes).toHaveBeenCalledWith(7, 'ALL', 7);
  });

  it('keeps the legend to the top four dishes', async () => {
    vi.mocked(reviewService.getCustomerLikedDishes).mockResolvedValue({
      customerId: 7,
      period: 'ALL',
      ratingCount: 5,
      dishes: ['A', 'B', 'C', 'D', 'E'].map((name, index) =>
        likedDish({ menuItemId: index + 1, dishName: 'Dish ' + name, ratingCount: 1 }),
      ),
    });
    renderWithProviders(<CustomerLikedFoods customerId={7} />);

    expect(await screen.findByText('Dish D (20%)')).toBeInTheDocument();
    expect(screen.queryByText('Dish E (20%)')).not.toBeInTheDocument();
  });

  it('offers All time, Monthly and Weekly, and asks again when switched', async () => {
    vi.mocked(reviewService.getCustomerLikedDishes).mockResolvedValue({
      customerId: 7,
      period: 'ALL',
      ratingCount: 10,
      dishes,
    });
    renderWithProviders(<CustomerLikedFoods customerId={7} />);
    await screen.findByText('Burger (60%)');

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      'All time',
      'Monthly',
      'Weekly',
    ]);
    await userEvent.click(screen.getByRole('tab', { name: 'Monthly' }));
    expect(reviewService.getCustomerLikedDishes).toHaveBeenLastCalledWith(7, 'MONTHLY', 7);
    await userEvent.click(screen.getByRole('tab', { name: 'Weekly' }));
    expect(reviewService.getCustomerLikedDishes).toHaveBeenLastCalledWith(7, 'WEEKLY', 7);
  });

  it('says so when the customer has not rated anything', async () => {
    vi.mocked(reviewService.getCustomerLikedDishes).mockResolvedValue({
      customerId: 7,
      period: 'ALL',
      ratingCount: 0,
      dishes: [],
    });
    renderWithProviders(<CustomerLikedFoods customerId={7} />);

    expect(await screen.findByText('No ratings from this customer yet')).toBeInTheDocument();
  });

  it('shows the error with a retry', async () => {
    vi.mocked(reviewService.getCustomerLikedDishes).mockRejectedValue(new Error('boom'));
    renderWithProviders(<CustomerLikedFoods customerId={7} />);

    expect(await screen.findByText('Unable to load the most liked food')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});
