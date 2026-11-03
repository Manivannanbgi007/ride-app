import type { Offer } from '@/types/shopping';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';

/** Which orders an offer takes: "Min. order ₹299.00", or "Any order". */
export function offerMinimumLabel(offer: Pick<Offer, 'minOrderAmount'>): string {
  return offer.minOrderAmount > 0
    ? `Min. order ${formatCurrency(offer.minOrderAmount, DEFAULT_CURRENCY)}`
    : 'Any order';
}

/** Where an offer can be used: at every restaurant, or only at the restaurant that runs it. */
export function offerScopeLabel(offer: Pick<Offer, 'restaurantId'>): string {
  return offer.restaurantId === null ? 'All restaurants' : 'Restaurant special';
}

/** Whether an offer ends within a week (it gets a countdown and shows under "Ending soon"). */
export function endsWithinWeek(offer: Pick<Offer, 'validUntil'>, now: number): boolean {
  const left = offerEndsAt(offer.validUntil) - now;
  return left > 0 && left < 7 * 86_400_000;
}

/** Pictures for offers that name no dish: one per offer, always the same for the same code */
const FALLBACK_IMAGES = [
  categoryImageOf('pizza'),
  categoryImageOf('burgers'),
  categoryImageOf('north indian'),
  categoryImageOf('biryani'),
  categoryImageOf('desserts'),
  categoryImageOf('chinese'),
].filter((image): image is string => image !== null);

/** Words in an offer's text that point to a food picture (most specific first) */
const OFFER_KEYWORDS: readonly (readonly [string, string])[] = [
  ['south indian', 'south indian'],
  ['biryani', 'biryani'],
  ['indian', 'north indian'],
  ['curry', 'north indian'],
  ['pizza', 'pizza'],
  ['italian', 'pizza'],
  ['burger', 'burgers'],
  ['chinese', 'chinese'],
  ['noodle', 'chinese'],
  ['dessert', 'desserts'],
  ['sweet', 'desserts'],
  ['drink', 'beverages'],
  ['beverage', 'beverages'],
];

function hashOf(text: string): number {
  let hash = 0;
  for (const character of text) {
    hash = (hash * 31 + character.charCodeAt(0)) | 0;
  }
  return Math.abs(hash);
}

/** The picture of an offer: the food its text mentions, otherwise one chosen by its code. */
export function offerImageOf(offer: Offer): string | null {
  const text = `${offer.summary} ${offer.description ?? ''}`.toLowerCase();
  const match = OFFER_KEYWORDS.find(([keyword]) => text.includes(keyword));
  if (match) {
    return categoryImageOf(match[1]);
  }
  return FALLBACK_IMAGES.length > 0
    ? FALLBACK_IMAGES[hashOf(offer.code) % FALLBACK_IMAGES.length]
    : null;
}

/** An offer can be used until the end of its last day, in the customer's time zone. */
export function offerEndsAt(validUntil: string): number {
  const [year, month, day] = validUntil.split('-').map(Number);
  return new Date(year, month - 1, day, 23, 59, 59, 999).getTime();
}

/** "04:12:09" under a day, "2d 04:12:09" above; "00:00:00" once over. */
export function formatCountdown(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const days = Math.floor(totalSeconds / 86_400);
  const clock = [
    Math.floor((totalSeconds % 86_400) / 3600),
    Math.floor((totalSeconds % 3600) / 60),
    totalSeconds % 60,
  ]
    .map((part) => String(part).padStart(2, '0'))
    .join(':');
  return days > 0 ? `${days}d ${clock}` : clock;
}

/** The offers still running, the soonest to end first (the "Limited time offers" section). */
export function soonestEnding(offers: readonly Offer[], now: number, count: number): Offer[] {
  return offers
    .filter((offer) => offerEndsAt(offer.validUntil) > now)
    .sort((first, second) => offerEndsAt(first.validUntil) - offerEndsAt(second.validUntil))
    .slice(0, count);
}

export type OfferUrgency = 'today' | 'soon' | 'limited';

/** How pressing an offer is: it ends within a day, within a week, or later. */
export function urgencyOf(millisecondsLeft: number): OfferUrgency {
  if (millisecondsLeft < 86_400_000) {
    return 'today';
  }
  return millisecondsLeft < 7 * 86_400_000 ? 'soon' : 'limited';
}
