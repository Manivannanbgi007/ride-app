import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import OwnerReviewsPage from '@/pages/Owner/OwnerReviewsPage';
import { ownerService } from '@/services/ownerService';
import { ratingService } from '@/services/ratingService';
import { menuItem, pageOf } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';
import type { PublicReview } from '@/types/rating';

vi.mock('@/services/ownerService', () => ({
  ownerService: { getMenuItems: vi.fn() },
}));
vi.mock('@/services/ratingService', () => ({
  ratingService: {
    getRestaurantRating: vi.fn(),
    getRestaurantReviews: vi.fn(),
    getDishRatings: vi.fn(),
    getDishRating: vi.fn(),
    getDishReviews: vi.fn(),
  },
}));

const review = (overrides: Partial<PublicReview> = {}): PublicReview => ({
  id: 1,
  reviewerName: 'Priya S.',
  rating: 5,
  comment: 'Hot and tasty',
  tags: [],
  dishes: [],
  createdAt: '2026-10-30T10:00:00Z',
  updatedAt: '2026-10-30T10:00:00Z',
  ...overrides,
});

const renderReviews = () => renderOwnerPage(<OwnerReviewsPage />, '/owner/reviews');

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getMenuItems).mockResolvedValue([
    menuItem({ id: 11, name: 'Chicken Biryani' }),
    menuItem({ id: 12, name: 'Masala Dosa' }),
  ]);
  vi.mocked(ratingService.getRestaurantRating).mockResolvedValue({
    id: 3,
    average: 4.1,
    count: 10,
    distribution: { '1': 1, '2': 1, '3': 1, '4': 2, '5': 5 },
  });
  vi.mocked(ratingService.getRestaurantReviews).mockResolvedValue(
    pageOf([review(), review({ id: 2, reviewerName: 'Arjun K.', rating: 2, comment: 'Cold' })]),
  );
  vi.mocked(ratingService.getDishRatings).mockResolvedValue([{ id: 12, average: 3.5, count: 4 }]);
  vi.mocked(ratingService.getDishRating).mockResolvedValue({
    id: 12,
    average: 3.5,
    count: 4,
    distribution: { '1': 0, '2': 1, '3': 1, '4': 1, '5': 1 },
  });
  vi.mocked(ratingService.getDishReviews).mockResolvedValue(pageOf([review({ id: 3 })]));
});

describe('OwnerReviewsPage', () => {
  it('sums the rating up at a glance: average, reviews, five-star share and unhappy ones', async () => {
    renderReviews();

    const glance = within(await screen.findByRole('list', { name: 'Reviews at a glance' }));
    expect(glance.getByText('Average rating').previousElementSibling).toHaveTextContent('4.1 / 5');
    expect(glance.getByText('Reviews').previousElementSibling).toHaveTextContent('10');
    expect(glance.getByText('Gave five stars').previousElementSibling).toHaveTextContent('50%');
    expect(glance.getByText('One or two stars').previousElementSibling).toHaveTextContent('2');
  });

  it('lists the reviews, flags the unhappy ones, and sorts them', async () => {
    renderReviews();

    const reviews = await screen.findByRole('list', { name: 'Reviews' });
    const [happy, unhappy] = within(reviews).getAllByRole('listitem');
    expect(within(happy).queryByText('Unhappy customer')).not.toBeInTheDocument();
    expect(within(unhappy).getByText('Unhappy customer')).toBeInTheDocument();
    expect(ratingService.getRestaurantReviews).toHaveBeenLastCalledWith(3, 'newest', 0, 6);

    await userEvent.click(screen.getByRole('tab', { name: 'Lowest' }));
    expect(ratingService.getRestaurantReviews).toHaveBeenLastCalledWith(3, 'lowest', 0, 6);
  });

  it('opens the reviews of a rated dish; a dish without ratings has none to open', async () => {
    renderReviews();

    await userEvent.click(
      await screen.findByRole('button', { name: 'Read the reviews of Masala Dosa' }),
    );
    expect(
      await screen.findByRole('dialog', { name: 'Reviews of Masala Dosa' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Read the reviews of Chicken Biryani' }),
    ).not.toBeInTheDocument();
  });

  it('says so while nobody has rated the restaurant, without asking for reviews', async () => {
    vi.mocked(ratingService.getRestaurantRating).mockResolvedValue({
      id: 3,
      average: null,
      count: 0,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    });
    renderReviews();

    expect(await screen.findByText('No reviews yet')).toBeInTheDocument();
    expect(
      screen.getByText('Your first rating appears here once a customer reviews a delivered order.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Reviews at a glance' })).not.toBeInTheDocument();
    expect(ratingService.getRestaurantReviews).not.toHaveBeenCalled();
  });
});
