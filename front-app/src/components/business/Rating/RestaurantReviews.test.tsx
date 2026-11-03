import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { RestaurantReviews } from '@/components/business/Rating';
import { ratingService } from '@/services/ratingService';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { PublicReview, RatingDetails } from '@/types/rating';

vi.mock('@/services/ratingService', () => ({
  ratingService: { getRestaurantRating: vi.fn(), getRestaurantReviews: vi.fn() },
}));

const details: RatingDetails = {
  id: 3,
  average: 4.3,
  count: 3,
  distribution: { '1': 0, '2': 0, '3': 1, '4': 0, '5': 2 },
};

function publicReview(overrides: Partial<PublicReview> = {}): PublicReview {
  return {
    id: 1,
    reviewerName: 'Priya S.',
    rating: 5,
    comment: 'Lovely biryani',
    tags: ['DELICIOUS'],
    dishes: [{ menuItemId: 11, dishName: 'Chicken Biryani', rating: 5 }],
    createdAt: '2026-06-24T10:15:00Z',
    updatedAt: '2026-06-24T10:15:00Z',
    ...overrides,
  };
}

const page = (content: PublicReview[]) => ({
  content,
  page: 0,
  size: 5,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});

const renderReviews = () =>
  renderWithProviders(<RestaurantReviews restaurantId={3} restaurantName="Biryani House" />);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RestaurantReviews', () => {
  it('shows the average, how the stars are spread and what customers wrote', async () => {
    vi.mocked(ratingService.getRestaurantRating).mockResolvedValue(details);
    vi.mocked(ratingService.getRestaurantReviews).mockResolvedValue(
      page([
        publicReview(),
        publicReview({ id: 2, reviewerName: 'Arjun M.', comment: null, tags: [] }),
      ]),
    );
    renderReviews();

    expect(await screen.findByText('4.3')).toBeInTheDocument();
    expect(screen.getByText('3 reviews')).toBeInTheDocument();
    const bars = screen.getByRole('list', { name: 'Reviews by stars' });
    expect(within(bars).getByRole('img', { name: '2 of 3 gave 5 stars' })).toBeInTheDocument();
    expect(within(bars).getByRole('img', { name: '0 of 3 gave 1 stars' })).toBeInTheDocument();
    expect(await screen.findByText('Lovely biryani')).toBeInTheDocument();
    // Only the shortened name is shown
    expect(screen.getByText('Priya S.')).toBeInTheDocument();
    expect(screen.getByText('Arjun M.')).toBeInTheDocument();
    expect(screen.getByText('Delicious')).toBeInTheDocument();
    expect(ratingService.getRestaurantReviews).toHaveBeenCalledWith(3, 'newest', 0, 5);
  });

  it('asks for the reviews in the order chosen', async () => {
    vi.mocked(ratingService.getRestaurantRating).mockResolvedValue(details);
    vi.mocked(ratingService.getRestaurantReviews).mockResolvedValue(page([publicReview()]));
    renderReviews();
    await screen.findByText('Lovely biryani');

    await userEvent.click(screen.getByRole('combobox', { name: 'Sort reviews' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Highest rated' }));

    expect(ratingService.getRestaurantReviews).toHaveBeenLastCalledWith(3, 'highest', 0, 5);
  });

  it('says "No ratings yet" and how to write the first review when there are none', async () => {
    vi.mocked(ratingService.getRestaurantRating).mockResolvedValue({
      id: 3,
      average: null,
      count: 0,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    });
    renderReviews();

    expect(await screen.findByText('No ratings yet')).toBeInTheDocument();
    expect(screen.getByText(/Order from Biryani House and rate your order/)).toBeInTheDocument();
    // No list is asked for when there is nothing to list
    expect(ratingService.getRestaurantReviews).not.toHaveBeenCalled();
  });

  it('shows the problem, and loads again on "Try again"', async () => {
    vi.mocked(ratingService.getRestaurantRating)
      .mockRejectedValueOnce(
        new ApiError({
          kind: 'server',
          status: 500,
          userMessage: 'Something went wrong on the server.',
          technicalMessage: 'boom',
        }),
      )
      .mockResolvedValue(details);
    vi.mocked(ratingService.getRestaurantReviews).mockResolvedValue(page([publicReview()]));
    renderReviews();

    expect(await screen.findByText('Unable to load the ratings')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));

    expect(await screen.findByText('4.3')).toBeInTheDocument();
  });
});
