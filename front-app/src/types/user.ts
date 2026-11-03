/** The roles of user-service (Role enum). */
export const USER_ROLES = [
  'CUSTOMER',
  'RESTAURANT_ADMIN',
  'DELIVERY_PARTNER',
  'PLATFORM_ADMIN',
] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const USER_STATUSES = ['ACTIVE', 'INACTIVE'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

/** GET /api/v1/users/me (user-service UserResponse). */
export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  lastLoginAt: string | null;
}

/** One row of the admin user list (AdminUserSummaryResponse). */
export interface AdminUserSummary {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  lastLoginAt: string | null;
}

/** A user for the admin screen (AdminUserDetailsResponse). */
export interface AdminUserDetails extends AdminUserSummary {
  updatedAt: string;
  addressCount: number;
}

/** The customer list's filters and order. */
export interface UserFilters {
  /** Name, email or phone contains (case-insensitive) */
  search?: string;
  role?: UserRole;
  status?: UserStatus;
  /** 'createdAt,desc', 'fullName,asc', ... (fields user-service allows) */
  sort: string;
}

/**
 * Who the application acts as: the person signed in to the open area. Only their access token says so to the APIs.
 */
export interface ActingUser {
  id: number;
  role: UserRole;
}

/** The admin the admin area acts as: their name and e-mail are known once they have signed in. */
export interface AdminSession extends ActingUser {
  fullName?: string;
  email?: string;
}

/** The customer who signed in (Sign in / Register), with what the customer area shows of them. */
export interface SignedInUser extends ActingUser {
  fullName: string;
  email: string;
}

/** PUT /api/v1/users/me: the email cannot be changed; an empty phone removes it. */
export interface UpdateProfileRequest {
  fullName: string;
  phone: string | null;
}

/** PUT /api/v1/users/me/password (204). */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
