import { afterEach, describe, expect, it } from 'vitest';

import { queryKeysFor } from '@/realtime/liveInvalidation';
import { delivery } from '@/test/fixtures';
import type { LiveEvent } from '@/types/live';
import {
  dayLabelOf,
  formatCountdown,
  formatDeliveryAddress,
  greetingOf,
  journeyStepOf,
  mapsLinkOf,
  nextPartnerStep,
  OFFER_ACCEPT_SECONDS,
  offerSecondsLeft,
  onlineForLabel,
  paidOnlineLabel,
  plateTextOf,
} from '@/utils/partnerAreaUtils';
import { QUERY_KEYS } from '@/utils/queryKeys';
import {
  actingUserOf,
  areaOf,
  loadSignedInPartner,
  saveSignedInPartner,
} from '@/utils/sessionUtils';
import { safePartnerReturnTo } from '@/utils/validationUtils';

const partnerUser = {
  id: 31,
  role: 'DELIVERY_PARTNER' as const,
  fullName: 'Ravi Kumar',
  email: 'ravi.rider@example.com',
};

describe('the partner area', () => {
  afterEach(() => sessionStorage.clear());

  it('is told from the other areas by the address', () => {
    expect(areaOf('/partner')).toBe('partner');
    expect(areaOf('/partner/deliveries/55')).toBe('partner');
    // Only a whole path segment counts
    expect(areaOf('/partners')).toBe('customer');
    expect(areaOf('/owner')).toBe('owner');
    expect(areaOf('/admin/deliveries')).toBe('admin');
  });

  it('acts as the signed-in partner there, and as nobody when nobody is signed in', () => {
    const session = { adminUser: null, customer: null, owner: null, partner: partnerUser };
    expect(actingUserOf(session, 'partner')?.id).toBe(31);
    expect(actingUserOf(session, 'customer')).toBeNull();
    expect(actingUserOf({ adminUser: null, customer: null }, 'partner')).toBeNull();
  });

  it('keeps the signed-in partner for the browser tab', () => {
    saveSignedInPartner(partnerUser);
    expect(loadSignedInPartner()).toEqual(partnerUser);
    saveSignedInPartner(null);
    expect(loadSignedInPartner()).toBeNull();
  });

  it('only goes back to partner-area addresses after signing in', () => {
    expect(safePartnerReturnTo('/partner/deliveries/55')).toBe('/partner/deliveries/55');
    expect(safePartnerReturnTo('/partner')).toBe('/partner');
    expect(safePartnerReturnTo('/partner/sign-in')).toBeNull();
    expect(safePartnerReturnTo('/partner/register')).toBeNull();
    expect(safePartnerReturnTo('/owner')).toBeNull();
    expect(safePartnerReturnTo('https://evil.example.com/partner')).toBeNull();
    expect(safePartnerReturnTo(null)).toBeNull();
  });
});

describe('the partner area helpers', () => {
  it('counts down the time to accept an offer', () => {
    const offeredAt = Date.parse('2026-10-12T10:00:00Z');
    const offer = { status: 'ASSIGNED' as const, assignedAt: '2026-10-12T10:00:00Z' };
    expect(offerSecondsLeft(offer, offeredAt)).toBe(OFFER_ACCEPT_SECONDS);
    expect(offerSecondsLeft(offer, offeredAt + 55_500)).toBe(65);
    expect(offerSecondsLeft(offer, offeredAt + 10 * 60_000)).toBe(0);
    expect(offerSecondsLeft({ ...offer, status: 'ACCEPTED' }, offeredAt)).toBeNull();
    expect(formatCountdown(65)).toBe('1:05');
    expect(formatCountdown(9)).toBe('0:09');
  });

  it('writes addresses out and links them to a maps app', () => {
    const { pickupAddress } = delivery();
    expect(formatDeliveryAddress(pickupAddress)).toBe('12 MG Road, Bengaluru 560001');
    expect(mapsLinkOf(pickupAddress)).toBe(
      'https://www.google.com/maps/search/?api=1&query=12%20MG%20Road%2C%20Bengaluru%20560001',
    );
    expect(formatDeliveryAddress(null)).toBeNull();
    expect(mapsLinkOf(null)).toBeNull();
  });

  it('knows the next step and how far a delivery is', () => {
    expect(nextPartnerStep('ASSIGNED')).toBe('accept');
    expect(nextPartnerStep('ACCEPTED')).toBe('pickup');
    expect(nextPartnerStep('PICKED_UP')).toBe('deliver');
    expect(nextPartnerStep('DELIVERED')).toBeNull();
    expect(nextPartnerStep('CANCELLED')).toBeNull();
    expect(journeyStepOf('ASSIGNED')).toBe(0);
    expect(journeyStepOf('DELIVERED')).toBe(3);
    expect(journeyStepOf('PENDING')).toBe(-1);
  });

  it('greets by the time of day and says how long the partner has been online', () => {
    expect(greetingOf(7)).toBe('Good morning');
    expect(greetingOf(12)).toBe('Good afternoon');
    expect(greetingOf(19)).toBe('Good evening');
    const since = '2026-10-12T10:00:00Z';
    const start = Date.parse(since);
    expect(onlineForLabel(since, start + 30_000)).toBe('just now');
    expect(onlineForLabel(since, start + 12 * 60_000)).toBe('12 min');
    expect(onlineForLabel(since, start + 65 * 60_000)).toBe('1 h 05 min');
    expect(onlineForLabel(null, start)).toBeNull();
  });

  it('labels the days of the history and writes number plates and payments out', () => {
    const now = new Date(2026, 9, 13, 15, 0);
    expect(dayLabelOf(new Date(2026, 9, 13, 0, 5).toISOString(), now)).toBe('Today');
    expect(dayLabelOf(new Date(2026, 9, 12, 23, 55).toISOString(), now)).toBe('Yesterday');
    expect(dayLabelOf(new Date(2026, 9, 8, 11, 0).toISOString(), now)).toBe('8 Oct 2026');
    expect(plateTextOf('ka01ab1234')).toBe('KA 01 AB 1234');
    expect(plateTextOf(' KA-05 MJ 77 ')).toBe('KA 05 MJ 77');
    expect(plateTextOf('')).toBe('');
    expect(paidOnlineLabel('UPI')).toBe('Paid online (UPI)');
    expect(paidOnlineLabel('NET_BANKING')).toBe('Paid online (Net banking)');
    expect(paidOnlineLabel(null)).toBe('Paid online');
  });
});

describe('live updates in the partner area', () => {
  const event = (type: LiveEvent['type']): LiveEvent => ({
    id: 'e1',
    type,
    occurredAt: '2026-10-12T10:00:00Z',
    deliveryId: 55,
    status: 'ASSIGNED',
  });

  it('reads the partner own work and inbox again when one of their deliveries changes', () => {
    expect(queryKeysFor(event('DELIVERY_STATUS_CHANGED'), partnerUser)).toEqual([
      QUERY_KEYS.partnerArea.all(31),
      QUERY_KEYS.inboxAll,
    ]);
    expect(queryKeysFor(event('CHAT_MESSAGE'), partnerUser)).toEqual([QUERY_KEYS.chat.mineAll(31)]);
    expect(queryKeysFor(event('RESTAURANT_CHANGED'), partnerUser)).toEqual([QUERY_KEYS.inboxAll]);
  });
});
