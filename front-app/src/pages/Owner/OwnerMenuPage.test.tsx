import { screen, waitFor, within, type BoundFunctions, type queries } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import OwnerMenuPage from '@/pages/Owner/OwnerMenuPage';
import { catalogService } from '@/services/catalogService';
import { ownerService } from '@/services/ownerService';
import { ratingService } from '@/services/ratingService';
import { chooseOption } from '@/test/chooseOption';
import { menuItem } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getMenuItems: vi.fn(),
    createMenuItem: vi.fn(),
    updateMenuItem: vi.fn(),
    updatePrice: vi.fn(),
    setAvailability: vi.fn(),
    enableMenuItem: vi.fn(),
    disableMenuItem: vi.fn(),
  },
}));
vi.mock('@/services/catalogService', () => ({
  catalogService: { getStoreCategories: vi.fn() },
}));

/** The restaurant of renderOwnerPage's default is number 3 */
const RESTAURANT_ID = 3;

const biryani = menuItem({ id: 11, name: 'Chicken Biryani', price: 299, version: 2 });
const paneer = menuItem({
  id: 12,
  name: 'Paneer Tikka',
  categoryId: 5,
  categoryName: 'Starters',
  foodType: 'VEG',
  price: 180,
  available: false,
  version: 4,
});
const egg = menuItem({
  id: 13,
  name: 'Egg Curry',
  categoryId: 5,
  categoryName: 'Starters',
  foodType: 'EGG',
  price: 120,
  status: 'DISABLED',
  version: 1,
});
const blocked = menuItem({ id: 14, name: 'Blocked Pizza', status: 'BLOCKED' });
const menu = [biryani, paneer, egg, blocked];

const platformCategories = [
  { id: 3, name: 'Biryani', description: null, imageUrl: null },
  { id: 5, name: 'Starters', description: null, imageUrl: null },
  { id: 7, name: 'Desserts', description: null, imageUrl: null },
];

const apiError = (status: number, message: string, fieldErrors?: Record<string, string>) =>
  new ApiError({
    kind: status === 409 ? 'conflict' : 'validation',
    status,
    userMessage: message,
    fieldErrors,
    technicalMessage: 'x',
  });

const renderMenu = (route = '/owner/menu') =>
  renderOwnerPage(<OwnerMenuPage />, '/owner/menu', { route });

const dishCard = async (name: string) => within(await screen.findByRole('article', { name }));

/** Opens the "..." menu of a dish and chooses one of its actions */
const chooseDishAction = async (dish: string, action: string) => {
  await userEvent.click(await screen.findByRole('button', { name: `More actions for ${dish}` }));
  await userEvent.click(await screen.findByRole('menuitem', { name: action }));
};

const openAddDialog = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Add dish' }));
  return within(await screen.findByRole('dialog'));
};

/** Fills the add form with a valid dish; the caller may change a field afterwards */
const fillValidDish = async (dialog: BoundFunctions<typeof queries>) => {
  await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Veg Biryani');
  await userEvent.type(dialog.getByRole('textbox', { name: 'Price (INR)' }), '249.5');
  await chooseOption(dialog.getByRole('combobox', { name: 'Category' }), 'Biryani');
  await userEvent.click(dialog.getByRole('radio', { name: 'Veg' }));
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(ownerService.getMenuItems).mockResolvedValue(menu);
  vi.mocked(catalogService.getStoreCategories).mockResolvedValue(platformCategories);
});

describe('OwnerMenuPage: the list', () => {
  it('shows every dish with its price, category, food type and state, and counts them', async () => {
    renderMenu();

    expect(await screen.findByRole('heading', { name: 'Menu' })).toBeInTheDocument();
    expect(await screen.findByText('4 dishes')).toBeInTheDocument();
    expect(ownerService.getMenuItems).toHaveBeenCalledWith(RESTAURANT_ID);

    const chicken = await dishCard('Chicken Biryani');
    expect(chicken.getByText('₹299.00')).toBeInTheDocument();
    expect(chicken.getByText('Biryani')).toBeInTheDocument();
    expect(chicken.getByRole('img', { name: 'Non-veg' })).toBeInTheDocument();
    expect(chicken.queryByText('Sold out')).not.toBeInTheDocument();
    expect(chicken.queryByText('Hidden')).not.toBeInTheDocument();

    const tikka = await dishCard('Paneer Tikka');
    expect(tikka.getByRole('img', { name: 'Veg' })).toBeInTheDocument();
    expect(
      tikka.getByText('Sold out', { selector: 'span:not([aria-hidden])' }),
    ).toBeInTheDocument();

    expect((await dishCard('Egg Curry')).getByText('Hidden')).toBeInTheDocument();
    expect((await dishCard('Blocked Pizza')).getByText('Blocked by admin')).toBeInTheDocument();
  });

  it('lists the dishes in name order', async () => {
    renderMenu();

    await screen.findByText('4 dishes');
    const names = screen
      .getAllByRole('heading', { level: 3 })
      .map((heading) => heading.textContent);
    expect(names).toEqual(['Blocked Pizza', 'Chicken Biryani', 'Egg Curry', 'Paneer Tikka']);
  });

  it('shows placeholders while the menu loads', () => {
    vi.mocked(ownerService.getMenuItems).mockReturnValue(new Promise(() => undefined));
    renderMenu();

    expect(screen.getByRole('status', { name: 'Loading your menu' })).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });

  it('invites the owner to add the first dish when the menu is empty', async () => {
    vi.mocked(ownerService.getMenuItems).mockResolvedValue([]);
    renderMenu();

    expect(await screen.findByText('Your menu is empty — add your first dish')).toBeInTheDocument();
    // Nothing to filter yet
    expect(screen.queryByRole('combobox', { name: 'Category' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add your first dish' }));
    expect(await screen.findByRole('dialog', { name: 'Add dish' })).toBeInTheDocument();
  });

  it('shows the error with a way to try again', async () => {
    vi.mocked(ownerService.getMenuItems).mockRejectedValueOnce(
      new ApiError({
        kind: 'network',
        userMessage: 'Unable to connect to the server.',
        technicalMessage: 'x',
      }),
    );
    renderMenu();

    expect(await screen.findByText('Unable to load your menu')).toBeInTheDocument();
    expect(screen.getByText('Unable to connect to the server.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /try again/i }));

    expect(await screen.findByRole('article', { name: 'Chicken Biryani' })).toBeInTheDocument();
    expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2);
  });

  it('keeps showing the dishes when a reload fails', async () => {
    const { queryClient } = renderMenu();
    await screen.findByText('4 dishes');

    vi.mocked(ownerService.getMenuItems).mockRejectedValue(apiError(500, 'Down'));
    await queryClient.invalidateQueries();

    await waitFor(() => expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('article', { name: 'Chicken Biryani' })).toBeInTheDocument();
  });
});

describe('OwnerMenuPage: filters', () => {
  it('searches by name', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Name' }), 'tikka');

    expect(await screen.findByText('1 of 4 dishes')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Paneer Tikka' })).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Chicken Biryani' })).not.toBeInTheDocument();
  });

  it('filters by category', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    await chooseOption(screen.getByRole('combobox', { name: 'Category' }), 'Starters');

    expect(await screen.findByText('2 of 4 dishes')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Egg Curry' })).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Paneer Tikka' })).toBeInTheDocument();
  });

  it('shows only veg dishes', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    await chooseOption(screen.getByRole('combobox', { name: 'Food type' }), 'Veg only');

    expect(await screen.findByText('1 of 4 dishes')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Paneer Tikka' })).toBeInTheDocument();
  });

  it.each([
    ['On the menu: 1 dish', ['Chicken Biryani']],
    ['Sold out: 1 dish', ['Paneer Tikka']],
    ['Hidden: 2 dishes', ['Blocked Pizza', 'Egg Curry']],
  ])('filters by status "%s"', async (chip, names) => {
    renderMenu();
    await screen.findByText('4 dishes');

    await userEvent.click(screen.getByRole('button', { name: chip }));
    expect(screen.getByRole('button', { name: chip })).toHaveAttribute('aria-pressed', 'true');

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(names.length));
    for (const name of names) {
      expect(screen.getByRole('article', { name })).toBeInTheDocument();
    }
  });

  it('starts from the filters in the address', async () => {
    renderMenu('/owner/menu?status=hidden&category=5');

    expect(await screen.findByText('1 of 4 dishes')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Egg Curry' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hidden: 1 dish' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('says when no dish matches, and clears the filters', async () => {
    renderMenu('/owner/menu?search=pizzzza');

    expect(await screen.findByText('No dish matches these filters')).toBeInTheDocument();
    expect(screen.getByText('0 of 4 dishes')).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();

    const clear = screen.getAllByRole('button', { name: 'Clear filters' });
    await userEvent.click(clear[clear.length - 1]);

    expect(await screen.findByText('4 dishes')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(4);
  });

  it('has nothing to clear before a filter is set', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeDisabled();
  });

  it('cuts a long menu into pages', async () => {
    vi.mocked(ownerService.getMenuItems).mockResolvedValue(
      Array.from({ length: 14 }, (_, index) =>
        menuItem({ id: 100 + index, name: `Dish ${String(index).padStart(2, '0')}` }),
      ),
    );
    renderMenu();

    expect(await screen.findByText('14 dishes')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(12);
    expect(screen.getByText('Showing 12 of 14 dishes')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(2));
    expect(screen.getByRole('article', { name: 'Dish 13' })).toBeInTheDocument();
  });
});

describe('OwnerMenuPage: what customers say', () => {
  // The other tests see no ratings (the shared default)
  afterEach(() => vi.mocked(ratingService.getDishRatings).mockResolvedValue([]));

  it("shows each dish's stars, and opens what customers wrote about a rated one", async () => {
    vi.mocked(ratingService.getDishRatings).mockResolvedValue([{ id: 11, average: 4.5, count: 2 }]);
    vi.mocked(ratingService.getDishRating).mockResolvedValue({
      id: 11,
      average: 4.5,
      count: 2,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 1, '5': 1 },
    });
    vi.mocked(ratingService.getDishReviews).mockResolvedValue({
      content: [
        {
          id: 5,
          reviewerName: 'Priya S.',
          rating: 4,
          comment: 'Rich and well spiced',
          tags: [],
          dishes: [{ menuItemId: 11, dishName: 'Chicken Biryani', rating: 5 }],
          createdAt: '2026-10-10T10:15:00Z',
          updatedAt: '2026-10-10T10:15:00Z',
        },
      ],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    renderMenu();

    const chicken = await dishCard('Chicken Biryani');
    expect(
      await chicken.findByRole('img', { name: 'Rated 4.5 out of 5, 2 reviews' }),
    ).toBeInTheDocument();
    // The ratings of the dishes on the page are asked for at once
    expect(ratingService.getDishRatings).toHaveBeenCalledWith([11, 12, 13, 14]);
    // A dish nobody rated has no reviews to read
    const tikka = await dishCard('Paneer Tikka');
    expect(tikka.getByRole('img', { name: 'No ratings yet' })).toBeInTheDocument();
    expect(tikka.queryByRole('button', { name: /Read the reviews/ })).not.toBeInTheDocument();

    await userEvent.click(
      chicken.getByRole('button', { name: 'Read the reviews of Chicken Biryani' }),
    );

    const dialog = within(
      await screen.findByRole('dialog', { name: 'Reviews of Chicken Biryani' }),
    );
    expect(await dialog.findByText('Rich and well spiced')).toBeInTheDocument();
    expect(ratingService.getDishReviews).toHaveBeenCalledWith(11, 0, 5);
  });
});

describe('OwnerMenuPage: counts and order', () => {
  it('counts the dishes by status, among the dishes the other filters let through', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    const chips = within(screen.getByRole('group', { name: 'Show dishes by status' }));
    expect(chips.getByRole('button', { name: 'All dishes: 4 dishes' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(chips.getByRole('button', { name: 'On the menu: 1 dish' })).toBeInTheDocument();
    expect(chips.getByRole('button', { name: 'Sold out: 1 dish' })).toBeInTheDocument();
    expect(chips.getByRole('button', { name: 'Hidden: 2 dishes' })).toBeInTheDocument();
    expect(
      chips.getByText('1 of the hidden dishes was blocked by the platform team.'),
    ).toBeInTheDocument();

    await userEvent.type(screen.getByRole('searchbox', { name: 'Name' }), 'tikka');

    expect(await chips.findByRole('button', { name: 'All dishes: 1 dish' })).toBeInTheDocument();
    expect(chips.getByRole('button', { name: 'Sold out: 1 dish' })).toBeInTheDocument();
    expect(chips.getByRole('button', { name: 'Hidden: 0 dishes' })).toBeInTheDocument();
  });

  it('sorts by price; dishes of the same price stay in name order', async () => {
    renderMenu();
    await screen.findByText('4 dishes');

    await chooseOption(screen.getByRole('combobox', { name: 'Sort by' }), 'Price, high to low');

    await waitFor(() =>
      expect(
        screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent),
      ).toEqual(['Blocked Pizza', 'Chicken Biryani', 'Paneer Tikka', 'Egg Curry']),
    );
  });

  it('starts from the order in the address', async () => {
    renderMenu('/owner/menu?sort=price');

    await screen.findByText('4 dishes');
    expect(
      screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent),
    ).toEqual(['Egg Curry', 'Paneer Tikka', 'Blocked Pizza', 'Chicken Biryani']);
    expect(screen.getByRole('combobox', { name: 'Sort by' })).toHaveTextContent(
      'Price, low to high',
    );
  });

  it('shows what customers read about a dish, or that nothing was written', async () => {
    vi.mocked(ownerService.getMenuItems).mockResolvedValue([
      biryani,
      { ...egg, description: null },
    ]);
    renderMenu();

    expect(
      (await dishCard('Chicken Biryani')).getByText('Slow-cooked with saffron'),
    ).toBeInTheDocument();
    expect((await dishCard('Egg Curry')).getByText('No description yet')).toBeInTheDocument();
  });
});

describe('OwnerMenuPage: adding a dish', () => {
  it('checks the form, then sends the dish as the API expects it', async () => {
    vi.mocked(ownerService.createMenuItem).mockResolvedValue(
      menuItem({ id: 50, name: 'Veg Biryani' }),
    );
    renderMenu();

    const dialog = await openAddDialog();
    await userEvent.click(dialog.getByRole('button', { name: 'Add dish' }));

    expect(await dialog.findByText('Please enter the name.')).toBeInTheDocument();
    expect(dialog.getByText('Choose a category.')).toBeInTheDocument();
    expect(dialog.getByText('Please enter the price.')).toBeInTheDocument();
    expect(dialog.getByText('Choose the food type.')).toBeInTheDocument();
    expect(ownerService.createMenuItem).not.toHaveBeenCalled();

    await fillValidDish(dialog);
    await userEvent.type(dialog.getByRole('textbox', { name: 'Description' }), '  Fragrant  ');
    await userEvent.type(
      dialog.getByRole('textbox', { name: 'Image URL (optional)' }),
      'https://img.example/veg.jpg',
    );
    await userEvent.click(dialog.getByRole('button', { name: 'Add dish' }));

    await waitFor(() =>
      expect(ownerService.createMenuItem).toHaveBeenCalledWith(RESTAURANT_ID, {
        name: 'Veg Biryani',
        description: 'Fragrant',
        price: 249.5,
        categoryId: 3,
        foodType: 'VEG',
        imageUrl: 'https://img.example/veg.jpg',
        ingredients: null,
        nutritionInfo: null,
      }),
    );
    expect(await screen.findByText('"Veg Biryani" was added to your menu.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // The list is asked for again
    expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2);
  });

  it('sends empty optional fields as null', async () => {
    vi.mocked(ownerService.createMenuItem).mockResolvedValue(menuItem({ id: 50 }));
    renderMenu();

    const dialog = await openAddDialog();
    await fillValidDish(dialog);
    await userEvent.click(dialog.getByRole('button', { name: 'Add dish' }));

    await waitFor(() => expect(ownerService.createMenuItem).toHaveBeenCalledTimes(1));
    expect(vi.mocked(ownerService.createMenuItem).mock.calls[0][1]).toMatchObject({
      description: null,
      imageUrl: null,
      ingredients: null,
      nutritionInfo: null,
    });
  });

  it('rejects a price outside 1.00 - 100000.00 and a bad image address before sending', async () => {
    renderMenu();

    const dialog = await openAddDialog();
    await fillValidDish(dialog);
    const price = dialog.getByRole('textbox', { name: 'Price (INR)' });
    await userEvent.clear(price);
    await userEvent.type(price, '0.5');
    await userEvent.type(dialog.getByRole('textbox', { name: 'Image URL (optional)' }), 'nope');
    await userEvent.click(dialog.getByRole('button', { name: 'Add dish' }));

    expect(
      await dialog.findByText('The price must be between 1.00 and 100000.00.'),
    ).toBeInTheDocument();
    expect(dialog.getByText('Enter a full web address (https://...).')).toBeInTheDocument();
    expect(ownerService.createMenuItem).not.toHaveBeenCalled();
  });

  it('offers the categories of the platform', async () => {
    renderMenu();

    const dialog = await openAddDialog();
    await userEvent.click(dialog.getByRole('combobox', { name: 'Category' }));

    const options = within(await screen.findByRole('listbox')).getAllByRole('option');
    expect(options.map((option) => option.textContent)).toEqual([
      'Choose a category',
      'Biryani',
      'Starters',
      'Desserts',
    ]);
  });

  it("keeps the dialog open and shows the server's refusal, also next to its field", async () => {
    vi.mocked(ownerService.createMenuItem).mockRejectedValue(
      apiError(400, 'The dish is not valid.', { name: 'A dish with this name already exists.' }),
    );
    renderMenu();

    const dialog = await openAddDialog();
    await fillValidDish(dialog);
    await userEvent.click(dialog.getByRole('button', { name: 'Add dish' }));

    expect(await dialog.findByText('The dish is not valid.')).toBeInTheDocument();
    expect(dialog.getByText('A dish with this name already exists.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    // The owner can try again: nothing is blocked
    expect(dialog.getByRole('button', { name: 'Add dish' })).toBeEnabled();
  });
});

describe('OwnerMenuPage: editing a dish', () => {
  it('fills the form with the dish and sends the whole dish with the version it read', async () => {
    vi.mocked(ownerService.getMenuItems).mockResolvedValue([
      menuItem({
        id: 11,
        name: 'Chicken Biryani',
        price: 299,
        version: 7,
        ingredients: 'Rice, chicken',
        imageUrl: 'https://img.example/b.jpg',
      }),
    ]);
    vi.mocked(ownerService.updateMenuItem).mockResolvedValue(
      menuItem({ name: 'Chicken Dum Biryani' }),
    );
    renderMenu();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit "Chicken Biryani"' }));

    expect(dialog.getByRole('textbox', { name: 'Name' })).toHaveValue('Chicken Biryani');
    expect(dialog.getByRole('textbox', { name: 'Price (INR)' })).toHaveValue('299.00');
    expect(dialog.getByRole('textbox', { name: 'Ingredients (optional)' })).toHaveValue(
      'Rice, chicken',
    );
    expect(dialog.getByRole('combobox', { name: 'Category' })).toHaveTextContent('Biryani');
    expect(dialog.getByRole('radio', { name: 'Non-veg' })).toBeChecked();

    const name = dialog.getByRole('textbox', { name: 'Name' });
    await userEvent.clear(name);
    await userEvent.type(name, 'Chicken Dum Biryani');
    await userEvent.click(dialog.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(ownerService.updateMenuItem).toHaveBeenCalledWith(RESTAURANT_ID, 11, {
        name: 'Chicken Dum Biryani',
        description: 'Slow-cooked with saffron',
        price: 299,
        categoryId: 3,
        foodType: 'NON_VEG',
        imageUrl: 'https://img.example/b.jpg',
        ingredients: 'Rice, chicken',
        nutritionInfo: null,
        version: 7,
      }),
    );
    expect(await screen.findByText('"Chicken Dum Biryani" was updated.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it("keeps a dish's own category in the list even when the platform switched it off", async () => {
    vi.mocked(catalogService.getStoreCategories).mockResolvedValue([platformCategories[1]]);
    renderMenu();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    const dialog = within(await screen.findByRole('dialog'));

    expect(dialog.getByRole('combobox', { name: 'Category' })).toHaveTextContent('Biryani');
  });

  it('shows the stale-version message in the dialog and reloads the menu', async () => {
    vi.mocked(ownerService.updateMenuItem).mockRejectedValue(
      apiError(409, 'This dish was changed since you opened it.'),
    );
    renderMenu();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save changes' }));

    expect(
      await dialog.findByText(/This dish was changed since you opened it\./),
    ).toBeInTheDocument();
    expect(dialog.getByText(/The menu has been reloaded/)).toBeInTheDocument();
    await waitFor(() => expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2));
  });

  it('does not reload the menu when the refusal is a name already in use', async () => {
    vi.mocked(ownerService.updateMenuItem).mockRejectedValue(
      apiError(409, 'A dish with this name already exists.'),
    );
    renderMenu();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Chicken Biryani' }));
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save changes' }));

    expect(await dialog.findByText('A dish with this name already exists.')).toBeInTheDocument();
    expect(dialog.queryByText(/The menu has been reloaded/)).not.toBeInTheDocument();
    expect(ownerService.getMenuItems).toHaveBeenCalledTimes(1);
  });
});

describe('OwnerMenuPage: editing the price', () => {
  it('sends the new price with the version of the dish', async () => {
    vi.mocked(ownerService.updatePrice).mockResolvedValue(menuItem({ price: 325.5 }));
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Change the price');
    const dialog = within(
      await screen.findByRole('dialog', { name: 'Price of "Chicken Biryani"' }),
    );
    const price = dialog.getByRole('textbox', { name: 'Price (INR)' });
    expect(price).toHaveValue('299.00');
    expect(dialog.getByText('Now ₹299.00')).toBeInTheDocument();

    await userEvent.clear(price);
    await userEvent.type(price, '325.50');
    await userEvent.click(dialog.getByRole('button', { name: 'Save price' }));

    await waitFor(() =>
      expect(ownerService.updatePrice).toHaveBeenCalledWith(RESTAURANT_ID, 11, 325.5, 2),
    );
    expect(
      await screen.findByText('The price of "Chicken Biryani" was changed.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('checks the price before sending', async () => {
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Change the price');
    const dialog = within(await screen.findByRole('dialog'));
    const price = dialog.getByRole('textbox', { name: 'Price (INR)' });
    await userEvent.clear(price);
    await userEvent.type(price, '100000.50');
    await userEvent.click(dialog.getByRole('button', { name: 'Save price' }));

    expect(
      await dialog.findByText('The price must be between 1.00 and 100000.00.'),
    ).toBeInTheDocument();
    expect(ownerService.updatePrice).not.toHaveBeenCalled();
  });

  it('shows the stale-version message and reloads the menu', async () => {
    vi.mocked(ownerService.updatePrice).mockRejectedValue(
      apiError(409, 'This dish was changed since you opened it.'),
    );
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Change the price');
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save price' }));

    expect(
      await dialog.findByText(/This dish was changed since you opened it\./),
    ).toBeInTheDocument();
    await waitFor(() => expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2));
  });
});

describe('OwnerMenuPage: sold out / in stock', () => {
  it('marks an in-stock dish as sold out', async () => {
    vi.mocked(ownerService.setAvailability).mockResolvedValue(menuItem({ available: false }));
    renderMenu();

    const toggle = await screen.findByRole('switch', { name: 'In stock: Chicken Biryani' });
    expect(toggle).toBeChecked();
    await userEvent.click(toggle);

    await waitFor(() =>
      expect(ownerService.setAvailability).toHaveBeenCalledWith(RESTAURANT_ID, 11, false),
    );
    expect(await screen.findByText('"Chicken Biryani" is marked sold out.')).toBeInTheDocument();
  });

  it('puts a sold-out dish back in stock', async () => {
    vi.mocked(ownerService.setAvailability).mockResolvedValue(menuItem({ available: true }));
    renderMenu();

    const toggle = await screen.findByRole('switch', { name: 'In stock: Paneer Tikka' });
    expect(toggle).not.toBeChecked();
    await userEvent.click(toggle);

    await waitFor(() =>
      expect(ownerService.setAvailability).toHaveBeenCalledWith(RESTAURANT_ID, 12, true),
    );
    expect(await screen.findByText('"Paneer Tikka" is back in stock.')).toBeInTheDocument();
  });

  it('waits while the request runs, and only for that dish', async () => {
    let finish: (item: ReturnType<typeof menuItem>) => void = () => undefined;
    vi.mocked(ownerService.setAvailability).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    renderMenu();

    await userEvent.click(await screen.findByRole('switch', { name: 'In stock: Chicken Biryani' }));

    const chicken = await dishCard('Chicken Biryani');
    await waitFor(() =>
      expect(chicken.getByRole('switch', { name: 'In stock: Chicken Biryani' })).toBeDisabled(),
    );
    expect(chicken.getByRole('button', { name: 'Edit Chicken Biryani' })).toBeDisabled();
    expect(
      chicken.getByRole('button', { name: 'More actions for Chicken Biryani' }),
    ).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'In stock: Paneer Tikka' })).toBeEnabled();

    finish(menuItem({ available: false }));
    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'In stock: Chicken Biryani' })).toBeEnabled(),
    );
  });

  it('shows the error and leaves the switch as it was when the request fails', async () => {
    vi.mocked(ownerService.setAvailability).mockRejectedValue(apiError(422, 'Cannot change now.'));
    renderMenu();

    await userEvent.click(await screen.findByRole('switch', { name: 'In stock: Chicken Biryani' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot change now.');
    expect(screen.getByRole('switch', { name: 'In stock: Chicken Biryani' })).toBeChecked();
  });
});

describe('OwnerMenuPage: hiding and showing', () => {
  it('asks before hiding a dish, and does nothing when the owner cancels', async () => {
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Hide from the menu');

    const dialog = within(
      await screen.findByRole('dialog', { name: 'Hide "Chicken Biryani" from the menu?' }),
    );
    expect(ownerService.disableMenuItem).not.toHaveBeenCalled();

    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(ownerService.disableMenuItem).not.toHaveBeenCalled();
  });

  it('hides the dish once the owner confirms', async () => {
    vi.mocked(ownerService.disableMenuItem).mockResolvedValue(menuItem({ status: 'DISABLED' }));
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Hide from the menu');
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.click(dialog.getByRole('button', { name: 'Hide dish' }));

    await waitFor(() =>
      expect(ownerService.disableMenuItem).toHaveBeenCalledWith(RESTAURANT_ID, 11),
    );
    expect(
      await screen.findByText('"Chicken Biryani" is hidden from the menu.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(ownerService.enableMenuItem).not.toHaveBeenCalled();
  });

  it('shows a hidden dish again at once, without asking', async () => {
    vi.mocked(ownerService.enableMenuItem).mockResolvedValue(
      menuItem({ id: 13, status: 'ACTIVE' }),
    );
    renderMenu();

    await chooseDishAction('Egg Curry', 'Show on the menu');

    await waitFor(() =>
      expect(ownerService.enableMenuItem).toHaveBeenCalledWith(RESTAURANT_ID, 13),
    );
    expect(await screen.findByText('"Egg Curry" is on the menu again.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('cannot show a dish an admin blocked', async () => {
    renderMenu();

    const pizza = await dishCard('Blocked Pizza');

    expect(pizza.getByRole('button', { name: 'Edit Blocked Pizza' })).toBeInTheDocument();
    expect(
      pizza.getByText('Only the platform team can put this dish back on the menu.'),
    ).toBeInTheDocument();

    await userEvent.click(pizza.getByRole('button', { name: 'More actions for Blocked Pizza' }));
    const actions = within(await screen.findByRole('menu'));
    expect(actions.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'Change the price',
    ]);
  });

  it('shows the refusal when hiding fails', async () => {
    vi.mocked(ownerService.disableMenuItem).mockRejectedValue(apiError(422, 'Not allowed now.'));
    renderMenu();

    await chooseDishAction('Chicken Biryani', 'Hide from the menu');
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Hide dish' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Not allowed now.');
  });

  it('reloads the menu when a quick action finds the dish changed meanwhile', async () => {
    vi.mocked(ownerService.setAvailability).mockRejectedValue(
      apiError(409, 'This dish was changed since you opened it.'),
    );
    renderMenu();

    await userEvent.click(await screen.findByRole('switch', { name: 'In stock: Chicken Biryani' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This dish was changed since you opened it.',
    );
    await waitFor(() => expect(ownerService.getMenuItems).toHaveBeenCalledTimes(2));
  });
});
