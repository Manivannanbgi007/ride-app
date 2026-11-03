import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import DeliveriesPage from '@/pages/Delivery/DeliveriesPage';
import DeliveryDetailsPage from '@/pages/Delivery/DeliveryDetailsPage';
import PartnerDetailsPage from '@/pages/Delivery/PartnerDetailsPage';
import { deliveryService } from '@/services/deliveryService';
import { delivery, deliverySummary, pageOf, partner } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/deliveryService', () => ({
  deliveryService: {
    searchDeliveries: vi.fn(),
    getDelayedDeliveries: vi.fn(),
    getSummary: vi.fn(),
    getDelivery: vi.fn(),
    reassignDelivery: vi.fn(),
    getDeliveryAuditLogs: vi.fn(),
    searchPartners: vi.fn(),
    getPartnerAvailability: vi.fn(),
    getPartner: vi.fn(),
    getPartnerDeliveries: vi.fn(),
    changePartnerStatus: vi.fn(),
    getPartnerAuditLogs: vi.fn(),
  },
}));

const renderDelivery = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/deliveries/:deliveryId" element={<DeliveryDetailsPage />} />
    </Routes>,
    { route: '/admin/deliveries/55' },
  );

const renderPartner = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/deliveries/partners/:userId" element={<PartnerDetailsPage />} />
    </Routes>,
    { route: '/admin/deliveries/partners/31' },
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(deliveryService.getSummary).mockResolvedValue({
    fromDate: '2026-10-08',
    toDate: '2026-10-08',
    byStatus: { PENDING: 1, ASSIGNED: 1, ACCEPTED: 2, PICKED_UP: 3, DELIVERED: 30, CANCELLED: 2 },
    total: 39,
    delivered: 30,
    cancelled: 2,
    averageDeliveryMinutes: 27.5,
    delayedNow: 3,
  });
  vi.mocked(deliveryService.searchDeliveries).mockResolvedValue(pageOf([deliverySummary()]));
  vi.mocked(deliveryService.getDeliveryAuditLogs).mockResolvedValue(pageOf([]));
  vi.mocked(deliveryService.getPartnerAuditLogs).mockResolvedValue(pageOf([]));
  vi.mocked(deliveryService.getPartnerDeliveries).mockResolvedValue(pageOf([]));
  vi.mocked(deliveryService.getDelivery).mockResolvedValue(delivery());
  vi.mocked(deliveryService.searchPartners).mockResolvedValue(
    pageOf([
      partner({ userId: 32, fullName: 'Asha Nair' }),
      partner({ userId: 33, fullName: 'Kiran Rao', city: 'Mysuru' }),
    ]),
  );
});

describe('DeliveriesPage', () => {
  it("shows the period's figures and the deliveries, and filters them", async () => {
    renderWithProviders(<DeliveriesPage />);

    expect(await screen.findByText('27.5')).toBeInTheDocument();
    expect(screen.getByText('39')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: '#55' })).toBeInTheDocument();
    // 12 minutes since the food was ready, partner on the way: not late yet
    expect(screen.getByText('12 min')).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Status'), 'Partner on the way');
    expect(deliveryService.searchDeliveries).toHaveBeenLastCalledWith(
      { status: 'ACCEPTED' },
      0,
      20,
    );
  });

  it('opens the late deliveries and the partners from their tabs', async () => {
    vi.mocked(deliveryService.getDelayedDeliveries).mockResolvedValue(
      pageOf([
        deliverySummary({
          id: 60,
          status: 'PENDING',
          partnerUserId: null,
          partnerName: null,
          minutesSinceReady: 25,
        }),
      ]),
    );
    vi.mocked(deliveryService.getPartnerAvailability).mockResolvedValue({
      byStatus: { OFFLINE: 4, AVAILABLE: 6, BUSY: 5, SUSPENDED: 1 },
      total: 16,
    });
    renderWithProviders(<DeliveriesPage />);

    await userEvent.click(await screen.findByRole('tab', { name: 'Delayed now (3)' }));
    expect(await screen.findByRole('link', { name: '#60' })).toBeInTheDocument();
    expect(screen.getByText('Nobody yet')).toBeInTheDocument();
    expect(screen.getByText('25 min')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Delivery partners' }));
    expect(await screen.findByText('Asha Nair')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
    expect(deliveryService.searchPartners).toHaveBeenLastCalledWith({}, 0, 20);
  });
});

describe('DeliveryDetailsPage reassign', () => {
  it("offers the pickup city's available partners and gives the delivery to one, with a reason", async () => {
    vi.mocked(deliveryService.reassignDelivery).mockResolvedValue(
      delivery({ status: 'ASSIGNED', partnerUserId: 32, partnerName: 'Asha Nair' }),
    );
    renderDelivery();

    await userEvent.click(await screen.findByRole('button', { name: 'Reassign' }));
    const dialog = screen.getByRole('dialog', { name: 'Reassign delivery #55' });
    expect(deliveryService.searchPartners).toHaveBeenCalledWith(
      { status: 'AVAILABLE', city: 'Bengaluru' },
      0,
      50,
    );
    await chooseOption(await within(dialog).findByLabelText('Give it to'), 'Asha Nair (Bengaluru)');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));
    expect(await within(dialog).findByText(/Please give a reason/)).toBeInTheDocument();
    expect(deliveryService.reassignDelivery).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Partner has a flat tyre');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));

    expect(deliveryService.reassignDelivery).toHaveBeenCalledWith(
      55,
      32,
      'Partner has a flat tyre',
    );
    expect(await screen.findByText('Delivery #55 was offered to Asha Nair.')).toBeInTheDocument();
  });

  it('puts a delivery back in the queue when no partner is chosen', async () => {
    vi.mocked(deliveryService.reassignDelivery).mockResolvedValue(
      delivery({ status: 'PENDING', partnerUserId: null, partnerName: null }),
    );
    renderDelivery();

    await userEvent.click(await screen.findByRole('button', { name: 'Reassign' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Partner not answering');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));

    expect(deliveryService.reassignDelivery).toHaveBeenCalledWith(
      55,
      null,
      'Partner not answering',
    );
    expect(await screen.findByText('Delivery #55 is back in the queue.')).toBeInTheDocument();
  });

  it('asks for a partner when the delivery is already waiting in the queue', async () => {
    vi.mocked(deliveryService.getDelivery).mockResolvedValue(
      delivery({ status: 'PENDING', partnerUserId: null, partnerName: null }),
    );
    renderDelivery();

    await userEvent.click(await screen.findByRole('button', { name: 'Reassign' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Speed it up');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));

    expect(await within(dialog).findByText(/already waiting for a partner/)).toBeInTheDocument();
    expect(deliveryService.reassignDelivery).not.toHaveBeenCalled();
  });

  it('does not offer to reassign once the food is picked up', async () => {
    vi.mocked(deliveryService.getDelivery).mockResolvedValue(delivery({ status: 'PICKED_UP' }));
    renderDelivery();

    expect(await screen.findByRole('button', { name: 'Reassign' })).toBeDisabled();
    expect(screen.getByText('The partner already has the food.')).toBeInTheDocument();
  });
});

describe('PartnerDetailsPage', () => {
  it('does not suspend a partner on a delivery, and links to that delivery', async () => {
    vi.mocked(deliveryService.getPartner).mockResolvedValue(
      partner({ status: 'BUSY', currentDeliveryId: 55 }),
    );
    renderPartner();

    expect(await screen.findByRole('button', { name: 'Suspend' })).toBeDisabled();
    expect(screen.getAllByRole('link', { name: '#55' })[0]).toHaveAttribute(
      'href',
      '/admin/deliveries/55',
    );
  });

  it('reactivates a suspended partner with a reason', async () => {
    vi.mocked(deliveryService.getPartner).mockResolvedValue(
      partner({ status: 'SUSPENDED', statusReason: 'Customer complaints' }),
    );
    vi.mocked(deliveryService.changePartnerStatus).mockResolvedValue(
      partner({ status: 'OFFLINE' }),
    );
    renderPartner();

    expect(await screen.findByText('Customer complaints')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reactivate' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Training done');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reactivate' }));

    expect(deliveryService.changePartnerStatus).toHaveBeenCalledWith(31, true, 'Training done');
    expect(await screen.findByText('Ravi Kumar was reactivated.')).toBeInTheDocument();
  });
});
