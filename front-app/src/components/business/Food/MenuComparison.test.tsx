import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { MenuComparison } from '@/components/business/Food/MenuComparison';
import { catalogService } from '@/services/catalogService';
import { categoryStat } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/catalogService', () => ({
  catalogService: { getCategoryStats: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(catalogService.getCategoryStats).mockResolvedValue([
    categoryStat({ categoryId: 1, categoryName: 'Burger', orderableItems: 3, totalItems: 4 }),
    categoryStat({
      categoryId: 2,
      categoryName: 'Pizza',
      orderableItems: 8,
      totalItems: 25,
      orderablePercent: 32,
    }),
  ]);
});

describe('MenuComparison', () => {
  it('shows a ring with the percentage per category, and what it means on hover', async () => {
    renderWithProviders(<MenuComparison />);

    expect(await screen.findByText('75%')).toBeInTheDocument();
    expect(screen.getByText('32%')).toBeInTheDocument();
    expect(screen.getByText('Burger')).toBeInTheDocument();
    expect(catalogService.getCategoryStats).toHaveBeenCalledWith(3);

    await userEvent.hover(screen.getByText('Pizza'));
    expect(await screen.findByText('8 of 25 dishes can be ordered now')).toBeInTheDocument();
  });

  it('hides the values, and swaps the rings for bars when Chart is off', async () => {
    renderWithProviders(<MenuComparison />);
    await screen.findByText('75%');

    await userEvent.click(screen.getByRole('checkbox', { name: 'Show Value' }));
    expect(screen.queryByText('75%')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show Value' }));

    await userEvent.click(screen.getByRole('checkbox', { name: 'Chart' }));
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: /Burger/ })).toHaveAttribute(
      'aria-valuenow',
      '75',
    );
    expect(screen.getByText('75%')).toBeInTheDocument();
  });

  it('says so when there are no categories or they cannot be loaded', async () => {
    vi.mocked(catalogService.getCategoryStats).mockResolvedValueOnce([]);
    const { unmount } = renderWithProviders(<MenuComparison />);
    expect(await screen.findByText('No categories yet')).toBeInTheDocument();
    unmount();

    vi.mocked(catalogService.getCategoryStats).mockRejectedValueOnce(
      new ApiError({ kind: 'server', status: 500, userMessage: 'Boom', technicalMessage: 'x' }),
    );
    renderWithProviders(<MenuComparison />);
    expect(await screen.findByText('Unable to load the menu comparison')).toBeInTheDocument();
  });
});
