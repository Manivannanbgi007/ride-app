import { useLocation } from 'react-router';

import { useAppSelector } from '@/store/hooks';
import {
  selectAdminUser,
  selectCustomer,
  selectOwner,
  selectPartner,
} from '@/store/slices/sessionSlice';
import type { ActingUser } from '@/types/user';
import { actingUserOf, areaOf } from '@/utils/sessionUtils';

/**
 * Who the current page acts as: the signed-in admin in the admin area, the signed-in owner in the owner area, the
 * signed-in delivery partner in the partner area, the signed-in customer (or nobody) everywhere else. The API client
 * makes the same choice for every request (api/axiosInstance.ts).
 */
export function useActingUser(): ActingUser | null {
  const { pathname } = useLocation();
  const adminUser = useAppSelector(selectAdminUser);
  const customer = useAppSelector(selectCustomer);
  const owner = useAppSelector(selectOwner);
  const partner = useAppSelector(selectPartner);
  return actingUserOf({ adminUser, customer, owner, partner }, areaOf(pathname));
}
