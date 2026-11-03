import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import CategoriesPage from '@/pages/Category/CategoriesPage';
import { catalogService } from '@/services/catalogService';
import { category } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/catalogService', () => ({
  catalogService: {
    getCategories: vi.fn(),
    createCategory: vi.fn(),
    updateCategory: vi.fn(),
    changeCategoryStatus: vi.fn(),
    removeCategory: vi.fn(),
    reorderCategories: vi.fn(),
    getCategoryAuditLogs: vi.fn(),
  },
}));

const CATEGORIES = [
  category(1, 'Pizza', { menuItemCount: 12 }),
  category(2, 'Burgers'),
  category(3, 'Seasonal', { status: 'INACTIVE' }),
];

describe('CategoriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(catalogService.getCategories).mockResolvedValue(CATEGORIES);
  });

  it('moves a category down, sending every category once in the new order', async () => {
    vi.mocked(catalogService.reorderCategories).mockResolvedValue([
      CATEGORIES[1],
      CATEGORIES[0],
      CATEGORIES[2],
    ]);
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Move Pizza down' }));

    expect(catalogService.reorderCategories).toHaveBeenCalledWith([2, 1, 3]);
    // The new order is shown: Burgers is first now
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Move Burgers up' })).toBeDisabled(),
    );
  });

  it('does not reorder while a filter hides categories', async () => {
    renderWithProviders(<CategoriesPage />);
    await screen.findByText('Pizza');

    await userEvent.type(screen.getByLabelText('Search'), 'bur');

    expect(screen.queryByText('Pizza')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move Burgers down' })).toBeDisabled();
    expect(
      screen.getByText('Clear the search and status to change the order.'),
    ).toBeInTheDocument();
  });

  it('cannot delete a category that dishes use', async () => {
    renderWithProviders(<CategoriesPage />);

    const blocked = await screen.findByRole('button', {
      name: 'Pizza is used by dishes and cannot be deleted',
    });
    expect(blocked).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Delete Burgers' })).toBeEnabled();

    // The switched-off button still says why in its tooltip (its wrapper listens for the pointer)
    await userEvent.hover(blocked.parentElement as HTMLElement);
    expect(screen.getByText('Pizza is used by dishes and cannot be deleted')).toBeInTheDocument();
  });

  it("shows an action button's title in a tooltip on hover", async () => {
    renderWithProviders(<CategoriesPage />);
    const edit = await screen.findByRole('button', { name: 'Edit Burgers' });

    await userEvent.hover(edit);
    expect(screen.getByText('Edit Burgers')).toBeInTheDocument();

    await userEvent.unhover(edit);
    await waitFor(() => expect(screen.queryByText('Edit Burgers')).not.toBeInTheDocument());
  });

  it('creates a category; a name that exists is explained in the dialog', async () => {
    vi.mocked(catalogService.createCategory).mockRejectedValueOnce(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: "A category named 'Pizza' already exists",
        technicalMessage: 'POST failed with 409',
      }),
    );
    vi.mocked(catalogService.createCategory).mockResolvedValueOnce(category(4, 'Rolls'));
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'New category' }));
    const dialog = screen.getByRole('dialog', { name: 'New category' });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));
    expect(await within(dialog).findByText('Please give the category a name.')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('Name'), 'Pizza');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already exists');

    await userEvent.clear(within(dialog).getByLabelText('Name'));
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Rolls');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));

    expect(catalogService.createCategory).toHaveBeenLastCalledWith({
      name: 'Rolls',
      description: null,
      imageUrl: null,
    });
    expect(await screen.findByText('Category "Rolls" created.')).toBeInTheDocument();
  });
});
