import { Store, TicketPercent } from 'lucide-react';
import { useMemo, type ReactNode } from 'react';
import { Link } from 'react-router';

import {
  CouponCenter,
  OfferSectionHeading,
  OffersHero,
  SavingsBanner,
  SeasonalPicks,
  type OfferSectionLink,
} from '@/components/business/Offers';
import { RestaurantCard } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { buttonStyles } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { useCart } from '@/hooks/api/cartHooks';
import { useOffers } from '@/hooks/api/couponHooks';
import { useRestaurantRatings } from '@/hooks/api/ratingHooks';
import { useStoreRestaurants } from '@/hooks/api/storefrontHooks';
import { useNow } from '@/hooks/useNow';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import type { Offer } from '@/types/shopping';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { soonestEnding } from '@/utils/offerUtils';
import { ratingOf } from '@/utils/ratingUtils';

const RESTAURANTS_SHOWN = 4;

const SECTION = {
  coupons: { id: 'all-coupons', label: 'All coupons' },
  seasonal: { id: 'seasonal-picks', label: 'Seasonal picks' },
  restaurants: { id: 'restaurant-deals', label: 'Restaurants' },
} as const;

/**
 * "Offers": the coupon ending soonest up front, then every coupon usable today as a ticket (with tabs: where it can
 * be used, ending soon), seasonal picks, restaurants to use them at, and a last nudge to order. With dishes in the
 * cart, that restaurant's own coupons are listed too and every coupon can be applied straight to the cart.
 */
export default function OffersPage() {
  const customer = useAppSelector(selectCustomer);
  const cart = useCart();
  const cartHasItems = Boolean(cart.data?.items.length);
  const offers = useOffers(cartHasItems && cart.data ? cart.data.restaurantId : null);
  // Which offer ends soonest only needs checking now and then; the countdowns tick by themselves
  const now = useNow(60_000);

  const all = useMemo(() => offers.data ?? [], [offers.data]);
  const spotlight = useMemo(() => soonestEnding(all, now, 1)[0], [all, now]);

  const sections: OfferSectionLink[] = [
    ...(all.length > 0 ? [SECTION.coupons] : []),
    SECTION.seasonal,
    SECTION.restaurants,
  ];

  return (
    <div className="flex flex-col gap-14">
      <OffersHero
        couponCount={offers.data?.length}
        spotlight={spotlight}
        cartHasItems={cartHasItems}
        sections={sections}
      />

      <Section
        id={SECTION.coupons.id}
        title="All coupons"
        subtitle="Every coupon you can use today. Copy a code, or apply it to your cart."
      >
        <AsyncContent
          isLoading={offers.isPending}
          error={offers.error}
          isEmpty={all.length === 0}
          errorTitle={ERROR_MESSAGES.OFFERS_LOAD_FAILED}
          onRetry={() => void offers.refetch()}
          isRetrying={offers.isFetching}
          loading={
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 3 }, (_, index) => (
                <Skeleton key={index} className="h-96 rounded-3xl bg-surface" />
              ))}
            </div>
          }
          empty={
            <div className="rounded-3xl bg-surface shadow-card">
              <EmptyState
                icon={TicketPercent}
                title="No coupons today"
                message="New offers appear here as soon as they start. The seasonal picks below are always worth a look."
                action={
                  <Link to={CUSTOMER_ROUTES.restaurants} className={buttonStyles('outline', 'sm')}>
                    <Store aria-hidden="true" className="size-4" />
                    Browse restaurants
                  </Link>
                }
              />
            </div>
          }
        >
          <CouponCenter offers={all} cartHasItems={cartHasItems} signedIn={customer !== null} />
        </AsyncContent>
      </Section>

      <Section
        id={SECTION.seasonal.id}
        title="Seasonal picks"
        subtitle="What people order most this time of year."
      >
        <SeasonalPicks />
      </Section>

      <RestaurantDeals offers={all} />

      <SavingsBanner signedIn={customer !== null} />
    </div>
  );
}

function Section({
  id,
  title,
  subtitle,
  link,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  link?: { to: string; label: string };
  children: ReactNode;
}) {
  return (
    // scroll-mt: the hero's chips jump here, and the sticky header must not cover the heading
    <section aria-labelledby={id} className="flex scroll-mt-28 flex-col gap-5">
      <OfferSectionHeading id={id} title={title} subtitle={subtitle} link={link} />
      {children}
    </section>
  );
}

/** A first look at the restaurants; one with a coupon of its own shows it on its picture. */
function RestaurantDeals({ offers }: { offers: readonly Offer[] }) {
  const restaurants = useStoreRestaurants({}, 0, RESTAURANTS_SHOWN);
  const ratings = useRestaurantRatings(
    restaurants.data?.content.map((restaurant) => restaurant.id) ?? [],
  );
  const dealOf = (restaurantId: number) =>
    offers.find((offer) => offer.restaurantId === restaurantId)?.summary;

  return (
    <Section
      id={SECTION.restaurants.id}
      title="Restaurants to use them at"
      link={{ to: CUSTOMER_ROUTES.restaurants, label: 'See all restaurants' }}
    >
      <AsyncContent
        isLoading={restaurants.isPending}
        error={restaurants.error}
        isEmpty={restaurants.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.STORE_RESTAURANTS_LOAD_FAILED}
        onRetry={() => void restaurants.refetch()}
        loading={
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: RESTAURANTS_SHOWN }, (_, index) => (
              <Skeleton key={index} className="h-60 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={<EmptyState title="No restaurants are taking orders yet" />}
      >
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {restaurants.data?.content.map((restaurant) => (
            <RestaurantCard
              key={restaurant.id}
              id={restaurant.id}
              name={restaurant.name}
              cuisines={restaurant.cuisines}
              place={restaurant.city}
              openNow={restaurant.openNow}
              rating={ratingOf(ratings, restaurant.id)}
              deal={dealOf(restaurant.id)}
            />
          ))}
        </div>
      </AsyncContent>
    </Section>
  );
}
