import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useLiveStatus } from '@/realtime/liveStatusContext';
import { deliveryService } from '@/services/deliveryService';
import type { DeliveryFilters, PartnerFilters } from '@/types/delivery';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** Late deliveries change minute by minute: these lists are refreshed while open. */
const LIVE_REFRESH_MS = 60_000;

export function useDeliveries(
  filters: DeliveryFilters,
  page: number,
  size: number,
  enabled = true,
) {
  return useQuery({
    queryKey: QUERY_KEYS.deliveries.list(filters, page, size),
    queryFn: () => deliveryService.searchDeliveries(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useDelayedDeliveries(page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.deliveries.delayed(page),
    queryFn: () => deliveryService.getDelayedDeliveries(page, size),
    placeholderData: keepPreviousData,
    refetchInterval: LIVE_REFRESH_MS,
  });
}

export function useDeliverySummary(fromDate?: string, toDate?: string, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.deliveries.summary(fromDate, toDate),
    queryFn: () => deliveryService.getSummary(fromDate, toDate),
    placeholderData: keepPreviousData,
    refetchInterval: LIVE_REFRESH_MS,
    enabled,
  });
}

export function useDelivery(deliveryId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.deliveries.details(deliveryId),
    queryFn: () => deliveryService.getDelivery(deliveryId),
  });
}

export function useDeliveryAuditLogs(deliveryId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.deliveries.auditLogs(deliveryId),
    queryFn: () => deliveryService.getDeliveryAuditLogs(deliveryId),
  });
}

/** After a reassignment both the deliveries and the partners change (the old one is free, the new one busy). */
export function useReassignDelivery() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      deliveryId,
      partnerUserId,
      reason,
    }: {
      deliveryId: number;
      partnerUserId: number | null;
      reason: string;
    }) => deliveryService.reassignDelivery(deliveryId, partnerUserId, reason),
    onSuccess: async (delivery) => {
      queryClient.setQueryData(QUERY_KEYS.deliveries.details(delivery.id), delivery);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.deliveries.all }),
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.partners.all }),
      ]);
    },
  });
}

export function usePartners(filters: PartnerFilters, page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.partners.list(filters, page, size),
    queryFn: () => deliveryService.searchPartners(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function usePartnerAvailability() {
  return useQuery({
    queryKey: QUERY_KEYS.partners.availability,
    queryFn: () => deliveryService.getPartnerAvailability(),
    refetchInterval: LIVE_REFRESH_MS,
  });
}

export function usePartner(userId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.partners.details(userId),
    queryFn: () => deliveryService.getPartner(userId),
  });
}

export function usePartnerDeliveries(userId: number, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.partners.deliveries(userId, page),
    queryFn: () => deliveryService.getPartnerDeliveries(userId, page, size),
    placeholderData: keepPreviousData,
  });
}

export function usePartnerAuditLogs(userId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.partners.auditLogs(userId),
    queryFn: () => deliveryService.getPartnerAuditLogs(userId),
  });
}

export function useChangePartnerStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      userId,
      activate,
      reason,
    }: {
      userId: number;
      activate: boolean;
      reason: string;
    }) => deliveryService.changePartnerStatus(userId, activate, reason),
    onSuccess: async (partner) => {
      queryClient.setQueryData(QUERY_KEYS.partners.details(partner.userId), partner);
      await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.partners.all });
    },
  });
}

/** The dashboard's live delivery card asks a little more often than the lists do. */
const LIVE_BOARD_REFRESH_MS = 30_000;

/** While the live connection is open, changes arrive by themselves: the timer is only a safety net. */
const LIVE_BOARD_SAFETY_REFRESH_MS = 5 * 60_000;

/** How many of the newest deliveries the live card lists. */
const LIVE_BOARD_ROWS = 5;

/**
 * What the dashboard's "Live Delivery Tracking" shows, kept fresh while the page is open: today's deliveries by
 * stage (and how many are late now), the partners by availability, and the newest deliveries. They share their
 * cache with the delivery pages (same keys), so opening those shows the same data at once.
 */
export function useLiveDeliveryBoard() {
  const refetchInterval =
    useLiveStatus() === 'open' ? LIVE_BOARD_SAFETY_REFRESH_MS : LIVE_BOARD_REFRESH_MS;
  const summary = useQuery({
    queryKey: QUERY_KEYS.deliveries.summary(undefined, undefined),
    queryFn: () => deliveryService.getSummary(),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const latest = useQuery({
    queryKey: QUERY_KEYS.deliveries.list({}, 0, LIVE_BOARD_ROWS),
    queryFn: () => deliveryService.searchDeliveries({}, 0, LIVE_BOARD_ROWS),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const partners = useQuery({
    queryKey: QUERY_KEYS.partners.availability,
    queryFn: () => deliveryService.getPartnerAvailability(),
    refetchInterval,
  });
  return { summary, latest, partners };
}
