import { Store, TicketPercent } from 'lucide-react';

import { CopyCode, OfferDeadline, UseOffer } from '@/components/business/Offers';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Skeleton } from '@/components/common/Loader';
import { useCart } from '@/hooks/api/cartHooks';
import { useOffers } from '@/hooks/api/couponHooks';
import type { Offer } from '@/types/shopping';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { offerMinimumLabel, offerScopeLabel } from '@/utils/offerUtils';

import { CARD_ROW_ITEM, cardRow } from './cardRow';
import { PALETTE, type PaletteColour } from './palette';
import { ColourIcon, SectionHeading, SectionLink } from './SectionHeading';

/** How many coupons the dashboard shows; the offers page has them all. */
const SHOWN = 3;
/** Each coupon's colour, in turn. */
const OFFER_COLOURS: readonly PaletteColour[] = ['amber', 'sunset', 'violet'];

/**
 * "Offers for you": the coupons promotion-service says can be used today (with the cart's restaurant's own when
 * the cart has dishes), each as a small card: what it gives, on which orders, until when, the code to copy, and
 * "Apply" to the cart. Whether a coupon fits the order is the cart's (the server's) answer, never this page's.
 */
export function DashboardOffers() {
  const cart = useCart();
  const cartHasItems = Boolean(cart.data?.items.length);
  const offers = useOffers(cartHasItems && cart.data ? cart.data.restaurantId : null);
  const shown = offers.data?.slice(0, SHOWN) ?? [];

  return (
    <section aria-labelledby="dashboard-offers-title" className="flex flex-col gap-3">
      <SectionHeading
        id="dashboard-offers-title"
        icon={TicketPercent}
        colour="amber"
        title={
          <>
            Offers for you
            {offers.data && offers.data.length > 0 && (
              <span className="ml-2 font-medium text-ink-soft">
                · {offers.data.length} you can use today
              </span>
            )}
          </>
        }
      >
        <SectionLink to={CUSTOMER_ROUTES.offers} colour="amber">
          All offers
        </SectionLink>
      </SectionHeading>
      <AsyncContent
        isLoading={offers.isPending}
        error={offers.error}
        isEmpty={shown.length === 0}
        errorTitle={ERROR_MESSAGES.OFFERS_LOAD_FAILED}
        onRetry={() => void offers.refetch()}
        isRetrying={offers.isFetching}
        loading={
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: SHOWN }, (_, index) => (
              <Skeleton key={index} className="h-40 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={
          <p className="flex items-center gap-3 rounded-2xl bg-surface px-4 py-3 text-sm text-ink-soft shadow-card ring-1 ring-line/60">
            <TicketPercent aria-hidden="true" className="size-4 shrink-0 text-warning-text" />
            No coupons today. New offers appear here as soon as they start.
          </p>
        }
      >
        <ul aria-label="Coupons" className={cardRow(3)}>
          {shown.map((offer, index) => (
            <li key={offer.code} className={CARD_ROW_ITEM}>
              <OfferCard
                offer={offer}
                colour={OFFER_COLOURS[index % OFFER_COLOURS.length]}
                cartHasItems={cartHasItems}
              />
            </li>
          ))}
        </ul>
      </AsyncContent>
    </section>
  );
}

/** One coupon, compact: what it gives, its conditions and how long it runs, then the code and "Apply". */
function OfferCard({
  offer,
  colour,
  cartHasItems,
}: {
  offer: Offer;
  colour: PaletteColour;
  cartHasItems: boolean;
}) {
  return (
    <article
      aria-label={offer.summary}
      className="relative flex w-full flex-col gap-2.5 overflow-hidden rounded-2xl bg-surface p-3.5 shadow-card ring-1 ring-line/60"
    >
      <span
        aria-hidden="true"
        className={cn('absolute inset-x-0 top-0 h-1', PALETTE[colour].line)}
      />
      <div className="flex items-start gap-3">
        <ColourIcon icon={TicketPercent} colour={colour} />
        <div className="min-w-0">
          <p className={cn('leading-tight font-extrabold', PALETTE[colour].text)}>
            {offer.summary}
          </p>
          {offer.description && (
            <p className="line-clamp-1 text-xs text-ink-soft" title={offer.description}>
              {offer.description}
            </p>
          )}
        </div>
      </div>
      <p className="flex flex-wrap items-center gap-1.5 text-[11px]">
        <span className="rounded-full bg-page px-2 py-0.5 font-semibold text-ink ring-1 ring-line">
          {offerMinimumLabel(offer)}
        </span>
        <span className="inline-flex items-center gap-1 rounded-full bg-page px-2 py-0.5 font-semibold text-ink ring-1 ring-line">
          <Store aria-hidden="true" className="size-3" />
          {offerScopeLabel(offer)}
        </span>
        <OfferDeadline offer={offer} className="px-2 py-0.5" />
      </p>
      <div className="mt-auto flex items-center gap-2">
        <CopyCode code={offer.code} className="h-9 flex-1" />
        <UseOffer offer={offer} cartHasItems={cartHasItems} />
      </div>
    </article>
  );
}
