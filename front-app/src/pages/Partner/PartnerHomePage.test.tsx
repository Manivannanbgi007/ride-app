import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import PartnerHomePage from '@/pages/Partner/PartnerHomePage';
import { partnerService } from '@/services/partnerService';
import { delivery, partner } from '@/test/fixtures';
import { renderPartnerPage } from '@/test/partnerTestUtils';

vi.mock('@/services/partnerService', () => ({
  partnerService: {
    getMe: vi.fn(),
    getCurrentDelivery: vi.fn(),
    changeAvailability: vi.fn(),
    acceptDelivery: vi.fn(),
    declineDelivery: vi.fn(),
    pickUpDelivery: vi.fn(),
    completeDelivery: vi.fn(),
  },
}));

const renderHome = () => renderPartnerPage(<PartnerHomePage />, '/partner');

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(null);
});

describe('the partner home page: online and offline', () => {
  it('lets a partner with a vehicle and city go online', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'OFFLINE' }));
    vi.mocked(partnerService.changeAvailability).mockResolvedValue(
      partner({ status: 'AVAILABLE' }),
    );
    renderHome();

    expect(
      await screen.findByText('You are offline. Go online to receive delivery offers.'),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Go online' }));

    expect(partnerService.changeAvailability).toHaveBeenCalledWith('AVAILABLE');
    expect(
      await screen.findByText('You are online: deliveries in your city will be offered to you.'),
    ).toBeInTheDocument();
    // Waiting for an offer in the partner's city
    expect(await screen.findByText('Waiting for a delivery in Bengaluru…')).toBeInTheDocument();
  });

  it('asks a new partner for the vehicle and city before they can go online', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(
      partner({
        status: 'OFFLINE',
        vehicleType: null,
        vehicleNumber: null,
        city: null,
        readyToWork: false,
      }),
    );
    renderHome();

    expect(await screen.findByText('One step before your first delivery')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Add them now' })).toHaveAttribute(
      'href',
      '/partner/profile',
    );
    expect(screen.getByRole('switch', { name: 'Available for deliveries' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Go online' })).not.toBeInTheDocument();
  });

  it('greets the partner and lets them go offline with the switch', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'AVAILABLE' }));
    vi.mocked(partnerService.changeAvailability).mockResolvedValue(partner({ status: 'OFFLINE' }));
    renderHome();

    expect(
      await screen.findByRole('heading', {
        name: /^Good (morning|afternoon|evening), Ravi$/,
        level: 1,
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('You are online')).toBeInTheDocument();
    const onlineSwitch = screen.getByRole('switch', { name: 'Available for deliveries' });
    expect(onlineSwitch).toBeChecked();

    await userEvent.click(onlineSwitch);

    expect(partnerService.changeAvailability).toHaveBeenCalledWith('OFFLINE');
    expect(
      await screen.findByText('You are offline: no new deliveries will be offered to you.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Available for deliveries' })).not.toBeChecked();
  });

  it('keeps the switch on while the partner is on a delivery', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ACCEPTED' }),
    );
    renderHome();

    expect(await screen.findByText('You are on a delivery')).toBeInTheDocument();
    const onlineSwitch = screen.getByRole('switch', { name: 'Available for deliveries' });
    expect(onlineSwitch).toBeChecked();
    expect(onlineSwitch).toBeDisabled();
  });

  it('tells a suspended partner why, and has no switch for them', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(
      partner({ status: 'SUSPENDED', statusReason: 'Documents expired' }),
    );
    renderHome();

    expect(await screen.findByText('Your account is suspended')).toBeInTheDocument();
    expect(screen.getByText(/Documents expired/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Go online' })).not.toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });

  it('shows why the server refused to go online', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'OFFLINE' }));
    vi.mocked(partnerService.changeAvailability).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'Finish delivery 55 first',
        technicalMessage: 'HTTP 409',
      }),
    );
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Go online' }));

    expect(await screen.findByText('Finish delivery 55 first')).toBeInTheDocument();
  });
});

describe('the partner home page: working through a delivery', () => {
  it('shows a new offer with the time left, and accepts it', async () => {
    const offer = delivery({
      status: 'ASSIGNED',
      assignedAt: new Date().toISOString(),
      acceptedAt: null,
    });
    vi.mocked(partnerService.getMe).mockResolvedValue(
      partner({ status: 'BUSY', currentDeliveryId: 55 }),
    );
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(offer);
    vi.mocked(partnerService.acceptDelivery).mockResolvedValue(delivery({ status: 'ACCEPTED' }));
    renderHome();

    expect(await screen.findByText('New delivery offer')).toBeInTheDocument();
    expect(
      screen.getByText(/Accept within \d:\d\d, or the delivery goes to another partner\./),
    ).toBeInTheDocument();
    expect(screen.getByText('Spice Garden')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '+919800000007' })).toHaveAttribute(
      'href',
      'tel:+919800000007',
    );
    expect(
      screen.getByRole('link', { name: 'Navigate to Spice Garden (opens in a new tab)' }),
    ).toHaveAttribute('href', expect.stringContaining('12%20MG%20Road'));

    await userEvent.click(screen.getByRole('button', { name: 'Accept delivery' }));

    expect(partnerService.acceptDelivery).toHaveBeenCalledWith(55);
    expect(await screen.findByText('Accepted: head to Spice Garden.')).toBeInTheDocument();
  });

  it('declines an offer with a reason', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ASSIGNED', assignedAt: new Date().toISOString(), acceptedAt: null }),
    );
    vi.mocked(partnerService.declineDelivery).mockResolvedValue(undefined);
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Decline' }));
    const dialog = await screen.findByRole('dialog', { name: 'Decline this delivery?' });
    await userEvent.type(within(dialog).getByLabelText('Reason (optional)'), 'Flat tyre');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Decline' }));

    expect(partnerService.declineDelivery).toHaveBeenCalledWith(55, 'Flat tyre');
    expect(
      await screen.findByText('Declined: the delivery goes to another partner.'),
    ).toBeInTheDocument();
  });

  it('picks up the food once the offer is accepted', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ACCEPTED' }),
    );
    vi.mocked(partnerService.pickUpDelivery).mockResolvedValue(delivery({ status: 'PICKED_UP' }));
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'I have picked up the food' }));

    expect(partnerService.pickUpDelivery).toHaveBeenCalledWith(55);
    expect(await screen.findByText('Picked up: take it to Priya Sharma.')).toBeInTheDocument();
  });

  it('confirms the exact cash for cash on delivery', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'PICKED_UP', paymentMethod: 'CASH_ON_DELIVERY', cashToCollect: 353.95 }),
    );
    vi.mocked(partnerService.completeDelivery).mockResolvedValue(delivery({ status: 'DELIVERED' }));
    renderHome();

    expect(await screen.findByText('Cash on delivery')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mark as delivered' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cash collected?' });
    await userEvent.click(within(dialog).getByRole('button', { name: /Yes, collected/ }));

    expect(partnerService.completeDelivery).toHaveBeenCalledWith(55, 353.95);
    expect(
      await screen.findByText('Delivered. Well done! You are available for the next delivery.'),
    ).toBeInTheDocument();
  });

  it('delivers an order paid online without any cash', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'PICKED_UP' }),
    );
    vi.mocked(partnerService.completeDelivery).mockResolvedValue(delivery({ status: 'DELIVERED' }));
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Mark as delivered' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delivered?' });
    expect(within(dialog).getByText(/there is nothing to collect/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Yes, delivered' }));

    expect(partnerService.completeDelivery).toHaveBeenCalledWith(55, null);
  });

  it('explains a step that is no longer possible', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ASSIGNED', assignedAt: new Date().toISOString(), acceptedAt: null }),
    );
    vi.mocked(partnerService.acceptDelivery).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'Delivery 55 is CANCELLED and cannot be accepted now',
        technicalMessage: 'HTTP 409',
      }),
    );
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Accept delivery' }));

    expect(
      await screen.findByText('Delivery 55 is CANCELLED and cannot be accepted now'),
    ).toBeInTheDocument();
    // What is true now is read again
    expect(vi.mocked(partnerService.getCurrentDelivery).mock.calls.length).toBeGreaterThan(1);
  });
});
