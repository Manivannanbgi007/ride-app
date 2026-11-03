import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import OwnerRestaurantPage from '@/pages/Owner/OwnerRestaurantPage';
import { ownerService } from '@/services/ownerService';
import { restaurantDetails } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';
import { customerRouteTo } from '@/utils/constants';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getRestaurant: vi.fn(),
    getRestaurants: vi.fn(),
    updateRestaurant: vi.fn(),
    resubmitRestaurant: vi.fn(),
  },
}));

const RESTAURANT_ID = 3;

/** The restaurant as stored: open on Mondays only, with a phone the form accepts as it is */
const stored = restaurantDetails({ phone: '+918040001234' });

const renderRestaurant = () => renderOwnerPage(<OwnerRestaurantPage />, '/owner/restaurant');

const saveBar = () => within(screen.getByRole('region', { name: 'Save your changes' }));

/** Waits for the form, then changes the restaurant's name */
const rename = async (name: string) => {
  const field = await screen.findByRole('textbox', { name: 'Restaurant name' });
  await userEvent.clear(field);
  await userEvent.type(field, name);
  return field;
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getRestaurant).mockResolvedValue(stored);
  vi.mocked(ownerService.getRestaurants).mockResolvedValue([]);
});

describe('OwnerRestaurantPage: the restaurant at a glance', () => {
  it('shows the status, the note of the platform team, the dates and the links', async () => {
    renderRestaurant();

    const panel = within(
      await screen.findByRole('complementary', { name: 'Your restaurant at a glance' }),
    );
    expect(
      panel.getByText('Live: customers can find you and order while you are open.'),
    ).toBeInTheDocument();
    expect(panel.getByText('Food license verified')).toBeInTheDocument();
    expect(panel.getByText('Registered')).toBeInTheDocument();
    expect(panel.getByText('Details last changed')).toBeInTheDocument();
    expect(panel.getByRole('link', { name: /See your page as customers do/ })).toHaveAttribute(
      'href',
      customerRouteTo.restaurantMenu(RESTAURANT_ID),
    );
    expect(panel.getByRole('link', { name: /Open the food licence document/ })).toHaveAttribute(
      'href',
      'https://docs.example.com/license.pdf',
    );
    expect(ownerService.getRestaurant).toHaveBeenCalledWith(RESTAURANT_ID);
  });

  it('offers neither the customer page nor a link that is not a web address while not live', async () => {
    vi.mocked(ownerService.getRestaurant).mockResolvedValue(
      restaurantDetails({
        status: 'PENDING',
        statusReason: null,
        approvedAt: null,
        documents: [
          { type: 'FOOD_LICENSE', url: 'javascript:alert(1)', submittedAt: '2026-09-01T09:00:00Z' },
        ],
      }),
    );
    renderRestaurant();

    const panel = within(
      await screen.findByRole('complementary', { name: 'Your restaurant at a glance' }),
    );
    expect(panel.getByText(/The platform team is reviewing your restaurant/)).toBeInTheDocument();
    expect(panel.getByText('Not yet')).toBeInTheDocument();
    expect(panel.queryByRole('link', { name: /customers do/ })).not.toBeInTheDocument();
    expect(panel.queryByRole('link', { name: /food licence/ })).not.toBeInTheDocument();
  });

  it('shows the error with a way to try again', async () => {
    vi.mocked(ownerService.getRestaurant).mockRejectedValue(new Error('down'));
    renderRestaurant();

    expect(
      await screen.findByText('Unable to load your restaurant', undefined, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });
});

describe('OwnerRestaurantPage: saving', () => {
  it('has nothing to save or discard before a change', async () => {
    renderRestaurant();

    await screen.findByRole('textbox', { name: 'Restaurant name' });
    expect(saveBar().getByText('Everything is saved')).toBeInTheDocument();
    expect(saveBar().getByRole('button', { name: 'Save changes' })).toBeDisabled();
    expect(saveBar().getByRole('button', { name: 'Discard changes' })).toBeDisabled();
  });

  it('sends the whole restaurant with the version it read', async () => {
    vi.mocked(ownerService.updateRestaurant).mockResolvedValue({ ...stored, version: 4 });
    renderRestaurant();

    await rename('Biryani House Express');
    expect(saveBar().getByText('You have unsaved changes')).toBeInTheDocument();
    vi.mocked(ownerService.getRestaurant).mockResolvedValue({
      ...stored,
      name: 'Biryani House Express',
      version: 4,
    });
    await userEvent.click(saveBar().getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(ownerService.updateRestaurant).toHaveBeenCalledWith(
        RESTAURANT_ID,
        expect.objectContaining({
          name: 'Biryani House Express',
          phone: '+918040001234',
          version: 3,
          operatingHours: [{ dayOfWeek: 'MONDAY', opensAt: '11:00', closesAt: '23:00' }],
        }),
      ),
    );
    expect(await screen.findByText('Your restaurant was updated.')).toBeInTheDocument();
    // The form starts again from what is stored now
    expect(await saveBar().findByText('Everything is saved')).toBeInTheDocument();
  });

  it('keeps the changes and says why when the restaurant changed meanwhile', async () => {
    vi.mocked(ownerService.updateRestaurant).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'This restaurant was changed by someone else. Reload the page and try again.',
        technicalMessage: 'x',
      }),
    );
    renderRestaurant();

    const field = await rename('Biryani House Express');
    await userEvent.click(saveBar().getByRole('button', { name: 'Save changes' }));

    expect(
      await screen.findByText(
        'This restaurant was changed by someone else. Reload the page and try again.',
      ),
    ).toBeInTheDocument();
    expect(field).toHaveValue('Biryani House Express');
    expect(saveBar().getByText('You have unsaved changes')).toBeInTheDocument();
  });

  it('checks the details and the hours before sending', async () => {
    renderRestaurant();

    const field = await rename(' ');
    await userEvent.click(saveBar().getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Please enter the name.')).toBeInTheDocument();
    expect(field).toHaveAttribute('aria-invalid', 'true');

    await rename('Biryani House');
    await userEvent.click(screen.getByRole('switch', { name: 'Monday: open' }));
    await userEvent.click(saveBar().getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Open on at least one day.')).toBeInTheDocument();
    expect(ownerService.updateRestaurant).not.toHaveBeenCalled();
  });

  it('puts everything back when the owner confirms "Discard changes"', async () => {
    renderRestaurant();

    const field = await rename('Something else');
    await userEvent.click(screen.getByRole('switch', { name: 'Tuesday: open' }));
    await userEvent.click(saveBar().getByRole('button', { name: 'Discard changes' }));

    const dialog = within(await screen.findByRole('dialog', { name: 'Discard your changes?' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Discard changes' }));

    await waitFor(() => expect(field).toHaveValue('Biryani House'));
    expect(screen.getByRole('switch', { name: 'Tuesday: open' })).not.toBeChecked();
    expect(saveBar().getByText('Everything is saved')).toBeInTheDocument();
  });

  it('keeps the changes when the owner does not confirm "Discard changes"', async () => {
    renderRestaurant();

    const field = await rename('Something else');
    await userEvent.click(saveBar().getByRole('button', { name: 'Discard changes' }));
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Keep editing' }),
    );

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(field).toHaveValue('Something else');
  });
});

describe('OwnerRestaurantPage: opening hours', () => {
  it("copies Monday's hours to every day", async () => {
    vi.mocked(ownerService.updateRestaurant).mockResolvedValue({ ...stored, version: 4 });
    renderRestaurant();

    await userEvent.click(
      await screen.findByRole('button', { name: "Copy Monday's hours to every day" }),
    );

    expect(screen.getByRole('switch', { name: 'Sunday: open' })).toBeChecked();
    // The time fields read out their time: 'Sunday opens at 11:00 am'
    expect(screen.getByRole('button', { name: 'Sunday opens at 11:00 am' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sunday closes at 11:00 pm' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: "Copy Monday's hours to every day" })).toBeDisabled();
    expect(saveBar().getByText('You have unsaved changes')).toBeInTheDocument();

    await userEvent.click(saveBar().getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(ownerService.updateRestaurant).toHaveBeenCalled());
    const [, update] = vi.mocked(ownerService.updateRestaurant).mock.calls[0];
    expect(update.operatingHours).toHaveLength(7);
  });
});

describe('OwnerRestaurantPage: leaving with unsaved changes', () => {
  it('asks first, and stays when the owner keeps editing', async () => {
    renderRestaurant();

    const field = await rename('Something else');
    await userEvent.click(screen.getByRole('link', { name: 'Register another' }));

    const dialog = within(await screen.findByRole('dialog', { name: 'Leave without saving?' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Keep editing' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(field).toHaveValue('Something else');
  });

  it('leaves when the owner confirms', async () => {
    renderRestaurant();

    await rename('Something else');
    await userEvent.click(screen.getByRole('link', { name: 'Register another' }));
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', {
        name: 'Leave without saving',
      }),
    );

    // The test knows only the restaurant page: once it is left, nothing is shown
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Restaurant' })).not.toBeInTheDocument(),
    );
  });

  it('does not ask while nothing was changed', async () => {
    renderRestaurant();

    await screen.findByRole('textbox', { name: 'Restaurant name' });
    await userEvent.click(screen.getByRole('link', { name: 'Register another' }));

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Restaurant' })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('lets a jump to a part of the form through', async () => {
    renderRestaurant();

    await rename('Something else');
    await userEvent.click(screen.getByRole('link', { name: 'Opening hours' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Restaurant' })).toBeInTheDocument();
  });
});
