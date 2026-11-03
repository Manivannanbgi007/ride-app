import { apiClient } from '@/api/apiClient';
import { sessionApi } from '@/api/sessionApi';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  AdminUserDetails,
  AdminUserSummary,
  UserFilters,
  ChangePasswordRequest,
  UpdateProfileRequest,
  UserProfile,
  UserRole,
} from '@/types/user';
import type { Address, AddressRequest } from '@/types/shopping';
import type { LoginResponse, RegisterRequest } from '@/types/storefront';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** user-service APIs. */
export const userService = {
  /** A new customer account (409 when the email or phone is already registered). */
  register: (request: RegisterRequest): Promise<UserProfile> =>
    apiClient.post<UserProfile>(API_ENDPOINTS.auth.register, request),

  /**
   * Signs in to one area (`role`: the kind of account it is for): an access token comes back and the browser keeps
   * the area's refresh cookie. 401 "Invalid email or password", 403 for a deactivated account, and 403 with code
   * WRONG_ACCOUNT_TYPE for an account of another kind.
   */
  login: (email: string, password: string, role?: UserRole): Promise<LoginResponse> =>
    sessionApi.login(email, password, role),

  /** The signed-in customer's addresses, the default first (at most 10). */
  getAddresses: () => apiClient.get<Address[]>(API_ENDPOINTS.users.addresses),

  /** The first address becomes the default; makeDefault moves the default to the new one. */
  addAddress: (request: AddressRequest) =>
    apiClient.post<Address>(API_ENDPOINTS.users.addresses, request),

  updateAddress: (addressId: number, request: AddressRequest) =>
    apiClient.put<Address>(API_ENDPOINTS.users.address(addressId), request),

  makeDefaultAddress: (addressId: number) =>
    apiClient.put<Address>(API_ENDPOINTS.users.defaultAddress(addressId)),

  /** Orders keep their own copy of the address, so any address can be deleted. */
  deleteAddress: (addressId: number) => apiClient.delete(API_ENDPOINTS.users.address(addressId)),

  /** Changes the signed-in user's name and phone (409 when the phone belongs to another account). */
  updateMyProfile: (request: UpdateProfileRequest) =>
    apiClient.put<UserProfile>(API_ENDPOINTS.users.me, request),

  /** 422 when the current password is wrong or the new one is the same. */
  changeMyPassword: (request: ChangePasswordRequest) =>
    apiClient.put<void>(API_ENDPOINTS.users.password, request),

  /** The acting user's own profile (name for the header greeting). */
  getMyProfile: (): Promise<UserProfile> => apiClient.get<UserProfile>(API_ENDPOINTS.users.me),

  /** Users matching the filters, one page (page 0 = the first). */
  searchUsers: (filters: UserFilters, page: number, size: number) =>
    apiClient.get<PageResponse<AdminUserSummary>>(API_ENDPOINTS.admin.users.list, {
      params: { ...filters, page, size },
    }),

  /** Any user's addresses, the default first. */
  getUserAddresses: (userId: number) =>
    apiClient.get<Address[]>(API_ENDPOINTS.admin.users.addresses(userId)),

  getUser: (userId: number) =>
    apiClient.get<AdminUserDetails>(API_ENDPOINTS.admin.users.details(userId)),

  /** Activate or deactivate (audited). An inactive user cannot log in or order. */
  changeStatus: (userId: number, activate: boolean, reason: string) =>
    apiClient.put<AdminUserDetails>(
      activate
        ? API_ENDPOINTS.admin.users.activate(userId)
        : API_ENDPOINTS.admin.users.deactivate(userId),
      { reason },
    ),

  /** Audited; the platform always keeps at least one active platform admin. */
  changeRole: (userId: number, role: UserRole, reason: string) =>
    apiClient.put<AdminUserDetails>(API_ENDPOINTS.admin.users.role(userId), { role, reason }),

  getAuditLogs: (userId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.users.auditLogs(userId), {
      params: { size: 50 },
    }),
};
