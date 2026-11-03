import { apiClient } from '@/api/apiClient';
import { liveTrackingMock } from '@/services/mock/liveTrackingMock';
import type { LiveTracking } from '@/types/liveTracking';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';
import { env } from '@/utils/env';

/**
 * The live map of an order: where the delivery partner is right now.
 *
 * delivery-service answers GET /api/v1/delivery/orders/{orderId}/live with the partner's latest GPS position from their
 * phone (sent while they ride: hooks/useShareLiveLocation) and the restaurant and the drop-off. For a demonstration
 * without a phone on the road, VITE_FEATURE_FAKE_LIVE_TRACKING=true simulates a ride along the order's real delivery
 * instead (services/mock/liveTrackingMock.ts), labelled as simulated on the page. Later the WebSocket (realtime/) can
 * push each new position instead of the page asking every few seconds.
 */
export const liveTrackingService = {
  getLiveTracking: (orderId: number): Promise<LiveTracking> =>
    env.features.fakeLiveTracking
      ? liveTrackingMock.getLiveTracking(orderId)
      : apiClient.get<LiveTracking>(API_ENDPOINTS.delivery.liveTracking(orderId)),
};
