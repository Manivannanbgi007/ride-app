import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { useShareLiveLocation } from '@/hooks/useShareLiveLocation';
import { partnerService } from '@/services/partnerService';
import { installFakeGeolocation, removeFakeGeolocation } from '@/test/fakeGeolocation';
import { delivery } from '@/test/fixtures';
import { signedInPartnerStore } from '@/test/partnerTestUtils';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { Delivery } from '@/types/delivery';

vi.mock('@/services/partnerService', () => ({
  partnerService: { shareLocation: vi.fn() },
}));

/** Shows what the hook says, as text a test can read. */
function Sharing({ current }: { current: Pick<Delivery, 'id' | 'status'> | null }) {
  const sharing = useShareLiveLocation(current);
  return (
    <div>
      <p>{`state: ${sharing.state}`}</p>
      <p>{`sent: ${sharing.lastSentAt ?? 'never'}`}</p>
      <button type="button" onClick={sharing.retry}>
        retry
      </button>
    </div>
  );
}

const renderSharing = (current: Pick<Delivery, 'id' | 'status'> | null) =>
  renderWithProviders(<Sharing current={current} />, { store: signedInPartnerStore() });

const MG_ROAD = { lat: 12.9716, lng: 77.5946 };
/** About 33 m north of MG_ROAD */
const A_BIT_FURTHER = { lat: 12.9719, lng: 77.5946 };

let clock = 1_000_000;
const advance = (ms: number) => {
  clock += ms;
};

beforeEach(() => {
  vi.clearAllMocks();
  clock = 1_000_000;
  vi.spyOn(Date, 'now').mockImplementation(() => clock);
  vi.mocked(partnerService.shareLocation).mockResolvedValue(undefined);
});

afterEach(() => {
  vi.restoreAllMocks();
  removeFakeGeolocation();
});

describe('useShareLiveLocation', () => {
  it('shares nothing before the partner accepts, nor without a delivery', () => {
    const phone = installFakeGeolocation();
    const { rerender } = renderSharing(delivery({ status: 'ASSIGNED' }));

    expect(screen.getByText('state: off')).toBeInTheDocument();
    rerender(<Sharing current={null} />);
    expect(screen.getByText('state: off')).toBeInTheDocument();
    expect(phone.geolocation.watchPosition).not.toHaveBeenCalled();
  });

  it('sends the first position at once, then only after a move or a quiet while', async () => {
    const phone = installFakeGeolocation();
    renderSharing(delivery({ id: 55, status: 'ACCEPTED' }));
    expect(screen.getByText('state: starting')).toBeInTheDocument();

    phone.report({ ...MG_ROAD, accuracy: 8.4, heading: 90.6 });
    expect(await screen.findByText('state: sharing')).toBeInTheDocument();
    expect(screen.getByText('sent: 1000000')).toBeInTheDocument();
    expect(partnerService.shareLocation).toHaveBeenCalledWith(55, {
      latitude: 12.9716,
      longitude: 77.5946,
      accuracyMeters: 8,
      headingDeg: 91,
    });

    // A second later, standing still: not sent
    advance(1_000);
    phone.report(MG_ROAD);
    // Four seconds later, 33 m further: sent (the phone does not know the heading: none is sent)
    advance(4_000);
    phone.report(A_BIT_FURTHER);
    expect(await screen.findByText('sent: 1005000')).toBeInTheDocument();
    expect(partnerService.shareLocation).toHaveBeenCalledTimes(2);
    expect(partnerService.shareLocation).toHaveBeenLastCalledWith(55, {
      latitude: 12.9719,
      longitude: 77.5946,
      accuracyMeters: 10,
      headingDeg: null,
    });
    // Moving again a second later: too soon
    advance(1_000);
    phone.report(MG_ROAD);
    // Ten seconds without sending: sent even without moving
    advance(10_000);
    phone.report(MG_ROAD);
    await waitFor(() => expect(partnerService.shareLocation).toHaveBeenCalledTimes(3));
  });

  it('keeps sharing after the pick-up and stops once the food is delivered', async () => {
    const phone = installFakeGeolocation();
    const { rerender } = renderSharing(delivery({ id: 55, status: 'ACCEPTED' }));
    phone.report(MG_ROAD);
    expect(await screen.findByText('state: sharing')).toBeInTheDocument();

    rerender(<Sharing current={delivery({ id: 55, status: 'PICKED_UP' })} />);
    expect(screen.getByText('state: sharing')).toBeInTheDocument();
    expect(phone.activeWatches()).toBe(1);

    rerender(<Sharing current={delivery({ id: 55, status: 'DELIVERED' })} />);
    expect(screen.getByText('state: off')).toBeInTheDocument();
    expect(phone.activeWatches()).toBe(0);
  });

  it('says when location is blocked, and asks the phone again on retry', async () => {
    const phone = installFakeGeolocation();
    renderSharing(delivery({ status: 'PICKED_UP' }));

    phone.fail(1);
    expect(await screen.findByText('state: denied')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'retry' }));
    expect(screen.getByText('state: starting')).toBeInTheDocument();
    expect(phone.geolocation.watchPosition).toHaveBeenCalledTimes(2);
    expect(phone.activeWatches()).toBe(1);
  });

  it('says when the phone cannot find its position, and recovers with the next one', async () => {
    const phone = installFakeGeolocation();
    renderSharing(delivery({ status: 'ACCEPTED' }));

    phone.fail(2);
    expect(await screen.findByText('state: unavailable')).toBeInTheDocument();
    phone.report(MG_ROAD);
    expect(await screen.findByText('state: sharing')).toBeInTheDocument();
  });

  it('asks nothing of the phone on a page without https', () => {
    const phone = installFakeGeolocation({ secure: false });
    renderSharing(delivery({ status: 'ACCEPTED' }));

    expect(screen.getByText('state: insecure')).toBeInTheDocument();
    expect(phone.geolocation.watchPosition).not.toHaveBeenCalled();
  });

  it('says when the browser has no location at all', () => {
    installFakeGeolocation();
    Reflect.deleteProperty(navigator, 'geolocation');
    renderSharing(delivery({ status: 'ACCEPTED' }));

    expect(screen.getByText('state: unsupported')).toBeInTheDocument();
  });

  it('retries a position that could not be sent, and asks again about a delivery no longer on its way', async () => {
    const phone = installFakeGeolocation();
    const { queryClient } = renderSharing(delivery({ status: 'ACCEPTED' }));
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    vi.mocked(partnerService.shareLocation).mockRejectedValueOnce(
      new ApiError({ kind: 'network', userMessage: 'offline', technicalMessage: 'Network Error' }),
    );
    phone.report(MG_ROAD);
    expect(await screen.findByText('state: retrying')).toBeInTheDocument();

    // The order was cancelled meanwhile: the pages learn it (and the hook then stops), no complaint is shown
    vi.mocked(partnerService.shareLocation).mockRejectedValueOnce(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'not on the way',
        technicalMessage: 'HTTP 409',
      }),
    );
    advance(11_000);
    phone.report(MG_ROAD);
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['partner-area', 31] }),
    );
    expect(screen.getByText('state: retrying')).toBeInTheDocument();
  });
});
