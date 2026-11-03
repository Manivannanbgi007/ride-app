import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import FoodNewPage from '@/pages/Food/FoodNewPage';
import { catalogService } from '@/services/catalogService';
import { restaurantService } from '@/services/restaurantService';
import { chooseOption } from '@/test/chooseOption';
import { category, menuItem, pageOf, restaurantSummary } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/catalogService', () => ({
  catalogService: { getCategories: vi.fn(), createMenuItem: vi.fn() },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { searchRestaurants: vi.fn() },
}));

/** The new item page, with the Foods list it goes back to. */
const renderNewItemPage = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/foods/new" element={<FoodNewPage />} />
      <Route path="/admin/foods" element={<p>The foods list</p>} />
    </Routes>,
    { route: '/admin/foods/new' },
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(catalogService.getCategories).mockResolvedValue([
    category(1, 'Pizza'),
    category(3, 'Biryani'),
  ]);
  vi.mocked(restaurantService.searchRestaurants).mockResolvedValue(
    pageOf([restaurantSummary()], 1, 0, 100),
  );
});

describe('the Add New Food Item page', () => {
  it('checks the form, creates the item and goes back to the foods', async () => {
    vi.mocked(catalogService.createMenuItem).mockResolvedValue(
      menuItem({ id: 50, name: 'Veg Biryani' }),
    );
    renderNewItemPage();

    expect(
      await screen.findByRole('heading', { name: 'Add New Food Item', level: 1 }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create menu item' }));
    expect(await screen.findByText('Please enter the name.')).toBeInTheDocument();
    expect(screen.getByText('Choose a category.')).toBeInTheDocument();
    expect(screen.getByText('Choose a restaurant.')).toBeInTheDocument();
    expect(screen.getByText('Please enter the price.')).toBeInTheDocument();
    expect(screen.getByText('Choose the food type.')).toBeInTheDocument();
    expect(catalogService.createMenuItem).not.toHaveBeenCalled();

    await chooseOption(screen.getByRole('combobox', { name: 'Restaurant' }), 'Biryani House');
    await chooseOption(screen.getByRole('combobox', { name: 'Category' }), 'Biryani');
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Veg Biryani');
    await userEvent.type(screen.getByRole('textbox', { name: 'Price (INR)' }), '0.5');
    await userEvent.click(screen.getByRole('radio', { name: 'Veg' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create menu item' }));
    expect(
      await screen.findByText('The price must be between 1.00 and 100000.00.'),
    ).toBeInTheDocument();

    const price = screen.getByRole('textbox', { name: 'Price (INR)' });
    await userEvent.clear(price);
    await userEvent.type(price, '249.5');
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Ingredients (optional)' }),
      'Basmati rice, vegetables',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Create menu item' }));

    await waitFor(() =>
      expect(catalogService.createMenuItem).toHaveBeenCalledWith({
        restaurantId: 3,
        name: 'Veg Biryani',
        description: null,
        price: 249.5,
        categoryId: 3,
        foodType: 'VEG',
        imageUrl: null,
        ingredients: 'Basmati rice, vegetables',
        nutritionInfo: null,
      }),
    );
    expect(await screen.findByText('"Veg Biryani" was added.')).toBeInTheDocument();
    expect(await screen.findByText('The foods list')).toBeInTheDocument();
    // Typing a whole form takes a while
  }, 20_000);

  it('keeps the server refusal on the page, with what was typed', async () => {
    vi.mocked(catalogService.createMenuItem).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'A menu item named "Veg Biryani" already exists in this restaurant',
        technicalMessage: 'HTTP 409',
      }),
    );
    renderNewItemPage();

    await chooseOption(
      await screen.findByRole('combobox', { name: 'Restaurant' }),
      'Biryani House',
    );
    await chooseOption(screen.getByRole('combobox', { name: 'Category' }), 'Biryani');
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Veg Biryani');
    await userEvent.type(screen.getByRole('textbox', { name: 'Price (INR)' }), '249');
    await userEvent.click(screen.getByRole('radio', { name: 'Veg' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create menu item' }));

    expect(await screen.findByText(/already exists in this restaurant/)).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Veg Biryani');
    expect(screen.queryByText('The foods list')).not.toBeInTheDocument();
  }, 20_000);

  it('goes back at once when nothing was typed', async () => {
    renderNewItemPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

    expect(await screen.findByText('The foods list')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('asks before discarding what was typed, from Cancel, the back link and a reload', async () => {
    renderNewItemPage();
    const name = await screen.findByRole('textbox', { name: 'Name' });
    await userEvent.type(name, 'Paneer Tikka');

    // Reloading or closing the tab: the browser asks
    const unload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unload);
    expect(unload.defaultPrevented).toBe(true);

    await userEvent.click(screen.getByRole('link', { name: 'Back to foods' }));
    const dialog = await screen.findByRole('dialog', { name: 'Discard this new item?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep editing' }));
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Paneer Tikka');

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await userEvent.click(
      within(await screen.findByRole('dialog', { name: 'Discard this new item?' })).getByRole(
        'button',
        { name: 'Discard' },
      ),
    );
    expect(await screen.findByText('The foods list')).toBeInTheDocument();
    expect(catalogService.createMenuItem).not.toHaveBeenCalled();
  });

  it('previews the picture, and says when it cannot be loaded', async () => {
    renderNewItemPage();
    expect(
      await screen.findByText('The picture appears here once you enter its address.'),
    ).toBeInTheDocument();

    await userEvent.type(
      screen.getByRole('textbox', { name: 'Image URL (optional)' }),
      'https://img.example.com/biryani.jpg',
    );
    const preview = screen.getByRole('img', { name: 'Preview of the dish' });
    expect(preview).toHaveAttribute('src', 'https://img.example.com/biryani.jpg');

    fireEvent.error(preview);
    expect(
      await screen.findByText('This picture could not be loaded: check the address.'),
    ).toBeInTheDocument();
  });

  it('says when the restaurants cannot be loaded, and loads them again', async () => {
    vi.mocked(restaurantService.searchRestaurants).mockRejectedValueOnce(
      new ApiError({
        kind: 'server',
        status: 503,
        userMessage: 'Try again later',
        technicalMessage: 'HTTP 503',
      }),
    );
    renderNewItemPage();

    expect(await screen.findByText('Unable to load the restaurants')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));

    await waitFor(() => expect(restaurantService.searchRestaurants).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.queryByText('Unable to load the restaurants')).not.toBeInTheDocument(),
    );
  });
});
