import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { LiveLocationNote } from '@/components/business/PartnerArea/LiveLocationNote';
import {
  LIVE_LOCATION_OFF,
  LiveLocationContext,
  type LiveLocationSharing,
} from '@/context/LiveLocationContext';

const renderNote = (sharing: Partial<LiveLocationSharing>, deliveryId = 55) =>
  render(
    <LiveLocationContext value={{ ...LIVE_LOCATION_OFF, deliveryId: 55, ...sharing }}>
      <LiveLocationNote deliveryId={deliveryId} />
    </LiveLocationContext>,
  );

describe('LiveLocationNote', () => {
  it('tells the partner their position reaches the customer, and when it was last sent', () => {
    renderNote({ state: 'sharing', lastSentAt: Date.now() });

    expect(screen.getByText('Sharing your live location with the customer')).toBeInTheDocument();
    expect(screen.getByText(/Sent just now\./)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('explains a blocked location and asks again on "Try again"', async () => {
    const retry = vi.fn();
    renderNote({ state: 'denied', retry });

    expect(screen.getByText('Location access is blocked')).toBeInTheDocument();
    expect(screen.getByText(/customer cannot see you on the map/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('says why a page without https cannot share the position', () => {
    renderNote({ state: 'insecure' });

    expect(screen.getByText('Location needs a secure (https) connection')).toBeInTheDocument();
  });

  it('shows nothing while nothing is shared, nor under another delivery', () => {
    const { container, unmount } = renderNote({ state: 'off', deliveryId: null });
    expect(container).toBeEmptyDOMElement();
    unmount();

    expect(renderNote({ state: 'sharing' }, 77).container).toBeEmptyDOMElement();
  });
});
