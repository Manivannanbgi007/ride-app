import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import ReviewsPage from '@/pages/Review/ReviewsPage';
import { restaurantService } from '@/services/restaurantService';
import { reviewService } from '@/services/reviewService';
import { chooseOption } from '@/test/chooseOption';
import { featuredDish, pageOf, review, reviewSummary } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import { periodOf } from '@/utils/dateUtils';

vi.mock('@/services/reviewService', () => ({
  reviewService: {
    searchReviews: vi.fn(),
    getSummary: vi.fn(),
    getFeaturedDishes: vi.fn(),
    createReview: vi.fn(),
    getOrderReview: vi.fn(),
  },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { searchRestaurants: vi.fn() },
}));
// The rated dishes' pictures come from the restaurants' menus
vi.mock('@/services/catalogService', () => ({
  catalogService: {
    getRestaurantMenu: vi.fn().mockResolvedValue({
      restaurantId: 3,
      restaurantName: 'Biryani House',
      sections: [
        {
          categoryId: 1,
          categoryName: 'Biryani',
          items: [
            {
              id: 11,
              name: 'Chicken Biryani',
              description: null,
              price: 299,
              foodType: 'NON_VEG',
              imageUrl: 'https://img/biryani.jpg',
              available: true,
            },
          ],
        },
      ],
    }),
  },
}));

const serverError = () =>
  new ApiError({
    kind: 'server',
    status: 500,
    userMessage: 'Something went wrong on the server.',
    technicalMessage: 'boom',
  });

const dishes = [
  featuredDish({ menuItemId: 11, dishName: 'Chicken Biryani' }),
  featuredDish({ menuItemId: 12, dishName: 'Paneer Tikka', imageUrl: 'https://img/paneer.jpg' }),
  featuredDish({ menuItemId: 13, dishName: 'Masala Dosa', averageRating: 3.8 }),
];

const dishTitle = (name: string) => screen.findByText(name, { selector: 'h3' });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(restaurantService.searchRestaurants).mockResolvedValue(pageOf([]));
  vi.mocked(reviewService.getSummary).mockResolvedValue(reviewSummary());
  vi.mocked(reviewService.getFeaturedDishes).mockResolvedValue(pageOf(dishes, 3, 0, 3));
  vi.mocked(reviewService.searchReviews).mockResolvedValue(
    pageOf([
      review({ id: 1, customerName: 'James Kowalski' }),
      review({ id: 2, customerName: 'Sofia Puvlova', comment: null, tags: [], dishRatings: [] }),
    ]),
  );
});

describe('ReviewsPage', () => {
  it('shows the featured dishes and the reviews', async () => {
    renderWithProviders(<ReviewsPage />);

    expect(await dishTitle('Chicken Biryani')).toBeInTheDocument();
    expect(await dishTitle('Paneer Tikka')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Paneer Tikka' })).toHaveAttribute(
      'src',
      'https://img/paneer.jpg',
    );
    expect(screen.getByRole('img', { name: 'No photo of Chicken Biryani' })).toBeInTheDocument();
    expect(
      screen.getAllByRole('img', { name: /Average rating 4\.5 out of 5, 12 ratings/ }),
    ).toHaveLength(2);

    expect(await screen.findByText('James Kowalski')).toBeInTheDocument();
    expect(screen.getByText('Sofia Puvlova')).toBeInTheDocument();
    expect(screen.getAllByText(/Biryani House · 24 June 2026/)).toHaveLength(2);
    expect(screen.getByText('Great food and fast delivery.')).toBeInTheDocument();
    expect(screen.getByText('Delicious')).toBeInTheDocument();
    expect(screen.getByText('Good service')).toBeInTheDocument();
    const dishChip = screen.getByText('Chicken Biryani', { selector: 'li' });
    // With the dish's picture from its restaurant's menu
    expect(await within(dishChip).findByRole('presentation')).toHaveAttribute(
      'src',
      'https://img/biryani.jpg',
    );
    expect(screen.getAllByRole('img', { name: '4 out of 5' }).length).toBeGreaterThan(0);
    // The summary
    expect(await screen.findByText('10 reviews')).toBeInTheDocument();
    expect(screen.getByText('4.2')).toBeInTheDocument();
  });

  it('asks for the last 30 days at first, and for the chosen period afterwards', async () => {
    renderWithProviders(<ReviewsPage />);
    await screen.findByText('James Kowalski');

    const last30 = periodOf(30);
    expect(reviewService.searchReviews).toHaveBeenLastCalledWith(
      expect.objectContaining({ from: last30.fromDate, to: last30.toDate, sort: 'createdAt,desc' }),
      0,
      10,
    );
    expect(reviewService.getFeaturedDishes).toHaveBeenLastCalledWith(
      { from: last30.fromDate, to: last30.toDate },
      0,
      3,
    );

    await chooseOption(screen.getByRole('combobox', { name: 'Filter Period' }), 'Last 7 days');

    const last7 = periodOf(7);
    await vi.waitFor(() =>
      expect(reviewService.getSummary).toHaveBeenLastCalledWith({
        from: last7.fromDate,
        to: last7.toDate,
      }),
    );
    expect(reviewService.getFeaturedDishes).toHaveBeenLastCalledWith(
      { from: last7.fromDate, to: last7.toDate },
      0,
      3,
    );

    await chooseOption(screen.getByRole('combobox', { name: 'Filter Period' }), 'All time');
    await vi.waitFor(() => expect(reviewService.getSummary).toHaveBeenLastCalledWith({}));
  });

  it('asks again with the chosen sort order', async () => {
    renderWithProviders(<ReviewsPage />);
    await screen.findByText('James Kowalski');

    await chooseOption(screen.getByRole('combobox', { name: 'Sort reviews' }), 'Highest rating');

    await vi.waitFor(() =>
      expect(reviewService.searchReviews).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: 'rating,desc' }),
        0,
        10,
      ),
    );
  });

  it('filters by tag and rating', async () => {
    renderWithProviders(<ReviewsPage />);
    await screen.findByText('James Kowalski');

    await userEvent.click(screen.getByRole('button', { name: /Filters/ }));
    await chooseOption(screen.getByRole('combobox', { name: 'Tag' }), 'Fast delivery');
    await chooseOption(screen.getByRole('combobox', { name: 'Rating' }), '5 stars');

    await vi.waitFor(() =>
      expect(reviewService.searchReviews).toHaveBeenLastCalledWith(
        expect.objectContaining({ tag: 'FAST_DELIVERY', rating: 5 }),
        0,
        10,
      ),
    );
  });

  it('adds the next page with Load More, which goes away on the last page', async () => {
    vi.mocked(reviewService.searchReviews)
      .mockResolvedValueOnce(pageOf([review({ id: 1, customerName: 'First Customer' })], 2, 0, 1))
      .mockResolvedValueOnce(pageOf([review({ id: 2, customerName: 'Second Customer' })], 2, 1, 1));
    renderWithProviders(<ReviewsPage />);

    await screen.findByText('First Customer');
    await userEvent.click(screen.getByRole('button', { name: /Load More/ }));

    expect(await screen.findByText('Second Customer')).toBeInTheDocument();
    expect(screen.getByText('First Customer')).toBeInTheDocument();
    expect(reviewService.searchReviews).toHaveBeenLastCalledWith(expect.anything(), 1, 10);
    expect(screen.queryByRole('button', { name: /Load More/ })).not.toBeInTheDocument();
  });

  it('says so when there are no reviews and no rated dishes', async () => {
    vi.mocked(reviewService.searchReviews).mockResolvedValue(pageOf([]));
    vi.mocked(reviewService.getFeaturedDishes).mockResolvedValue(pageOf([], 0, 0, 3));
    vi.mocked(reviewService.getSummary).mockResolvedValue(
      reviewSummary({ count: 0, averageRating: null }),
    );
    renderWithProviders(<ReviewsPage />);

    expect(await screen.findByText('No reviews yet')).toBeInTheDocument();
    expect(await screen.findByText('No rated dishes in this period')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Load More/ })).not.toBeInTheDocument();
  });

  it('explains a failure and tries again', async () => {
    vi.mocked(reviewService.searchReviews).mockRejectedValueOnce(serverError());
    vi.mocked(reviewService.getFeaturedDishes).mockRejectedValueOnce(serverError());
    renderWithProviders(<ReviewsPage />);

    expect(await screen.findByText('Unable to load the reviews')).toBeInTheDocument();
    expect(await screen.findByText('Unable to load the featured dishes')).toBeInTheDocument();

    const [dishesRetry, reviewsRetry] = screen.getAllByRole('button', { name: /Try again/ });
    await userEvent.click(reviewsRetry);
    expect(await screen.findByText('James Kowalski')).toBeInTheDocument();
    await userEvent.click(dishesRetry);
    expect(await dishTitle('Masala Dosa')).toBeInTheDocument();
  });

  it('pages through the featured dishes with the arrows', async () => {
    vi.mocked(reviewService.getFeaturedDishes)
      .mockResolvedValueOnce(pageOf(dishes, 4, 0, 3))
      .mockResolvedValueOnce(
        pageOf([featuredDish({ menuItemId: 14, dishName: 'Fish Curry' })], 4, 1, 3),
      );
    renderWithProviders(<ReviewsPage />);

    await dishTitle('Masala Dosa');
    expect(screen.getByRole('button', { name: 'Previous dishes' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Next dishes' }));

    expect(await dishTitle('Fish Curry')).toBeInTheDocument();
    expect(reviewService.getFeaturedDishes).toHaveBeenLastCalledWith(
      { from: periodOf(30).fromDate, to: periodOf(30).toDate },
      1,
      3,
    );
    expect(screen.queryByText('Masala Dosa', { selector: 'h3' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next dishes' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Previous dishes' }));
    expect(await dishTitle('Masala Dosa')).toBeInTheDocument();
    const region = screen.getByRole('region', { name: 'Featured dishes' });
    expect(within(region).getAllByRole('article')).toHaveLength(3);
  });
});
