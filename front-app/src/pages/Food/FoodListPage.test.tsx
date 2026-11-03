import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import FoodListPage from '@/pages/Food/FoodListPage';
import { catalogService } from '@/services/catalogService';
import { restaurantService } from '@/services/restaurantService';
import { category, categoryStat, menuItem, pageOf, restaurantSummary } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FOOD_VIEW_STORAGE_KEY } from '@/hooks/useFoodViewMode';

vi.mock('@/services/catalogService', () => ({
  catalogService: {
    searchMenuItems: vi.fn(),
    getCategories: vi.fn(),
    getCategoryStats: vi.fn(),
    createMenuItem: vi.fn(),
    updateMenuItem: vi.fn(),
    duplicateMenuItem: vi.fn(),
    removeMenuItem: vi.fn(),
  },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { searchRestaurants: vi.fn() },
}));

const biryani = menuItem();
const pizza = menuItem({
  id: 12,
  name: 'Margherita Pizza',
  categoryId: 1,
  categoryName: 'Pizza',
  foodType: 'VEG',
  status: 'DISABLED',
});
const tenItems = Array.from({ length: 10 }, (_, index) =>
  menuItem({ id: 100 + index, name: `Dish ${index}` }),
);

const conflict = (message: string) =>
  new ApiError({ kind: 'conflict', status: 409, userMessage: message, technicalMessage: 'x' });

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.mocked(catalogService.searchMenuItems).mockResolvedValue(pageOf([biryani, pizza], 2, 0, 10));
  vi.mocked(catalogService.getCategories).mockResolvedValue([
    category(1, 'Pizza'),
    category(3, 'Biryani'),
  ]);
  vi.mocked(catalogService.getCategoryStats).mockResolvedValue([categoryStat()]);
  vi.mocked(restaurantService.searchRestaurants).mockResolvedValue(
    pageOf([restaurantSummary()], 1, 0, 100),
  );
});

describe('FoodListPage', () => {
  it('names the restaurant from the list itself, even when it is not among the first restaurants', async () => {
    vi.mocked(catalogService.searchMenuItems).mockResolvedValue(
      pageOf(
        [
          menuItem({
            id: 90,
            name: 'Far Away Dish',
            restaurantId: 999,
            restaurantName: 'Spice Hub',
          }),
        ],
        1,
        0,
        10,
      ),
    );
    renderWithProviders(<FoodListPage />);

    const card = await screen.findByRole('article', { name: 'Far Away Dish' });
    expect(within(card).getByText('Spice Hub')).toBeInTheDocument();
    expect(within(card).queryByText('Restaurant #999')).not.toBeInTheDocument();
  });

  it('shows the items as cards with type / category, restaurant and actions, 10 per page', async () => {
    renderWithProviders(<FoodListPage />);

    const card = await screen.findByRole('article', { name: 'Chicken Biryani' });
    expect(within(card).getByText('Non-veg / Biryani')).toBeInTheDocument();
    expect(await within(card).findByText('Biryani House')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'View Chicken Biryani' })).toHaveAttribute(
      'href',
      '/admin/foods/11',
    );
    expect(within(card).getByRole('button', { name: 'Edit Chicken Biryani' })).toBeEnabled();
    expect(within(card).getByRole('button', { name: 'Duplicate Chicken Biryani' })).toBeEnabled();
    expect(
      within(screen.getByRole('article', { name: 'Margherita Pizza' })).getByText(
        'Hidden by owner',
      ),
    ).toBeInTheDocument();
    expect(catalogService.searchMenuItems).toHaveBeenCalledWith(
      expect.objectContaining({ sort: 'updatedAt,desc' }),
      0,
      10,
    );
  });

  it('can delete only a hidden or blocked item, after a reason is given', async () => {
    vi.mocked(catalogService.removeMenuItem).mockResolvedValue(undefined);
    renderWithProviders(<FoodListPage />);

    const active = await screen.findByRole('button', { name: 'Delete Chicken Biryani' });
    expect(active).toBeDisabled();
    await userEvent.hover(active);
    expect(
      await screen.findByText(
        'Disable the item first: only disabled or blocked items can be removed',
      ),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Delete Margherita Pizza' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Reason' }), 'Invalid dish');
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(catalogService.removeMenuItem).toHaveBeenCalledWith(12, 'Invalid dish'),
    );
    expect(await screen.findByText('"Margherita Pizza" was deleted.')).toBeInTheDocument();
  });

  it('duplicates an item, tells so and reloads the list', async () => {
    vi.mocked(catalogService.duplicateMenuItem).mockResolvedValue(
      menuItem({ id: 13, name: 'Chicken Biryani (copy)', status: 'DISABLED' }),
    );
    renderWithProviders(<FoodListPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Chicken Biryani' }));

    await waitFor(() => expect(catalogService.duplicateMenuItem).toHaveBeenCalledWith(11));
    expect(await screen.findByText(/Copy created/)).toBeInTheDocument();
    await waitFor(() => expect(catalogService.searchMenuItems).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(catalogService.getCategoryStats).toHaveBeenCalledTimes(2));
  });

  it('edits an item: prefilled, sends the version, and explains a 409', async () => {
    vi.mocked(catalogService.updateMenuItem).mockRejectedValueOnce(
      conflict('Concurrent modification'),
    );
    renderWithProviders(<FoodListPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    const name = await screen.findByRole('textbox', { name: 'Name' });
    expect(name).toHaveValue('Chicken Biryani');
    expect(screen.getByRole('textbox', { name: 'Price (INR)' })).toHaveValue('299.00');
    expect(screen.getByRole('radio', { name: 'Non-veg' })).toBeChecked();

    await userEvent.clear(name);
    await userEvent.type(name, 'Chicken Dum Biryani');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(
      await screen.findByText('This item was changed by someone else; reload and try again.'),
    ).toBeInTheDocument();
    expect(catalogService.updateMenuItem).toHaveBeenCalledWith(11, {
      name: 'Chicken Dum Biryani',
      description: 'Slow-cooked with saffron',
      price: 299,
      categoryId: 3,
      foodType: 'NON_VEG',
      ingredients: null,
      nutritionInfo: null,
      imageUrl: null,
      version: 2,
    });

    vi.mocked(catalogService.updateMenuItem).mockResolvedValueOnce(
      menuItem({ name: 'Chicken Dum Biryani' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('"Chicken Dum Biryani" was updated.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument(),
    );
  });

  it('keeps the server message when the name is taken', async () => {
    vi.mocked(catalogService.updateMenuItem).mockRejectedValue(
      conflict('A menu item named "Chicken Biryani" already exists in this restaurant'),
    );
    renderWithProviders(<FoodListPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText(/already exists in this restaurant/)).toBeInTheDocument();
  });

  it('opens the "Add New Food Item" page for a new menu item (the form is tested there)', async () => {
    renderWithProviders(<FoodListPage />);

    expect(await screen.findByRole('link', { name: 'New Menu' })).toHaveAttribute(
      'href',
      '/admin/foods/new',
    );
    // No dialog any more for a new item
    expect(screen.queryByRole('button', { name: 'New Menu' })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('switches between cards and the table, and remembers the choice', async () => {
    const { unmount } = renderWithProviders(<FoodListPage />);

    await screen.findByRole('article', { name: 'Chicken Biryani' });
    expect(screen.getByRole('button', { name: 'Grid view' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    await userEvent.click(screen.getByRole('button', { name: 'List view' }));
    expect(await screen.findByRole('table', { name: 'Menu items' })).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
    expect(localStorage.getItem(FOOD_VIEW_STORAGE_KEY)).toBe('list');

    unmount();
    renderWithProviders(<FoodListPage />);
    expect(await screen.findByRole('table', { name: 'Menu items' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'List view' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('searches by name', async () => {
    renderWithProviders(<FoodListPage />);

    await userEvent.type(
      await screen.findByRole('searchbox', { name: /Search menu items/ }),
      'dum',
    );

    await waitFor(() =>
      expect(catalogService.searchMenuItems).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'dum' }),
        0,
        10,
      ),
    );
  });

  it('counts the items under the cards and pages through them', async () => {
    vi.mocked(catalogService.searchMenuItems).mockResolvedValue(pageOf(tenItems, 100, 0, 10));
    renderWithProviders(<FoodListPage />);

    expect(await screen.findByText('Showing 10 from 100 menu items')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(10);

    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));
    await waitFor(() =>
      expect(catalogService.searchMenuItems).toHaveBeenLastCalledWith(expect.anything(), 1, 10),
    );
  });

  it('says so when there is nothing, or the list cannot be loaded', async () => {
    vi.mocked(catalogService.searchMenuItems).mockResolvedValueOnce(pageOf([]));
    const { unmount } = renderWithProviders(<FoodListPage />);
    expect(await screen.findByText('No menu items yet')).toBeInTheDocument();
    unmount();

    vi.mocked(catalogService.searchMenuItems).mockRejectedValueOnce(
      new ApiError({ kind: 'server', status: 500, userMessage: 'Boom', technicalMessage: 'x' }),
    );
    renderWithProviders(<FoodListPage />);
    expect(await screen.findByText('Unable to load the menu items')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Try again/ }));
    expect(await screen.findByRole('article', { name: 'Chicken Biryani' })).toBeInTheDocument();
  });
});
