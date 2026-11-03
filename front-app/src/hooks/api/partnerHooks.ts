import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { partnerService } from '@/services/partnerService';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import type { Partner } from '@/types/delivery';
import type {
  PartnerAvailabilityChoice,
  PartnerDeliveryAction,
  PartnerHistoryStatus,
  PartnerProfileUpdate,
} from '@/types/partner';
import { QUERY_KEYS } from '@/utils/queryKeys';

/**
 * While the partner is online the current delivery is asked for this often: delivery-service's assignment job offers
 * waiting deliveries every 5 seconds. Live updates usually bring a new offer sooner; this is the safety net.
 */
export const CURRENT_DELIVERY_REFRESH_MS = 5_000;

/** The profile carries the status delivery-service reports (online, on a delivery): it is read again this often. */
export const PROFILE_REFRESH_MS = 15_000;

/**
 * delivery-partner-service learns the partner's new status from delivery-service a moment after a step: the profile
 * is read again once more after this pause, so the status shown follows.
 */
const STATUS_FOLLOW_UP_MS = 2_000;

/** The signed-in partner's id (0 when nobody is signed in: queries that need it stay switched off). */
export function usePartnerId(): number {
  return useAppSelector(selectPartner)?.id ?? 0;
}

/** The partner's profile and status (created on the first call if the account is a delivery partner). */
export function usePartnerProfile() {
  const partnerId = usePartnerId();
  return useQuery({
    queryKey: QUERY_KEYS.partnerArea.me(partnerId),
    queryFn: () => partnerService.getMe(),
    enabled: partnerId > 0,
    refetchInterval: PROFILE_REFRESH_MS,
  });
}

/**
 * The delivery offered to the partner or being worked on, or null. Asked for regularly while `watching` (the partner
 * is online or on a delivery): new offers arrive by themselves.
 */
export function useCurrentDelivery(watching: boolean) {
  const partnerId = usePartnerId();
  return useQuery({
    queryKey: QUERY_KEYS.partnerArea.current(partnerId),
    queryFn: () => partnerService.getCurrentDelivery(),
    enabled: partnerId > 0,
    refetchInterval: watching ? CURRENT_DELIVERY_REFRESH_MS : false,
  });
}

export function usePartnerDeliveries(status: PartnerHistoryStatus, page: number, size: number) {
  const partnerId = usePartnerId();
  return useQuery({
    queryKey: QUERY_KEYS.partnerArea.history(partnerId, status, page, size),
    queryFn: () => partnerService.getDeliveries(status === 'ALL' ? undefined : status, page, size),
    enabled: partnerId > 0,
    placeholderData: keepPreviousData,
  });
}

export function usePartnerDelivery(deliveryId: number) {
  const partnerId = usePartnerId();
  return useQuery({
    queryKey: QUERY_KEYS.partnerArea.delivery(partnerId, deliveryId),
    queryFn: () => partnerService.getDelivery(deliveryId),
    enabled: partnerId > 0 && deliveryId > 0,
  });
}

/** After a step the partner took: their deliveries, and their status now and once more a moment later. */
function useRefreshWork() {
  const partnerId = usePartnerId();
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.partnerArea.all(partnerId) });
    window.setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.partnerArea.me(partnerId) });
    }, STATUS_FOLLOW_UP_MS);
  };
}

/** Saves vehicle and city; the answer is the profile as it is now. */
export function useUpdatePartnerProfile() {
  const partnerId = usePartnerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: PartnerProfileUpdate) => partnerService.updateProfile(update),
    onSuccess: (partner) => queryClient.setQueryData(QUERY_KEYS.partnerArea.me(partnerId), partner),
  });
}

/**
 * Online or offline. delivery-service answers with the partner's status as it decided it: that answer is shown at
 * once (the profile service's copy follows a moment later).
 */
export function useChangeAvailability() {
  const partnerId = usePartnerId();
  const queryClient = useQueryClient();
  const refreshWork = useRefreshWork();
  return useMutation({
    mutationFn: (status: PartnerAvailabilityChoice) => partnerService.changeAvailability(status),
    onSuccess: (partner: Partner) => {
      queryClient.setQueryData(QUERY_KEYS.partnerArea.me(partnerId), partner);
      void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.partnerArea.current(partnerId) });
      window.setTimeout(refreshWork, STATUS_FOLLOW_UP_MS);
    },
  });
}

interface DeliveryStep {
  deliveryId: number;
  action: PartnerDeliveryAction;
  /** decline: why (optional) */
  reason?: string;
  /** deliver: the cash collected at the door (cash on delivery), else null */
  cashCollected?: number | null;
}

/**
 * Accept, decline, pick up or deliver: afterwards everything about the partner's work is read again, also after a
 * refusal (the offer went to someone else, the order was cancelled: the screen then shows what is true now).
 */
export function usePartnerDeliveryStep() {
  const refreshWork = useRefreshWork();
  return useMutation({
    mutationFn: ({
      deliveryId,
      action,
      reason,
      cashCollected = null,
    }: DeliveryStep): Promise<unknown> => {
      switch (action) {
        case 'accept':
          return partnerService.acceptDelivery(deliveryId);
        case 'decline':
          return partnerService.declineDelivery(deliveryId, reason);
        case 'pickup':
          return partnerService.pickUpDelivery(deliveryId);
        case 'deliver':
          return partnerService.completeDelivery(deliveryId, cashCollected);
      }
    },
    onSettled: () => refreshWork(),
  });
}
