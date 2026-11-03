import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DishReviewsDialog } from '@/components/business/Rating/DishReviewsDialog';
import { ratingService } from '@/services/ratingService';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { PublicReview } from '@/types/rating';

const DOSA = { id: 3, name: 'Masala Dosa' };

const review = (overrides: Partial<PublicReview> = {}): PublicReview => ({
  id: 1,
  reviewerName: 'Ana R.',
  rating: 4,
  comment: 'Crispy dosa and the sambar was still hot.',
  tags: ['DELICIOUS'],
  dishes: [
    { menuItemId: 3, dishName: 'Masala Dosa', rating: 4 },
    { menuItemId: 2, dishName: 'Paneer Butter Masala', rating: 5 },
  ],
  createdAt: '2026-10-18T09:00:00Z',
  updatedAt: '2026-10-18T09:00:00Z',
  ...overrides,
});

const pageOf = (content: PublicReview[], page = 0, totalElements = content.length) => ({
  content,
  page,
  size: 5,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / 5)),
  first: page === 0,
  last: (page + 1) * 5 >= totalElements,
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(ratingService.getDishRating).mockResolvedValue({
    id: 3,
    average: 3.5,
    count: 2,
    distribution: { '1': 0, '2': 0, '3': 1, '4': 1, '5': 0 },
  });
  vi.mocked(ratingService.getDishReviews).mockResolvedValue(
    pageOf([
      review(),
      review({
        id: 2,
        reviewerName: 'Venkataramanan I.',
        rating: 3,
        comment: null,
        tags: [],
        dishes: [{ menuItemId: 3, dishName: 'Masala Dosa', rating: 3 }],
      }),
    ]),
  );
});

describe('DishReviewsDialog', () => {
  it("shows the dish's average and what customers wrote, with the stars this dish got", async () => {
    renderWithProviders(<DishReviewsDialog dish={DOSA} onClose={() => undefined} />);

    const dialog = within(screen.getByRole('dialog', { name: 'Reviews of Masala Dosa' }));
    expect(await dialog.findByText('3.5')).toBeInTheDocument();
    expect(ratingService.getDishRating).toHaveBeenCalledWith(3);
    expect(await dialog.findByRole('list', { name: 'Reviews of this dish' })).toBeInTheDocument();
    expect(ratingService.getDishReviews).toHaveBeenCalledWith(3, 0, 5);

    const ana = within(dialog.getByRole('article', { name: 'Review by Ana R.' }));
    expect(ana.getByText('Crispy dosa and the sambar was still hot.')).toBeInTheDocument();
    // The dish's own stars (4), not the other dish's (5); and the whole order's
    expect(ana.getByText('This dish').nextElementSibling).toHaveAccessibleName('4 out of 5');
    expect(ana.getByText('The whole order:')).toBeInTheDocument();
    expect(ana.getByRole('list', { name: 'Tags' })).toBeInTheDocument();

    const second = within(dialog.getByRole('article', { name: 'Review by Venkataramanan I.' }));
    expect(second.getByText('No comment written')).toBeInTheDocument();
  });

  it('says when nobody has rated the dish yet, without asking for reviews', async () => {
    vi.mocked(ratingService.getDishRating).mockResolvedValue({
      id: 3,
      average: null,
      count: 0,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    });
    renderWithProviders(
      <DishReviewsDialog dish={DOSA} onClose={() => undefined} emptyMessage="Nothing yet." />,
    );

    expect(await screen.findByText('No reviews for this dish yet')).toBeInTheDocument();
    expect(screen.getByText('Nothing yet.')).toBeInTheDocument();
    expect(ratingService.getDishReviews).not.toHaveBeenCalled();
  });

  it('pages through the reviews', async () => {
    vi.mocked(ratingService.getDishRating).mockResolvedValue({
      id: 3,
      average: 4,
      count: 7,
      distribution: { '1': 0, '2': 0, '3': 1, '4': 5, '5': 1 },
    });
    vi.mocked(ratingService.getDishReviews).mockImplementation((_id, page) =>
      Promise.resolve(
        page === 0
          ? pageOf(
              [1, 2, 3, 4, 5].map((id) => review({ id, reviewerName: `Guest ${id}.` })),
              0,
              7,
            )
          : pageOf(
              [6, 7].map((id) => review({ id, reviewerName: `Guest ${id}.` })),
              1,
              7,
            ),
      ),
    );
    renderWithProviders(<DishReviewsDialog dish={DOSA} onClose={() => undefined} />);

    expect(await screen.findByRole('article', { name: 'Review by Guest 5.' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(await screen.findByRole('article', { name: 'Review by Guest 7.' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('article', { name: 'Review by Guest 1.' })).not.toBeInTheDocument(),
    );
    expect(ratingService.getDishReviews).toHaveBeenLastCalledWith(3, 1, 5);
  });

  it('is closed while no dish is chosen, and closes on request', async () => {
    const onClose = vi.fn();
    const { rerender } = renderWithProviders(<DishReviewsDialog dish={null} onClose={onClose} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    rerender(<DishReviewsDialog dish={DOSA} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalled();
  });
});
