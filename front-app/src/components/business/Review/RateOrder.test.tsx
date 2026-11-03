import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { RateOrder } from '@/components/business/Review';
import { reviewService } from '@/services/reviewService';
import { makeStore } from '@/store/store';
import { orderDetails, priya, review } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/reviewService', () => ({
  reviewService: {
    createReview: vi.fn(),
    getOrderReview: vi.fn(),
    updateReview: vi.fn(),
    deleteReview: vi.fn(),
  },
}));

const notFound = () =>
  new ApiError({
    kind: 'notFound',
    status: 404,
    userMessage: 'No review yet.',
    technicalMessage: 'not found',
  });

const delivered = orderDetails({
  status: 'DELIVERED',
  items: [
    {
      menuItemId: 11,
      name: 'Chicken Biryani',
      foodType: 'NON_VEG',
      unitPrice: 299,
      quantity: 2,
      lineTotal: 598,
    },
    {
      menuItemId: 12,
      name: 'Raita',
      foodType: 'VEG',
      unitPrice: 49,
      quantity: 1,
      lineTotal: 49,
    },
  ],
});

const renderRate = (order = delivered) =>
  renderWithProviders(<RateOrder order={order} />, {
    store: makeStore({ session: { adminUser: null, customer: priya } }),
  });

const overallOf = (dialog: HTMLElement) =>
  within(dialog).getByRole('radiogroup', { name: 'Overall rating' });

const openDialog = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Rate this order' }));
  return screen.getByRole('dialog');
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(reviewService.getOrderReview).mockRejectedValue(notFound());
});

describe('RateOrder', () => {
  it('is not shown before the order is delivered', () => {
    renderRate(orderDetails({ status: 'PLACED' }));

    expect(screen.queryByText('Rate this order')).not.toBeInTheDocument();
    expect(reviewService.getOrderReview).not.toHaveBeenCalled();
  });

  it('sends the rating, the tags, the comment and the dish ratings', async () => {
    vi.mocked(reviewService.createReview).mockResolvedValue(
      review({ orderId: 42, rating: 4, comment: 'Tasty', tags: ['DELICIOUS'], dishRatings: [] }),
    );
    renderRate();
    const dialog = await openDialog();

    await userEvent.click(within(overallOf(dialog)).getByRole('radio', { name: '4 stars' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delicious' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Fast delivery' }));
    await userEvent.type(within(dialog).getByLabelText('Comment (optional)'), ' Tasty ');
    const biryani = within(dialog).getByRole('radiogroup', { name: 'Rate Chicken Biryani' });
    await userEvent.click(within(biryani).getByRole('radio', { name: '5 stars' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit review' }));

    expect(reviewService.createReview).toHaveBeenCalledWith({
      orderId: 42,
      rating: 4,
      comment: 'Tasty',
      tags: ['DELICIOUS', 'FAST_DELIVERY'],
      dishRatings: [{ menuItemId: 11, rating: 5 }],
    });
    // The dialog closes, the customer is thanked and sees the review as written
    expect(await screen.findByText('Thank you for your review!')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Your review')).toBeInTheDocument();
    expect(screen.getByText('Tasty')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '4 out of 5' })).toBeInTheDocument();
  });

  it('asks for the overall rating before sending', async () => {
    renderRate();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit review' }));

    expect(
      await within(dialog).findByText('Please choose a rating from 1 to 5 stars.'),
    ).toBeInTheDocument();
    expect(reviewService.createReview).not.toHaveBeenCalled();
  });

  it('accepts no more than four tags', async () => {
    renderRate();
    const dialog = await openDialog();

    for (const tag of ['Delicious', 'Excellent', 'Good service', 'Fast delivery']) {
      await userEvent.click(within(dialog).getByRole('button', { name: tag }));
    }

    expect(within(dialog).getByRole('button', { name: 'Good value' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Good packaging' })).toBeDisabled();
    // A chosen tag can be taken back, which frees a place
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excellent' }));
    expect(within(dialog).getByRole('button', { name: 'Good value' })).toBeEnabled();
  });

  it('can be filled in with the keyboard and shows the comment counter', async () => {
    renderRate();
    const dialog = await openDialog();

    const overall = within(dialog).getByRole('radiogroup', { name: 'Overall rating' });
    within(overall).getAllByRole('radio')[0].focus();
    await userEvent.keyboard('{ArrowRight}{ArrowRight}');

    expect(within(overall).getByRole('radio', { name: '3 stars' })).toBeChecked();
    await userEvent.type(within(dialog).getByLabelText('Comment (optional)'), 'abc');
    expect(within(dialog).getByText('3/1000')).toBeInTheDocument();
  });

  it('shows the server refusal when the order is already reviewed, and stays open', async () => {
    vi.mocked(reviewService.createReview).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'You have already reviewed this order.',
        technicalMessage: 'conflict',
      }),
    );
    renderRate();
    const dialog = await openDialog();

    await userEvent.click(within(overallOf(dialog)).getByRole('radio', { name: '5 stars' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit review' }));

    expect(
      await within(dialog).findByText('You have already reviewed this order.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows an existing review without a button to rate again', async () => {
    vi.mocked(reviewService.getOrderReview).mockResolvedValue(
      review({
        rating: 5,
        comment: 'Loved it',
        tags: ['EXCELLENT'],
        dishRatings: [{ menuItemId: 11, dishName: 'Chicken Biryani', rating: 4 }],
      }),
    );
    renderRate();

    expect(await screen.findByText('Loved it')).toBeInTheDocument();
    expect(screen.getByText('Excellent')).toBeInTheDocument();
    expect(screen.getByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rate this order' })).not.toBeInTheDocument();
  });

  it('changes the own review: the dialog starts with what was written and saves the changes', async () => {
    vi.mocked(reviewService.getOrderReview).mockResolvedValue(
      review({
        id: 9,
        rating: 3,
        comment: 'Okay',
        tags: ['DELICIOUS'],
        dishRatings: [{ menuItemId: 11, dishName: 'Chicken Biryani', rating: 4 }],
      }),
    );
    vi.mocked(reviewService.updateReview).mockResolvedValue(
      review({ id: 9, rating: 5, comment: 'Okay', tags: ['DELICIOUS'], dishRatings: [] }),
    );
    renderRate();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit review' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit your review of order #42' });
    expect(within(overallOf(dialog)).getByRole('radio', { name: '3 stars' })).toBeChecked();
    expect(within(dialog).getByLabelText('Comment (optional)')).toHaveValue('Okay');
    const biryani = within(dialog).getByRole('radiogroup', { name: 'Rate Chicken Biryani' });
    expect(within(biryani).getByRole('radio', { name: '4 stars' })).toBeChecked();

    await userEvent.click(within(overallOf(dialog)).getByRole('radio', { name: '5 stars' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    expect(reviewService.updateReview).toHaveBeenCalledWith(9, {
      rating: 5,
      comment: 'Okay',
      tags: ['DELICIOUS'],
      dishRatings: [{ menuItemId: 11, rating: 4 }],
    });
    expect(reviewService.createReview).not.toHaveBeenCalled();
    expect(await screen.findByText('Your review was updated.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('deletes the own review after asking, and offers to rate the order again', async () => {
    vi.mocked(reviewService.getOrderReview).mockResolvedValue(review({ id: 9 }));
    vi.mocked(reviewService.deleteReview).mockResolvedValue(undefined);
    renderRate();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete review' }));
    // Nothing is deleted before the customer confirms
    expect(reviewService.deleteReview).not.toHaveBeenCalled();
    const confirm = screen.getByRole('dialog', { name: 'Delete your review?' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete review' }));

    expect(reviewService.deleteReview).toHaveBeenCalledWith(9);
    expect(await screen.findByText('Your review was deleted.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Rate this order' })).toBeInTheDocument();
  });

  it('keeps the review and says why when it cannot be deleted', async () => {
    vi.mocked(reviewService.getOrderReview).mockResolvedValue(review({ id: 9 }));
    vi.mocked(reviewService.deleteReview).mockRejectedValue(
      new ApiError({
        kind: 'server',
        status: 500,
        userMessage: 'Something went wrong on the server.',
        technicalMessage: 'boom',
      }),
    );
    renderRate();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete review' }));
    const confirm = screen.getByRole('dialog', { name: 'Delete your review?' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete review' }));

    expect(await screen.findByText('Something went wrong on the server.')).toBeInTheDocument();
    expect(screen.getByText('Great food and fast delivery.')).toBeInTheDocument();
  });

  it('explains when the review cannot be looked up', async () => {
    vi.mocked(reviewService.getOrderReview).mockRejectedValue(
      new ApiError({
        kind: 'server',
        status: 500,
        userMessage: 'Something went wrong on the server.',
        technicalMessage: 'boom',
      }),
    );
    renderRate();

    expect(await screen.findByText('Unable to load your review')).toBeInTheDocument();
  });
});
