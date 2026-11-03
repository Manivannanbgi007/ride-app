import { MapPin, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { CategoryTiles, RestaurantCard } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { useRestaurantRatings } from '@/hooks/api/ratingHooks';
import { useStoreCategories, useStoreRestaurants } from '@/hooks/api/storefrontHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { ratingOf } from '@/utils/ratingUtils';

const RESTAURANTS_SHOWN = 8;

/** The customer area's first page: find food, browse by category, and a first look at the restaurants. */
export default function HomePage() {
  const customer = useAppSelector(selectCustomer);
  const categories = useStoreCategories();
  const restaurants = useStoreRestaurants({}, 0, RESTAURANTS_SHOWN);
  const ratings = useRestaurantRatings(
    restaurants.data?.content.map((restaurant) => restaurant.id) ?? [],
  );

  return (
    <div className="flex flex-col gap-12">
      <Hero firstName={customer?.fullName.split(' ')[0]} />

      <section aria-labelledby="home-categories" className="flex flex-col gap-5">
        <div className="flex items-end justify-between gap-4">
          <h2 id="home-categories" className="text-2xl font-bold">
            What would you like to eat?
          </h2>
          <Link
            to={CUSTOMER_ROUTES.categories}
            className="text-sm font-semibold text-brand-text hover:underline"
          >
            See all categories
          </Link>
        </div>
        <AsyncContent
          isLoading={categories.isPending}
          error={categories.error}
          errorTitle={ERROR_MESSAGES.CATEGORIES_LOAD_FAILED}
          onRetry={() => void categories.refetch()}
          loading={<Skeleton className="h-32 rounded-2xl bg-surface" />}
        >
          {categories.data && <CategoryTiles categories={categories.data} />}
        </AsyncContent>
      </section>

      <section aria-labelledby="home-restaurants" className="flex flex-col gap-5">
        <div className="flex items-end justify-between gap-4">
          <h2 id="home-restaurants" className="text-2xl font-bold">
            Restaurants
          </h2>
          <Link
            to={CUSTOMER_ROUTES.restaurants}
            className="text-sm font-semibold text-brand-text hover:underline"
          >
            See all restaurants
          </Link>
        </div>
        <AsyncContent
          isLoading={restaurants.isPending}
          error={restaurants.error}
          isEmpty={restaurants.data?.content.length === 0}
          errorTitle={ERROR_MESSAGES.STORE_RESTAURANTS_LOAD_FAILED}
          onRetry={() => void restaurants.refetch()}
          loading={
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {Array.from({ length: 4 }, (_, index) => (
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
              />
            ))}
          </div>
        </AsyncContent>
      </section>
    </div>
  );
}

/** "Hungry?": what and where, then the search (or the restaurants of a city). */
function Hero({ firstName }: { firstName?: string }) {
  const navigate = useNavigate();
  const [what, setWhat] = useState('');
  const [city, setCity] = useState('');

  const find = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = new URLSearchParams();
    if (what.trim()) {
      params.set('q', what.trim());
    }
    if (city.trim()) {
      params.set('city', city.trim());
    }
    const query = params.toString();
    const page = what.trim() ? CUSTOMER_ROUTES.search : CUSTOMER_ROUTES.restaurants;
    void navigate(query ? `${page}?${query}` : page);
  };

  return (
    <section className="relative overflow-hidden rounded-3xl bg-linear-to-br from-brand-600 via-brand-700 to-brand-900 px-6 py-12 text-white shadow-brand-lg sm:px-12 sm:py-16">
      {/* Soft shapes and a light glow behind the text: depth without a picture to download */}
      <span
        aria-hidden="true"
        className="absolute -top-24 -right-16 size-80 rounded-full bg-white/10"
      />
      <span
        aria-hidden="true"
        className="absolute -right-4 -bottom-28 size-72 rounded-full bg-lime-500/20 blur-2xl"
      />
      <span
        aria-hidden="true"
        className="absolute top-10 right-1/3 size-24 rounded-full bg-sunset-500/25 blur-xl"
      />
      <p className="relative text-sm font-semibold text-white/80">
        {firstName ? `Hello, ${firstName}!` : 'Hello!'}
      </p>
      <h1 className="relative mt-2 max-w-2xl text-4xl leading-[1.1] font-extrabold tracking-tight sm:text-5xl">
        Hungry? Good food from your city&apos;s restaurants.
      </h1>
      <form
        onSubmit={find}
        className="relative mt-8 grid max-w-3xl gap-3 rounded-2xl bg-surface p-3 text-ink shadow-[0_20px_40px_-16px_rgb(0_0_0/0.35)] sm:grid-cols-[2fr_1fr_auto]"
      >
        <label className="flex items-center gap-2 rounded-xl px-3 text-ink">
          <Search aria-hidden="true" className="size-5 text-ink-soft" />
          <span className="sr-only">Dish or restaurant</span>
          <input
            value={what}
            onChange={(event) => setWhat(event.target.value)}
            maxLength={100}
            placeholder="Biryani, pizza, a restaurant..."
            className="h-11 w-full bg-transparent text-sm placeholder:text-ink-soft focus:outline-none"
          />
        </label>
        <label className="flex items-center gap-2 rounded-xl px-3 text-ink sm:border-l sm:border-line">
          <MapPin aria-hidden="true" className="size-5 text-ink-soft" />
          <span className="sr-only">City</span>
          <input
            value={city}
            onChange={(event) => setCity(event.target.value)}
            maxLength={100}
            placeholder="City"
            className="h-11 w-full bg-transparent text-sm placeholder:text-ink-soft focus:outline-none"
          />
        </label>
        <Button type="submit">Find food</Button>
      </form>
    </section>
  );
}
