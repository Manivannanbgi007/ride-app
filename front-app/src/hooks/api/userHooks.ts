import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { userService } from '@/services/userService';
import { useActingUser } from '@/hooks/useActingUser';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { customerSignedIn, selectCustomer } from '@/store/slices/sessionSlice';
import type {
  AdminUserDetails,
  ChangePasswordRequest,
  UpdateProfileRequest,
  UserFilters,
  UserRole,
} from '@/types/user';
import { saveSignedInCustomer } from '@/utils/sessionUtils';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** The acting user's profile; not asked for when the application acts as nobody. */
export function useCurrentUser() {
  const actingUser = useActingUser();
  return useQuery({
    queryKey: QUERY_KEYS.currentUser(actingUser?.id ?? 0),
    queryFn: () => userService.getMyProfile(),
    enabled: actingUser !== null,
    // A name rarely changes: no need to ask again while the app is open
    staleTime: Infinity,
  });
}

/** One page of users. While the next page loads, the current one stays on screen. */
export function useUsers(filters: UserFilters, page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.users.list(filters, page, size),
    queryFn: () => userService.searchUsers(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useUser(userId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.users.details(userId),
    queryFn: () => userService.getUser(userId),
  });
}

/** A user's addresses (default first) for the admin; the profile card treats them as optional extras. */
export function useUserAddresses(userId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.users.addresses(userId),
    queryFn: () => userService.getUserAddresses(userId),
  });
}

export function useUserAuditLogs(userId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.users.auditLogs(userId),
    queryFn: () => userService.getAuditLogs(userId),
  });
}

/** After a change: show the new user at once, then reload lists and the history. */
function useUserChanged() {
  const queryClient = useQueryClient();
  return async (user: AdminUserDetails) => {
    queryClient.setQueryData(QUERY_KEYS.users.details(user.id), user);
    await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.users.all });
  };
}

export function useChangeUserStatus() {
  const onSuccess = useUserChanged();
  return useMutation({
    mutationFn: ({
      userId,
      activate,
      reason,
    }: {
      userId: number;
      activate: boolean;
      reason: string;
    }) => userService.changeStatus(userId, activate, reason),
    onSuccess,
  });
}

export function useChangeUserRole() {
  const onSuccess = useUserChanged();
  return useMutation({
    mutationFn: ({ userId, role, reason }: { userId: number; role: UserRole; reason: string }) =>
      userService.changeRole(userId, role, reason),
    onSuccess,
  });
}

/**
 * The signed-in customer changes their name or phone. The new profile is cached, and the name the customer area
 * shows (kept with the sign-in) follows.
 */
export function useUpdateMyProfile() {
  const queryClient = useQueryClient();
  const dispatch = useAppDispatch();
  const customer = useAppSelector(selectCustomer);
  return useMutation({
    mutationFn: (request: UpdateProfileRequest) => userService.updateMyProfile(request),
    onSuccess: (profile) => {
      queryClient.setQueryData(QUERY_KEYS.currentUser(profile.id), profile);
      if (customer?.id === profile.id) {
        const updated = { ...customer, fullName: profile.fullName };
        saveSignedInCustomer(updated);
        dispatch(customerSignedIn(updated));
      }
    },
  });
}

export function useChangeMyPassword() {
  return useMutation({
    mutationFn: (request: ChangePasswordRequest) => userService.changeMyPassword(request),
  });
}
