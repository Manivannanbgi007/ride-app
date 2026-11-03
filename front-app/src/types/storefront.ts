/**
 * What every visitor of the customer area can see without signing in: restaurants (restaurant-service), the menu
 * and categories (catalog-service) and search (search-service). Only ACTIVE restaurants and listed dishes appear.
 */
import type { FoodType } from '@/types/catalog';
import type { DayOfWeek } from '@/types/restaurant';
import type { UserRole } from '@/types/user';

/**
 * POST /api/v1/auth/register: a customer account, unless the partner or restaurant owner sign-up asks for a delivery
 * partner or restaurant owner one.
 */
export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  /** Left out by the customer sign-up (user-service then creates a customer) */
  role?: Extract<UserRole, 'DELIVERY_PARTNER' | 'RESTAURANT_ADMIN'>;
}

/** POST /api/v1/auth/login and /refresh: who signed in, and the access token of their session. */
export interface LoginResponse {
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
  lastLoginAt: string | null;
  /** Sent as `Authorization: Bearer ...` on API calls; kept in memory only (api/tokenStore.ts) */
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds the access token is valid (15 minutes) */
  expiresIn: number;
}

/** One restaurant of GET /api/v1/restaurants. */
export interface StoreRestaurantSummary {
  id: number;
  name: string;
  cuisines: string[];
  city: string;
  openNow: boolean;
}

export interface StoreAddress {
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  latitude: number | null;
  longitude: number | null;
}

/** One opening slot; times are 'HH:mm:ss' in the restaurant's time zone (a slot never crosses midnight). */
export interface OpeningSlot {
  dayOfWeek: DayOfWeek;
  opensAt: string;
  closesAt: string;
}

/** GET /api/v1/restaurants/{id}. */
export interface StoreRestaurant {
  id: number;
  name: string;
  description: string | null;
  cuisines: string[];
  phone: string;
  address: StoreAddress;
  timeZone: string;
  /** Sorted by day, then opening time */
  operatingHours: OpeningSlot[];
  openNow: boolean;
}

/** The restaurant list's filters (all optional; city is an exact name, cuisine a part of one). */
export interface StoreRestaurantFilters {
  search?: string;
  city?: string;
  cuisine?: string;
  /** 'name' (A-Z) or 'createdAt,desc' (newest) */
  sort?: string;
}

/** GET /api/v1/categories: the active food categories, in menu order. */
export interface StoreCategory {
  id: number;
  name: string;
  description: string | null;
  imageUrl: string | null;
}

/** GET /api/v1/restaurants/{id}/menu. Sold-out dishes are listed with available = false. */
export interface RestaurantMenu {
  restaurantId: number;
  restaurantName: string;
  sections: MenuSection[];
}

export interface MenuSection {
  categoryId: number;
  categoryName: string;
  items: MenuEntry[];
}

export interface MenuEntry {
  id: number;
  name: string;
  description: string | null;
  price: number;
  foodType: FoodType;
  imageUrl: string | null;
  available: boolean;
}

/** search-service answers (results lag a few seconds behind changes). */
export interface SearchResults<T, F> {
  query: string | null;
  totalHits: number;
  page: number;
  size: number;
  totalPages: number;
  results: T[];
  facets: F;
}

export interface FacetValue {
  value: string;
  count: number;
}

export interface RestaurantHit {
  id: number;
  name: string;
  description: string | null;
  cuisines: string[];
  city: string;
  addressLine: string;
  /** Only when searching near a place */
  distanceKm: number | null;
  /** Average of the customers' ratings (one decimal); null while nobody has rated it */
  ratingAverage?: number | null;
  /** How many ratings it is made of (0 while nobody has rated it) */
  ratingCount?: number;
}

export interface FoodHit {
  id: number;
  name: string;
  description: string | null;
  price: number;
  foodType: FoodType;
  imageUrl: string | null;
  categoryId: number;
  categoryName: string;
  restaurantId: number;
  restaurantName: string;
  restaurantCity: string;
  /** Average of the customers' ratings of this dish (one decimal); null while nobody has rated it */
  ratingAverage?: number | null;
  /** How many ratings it is made of (0 while nobody has rated it) */
  ratingCount?: number;
}

export type FoodSort = 'RELEVANCE' | 'PRICE_LOW_TO_HIGH' | 'PRICE_HIGH_TO_LOW' | 'RATING';

/** RATING: the best rated first (the most reviews first among equals); restaurants nobody has rated come last. */
export type RestaurantSort = 'RELEVANCE' | 'RATING';

export interface RestaurantSearchParams {
  q?: string;
  city?: string;
  cuisine?: string;
  /** Only restaurants rated at least this high (1 to 5); unrated ones are left out */
  minRating?: number;
  sort?: RestaurantSort;
}

export interface FoodSearchParams {
  q?: string;
  city?: string;
  categoryId?: number;
  foodType?: FoodType;
  /** Only dishes rated at least this high (1 to 5); unrated ones are left out */
  minRating?: number;
  sort?: FoodSort;
}

/** GET /api/v1/search/suggest: up to 5 of each, for the search box. */
export interface Suggestions {
  query: string;
  restaurants: Suggestion[];
  foods: Suggestion[];
  categories: Suggestion[];
}

export interface Suggestion {
  id: number;
  name: string;
  /** Restaurants: the city; dishes: the restaurant */
  detail: string | null;
}
