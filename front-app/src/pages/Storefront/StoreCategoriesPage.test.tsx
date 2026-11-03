import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import StoreCategoriesPage from '@/pages/Storefront/StoreCategoriesPage';
import { catalogService } from '@/services/catalogService';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { StoreCategory } from '@/types/storefront';

vi.mock('@/services/catalogService', () => ({
  catalogService: { getStoreCategories: vi.fn() },
}));

const categories: StoreCategory[] = [
  {
    id: 3,
    name: 'Biryani',
    description: 'Biryani and pulao',
    imageUrl: '/images/categories/biryani.jpg',
  },
  { id: 7, name: 'Desserts', description: null, imageUrl: null },
];

describe('StoreCategoriesPage', () => {
  beforeEach(() => {
    vi.mocked(catalogService.getStoreCategories).mockReset();
  });

  it('lists every category with its name, and opens the dishes of the one chosen', async () => {
    vi.mocked(catalogService.getStoreCategories).mockResolvedValue(categories);
    renderWithProviders(<StoreCategoriesPage />, { route: '/customer/categories' });

    expect(await screen.findByRole('heading', { name: 'Food categories', level: 1 })).toBeVisible();
    expect(await screen.findByText('2 categories to choose from')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Biryani/ })).toHaveAttribute(
      'href',
      '/customer/search?tab=dishes&categoryId=3',
    );
    expect(screen.getByRole('link', { name: /Desserts/ })).toHaveAttribute(
      'href',
      '/customer/search?tab=dishes&categoryId=7',
    );
    expect(screen.getByText('Biryani and pulao')).toBeInTheDocument();
  });

  it('falls back to the category picture that ships with the app, then to an icon', async () => {
    vi.mocked(catalogService.getStoreCategories).mockResolvedValue(categories);
    const { container } = renderWithProviders(<StoreCategoriesPage />);

    await screen.findByRole('link', { name: /Desserts/ });
    const pictures = [...container.querySelectorAll('img')].map((image) =>
      image.getAttribute('src'),
    );
    // Desserts has no picture of its own: the shipped one is used
    expect(pictures).toEqual(['/images/categories/biryani.jpg', '/images/categories/desserts.jpg']);
  });

  it('says so when there are no categories', async () => {
    vi.mocked(catalogService.getStoreCategories).mockResolvedValue([]);
    renderWithProviders(<StoreCategoriesPage />);

    expect(await screen.findByText('There are no food categories yet')).toBeInTheDocument();
  });

  it('shows the problem, and loads again on "Try again"', async () => {
    vi.mocked(catalogService.getStoreCategories)
      .mockRejectedValueOnce(
        new ApiError({
          kind: 'server',
          status: 500,
          userMessage: 'Down',
          technicalMessage: 'Down',
        }),
      )
      .mockResolvedValue(categories);
    renderWithProviders(<StoreCategoriesPage />);

    expect(await screen.findByText('Unable to load the categories')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));

    expect(await screen.findByRole('link', { name: /Biryani/ })).toBeInTheDocument();
  });
});
