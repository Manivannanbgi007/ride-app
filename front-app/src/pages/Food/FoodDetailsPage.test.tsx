import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import FoodDetailsPage from '@/pages/Food/FoodDetailsPage';
import { catalogService } from '@/services/catalogService';
import { orderService } from '@/services/orderService';
import { restaurantService } from '@/services/restaurantService';
import { reviewService } from '@/services/reviewService';
import {
  adminRestaurant,
  dishRevenue,
  menuItem,
  pageOf,
  restaurantSummary,
  review,
  reviewSummary,
} from '@/test/fixtures';
import { chooseOption, optionsOf } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { Category } from '@/types/catalog';

vi.mock('@/services/catalogService', () => ({
  catalogService: {
    searchMenuItems: vi.fn(),
    getMenuItem: vi.fn(),
    changeStatus: vi.fn(),
    changeCategory: vi.fn(),
    removeMenuItem: vi.fn(),
    getAuditLogs: vi.fn(),
    getCategories: vi.fn(),
    createMenuItem: vi.fn(),
    updateMenuItem: vi.fn(),
  },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { getRestaurant: vi.fn(), searchRestaurants: vi.fn() },
}));
vi.mock('@/services/orderService', () => ({
  orderService: { getDishRevenue: vi.fn() },
}));
vi.mock('@/services/reviewService', () => ({
  reviewService: { searchReviews: vi.fn(), getSummary: vi.fn() },
}));

function category(id: number, name: string, status: Category['status'] = 'ACTIVE'): Category {
  return {
    id,
    name,
    description: null,
    imageUrl: null,
    displayOrder: id,
    status,
    menuItemCount: 3,
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-09-01T09:00:00Z',
    version: 0,
  };
}

const renderItem = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/foods/:itemId" element={<FoodDetailsPage />} />
      <Route path="/admin/foods" element={<p>Food list</p>} />
    </Routes>,
    { route: '/admin/foods/11' },
  );

describe('FoodDetailsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(menuItem());
    vi.mocked(catalogService.getAuditLogs).mockResolvedValue(pageOf([]));
    vi.mocked(catalogService.getCategories).mockResolvedValue([
      category(1, 'Pizza'),
      category(3, 'Biryani'),
      category(4, 'Seasonal', 'INACTIVE'),
    ]);
    vi.mocked(restaurantService.getRestaurant).mockResolvedValue(adminRestaurant());
    vi.mocked(restaurantService.searchRestaurants).mockResolvedValue(pageOf([restaurantSummary()]));
    vi.mocked(orderService.getDishRevenue).mockResolvedValue(dishRevenue());
    vi.mocked(reviewService.searchReviews).mockResolvedValue(pageOf([]));
    vi.mocked(reviewService.getSummary).mockResolvedValue(reviewSummary({ count: 0 }));
  });

  it("shows the dish with its restaurant's name; a listed dish can be blocked, not removed", async () => {
    renderItem();

    expect(
      await screen.findByRole('heading', { name: 'Chicken Biryani', level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Biryani House' })).toHaveAttribute(
      'href',
      '/admin/restaurants/3',
    );
    expect(screen.getByText('Stock available')).toBeInTheDocument();
    expect(screen.getByText('Slow-cooked with saffron')).toBeInTheDocument();
    expect(screen.getByText('₹299.00')).toBeInTheDocument();
    expect(screen.getByText(/Category: Non-veg/)).toHaveTextContent('Category: Non-veg / Biryani');
    expect(screen.getByRole('button', { name: 'Block' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument();
  });

  it('a blocked dish can be released or removed; removing it leads back to the list', async () => {
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(menuItem({ status: 'BLOCKED' }));
    vi.mocked(catalogService.removeMenuItem).mockResolvedValue(undefined);
    renderItem();

    expect(await screen.findByRole('button', { name: 'Release' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Block' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Duplicate of item 12');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove' }));

    expect(catalogService.removeMenuItem).toHaveBeenCalledWith(11, 'Duplicate of item 12');
    expect(await screen.findByText('Food list')).toBeInTheDocument();
  });

  it('moves the dish to another active category', async () => {
    vi.mocked(catalogService.changeCategory).mockResolvedValue(
      menuItem({ categoryId: 1, categoryName: 'Pizza' }),
    );
    renderItem();

    await userEvent.click(await screen.findByRole('button', { name: 'Change category' }));
    const dialog = screen.getByRole('dialog');
    const categories = within(dialog).getByLabelText('New category');
    // Neither the current category nor an inactive one is offered
    expect(await optionsOf(categories)).toEqual(['Choose a category', 'Pizza']);

    await chooseOption(categories, 'Pizza');
    await userEvent.type(
      within(dialog).getByLabelText('Reason'),
      'Listed under the wrong category',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Move item' }));

    expect(catalogService.changeCategory).toHaveBeenCalledWith(
      11,
      1,
      'Listed under the wrong category',
    );
    expect(await screen.findByText('"Chicken Biryani" moved to Pizza.')).toBeInTheDocument();
  });

  it('shows the ingredients and nutrition, or a hint to add them', async () => {
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(
      menuItem({ ingredients: 'Rice\nChicken', nutritionInfo: null }),
    );
    renderItem();

    const ingredients = await screen.findByRole('region', { name: 'Ingredients' });
    expect(within(ingredients).getByText(/Rice/)).toHaveClass('whitespace-pre-wrap');
    expect(
      within(screen.getByRole('region', { name: 'Nutrition Info' })).getByText(
        'Not added yet. Use Edit Menu to add it.',
      ),
    ).toBeInTheDocument();
  });

  it.each([
    [{ status: 'BLOCKED' as const }, 'Blocked by admin'],
    [{ status: 'DISABLED' as const }, 'Hidden by owner'],
    [{ available: false }, 'Sold out'],
  ])('labels the stock of %j as "%s"', async (overrides, label) => {
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(menuItem(overrides));
    renderItem();
    expect(await screen.findByText(label)).toBeInTheDocument();
  });

  it('Edit Menu opens the form with the dish and its ingredients, and sends the new texts', async () => {
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(
      menuItem({ ingredients: 'Rice', nutritionInfo: '450 kcal' }),
    );
    vi.mocked(catalogService.updateMenuItem).mockResolvedValue(menuItem());
    renderItem();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Menu' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByRole('textbox', { name: 'Name' })).toHaveValue('Chicken Biryani');
    const ingredients = dialog.getByRole('textbox', { name: 'Ingredients (optional)' });
    expect(ingredients).toHaveValue('Rice');
    const nutrition = dialog.getByRole('textbox', { name: 'Nutrition info (optional)' });
    expect(nutrition).toHaveValue('450 kcal');
    expect(dialog.getByText('4/1000')).toBeInTheDocument();

    await userEvent.clear(nutrition);
    await userEvent.type(ingredients, ' and spice');
    await userEvent.click(dialog.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(catalogService.updateMenuItem).toHaveBeenCalledWith(
        11,
        expect.objectContaining({
          ingredients: 'Rice and spice',
          nutritionInfo: null,
          version: 2,
        }),
      ),
    );
  });

  it("Add Menu opens the create form for the dish's restaurant and category, without copying the texts", async () => {
    vi.mocked(catalogService.getMenuItem).mockResolvedValue(
      menuItem({ ingredients: 'Rice', nutritionInfo: '450 kcal' }),
    );
    vi.mocked(catalogService.createMenuItem).mockResolvedValue(menuItem({ id: 50 }));
    renderItem();

    await userEvent.click(await screen.findByRole('button', { name: 'Add Menu' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByRole('combobox', { name: 'Restaurant' })).toHaveTextContent('Biryani House');
    expect(dialog.getByRole('combobox', { name: 'Category' })).toHaveTextContent('Biryani');
    expect(dialog.getByRole('textbox', { name: 'Name' })).toHaveValue('');
    expect(dialog.getByRole('textbox', { name: 'Ingredients (optional)' })).toHaveValue('');

    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Veg Biryani');
    await userEvent.type(dialog.getByRole('textbox', { name: 'Price (INR)' }), '199');
    await userEvent.click(dialog.getByRole('radio', { name: /^Veg/ }));
    await userEvent.click(dialog.getByRole('button', { name: 'Create menu item' }));

    await waitFor(() =>
      expect(catalogService.createMenuItem).toHaveBeenCalledWith({
        restaurantId: 3,
        categoryId: 3,
        name: 'Veg Biryani',
        description: null,
        price: 199,
        foodType: 'VEG',
        imageUrl: null,
        ingredients: null,
        nutritionInfo: null,
      }),
    );
  });

  it('shows the revenue and asks again when the period changes', async () => {
    vi.mocked(orderService.getDishRevenue).mockImplementation((_id, period) =>
      Promise.resolve(
        period === 'DAILY'
          ? dishRevenue({ period, totalRevenue: 0, totalQuantity: 0 })
          : dishRevenue({ period }),
      ),
    );
    renderItem();

    expect(await screen.findByText(/from 200 sold in the last 12 months/)).toBeInTheDocument();
    expect(screen.getByText('₹59,800.00')).toBeInTheDocument();
    expect(orderService.getDishRevenue).toHaveBeenCalledWith(11, 'MONTHLY');
    expect(
      screen.getByRole('img', { name: /Revenue chart for the last 12 months/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Monthly' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(screen.getByRole('tab', { name: 'Weekly' }));
    await waitFor(() => expect(orderService.getDishRevenue).toHaveBeenCalledWith(11, 'WEEKLY'));
    expect(await screen.findByText(/in the last 12 weeks/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Daily' }));
    expect(await screen.findByText('No sales yet')).toBeInTheDocument();
  });

  it('explains a failed revenue request and tries again', async () => {
    vi.mocked(orderService.getDishRevenue).mockRejectedValueOnce(new Error('boom'));
    renderItem();

    expect(await screen.findByText('Unable to load the revenue of this dish')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(/from 200 sold/)).toBeInTheDocument();
  });

  it("lists the dish's reviews with the dish rating, and loads more", async () => {
    const first = review({
      id: 1,
      customerName: 'Johnny Ahmad',
      comment: null,
      rating: 2,
      createdAt: new Date(Date.now() - 3_600_000).toISOString(),
      dishRatings: [
        { menuItemId: 5, dishName: 'Other', rating: 1 },
        { menuItemId: 11, dishName: 'Chicken Biryani', rating: 4 },
      ],
    });
    const second = review({ id: 2, customerName: 'Maria Vania', comment: 'Tasty' });
    vi.mocked(reviewService.searchReviews).mockImplementation((_filters, page) =>
      Promise.resolve(
        page === 0
          ? { ...pageOf([first], 2, 0, 1), last: false }
          : { ...pageOf([second], 2, 1, 1), last: true },
      ),
    );
    vi.mocked(reviewService.getSummary).mockResolvedValue(
      reviewSummary({ count: 12, averageRating: 4.3 }),
    );
    renderItem();

    const heading = await screen.findByRole('heading', { name: 'Customer Reviews' });
    await waitFor(() => expect(heading.parentElement).toHaveTextContent('4.3 · 12 ratings'));
    expect(reviewService.searchReviews).toHaveBeenCalledWith(
      { menuItemId: 11, sort: 'createdAt,desc' },
      0,
      3,
    );
    expect(reviewService.getSummary).toHaveBeenCalledWith({ menuItemId: 11 });
    const card = (await screen.findByText('Johnny Ahmad')).closest('li') as HTMLElement;
    expect(within(card).getByText('1 hour ago')).toBeInTheDocument();
    expect(within(card).getByText('No comment')).toBeInTheDocument();
    expect(within(card).getByRole('img', { name: '4 out of 5' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Load more' }));
    expect(await screen.findByText('Maria Vania')).toBeInTheDocument();
    expect(screen.getByText('Johnny Ahmad')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
  });

  it('retries a failed review request, and says so when the dish has no reviews', async () => {
    vi.mocked(reviewService.searchReviews).mockRejectedValueOnce(new Error('boom'));
    renderItem();

    expect(await screen.findByText('Unable to load the reviews of this dish')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('No reviews for this dish yet')).toBeInTheDocument();
  });
});
