/** restaurant-service (admin). */

/**
 * Restaurant lifecycle. PENDING (submitted) -> APPROVED or REJECTED (admin review) -> ACTIVE (live) <-> INACTIVE;
 * ACTIVE or INACTIVE -> SUSPENDED (policy problem) -> ACTIVE. A REJECTED restaurant is resubmitted by its owner.
 */
export const RESTAURANT_STATUSES = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'ACTIVE',
  'INACTIVE',
  'SUSPENDED',
] as const;
export type RestaurantStatus = (typeof RESTAURANT_STATUSES)[number];

/** The admin's status actions and the statuses each one is allowed from (restaurant-service's transition map). */
export const RESTAURANT_ACTIONS = {
  approve: { to: 'APPROVED', from: ['PENDING'] },
  reject: { to: 'REJECTED', from: ['PENDING'] },
  activate: { to: 'ACTIVE', from: ['APPROVED', 'INACTIVE', 'SUSPENDED'] },
  deactivate: { to: 'INACTIVE', from: ['ACTIVE'] },
  suspend: { to: 'SUSPENDED', from: ['ACTIVE', 'INACTIVE'] },
} as const satisfies Record<string, { to: RestaurantStatus; from: readonly RestaurantStatus[] }>;
export type RestaurantAction = keyof typeof RESTAURANT_ACTIONS;

export type DocumentType =
  'FOOD_LICENSE' | 'TAX_REGISTRATION' | 'OWNER_ID_PROOF' | 'BANK_ACCOUNT_PROOF' | 'OTHER';

export type DayOfWeek =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** One row of the admin restaurant list (RestaurantSummaryResponse). */
export interface RestaurantSummary {
  id: number;
  name: string;
  ownerUserId: number;
  cuisines: string[];
  city: string;
  status: RestaurantStatus;
  statusReason: string | null;
  createdAt: string;
  statusChangedAt: string;
}

export interface RestaurantAddress {
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface OperatingHours {
  dayOfWeek: DayOfWeek;
  /** 'HH:mm:ss' in the restaurant's time zone */
  opensAt: string;
  closesAt: string;
}

export interface RestaurantDocument {
  type: DocumentType;
  url: string;
  submittedAt: string;
}

/** Everything about a restaurant (RestaurantDetailsResponse). */
export interface RestaurantDetails {
  id: number;
  ownerUserId: number;
  name: string;
  description: string | null;
  cuisines: string[];
  phone: string;
  email: string;
  address: RestaurantAddress;
  timeZone: string;
  legalBusinessName: string;
  taxRegistrationNumber: string | null;
  foodLicenseNumber: string;
  status: RestaurantStatus;
  /** Why the last status change was made (shown to the owner) */
  statusReason: string | null;
  statusChangedAt: string;
  approvedAt: string | null;
  operatingHours: OperatingHours[];
  documents: RestaurantDocument[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

/**
 * The owner as user-service reports it. When user-service cannot be reached, lookupStatus is UNAVAILABLE and the
 * other fields are null: the screen still works with less owner information.
 */
export interface RestaurantOwner {
  userId: number;
  lookupStatus: 'FOUND' | 'NOT_FOUND' | 'UNAVAILABLE';
  fullName: string | null;
  email: string | null;
  phone: string | null;
  accountStatus: string | null;
}

/** GET /api/v1/admin/restaurants/{id}: the restaurant and its owner. */
export interface AdminRestaurantDetails {
  restaurant: RestaurantDetails;
  owner: RestaurantOwner;
}

/** The restaurant list's filters and order. */
export interface RestaurantFilters {
  /** Name, legal business name or city contains */
  search?: string;
  status?: RestaurantStatus;
  /** Exact city (any letter case) */
  city?: string;
  /** 'createdAt,desc', 'createdAt,asc' (oldest waiting first), 'name,asc', 'statusChangedAt,desc' */
  sort: string;
}
